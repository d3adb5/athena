package com.limelight.hestia;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Validated, immutable discovery result for the Hermes Hestia v1 API.
 *
 * No networking is performed here. A missing or incompatible response simply
 * yields an unsupported result, so legacy GameStream hosts remain usable.
 */
public final class HestiaCapabilities {
    public static final int CLIENT_PROTOCOL = 1;

    private final boolean supported;
    private final String serverVersion;
    private final boolean virtualDisplay;
    private final boolean clipboardSync;
    private final boolean serverCommands;
    private final boolean displayRecovery;

    private HestiaCapabilities(boolean supported, String serverVersion,
            boolean virtualDisplay, boolean clipboardSync,
            boolean serverCommands, boolean displayRecovery) {
        this.supported = supported;
        this.serverVersion = serverVersion;
        this.virtualDisplay = virtualDisplay;
        this.clipboardSync = clipboardSync;
        this.serverCommands = serverCommands;
        this.displayRecovery = displayRecovery;
    }

    public static HestiaCapabilities unsupported() {
        return new HestiaCapabilities(false, "", false, false, false, false);
    }

    public static HestiaCapabilities parse(String json) {
        if (json == null || json.isEmpty()) {
            return unsupported();
        }

        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return unsupported();
            }
            JsonObject obj = element.getAsJsonObject();
            if (!isTrue(obj, "ok") || !isString(obj, "server_version")
                    || !isString(obj, "base") || !isString(obj, "server_name")
                    || !isObject(obj, "features") || !isObject(obj, "compatibility")
                    || !isObject(obj, "limits")) {
                return unsupported();
            }
            Integer version = integer(obj, "hestia_protocol");
            Integer minimum = integer(obj, "min_client_protocol");
            Integer maximum = integer(obj, "max_client_protocol");
            if (version == null || version != CLIENT_PROTOCOL
                    || minimum == null || maximum == null
                    || minimum > CLIENT_PROTOCOL || maximum < CLIENT_PROTOCOL
                    || minimum > maximum) {
                return unsupported();
            }

            JsonObject features = obj.getAsJsonObject("features");
            return new HestiaCapabilities(true, obj.get("server_version").getAsString(),
                    isTrue(features, "virtual_display"),
                    isTrue(features, "clipboard_sync"),
                    isTrue(features, "server_commands"),
                    isTrue(features, "display_recovery"));
        } catch (IllegalStateException | NumberFormatException | JsonSyntaxException ex) {
            return unsupported();
        }
    }

    private static boolean isObject(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject();
    }

    private static boolean isString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isString();
    }

    private static boolean isTrue(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isBoolean()
                && object.get(key).getAsBoolean();
    }

    private static Integer integer(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double number = object.get(key).getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            return null;
        }
        return (int) number;
    }

    public boolean isSupported() { return supported; }
    public String getServerVersion() { return serverVersion; }
    public boolean hasVirtualDisplay() { return virtualDisplay; }
    public boolean hasClipboardSync() { return clipboardSync; }
    public boolean hasServerCommands() { return serverCommands; }
    public boolean hasDisplayRecovery() { return displayRecovery; }
}
