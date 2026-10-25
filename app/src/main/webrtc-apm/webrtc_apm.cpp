// JNI bridge to the WebRTC audio processing module (AEC3, noise suppression, AGC) from
// pulseaudio/webrtc-audio-processing v1.3. Backs com.limelight.binding.audio.WebRtcApm.
// Works on 10 ms mono frames at 48 kHz.

#include <jni.h>
#include <stdint.h>

#include "modules/audio_processing/include/audio_processing.h"

using webrtc::AudioProcessing;

static const int kRate = 48000;
static const int kFrame = kRate / 100;
static const webrtc::StreamConfig kConfig(kRate, 1);

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_limelight_binding_audio_WebRtcApm_nativeCreate(JNIEnv *, jclass, jboolean aec, jboolean ns, jboolean agc) {
    AudioProcessing *apm = webrtc::AudioProcessingBuilder().Create();
    if (apm == nullptr) {
        return 0;
    }
    AudioProcessing::Config config;
    config.echo_canceller.enabled = aec;
    config.echo_canceller.mobile_mode = false; // full AEC3, not AECM
    config.high_pass_filter.enabled = aec || ns;
    config.noise_suppression.enabled = ns;
    config.noise_suppression.level = AudioProcessing::Config::NoiseSuppression::kHigh;
    config.gain_controller1.enabled = agc;
    config.gain_controller1.mode = AudioProcessing::Config::GainController1::kAdaptiveDigital;
    // The analog controller would ignore the digital mode and ratchet the gain up for good,
    // since nothing applies its recommended level to the microphone
    config.gain_controller1.analog_gain_controller.enabled = false;
    apm->ApplyConfig(config);
    return (jlong) (intptr_t) apm;
}

// Far end: what the speaker is about to play
JNIEXPORT void JNICALL
Java_com_limelight_binding_audio_WebRtcApm_nativeProcessRender(JNIEnv *env, jclass, jlong handle, jshortArray frame) {
    int16_t buf[kFrame];
    env->GetShortArrayRegion(frame, 0, kFrame, buf);
    ((AudioProcessing *) (intptr_t) handle)->ProcessReverseStream(buf, kConfig, kConfig, buf);
}

// Near end: one microphone frame at offset, processed in place
JNIEXPORT void JNICALL
Java_com_limelight_binding_audio_WebRtcApm_nativeProcessCapture(JNIEnv *env, jclass, jlong handle, jshortArray frame, jint offset) {
    int16_t buf[kFrame];
    env->GetShortArrayRegion(frame, offset, kFrame, buf);
    ((AudioProcessing *) (intptr_t) handle)->ProcessStream(buf, kConfig, kConfig, buf);
    env->SetShortArrayRegion(frame, offset, kFrame, buf);
}

JNIEXPORT void JNICALL
Java_com_limelight_binding_audio_WebRtcApm_nativeDestroy(JNIEnv *, jclass, jlong handle) {
    delete (AudioProcessing *) (intptr_t) handle;
}

}
