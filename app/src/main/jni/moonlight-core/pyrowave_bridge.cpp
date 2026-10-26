#include <jni.h>

#ifdef HESTIA_HAVE_PYROWAVE
#include <vulkan/vulkan.h>
#include <pyrowave.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <cstdint>
#include <memory>
#include <new>
#include <vector>

namespace {
constexpr int needFrame = -1;
constexpr int maxExtent = 4096;
constexpr int maxPacket = 64 * 1024 * 1024;

bool apiCompatible() {
    uint32_t major = 0, minor = 0, patch = 0;
    pyrowave_get_api_version(&major, &minor, &patch);
    return major == PYROWAVE_API_VERSION_MAJOR &&
           (major == 0 ? minor == PYROWAVE_API_VERSION_MINOR : minor >= PYROWAVE_API_VERSION_MINOR);
}

struct Decoder {
    pyrowave_device device = nullptr;
    pyrowave_decoder decoder = nullptr;
    ANativeWindow* window = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    GLuint program = 0;
    GLuint textures[3] = {};
    int width = 0, height = 0;
    std::vector<uint8_t> pixels;

    ~Decoder() {
        if (display != EGL_NO_DISPLAY) {
            if (context != EGL_NO_CONTEXT && surface != EGL_NO_SURFACE &&
                    eglMakeCurrent(display, surface, surface, context)) {
                glDeleteTextures(3, textures);
                if (program) glDeleteProgram(program);
            }
            eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
            if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
            eglTerminate(display);
        }
        if (window) ANativeWindow_release(window);
        if (decoder) pyrowave_decoder_destroy(decoder);
        if (device) pyrowave_device_destroy(device);
    }

    bool create(int w, int h) {
        if (!apiCompatible() || pyrowave_create_default_device(&device) != PYROWAVE_SUCCESS) return false;
        pyrowave_decoder_create_info info {};
        info.device = device;
        info.width = width = w;
        info.height = height = h;
        info.chroma = PYROWAVE_CHROMA_SUBSAMPLING_420;
        info.fragment_path = pyrowave_decoder_device_prefers_fragment_path(device);
        if (pyrowave_decoder_create(&info, &decoder) != PYROWAVE_SUCCESS) return false;
        pixels.resize(static_cast<size_t>(w) * h * 3 / 2);
        return true;
    }

    static GLuint shader(GLenum type, const char* text) {
        GLuint id = glCreateShader(type);
        glShaderSource(id, 1, &text, nullptr);
        glCompileShader(id);
        GLint ok = 0;
        glGetShaderiv(id, GL_COMPILE_STATUS, &ok);
        if (!ok) { glDeleteShader(id); return 0; }
        return id;
    }

