package com.limelight.hestia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.Arrays;

/**
 * Validated, immutable discovery result for the Hermes Hestia v1 API.
 *
 * No networking is performed here. A missing or incompatible response simply
 * yields an unsupported result, so legacy GameStream hosts remain usable.
 *
 * A host can be recognised as Hermes ({@link #isHermes()}) without being
 * usable ({@link #isSupported()}): that is the "update Hermes or Hestia"
 * case, which the UI reports as an error instead of silently treating the
 * host as a plain GameStream server.
 */
public final class HestiaCapabilities {
    public static final int CLIENT_PROTOCOL = 1;

    /** Fallback when the host does not advertise its frame-rate list. */
    static final int[] DEFAULT_SUPPORTED_FPS = {30, 40, 45, 60, 90, 120};

    private static final HestiaCapabilities UNSUPPORTED = new HestiaCapabilities(
            false, false, "", 0, 0, 0, false, false, false, false, false,
            DEFAULT_SUPPORTED_FPS);

    private final boolean supported;
    private final boolean hermes;
    private final String serverVersion;
    private final int serverProtocol;
    private final int minClientProtocol;
    private final int maxClientProtocol;
    private final boolean virtualDisplay;
    private final boolean clipboardSync;
    private final boolean serverCommands;
    private final boolean displayRecovery;
    private final boolean gamescopeSession;
    private final int[] supportedFps;

    private HestiaCapabilities(boolean supported, boolean hermes, String serverVersion,
            int serverProtocol, int minClientProtocol, int maxClientProtocol,
            boolean virtualDisplay, boolean clipboardSync,
            boolean serverCommands, boolean displayRecovery, boolean gamescopeSession,
            int[] supportedFps) {
        this.supported = supported;
        this.hermes = hermes;
        this.serverVersion = serverVersion;
        this.serverProtocol = serverProtocol;
        this.minClientProtocol = minClientProtocol;
        this.maxClientProtocol = maxClientProtocol;
        this.virtualDisplay = virtualDisplay;
        this.clipboardSync = clipboardSync;
        this.serverCommands = serverCommands;
        this.displayRecovery = displayRecovery;
        this.gamescopeSession = gamescopeSession;
        this.supportedFps = supportedFps;
    }

    public static HestiaCapabilities unsupported() {
        return UNSUPPORTED;
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
            if (!isTrue(obj, "ok") || !isString(obj, "server_name")
                    || !"Hermes".equals(obj.get("server_name").getAsString())) {
                // Not Hermes at all: an ordinary GameStream host.
                return unsupported();
            }

            String version = isString(obj, "server_version")
                    ? obj.get("server_version").getAsString() : "";
            Integer protocol = integer(obj, "hestia_protocol");
            Integer minimum = integer(obj, "min_client_protocol");
            Integer maximum = integer(obj, "max_client_protocol");
            HestiaCapabilities incompatible = new HestiaCapabilities(false, true, version,
                    protocol != null ? protocol : 0,
                    minimum != null ? minimum : 0,
                    maximum != null ? maximum : 0,
                    false, false, false, false, false, DEFAULT_SUPPORTED_FPS);

            if (!isString(obj, "server_version") || !isString(obj, "base")
                    || !isObject(obj, "features") || !isObject(obj, "compatibility")
                    || !isObject(obj, "limits")) {
                return incompatible;
            }
            // Do not activate host-control APIs on a server impersonating the
            // protocol with the wrong compatibility identifiers.
            if (!"Apollo".equals(obj.get("base").getAsString())) {
                return incompatible;
            }
            if (protocol == null || protocol != CLIENT_PROTOCOL
                    || minimum == null || maximum == null
                    || minimum > CLIENT_PROTOCOL || maximum < CLIENT_PROTOCOL
                    || minimum > maximum) {
                return incompatible;
            }

            JsonObject features = obj.getAsJsonObject("features");
            int[] fps = parseFps(obj.getAsJsonObject("limits"));
            return new HestiaCapabilities(true, true, version,
                    protocol, minimum, maximum,
                    isTrue(features, "virtual_display"),
                    isTrue(features, "clipboard_sync"),
                    isTrue(features, "server_commands"),
                    isTrue(features, "display_recovery"),
                    isTrue(features, "gamescope_session"),
                    fps);
        } catch (IllegalStateException | NumberFormatException | JsonSyntaxException ex) {
            return unsupported();
        }
    }

    private static int[] parseFps(JsonObject limits) {
        if (!limits.has("supported_fps") || !limits.get("supported_fps").isJsonArray()) {
            return DEFAULT_SUPPORTED_FPS;
        }
        JsonArray array = limits.getAsJsonArray("supported_fps");
        int[] values = new int[array.size()];
        int count = 0;
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            double number = item.getAsDouble();
            if (number >= 1 && number <= 1000 && number == Math.rint(number)) {
                values[count++] = (int) number;
            }
        }
        if (count == 0) {
            return DEFAULT_SUPPORTED_FPS;
        }
        int[] result = Arrays.copyOf(values, count);
        Arrays.sort(result);
        return result;
    }

    static boolean isObject(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject();
    }

    static boolean isString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isString();
    }

    static boolean isBoolean(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isBoolean();
    }

    static boolean isTrue(JsonObject object, String key) {
        return isBoolean(object, key) && object.get(key).getAsBoolean();
    }

    static Integer integer(JsonObject object, String key) {
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

    static Long longValue(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double number = object.get(key).getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Long.MIN_VALUE || number > Long.MAX_VALUE) {
            return null;
        }
        return (long) number;
    }

    static String string(JsonObject object, String key) {
        return isString(object, key) ? object.get(key).getAsString() : null;
    }

    /** True when the protocol handshake succeeded and v1 extensions may be used. */
    public boolean isSupported() { return supported; }
    /** True when the host identified itself as Hermes, compatible or not. */
    public boolean isHermes() { return hermes; }
    /** True for a Hermes host whose protocol range does not include this client. */
    public boolean isIncompatibleHermes() { return hermes && !supported; }
    public String getServerVersion() { return serverVersion; }
    public int getServerProtocol() { return serverProtocol; }
    public int getMinClientProtocol() { return minClientProtocol; }
    public int getMaxClientProtocol() { return maxClientProtocol; }
    public boolean hasVirtualDisplay() { return virtualDisplay; }
    public boolean hasClipboardSync() { return clipboardSync; }
    public boolean hasServerCommands() { return serverCommands; }
    public boolean hasDisplayRecovery() { return displayRecovery; }
    public boolean hasGamescopeSession() { return gamescopeSession; }

    /** Frame rates accepted by session/prepare, sorted ascending. */
    public int[] getSupportedFps() { return supportedFps.clone(); }

    public boolean supportsFps(int fps) {
        return Arrays.binarySearch(supportedFps, fps) >= 0;
    }
}
