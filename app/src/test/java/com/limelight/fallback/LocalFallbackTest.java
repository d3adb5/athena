package com.limelight.fallback;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.limelight.fallback.LocalFallback.Network.AWAY;
import static com.limelight.fallback.LocalFallback.Network.HOME;
import static com.limelight.fallback.LocalFallback.Network.NONE;
import static com.limelight.fallback.LocalFallback.Network.UNKNOWN;
import static com.limelight.fallback.LocalFallback.Step.ASK;
import static com.limelight.fallback.LocalFallback.Step.FALL_BACK;
import static com.limelight.fallback.LocalFallback.Step.KEEP_TRYING;
import static com.limelight.fallback.LocalFallback.Step.WAIT_FOR_NETWORK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalFallbackTest {
    private static final List<String> HOST = Collections.singletonList("192.168.1.237");

    @Test
    public void sameSubnetIsHome() {
        assertEquals(HOME, LocalFallback.classify(Collections.singletonList("192.168.1.122/24"), HOST));
    }

    @Test
    public void otherSubnetIsAway() {
        assertEquals(AWAY, LocalFallback.classify(Collections.singletonList("10.0.0.5/24"), HOST));
    }

    @Test
    public void anyNetworkHoldingTheHostIsHome() {
        assertEquals(HOME, LocalFallback.classify(Arrays.asList("100.64.1.2/32", "192.168.1.9/24"), HOST));
    }

    @Test
    public void prefixDecidesNotTheFirstOctets() {
        assertEquals(AWAY, LocalFallback.classify(Collections.singletonList("192.168.1.10/30"), HOST));
        assertEquals(HOME, LocalFallback.classify(Collections.singletonList("192.168.7.10/16"), HOST));
    }

    @Test
    public void noAddressIsNoNetwork() {
        assertEquals(NONE, LocalFallback.classify(Collections.<String>emptyList(), HOST));
        assertEquals(NONE, LocalFallback.classify(Arrays.asList("127.0.0.1/8", "169.254.3.4/16"), HOST));
        assertEquals(NONE, LocalFallback.classify(Collections.singletonList("garbage"), HOST));
    }

    @Test
    public void hostnamesCantBeCompared() {
        assertEquals(UNKNOWN, LocalFallback.classify(Collections.singletonList("10.0.0.5/24"),
                Arrays.asList("gilgamesh.local", null, "fe80::1")));
        assertEquals(HOME, LocalFallback.classify(Collections.singletonList("192.168.1.5/24"),
                Arrays.asList("gilgamesh.local", "192.168.1.237")));
    }

    @Test
    public void ipv4ParsesOnlyLiterals() {
        assertEquals(0xC0A801EDL, LocalFallback.ipv4("192.168.1.237"));
        assertEquals(-1, LocalFallback.ipv4("192.168.1"));
        assertEquals(-1, LocalFallback.ipv4("192.168.1.256"));
        assertEquals(-1, LocalFallback.ipv4("1.2.3.x"));
        assertEquals(-1, LocalFallback.ipv4("1..2.3"));
    }

    @Test
    public void awayFallsBackAtOnceWithoutWaking() {
        assertEquals(FALL_BACK, LocalFallback.onOffline(AWAY, true, false, 10));
    }

    @Test
    public void homeKeepsWakingThenOnlyAsks() {
        assertEquals(KEEP_TRYING, LocalFallback.onOffline(HOME, true, false, 3));
        assertEquals(ASK, LocalFallback.onOffline(HOME, true, true, 0));
        assertEquals(KEEP_TRYING, LocalFallback.onOffline(UNKNOWN, true, false, 1));
        assertEquals(ASK, LocalFallback.onOffline(UNKNOWN, true, false, 0));
    }

    @Test
    public void wifiConnectingIsWaitedForOnce() {
        assertEquals(WAIT_FOR_NETWORK, LocalFallback.onOffline(NONE, true, false, 10));
        assertEquals(FALL_BACK, LocalFallback.onOffline(NONE, true, true, 10));
    }

    @Test
    public void wifiOffFallsBackAtOnce() {
        assertEquals(FALL_BACK, LocalFallback.onOffline(NONE, false, false, 10));
    }

    @Test
    public void onlyChosenSteamGamesHaveALocalId() {
        HashSet<String> chosen = new HashSet<>(Arrays.asList("2602230", "504230"));
        assertEquals("2602230", LocalFallback.localId(" 2602230 ", chosen));
        assertNull(LocalFallback.localId("620", chosen));
        assertNull(LocalFallback.localId(null, chosen));
        assertNull(LocalFallback.localId("504230", Collections.<String>emptySet()));
    }

    @Test
    public void steamIdsFitAnInt() {
        assertTrue(LocalEmulator.validSteamId("2602230"));
        assertFalse(LocalEmulator.validSteamId("0"));
        assertFalse(LocalEmulator.validSteamId("012"));
        assertFalse(LocalEmulator.validSteamId("4294967296"));
        assertFalse(LocalEmulator.validSteamId("12a"));
        assertFalse(LocalEmulator.validSteamId(""));
    }

    @Test
    public void emulatorKeys() {
        assertEquals(LocalEmulator.DROIDDECK, LocalEmulator.fromKey("droiddeck"));
        assertEquals(LocalEmulator.GAMENATIVE, LocalEmulator.fromKey("gamenative"));
        assertNull(LocalEmulator.fromKey(LocalEmulator.OFF));
        assertNull(LocalEmulator.fromKey(null));
    }
}
