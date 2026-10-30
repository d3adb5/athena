package com.limelight.hestia;

import com.google.gson.JsonObject;

/**
 * Parsed {@code GET /api/hestia/v1/stream/info} response: how Hermes serves
 * the stream, as its capture-path check found it just before the stream
 * started - what it captures, the encoder and the GPU it encodes on.
 */
public final class HestiaStreamInfo {
    private final boolean ok;
    private final boolean frameSeen;
    private final String capture;
    private final String encoder;
    private final String gpu;
    private final String failure;

    private HestiaStreamInfo(boolean ok, boolean frameSeen, String capture, String encoder,
            String gpu, String failure) {
        this.ok = ok;
        this.frameSeen = frameSeen;
        this.capture = capture;
        this.encoder = encoder;
        this.gpu = gpu;
        this.failure = failure;
    }

    /**
     * Returns null unless the response is a successful document that holds a
     * check result. A host without the endpoint answers 404, which is null too.
     */
    public static HestiaStreamInfo parse(HestiaApiResponse response) {
        if (response == null || !response.isOk()) {
            return null;
        }
        JsonObject obj = response.jsonObject();
        if (obj == null || !HestiaCapabilities.isObject(obj, "readiness")) {
            return null;
        }
        JsonObject r = obj.getAsJsonObject("readiness");
        return new HestiaStreamInfo(
                HestiaCapabilities.isTrue(r, "ok"),
                HestiaCapabilities.isTrue(r, "frame_seen"),
                string(r, "capture"),
                string(r, "encoder"),
                string(r, "gpu"),
                string(r, "failure"));
    }

    private static String string(JsonObject object, String key) {
        return HestiaCapabilities.isString(object, key) ? object.get(key).getAsString() : "";
    }

    public boolean isOk() { return ok; }
    public boolean isFrameSeen() { return frameSeen; }
    public String getCapture() { return capture; }
    public String getEncoder() { return encoder; }
    public String getGpu() { return gpu; }
    public String getFailure() { return failure; }

    /**
     * The GPU as a person reads it: "AMD 1002:7550 (amdgpu)" from Hermes'
     * "/dev/dri/renderD128 (amdgpu, AMD 1002:7550)". Anything else is shown
     * as Hermes wrote it.
     */
    public static String gpuName(String gpu) {
        if (gpu == null) {
            return "";
        }
        int open = gpu.indexOf(" (");
        int comma = gpu.indexOf(", ", open);
        if (open < 0 || comma < 0 || !gpu.endsWith(")")) {
            return gpu;
        }
        String driver = gpu.substring(open + 2, comma);
        String device = gpu.substring(comma + 2, gpu.length() - 1);
        return device + " (" + driver + ")";
    }

    /** The encoder as a person reads it: "vaapi" is VAAPI, "nvenc" NVENC. */
    public static String encoderName(String encoder) {
        return encoder == null ? "" : encoder.toUpperCase(java.util.Locale.ROOT);
    }

    /** "the detached desktop via KWin screencast" begins with a capital. */
    public static String sentenceCase(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.startsWith("the ")) {
            text = text.substring(4);
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
