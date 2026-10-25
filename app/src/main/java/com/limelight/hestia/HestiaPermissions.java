package com.limelight.hestia;

import com.google.gson.JsonObject;

/** Parsed {@code GET /api/hestia/v1/client/permissions} response. */
public final class HestiaPermissions {
    private final String clientId;
    private final boolean viewStreams;
    private final boolean listApps;
    private final boolean launchApps;
    private final boolean keyboardInput;
    private final boolean mouseInput;
    private final boolean gamepadInput;
    private final boolean virtualDisplay;
    private final boolean serverCommands;
    private final boolean clipboardSync;
    private final boolean displayRecovery;

    private HestiaPermissions(String clientId, JsonObject p) {
        this.clientId = clientId;
        this.viewStreams = HestiaCapabilities.isTrue(p, "view_streams");
        this.listApps = HestiaCapabilities.isTrue(p, "list_apps");
        this.launchApps = HestiaCapabilities.isTrue(p, "launch_apps");
        this.keyboardInput = HestiaCapabilities.isTrue(p, "keyboard_input");
        this.mouseInput = HestiaCapabilities.isTrue(p, "mouse_input");
        this.gamepadInput = HestiaCapabilities.isTrue(p, "gamepad_input");
        this.virtualDisplay = HestiaCapabilities.isTrue(p, "virtual_display");
        this.serverCommands = HestiaCapabilities.isTrue(p, "server_commands");
        this.clipboardSync = HestiaCapabilities.isTrue(p, "clipboard_sync");
        this.displayRecovery = HestiaCapabilities.isTrue(p, "display_recovery");
    }

    /** Returns null unless the response is a successful, well-formed document. */
    public static HestiaPermissions parse(HestiaApiResponse response) {
        if (response == null || !response.isOk()) {
            return null;
        }
        JsonObject obj = response.jsonObject();
        if (obj == null || !HestiaCapabilities.isObject(obj, "permissions")) {
            return null;
        }
        String clientId = HestiaCapabilities.string(obj, "client_id");
        return new HestiaPermissions(clientId != null ? clientId : "",
                obj.getAsJsonObject("permissions"));
    }

    public String getClientId() { return clientId; }
    public boolean canViewStreams() { return viewStreams; }
    public boolean canListApps() { return listApps; }
    public boolean canLaunchApps() { return launchApps; }
    public boolean canUseKeyboard() { return keyboardInput; }
    public boolean canUseMouse() { return mouseInput; }
    public boolean canUseGamepad() { return gamepadInput; }
    public boolean canUseVirtualDisplay() { return virtualDisplay; }
    public boolean canRunServerCommands() { return serverCommands; }
    public boolean canSyncClipboard() { return clipboardSync; }
    public boolean canRecoverDisplay() { return displayRecovery; }
}
