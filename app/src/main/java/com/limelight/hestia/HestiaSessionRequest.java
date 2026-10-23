package com.limelight.hestia;

import com.google.gson.JsonObject;

/** Strict Android implementation of Hermes's Hestia v1 session/prepare schema. */
public final class HestiaSessionRequest {
    private static final int[] SUPPORTED_FPS = {30, 40, 45, 60, 90, 120};
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
        if (width <= 0 || width > 7680 || height <= 0 || height > 4320
                || screenRefreshRate <= 0 || bitrateKbps <= 0
                || scaleFactor < 1 || scaleFactor > 500
                || appId < 0 || clientVersion == null || clientVersion.isEmpty()) {
            return null;
        }
        boolean supported = false;
        for (int allowedFps : SUPPORTED_FPS) {
            if (fps == allowedFps) {
                supported = true;
                break;
            }
        }
        if (!supported) {
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
}
