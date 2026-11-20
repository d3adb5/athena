package com.limelight.fallback;

import java.util.Collection;

/**
 * Whether a shortcut whose host doesn't answer plays the game on a local PC emulator instead. Pure: the trampoline
 * passes in what it sees. Only "host unreachable" gets here; a host that answers never falls back.
 */
public final class LocalFallback {
    /** Where the device is relative to the host, from addresses alone. */
    public enum Network {
        /** No network with an address: Wi-Fi off, or not connected yet. */
        NONE,
        /** On a network whose prefix holds one of the host's LAN addresses. */
        HOME,
        /** Connected, but no prefix holds a host LAN address. */
        AWAY,
        /** Connected, but the host has no literal IPv4 LAN address to compare. */
        UNKNOWN
    }

    public enum Step {
        /** Wake the host and poll again, as without the fallback. */
        KEEP_TRYING,
        /** Wi-Fi is on but not connected (waking from sleep): wait for it before deciding. */
        WAIT_FOR_NETWORK,
        /** Away: offer the local game with a countdown that starts it. */
        FALL_BACK,
        /** Maybe at home (host asleep, Ares restarting): offer the local game, but only on a press. */
        ASK
    }

    /** Seconds the away notice counts down before it starts the local game. */
    public static final int COUNTDOWN_S = 5;
    /** Longest wait for Wi-Fi to connect before a host that doesn't answer counts as away. */
    public static final int NETWORK_WAIT_MS = 20000;

    private LocalFallback() {}

    /**
     * The next step once polls call the host offline. wakeTriesLeft is 0 when the host has no MAC address.
     * Away needs no Wake-on-LAN: its packets can't reach the host's network.
     */
    public static Step onOffline(Network network, boolean wifiEnabled, boolean waitedForNetwork, int wakeTriesLeft) {
        switch (network) {
            case NONE:
                return wifiEnabled && !waitedForNetwork ? Step.WAIT_FOR_NETWORK : Step.FALL_BACK;
            case AWAY:
                return Step.FALL_BACK;
            default:
                return wakeTriesLeft > 0 ? Step.KEEP_TRYING : Step.ASK;
        }
    }

    /**
     * deviceAddresses: "a.b.c.d/prefix" of every connected network (IPv6 ones ignored). hostAddresses: the host's
     * LAN addresses as saved (hostnames and IPv6 can't be compared).
     */
    public static Network classify(Collection<String> deviceAddresses, Collection<String> hostAddresses) {
        boolean connected = false;
        boolean comparable = false;
        for (String device : deviceAddresses) {
            int slash = device == null ? -1 : device.indexOf('/');
            long net = slash < 0 ? -1 : ipv4(device.substring(0, slash));
            int prefix = slash < 0 ? -1 : prefix(device.substring(slash + 1));
            if (net < 0 || prefix < 0 || isLoopbackOrLinkLocal(net)) {
                continue;
            }
            connected = true;
            for (String host : hostAddresses) {
                long h = host == null ? -1 : ipv4(host.trim());
                if (h < 0) {
                    continue;
                }
                comparable = true;
                long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
                if ((net & mask) == (h & mask)) {
                    return Network.HOME;
                }
            }
        }
        if (!connected) {
            return Network.NONE;
        }
        return comparable ? Network.AWAY : Network.UNKNOWN;
    }

    /** A dotted IPv4 literal as an unsigned 32-bit value, or -1. */
    static long ipv4(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) {
            return -1;
        }
        long value = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return -1;
            }
            int octet = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return -1;
                }
                octet = octet * 10 + (c - '0');
            }
            if (octet > 255) {
                return -1;
            }
            value = (value << 8) | octet;
        }
        return value;
    }

    private static int prefix(String text) {
        try {
            int p = Integer.parseInt(text.trim());
            return p >= 0 && p <= 32 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isLoopbackOrLinkLocal(long address) {
        return (address >>> 24) == 127 || (address >>> 16) == 0xA9FE;
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
