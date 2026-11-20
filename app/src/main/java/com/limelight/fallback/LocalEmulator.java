package com.limelight.fallback;

/**
 * PC emulators that start a Steam game from another app: action + extras app_id (int) and game_source "STEAM".
 * GameNative defined the intent; DroidDeck accepts the same one under its own action.
 */
public enum LocalEmulator {
    DROIDDECK("droiddeck", "DroidDeck", "com.droiddeck.launcher", "com.droiddeck.launcher.LAUNCH_GAME"),
    GAMENATIVE("gamenative", "GameNative", "app.gamenative", "app.gamenative.LAUNCH_GAME");

    public static final String OFF = "off";
    public static final String EXTRA_APP_ID = "app_id";
    public static final String EXTRA_GAME_SOURCE = "game_source";
    public static final String SOURCE_STEAM = "STEAM";

    public final String key;
    public final String label;
    public final String packageName;
    public final String action;

    LocalEmulator(String key, String label, String packageName, String action) {
        this.key = key;
        this.label = label;
        this.packageName = packageName;
        this.action = action;
    }

    /** The emulator a preference value names, or null for off and unknown values. */
    public static LocalEmulator fromKey(String key) {
        for (LocalEmulator e : values()) {
            if (e.key.equals(key)) {
                return e;
            }
        }
        return null;
    }

    /** A Steam app id both emulators take as an int extra. */
    public static boolean validSteamId(String id) {
        if (id == null || id.isEmpty() || id.length() > 10 || id.charAt(0) == '0') {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (id.charAt(i) < '0' || id.charAt(i) > '9') {
                return false;
            }
        }
        return Long.parseLong(id) <= Integer.MAX_VALUE;
    }
}
