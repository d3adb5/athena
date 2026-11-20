package com.limelight.fallback;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static com.limelight.fallback.LocalFallback.Place.AWAY;
import static com.limelight.fallback.LocalFallback.Place.HOME;
import static com.limelight.fallback.LocalFallback.Place.NOT_SET;
import static com.limelight.fallback.LocalFallback.Place.NO_NETWORK;
import static com.limelight.fallback.LocalFallback.Place.UNREADABLE;
import static com.limelight.fallback.LocalFallback.Step.AS_BEFORE;
import static com.limelight.fallback.LocalFallback.Step.ASK;
import static com.limelight.fallback.LocalFallback.Step.FALL_BACK;
import static com.limelight.fallback.LocalFallback.Step.NOT_INSTALLED;
import static com.limelight.fallback.LocalFallback.Step.WAIT_FOR_NETWORK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalFallbackTest {
    private static final String HOME_WIFI = "Flint MLO";

    @Test
    public void homeWifiByExactNameWithoutQuotes() {
        assertEquals(HOME, LocalFallback.place(HOME_WIFI, "\"Flint MLO\"", true, true));
        assertEquals(HOME, LocalFallback.place(" Flint MLO ", "\"Flint MLO\"", true, true));
        assertEquals(AWAY, LocalFallback.place(HOME_WIFI, "\"flint mlo\"", true, true));
        assertEquals(AWAY, LocalFallback.place(HOME_WIFI, "\"Hotel\"", true, true));
    }

    @Test
    public void noNameSetCantTell() {
        assertEquals(NOT_SET, LocalFallback.place("", "\"Hotel\"", true, true));
        assertEquals(NOT_SET, LocalFallback.place(null, null, false, false));
        assertEquals(AS_BEFORE, LocalFallback.onOffline(NOT_SET, true, true, true));
        assertEquals(AS_BEFORE, LocalFallback.onOffline(NOT_SET, false, true, true));
    }

    @Test
    public void hiddenNameIsUnreadable() {
        assertEquals(UNREADABLE, LocalFallback.place(HOME_WIFI, "<unknown ssid>", true, true));
        assertEquals(UNREADABLE, LocalFallback.place(HOME_WIFI, null, true, true));
    }

    @Test
    public void withoutWifi() {
        assertEquals(AWAY, LocalFallback.place(HOME_WIFI, null, false, true));
        assertEquals(NO_NETWORK, LocalFallback.place(HOME_WIFI, null, false, false));
    }

    @Test
    public void homeIsAlwaysAsBefore() {
        assertEquals(AS_BEFORE, LocalFallback.onOffline(HOME, true, true, false));
        assertEquals(AS_BEFORE, LocalFallback.onOffline(HOME, false, true, true));
    }

    @Test
    public void awayCountsDownOrSaysNotInstalled() {
        assertEquals(FALL_BACK, LocalFallback.onOffline(AWAY, true, true, false));
        assertEquals(NOT_INSTALLED, LocalFallback.onOffline(AWAY, false, true, false));
    }

    @Test
    public void unreadableNeverStartsByItself() {
        assertEquals(ASK, LocalFallback.onOffline(UNREADABLE, true, true, true));
        assertEquals(AS_BEFORE, LocalFallback.onOffline(UNREADABLE, false, true, true));
    }

    @Test
    public void wifiConnectingIsWaitedForOnce() {
        assertEquals(WAIT_FOR_NETWORK, LocalFallback.onOffline(NO_NETWORK, true, true, false));
        assertEquals(FALL_BACK, LocalFallback.onOffline(NO_NETWORK, true, true, true));
        assertEquals(NOT_INSTALLED, LocalFallback.onOffline(NO_NETWORK, false, true, true));
    }

    @Test
    public void wifiOffIsAwayAtOnce() {
        assertEquals(FALL_BACK, LocalFallback.onOffline(NO_NETWORK, true, false, false));
        assertEquals(NOT_INSTALLED, LocalFallback.onOffline(NO_NETWORK, false, false, false));
    }

    @Test
    public void ssidQuotesStripped() {
        assertEquals("a", LocalFallback.ssidName("\"a\""));
        assertEquals("0a1b", LocalFallback.ssidName("0a1b"));
        assertNull(LocalFallback.ssidName("<unknown ssid>"));
        assertNull(LocalFallback.ssidName(""));
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
