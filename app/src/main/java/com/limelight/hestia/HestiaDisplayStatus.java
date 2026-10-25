package com.limelight.hestia;

import com.google.gson.JsonObject;

/** Parsed {@code GET /api/hestia/v1/display/status} response. */
public final class HestiaDisplayStatus {
    private final boolean virtualDisplayActive;
    private final String backend;
    private final int width;
    private final int height;
    private final int fps;
    private final boolean physicalDisabledDuringStream;
    private final boolean recoveryStateExists;

    private HestiaDisplayStatus(boolean virtualDisplayActive, String backend,
            int width, int height, int fps,
            boolean physicalDisabledDuringStream, boolean recoveryStateExists) {
        this.virtualDisplayActive = virtualDisplayActive;
        this.backend = backend;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.physicalDisabledDuringStream = physicalDisabledDuringStream;
        this.recoveryStateExists = recoveryStateExists;
    }

    /** Returns null unless the response is a successful, well-formed document. */
    public static HestiaDisplayStatus parse(HestiaApiResponse response) {
        if (response == null || !response.isOk()) {
            return null;
        }
        JsonObject obj = response.jsonObject();
        if (obj == null) {
            return null;
        }

        boolean active = HestiaCapabilities.isTrue(obj, "active");
        String backend = "none";
        int width = 0;
        int height = 0;
        int fps = 0;
        if (HestiaCapabilities.isObject(obj, "virtual_display")) {
            JsonObject display = obj.getAsJsonObject("virtual_display");
            active = active || HestiaCapabilities.isTrue(display, "enabled");
            String reportedBackend = HestiaCapabilities.string(display, "backend");
            if (reportedBackend != null && !reportedBackend.isEmpty()) {
                backend = reportedBackend;
            }
            width = positive(HestiaCapabilities.integer(display, "width"));
            height = positive(HestiaCapabilities.integer(display, "height"));
            fps = positive(HestiaCapabilities.integer(display, "fps"));
        }

        boolean disabled = false;
        boolean recovery = false;
        if (HestiaCapabilities.isObject(obj, "physical_display")) {
            JsonObject physical = obj.getAsJsonObject("physical_display");
            disabled = HestiaCapabilities.isTrue(physical, "disabled_during_stream");
            recovery = HestiaCapabilities.isTrue(physical, "recovery_state_exists");
        }

        return new HestiaDisplayStatus(active, backend, width, height, fps, disabled, recovery);
    }

    private static int positive(Integer value) {
        return value != null && value > 0 ? value : 0;
    }

    public boolean isVirtualDisplayActive() { return virtualDisplayActive; }
    public String getBackend() { return backend; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getFps() { return fps; }
    /** The host's physical monitors are switched off in favour of the virtual one. */
    public boolean isPhysicalDisplayDisabled() { return physicalDisabledDuringStream || recoveryStateExists; }
}