    bool createPresentation() {
        display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr)) return false;
        const EGLint attributes[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
        EGLConfig config = nullptr;
        EGLint count = 0, visual = 0;
        if (!eglChooseConfig(display, attributes, &config, 1, &count) || count == 0) return false;
        if (!eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &visual)) return false;
        if (ANativeWindow_setBuffersGeometry(window, width, height, visual) != 0) return false;
        const EGLint ctxAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        context = eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttributes);
        surface = eglCreateWindowSurface(display, config, window, nullptr);
        if (context == EGL_NO_CONTEXT || surface == EGL_NO_SURFACE ||
                !eglMakeCurrent(display, surface, surface, context)) return false;
        eglSwapInterval(display, 0);
        GLuint vs = shader(GL_VERTEX_SHADER,
            "attribute vec2 pos; attribute vec2 uv; varying vec2 tc;"
            "void main(){gl_Position=vec4(pos,0.,1.);tc=uv;}");
        GLuint fs = shader(GL_FRAGMENT_SHADER,
            "precision mediump float; varying vec2 tc; uniform sampler2D yTex,uTex,vTex;"
            "void main(){float y=(texture2D(yTex,tc).r-16./255.)*(255./219.);"
            "float u=(texture2D(uTex,tc).r-128./255.)*(255./224.);"
            "float v=(texture2D(vTex,tc).r-128./255.)*(255./224.);"
            "gl_FragColor=vec4(y+1.5748*v,y-0.187324*u-0.468124*v,y+1.8556*u,1.);}");
        if (!vs || !fs) { if(vs) glDeleteShader(vs); if(fs) glDeleteShader(fs); return false; }
        program = glCreateProgram();
        glAttachShader(program, vs); glAttachShader(program, fs);
        glBindAttribLocation(program, 0, "pos"); glBindAttribLocation(program, 1, "uv");
        glLinkProgram(program);
        glDeleteShader(vs); glDeleteShader(fs);
        GLint ok = 0; glGetProgramiv(program, GL_LINK_STATUS, &ok);
        if (!ok) return false;
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "yTex"), 0);
        glUniform1i(glGetUniformLocation(program, "uTex"), 1);
        glUniform1i(glGetUniformLocation(program, "vTex"), 2);
        glGenTextures(3, textures);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        for (int i = 0; i < 3; ++i) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, i ? width/2 : width,
                i ? height/2 : height, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, nullptr);
        }
        return glGetError() == GL_NO_ERROR;
    }

    bool present() {
        // Release the context after each frame: setup, submission and cleanup
        // are called by different common-c threads.
        bool ready = context == EGL_NO_CONTEXT ? createPresentation() :
            eglMakeCurrent(display, surface, surface, context) == EGL_TRUE;
        if (!ready) {
            if (display != EGL_NO_DISPLAY)
                eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            return false;
        }
        const size_t luma = static_cast<size_t>(width) * height;
        const size_t offsets[] = {0, luma, luma + luma/4};
        for (int i = 0; i < 3; ++i) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, i ? width/2 : width,
                i ? height/2 : height, GL_LUMINANCE, GL_UNSIGNED_BYTE, pixels.data()+offsets[i]);
        }
        const GLfloat vertices[] = {-1,-1,0,1, 1,-1,1,1, -1,1,0,0, 1,1,1,0};
        EGLint w = 0, h = 0;
        eglQuerySurface(display, surface, EGL_WIDTH, &w);
        eglQuerySurface(display, surface, EGL_HEIGHT, &h);
        glViewport(0, 0, w, h);
        glUseProgram(program);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4*sizeof(GLfloat), vertices);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4*sizeof(GLfloat), vertices+2);
        glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        bool ok = glGetError() == GL_NO_ERROR && eglSwapBuffers(display, surface) == EGL_TRUE;
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return ok;
    }

    bool decode(const uint8_t* packet, size_t length) {
        pyrowave_decoder_clear(decoder);
        if (pyrowave_decoder_push_packet(decoder, packet, length) != PYROWAVE_SUCCESS ||
                !pyrowave_decoder_decode_is_ready(decoder, false)) return false;
        size_t luma = static_cast<size_t>(width) * height;
        pyrowave_cpu_buffer output {};
        output.data[0] = pixels.data();
        output.data[1] = pixels.data()+luma;
        output.data[2] = pixels.data()+luma+luma/4;
        output.row_stride_in_bytes[0] = width;
        output.row_stride_in_bytes[1] = output.row_stride_in_bytes[2] = width/2;
        output.plane_size_in_bytes[0] = luma;
        output.plane_size_in_bytes[1] = output.plane_size_in_bytes[2] = luma/4;
        output.width = width; output.height = height;
        output.format = PYROWAVE_CPU_BUFFER_FORMAT_YUV420P;
        return pyrowave_decoder_decode_cpu_buffer_synchronous(decoder, &output) == PYROWAVE_SUCCESS && present();
    }
};
}
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_PyroWaveBridge_nativeProbe(JNIEnv*, jclass) {
#ifdef HESTIA_HAVE_PYROWAVE
    try { Decoder probe; return probe.create(64, 64) ? JNI_TRUE : JNI_FALSE; }
    catch (...) { return JNI_FALSE; }
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_jni_PyroWaveBridge_nativeCreate(JNIEnv* env, jclass, jobject surface, jint width, jint height) {
#ifdef HESTIA_HAVE_PYROWAVE
    if (!surface || width <= 0 || height <= 0 || width > maxExtent || height > maxExtent ||
            (width & 1) || (height & 1)) return 0;
    try {
        std::unique_ptr<Decoder> decoder(new Decoder);
        decoder->window = ANativeWindow_fromSurface(env, surface);
        if (!decoder->window || !decoder->create(width, height) || !decoder->createPresentation()) return 0;
        eglMakeCurrent(decoder->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return reinterpret_cast<jlong>(decoder.release());
    } catch (...) { return 0; }
#else
    (void)env; (void)surface; (void)width; (void)height;
    return 0;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_PyroWaveBridge_nativeDecode(JNIEnv* env, jclass, jlong handle, jbyteArray packet, jint length) {
#ifdef HESTIA_HAVE_PYROWAVE
    if (!handle || !packet || length <= 0 || length > maxPacket || length > env->GetArrayLength(packet)) return needFrame;
    jbyte* data = env->GetByteArrayElements(packet, nullptr);
    if (!data) return needFrame;
    bool ok = false;
    try { ok = reinterpret_cast<Decoder*>(handle)->decode(reinterpret_cast<uint8_t*>(data), length); }
    catch (...) { ok = false; }
    env->ReleaseByteArrayElements(packet, data, JNI_ABORT);
    return ok ? 0 : needFrame;
#else
    (void)env; (void)handle; (void)packet; (void)length;
    return -1;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_PyroWaveBridge_nativeDestroy(JNIEnv*, jclass, jlong handle) {
#ifdef HESTIA_HAVE_PYROWAVE
    delete reinterpret_cast<Decoder*>(handle);
#else
    (void)handle;
#endif
}
