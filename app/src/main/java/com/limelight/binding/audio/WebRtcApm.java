package com.limelight.binding.audio;

import com.limelight.LimeLog;

// WebRTC audio processing (AEC3 echo canceller, noise suppressor, digital AGC) on 10 ms mono
// frames at 48 kHz, built from pulseaudio/webrtc-audio-processing by app/src/main/webrtc-apm.
// Render (playback) and capture run on different threads; every call holds the instance lock
// so release() cannot free the native side under either of them.
public class WebRtcApm {
    public static final int FRAME_SAMPLES = 480;

    private static final boolean AVAILABLE;
    static {
        boolean loaded = false;
        try {
            System.loadLibrary("webrtcapm");
            loaded = true;
        } catch (Throwable t) {
            LimeLog.warning("WebRTC audio processing unavailable: " + t);
        }
        AVAILABLE = loaded;
    }

    private long handle;
    private final short[] render = new short[FRAME_SAMPLES];
    private int renderFill;

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public WebRtcApm(boolean aec, boolean ns, boolean agc) {
        if (AVAILABLE) {
            handle = nativeCreate(aec, ns, agc);
        }
    }

    public boolean isInitialized() {
        return handle != 0;
    }

    // Interleaved playback PCM, downmixed to mono and fed as the echo reference
    public synchronized void render(short[] pcm, int channels) {
        if (handle == 0) {
            return;
        }
        for (int i = 0; i + channels <= pcm.length; i += channels) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += pcm[i + c];
            }
            render[renderFill++] = (short) (sum / channels);
            if (renderFill == FRAME_SAMPLES) {
                nativeProcessRender(handle, render);
                renderFill = 0;
            }
        }
    }

    // Processes FRAME_SAMPLES microphone samples at offset in place
    public synchronized void capture(short[] pcm, int offset) {
        if (handle != 0) {
            nativeProcessCapture(handle, pcm, offset);
        }
    }

    public synchronized void release() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    private static native long nativeCreate(boolean aec, boolean ns, boolean agc);
    private static native void nativeProcessRender(long handle, short[] frame);
    private static native void nativeProcessCapture(long handle, short[] frame, int offset);
    private static native void nativeDestroy(long handle);
}
