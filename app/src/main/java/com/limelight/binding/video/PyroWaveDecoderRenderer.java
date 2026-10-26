package com.limelight.binding.video;

import android.os.SystemClock;
import android.view.Surface;

import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.nvstream.jni.PyroWaveBridge;

/** SDR 4:2:0 baseline: Vulkan decode, CPU readback, GLES presentation. */
public final class PyroWaveDecoderRenderer extends VideoDecoderRenderer {
    interface Backend {
        long create(Surface surface, int width, int height);
        int decode(long handle, byte[] data, int length);
        void destroy(long handle);
    }

    private final Backend backend;

    public PyroWaveDecoderRenderer() {
        this(new Backend() {
            public long create(Surface surface, int width, int height) {
                return PyroWaveBridge.nativeCreate(surface, width, height);
            }
            public int decode(long handle, byte[] data, int length) {
                return PyroWaveBridge.nativeDecode(handle, data, length);
            }
            public void destroy(long handle) { PyroWaveBridge.nativeDestroy(handle); }
        });
    }

    PyroWaveDecoderRenderer(Backend backend) { this.backend = backend; }

    private Surface surface;
    private long handle;
    private volatile boolean stopping;
    private long decodedFrames;
    private long decodeTimeNs;
    private long totalTimeMs;

    public synchronized void setRenderTarget(Surface target) { surface = target; }

    @Override
    public synchronized int setup(int format, int width, int height, int redrawRate) {
        cleanup();
        stopping = false;
        decodedFrames = decodeTimeNs = totalTimeMs = 0;
        if (format != MoonBridge.VIDEO_FORMAT_PYROWAVE || surface == null || !surface.isValid()
                || width <= 0 || height <= 0 || width > 4096 || height > 4096
                || (width & 1) != 0 || (height & 1) != 0) {
            return -1;
        }
        handle = backend.create(surface, width, height);
        return handle != 0 ? 0 : -1;
    }

    @Override public void start() {}
    public void prepareForStop() { stopping = true; }
    @Override public void stop() { prepareForStop(); }

    @Override
    public synchronized void cleanup() {
        if (handle != 0) {
            backend.destroy(handle);
            handle = 0;
        }
    }

    @Override
    public synchronized int submitDecodeUnit(byte[] data, int length, int type, int frameNumber,
            int frameType, char hostLatency, long receiveTimeMs, long enqueueTimeMs) {
        if (stopping) return MoonBridge.DR_OK;
        if (handle == 0 || type != MoonBridge.BUFFER_TYPE_PICDATA
                || data == null || length <= 0 || length > data.length) {
            return MoonBridge.DR_NEED_IDR;
        }
        long begin = System.nanoTime();
        int result = backend.decode(handle, data, length);
        if (result == MoonBridge.DR_OK) {
            decodedFrames++;
            decodeTimeNs += System.nanoTime() - begin;
            totalTimeMs += Math.max(0, SystemClock.uptimeMillis() - receiveTimeMs);
        }
        return result;
    }

    // Use common-c's bounded decode queue; never block its receive thread on Vulkan/WSI.
    @Override public int getCapabilities() { return 0; }
    @Override public void setHdrMode(boolean enabled, byte[] metadata) {}
    public synchronized int getAverageDecoderLatency() {
        return decodedFrames == 0 ? 0 : (int) (decodeTimeNs / decodedFrames / 1000000);
    }
    public synchronized int getAverageEndToEndLatency() {
        return decodedFrames == 0 ? 0 : (int) (totalTimeMs / decodedFrames);
    }
}
