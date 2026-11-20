package com.limelight.fallback;

import java.util.Collection;

/**
 * Whether a shortcut whose host doesn't answer plays the game on a local PC emulator instead. Pure: the trampoline
 * passes in what it sees. Home is the Wi-Fi name the user sets; a host that answers never falls back.
 */
public final class LocalFallback {
    /** Where the device is, by the connected Wi-Fi's name. */
    public enum Place {
        /** No home Wi-Fi name set: home and away can't be told apart. */
        NOT_SET,
        /** On the home Wi-Fi. */
        HOME,
        /** On another Wi-Fi, or connected without Wi-Fi. */
        AWAY,
        /** No network at all: Wi-Fi off, or not connected yet. */
        NO_NETWORK,
        /** On a Wi-Fi whose name Android won't tell (no location permission, location off). */
        UNREADABLE
    }

    public enum Step {
        /** Athena as without the fallback: wake the host, poll, its usual error. */
        AS_BEFORE,
        /** Wi-Fi is on but not connected (waking from sleep): wait for it before deciding. */
        WAIT_FOR_NETWORK,
        /** Away: offer the local game with a countdown that starts it. */
        FALL_BACK,
        /** Maybe at home: offer the local game, but only on a press. */
        ASK,
        /** Away and the game isn't installed locally: say so instead of the usual error. */
        NOT_INSTALLED
    }

    /** Seconds the away notice counts down before it starts the local game. */
    public static final int COUNTDOWN_S = 5;
    /** Longest wait for Wi-Fi to connect before a host that doesn't answer counts as away. */
    public static final int NETWORK_WAIT_MS = 20000;

    private static final String UNKNOWN_SSID = "<unknown ssid>";

    private LocalFallback() {}

    /**
     * ssid: WifiManager's name (quoted, or "<unknown ssid>"), or null without Wi-Fi. wifiConnected: a Wi-Fi network
     * is up; anyNetwork: any network is.
     */
    public static Place place(String homeName, String ssid, boolean wifiConnected, boolean anyNetwork) {
        String home = homeName == null ? "" : homeName.trim();
        if (home.isEmpty()) {
            return Place.NOT_SET;
        }
        if (wifiConnected) {
            String name = ssidName(ssid);
            if (name == null) {
                return Place.UNREADABLE;
            }
            return name.equals(home) ? Place.HOME : Place.AWAY;
        }
        return anyNetwork ? Place.AWAY : Place.NO_NETWORK;
    }

    /** The SSID without Android's quotes, or null when Android hides it. */
    static String ssidName(String ssid) {
        if (ssid == null || ssid.isEmpty() || ssid.equals(UNKNOWN_SSID)) {
            return null;
        }
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    /**
     * The step once polls call the host offline, with the fallback on. installed: an emulator is chosen and installed
     * and this game is ticked for it.
     */
    public static Step onOffline(Place place, boolean installed, boolean wifiEnabled, boolean waitedForNetwork) {
        switch (place) {
            case NO_NETWORK:
                if (wifiEnabled && !waitedForNetwork) {
                    return Step.WAIT_FOR_NETWORK;
                }
                // fall through: still no network, so away
            case AWAY:
                return installed ? Step.FALL_BACK : Step.NOT_INSTALLED;
            case UNREADABLE:
                return installed ? Step.ASK : Step.AS_BEFORE;
            default:
                return Step.AS_BEFORE;
        }
    }

    /** The local id of a Steam game for an emulator that launches by Steam app id, or null. */
    public static String localId(String steamAppId, Collection<String> chosen) {
        if (steamAppId == null) {
            return null;
        }
        String id = steamAppId.trim();
        return LocalEmulator.validSteamId(id) && chosen.contains(id) ? id : null;
    }
}
