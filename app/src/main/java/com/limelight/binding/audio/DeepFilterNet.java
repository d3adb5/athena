package com.limelight.binding.audio;

import com.limelight.LimeLog;

// DeepFilterNet3 noise suppression (neural, embedded model) on 10 ms mono frames at 48 kHz, built
// from Rikorose/DeepFilterNet by app/src/main/deepfilter. Calls hold the instance lock so release()
// cannot free the native side under the capture thread.
public class DeepFilterNet {
    public static final int FRAME_SAMPLES = 480;

    private static final boolean AVAILABLE;
    static {
        boolean loaded = false;
        try {
            System.loadLibrary("deepfilter");
            loaded = true;
        } catch (Throwable t) {
            LimeLog.warning("DeepFilterNet unavailable: " + t);
        }
        AVAILABLE = loaded;
    }

    // Mumla's defaults: the model unconstrained, no post-filter
    public static final class Params {
        // Most noise removed, in dB; 100 leaves the model unconstrained
        public float attenLimitDb = 100;
        // Post-filter strength (beta), 0 off; curbs the residual noise between words
        public float postFilterBeta = 0;
        // Local SNR in dB below which a frame counts as noise only, above which the ERB
        // stage and the deep filter stage are skipped as already clean
        public float minDbThresh = -15;
        public float maxDbErbThresh = 35;
        public float maxDbDfThresh = 35;

        @Override
        public String toString() {
            return "limit " + attenLimitDb + " dB, post-filter " + postFilterBeta +
                    ", thresholds " + minDbThresh + "/" + maxDbErbThresh + "/" + maxDbDfThresh + " dB";
        }
    }

    private long handle;

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public DeepFilterNet(Params params) {
        if (AVAILABLE) {
            handle = nativeCreate(params.attenLimitDb, params.postFilterBeta,
                    params.minDbThresh, params.maxDbErbThresh, params.maxDbDfThresh);
            if (handle != 0 && nativeFrameSize(handle) != FRAME_SAMPLES) {
                release();
            }
        }
    }

    public boolean isInitialized() {
        return handle != 0;
    }

    // Processes FRAME_SAMPLES microphone samples at offset in place
    public synchronized void process(short[] pcm, int offset) {
        if (handle != 0) {
            nativeProcess(handle, pcm, offset);
        }
    }

    public synchronized void release() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    private static native long nativeCreate(float attenLimitDb, float postFilterBeta,
                                            float minDbThresh, float maxDbErbThresh, float maxDbDfThresh);
    private static native int nativeFrameSize(long handle);
    private static native void nativeProcess(long handle, short[] frame, int offset);
    private static native void nativeDestroy(long handle);
}
