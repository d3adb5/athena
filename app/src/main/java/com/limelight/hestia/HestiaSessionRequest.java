package com.limelight.hestia;

import com.google.gson.JsonObject;

/** Strict Android implementation of Hermes's Hestia v1 session/prepare schema. */
public final class HestiaSessionRequest {
    /** Why a request was not built; surfaced to the user instead of a silent fallback. */
    public enum Rejection {
        NONE,
        /** Hermes v1 only accepts the frame rates it advertises. */
        FPS_UNSUPPORTED,
        /** Above the 7680x4320 host limit, or a scale factor outside 1-500%. */
        OUT_OF_RANGE,
        /** Zero/negative sizes, bitrate, app id or missing client version. */
        INVALID,
    }

    private final String json;

    private HestiaSessionRequest(String json) {
        this.json = json;
    }

    public String toJson() {
        return json;
    }

    /**
     * The server rejects unsupported modes rather than negotiating. Return
     * null and continue normal GameStream startup instead of sending invalid
     * settings or making a possibly harmful display request.
     */
    public static HestiaSessionRequest create(int width, int height, int fps,
            int bitrateKbps, int scaleFactor, boolean hdr, boolean virtualDisplay,
            int appId, int screenRefreshRate, String clientVersion) {
        if (check(width, height, fps, bitrateKbps, scaleFactor, appId,
                screenRefreshRate, clientVersion) != Rejection.NONE) {
            return null;
        }

        JsonObject client = new JsonObject();
        client.addProperty("name", "Hestia Android");
        client.addProperty("version", clientVersion);
        // Hermes v1 currently allows linux/windows/macos/steamdeck/unknown.
        // Do not misrepresent Android as Windows; use unknown until Hermes
        // explicitly adds "android" to its protocol validation.
        client.addProperty("platform", "unknown");
        client.addProperty("display_width", width);
        client.addProperty("display_height", height);
        client.addProperty("refresh_rate", screenRefreshRate);
        client.addProperty("hdr", hdr);

        JsonObject stream = new JsonObject();
        stream.addProperty("requested_width", width);
        stream.addProperty("requested_height", height);
        stream.addProperty("requested_fps", fps);
        stream.addProperty("codec", "h264"); // Protocol v1 host ignores this hint.
        stream.addProperty("bitrate_kbps", bitrateKbps);
        stream.addProperty("hdr_mode", hdr ? "hdr" : "sdr");
        stream.addProperty("scale_factor", scaleFactor);

        JsonObject display = new JsonObject();
        display.addProperty("enabled", virtualDisplay);
        display.addProperty("backend", "auto");
        display.addProperty("desktop_integration", "auto");
        display.addProperty("recover_physical_monitor", false);

        JsonObject app = new JsonObject();
        app.addProperty("id", Integer.toString(appId));
        app.addProperty("launch_mode", "normal");

        JsonObject request = new JsonObject();
        request.add("client", client);
        request.add("stream", stream);
        request.add("virtual_display", display);
        request.add("app", app);
        return new HestiaSessionRequest(request.toString());
    }

    /** Validate settings against the Hermes v1 session/prepare schema. */
    public static Rejection check(int width, int height, int fps, int bitrateKbps,
            int scaleFactor, int appId, int screenRefreshRate, String clientVersion) {
        if (width <= 0 || height <= 0 || screenRefreshRate <= 0 || bitrateKbps <= 0
                || appId < 0 || clientVersion == null || clientVersion.isEmpty()) {
            return Rejection.INVALID;
        }
        if (width > 7680 || height > 4320 || scaleFactor < 1 || scaleFactor > 500) {
            return Rejection.OUT_OF_RANGE;
        }
        for (int allowedFps : HestiaCapabilities.DEFAULT_SUPPORTED_FPS) {
            if (fps == allowedFps) {
                return Rejection.NONE;
            }
        }
        return Rejection.FPS_UNSUPPORTED;
    }

    /** Frame rates Hermes v1 accepts, for user-facing messages. */
    public static int[] supportedFps() {
        return HestiaCapabilities.DEFAULT_SUPPORTED_FPS.clone();
    }
}
