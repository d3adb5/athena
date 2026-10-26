package com.limelight.nvstream.jni;

import android.os.Build;
import android.view.Surface;

/** Optional Vulkan codec. Native stubs keep ordinary APKs usable without its SDK. */
public final class PyroWaveBridge {
    static { System.loadLibrary("moonlight-core"); }

    private PyroWaveBridge() {}

    public static boolean isSupported() {
        // Vulkan is optional on older Android releases and on some devices.
        return Build.VERSION.SDK_INT >= 24 && nativeProbe();
    }

    public static native boolean nativeProbe();
    public static native long nativeCreate(Surface surface, int width, int height);
    public static native int nativeDecode(long handle, byte[] packet, int length);
    public static native void nativeDestroy(long handle);
}
