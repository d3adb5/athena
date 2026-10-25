package com.limelight.hestia;

import org.junit.Test;

import static org.junit.Assert.*;

public class HestiaCapabilitiesTest {
    private static final String VALID =
            "{\"ok\":true,\"server_name\":\"Hermes\",\"base\":\"Apollo\","
            + "\"server_version\":\"0.7.0\",\"hestia_protocol\":1,"
            + "\"min_client_protocol\":1,\"max_client_protocol\":1,"
            + "\"compatibility\":{},\"limits\":{},"
            + "\"features\":{\"virtual_display\":true,\"clipboard_sync\":true,"
            + "\"display_recovery\":false,\"server_commands\":true}}";

    @Test public void parsesV1Features() {
        HestiaCapabilities result = HestiaCapabilities.parse(VALID);
        assertTrue(result.isSupported());
        assertEquals("0.7.0", result.getServerVersion());
        assertTrue(result.hasVirtualDisplay());
        assertTrue(result.hasClipboardSync());
        assertTrue(result.hasServerCommands());
        assertFalse(result.hasDisplayRecovery());
    }

    @Test public void handlesLegacyAndMalformedPayloads() {
        assertFalse(HestiaCapabilities.parse(null).isSupported());
        assertFalse(HestiaCapabilities.parse("").isSupported());
        assertFalse(HestiaCapabilities.parse("<html>not found</html>").isSupported());
        assertFalse(HestiaCapabilities.parse("[]").isSupported());
        assertFalse(HestiaCapabilities.parse("{}").isSupported());
    }

    @Test public void rejectsUnsupportedVersionsAndInvalidTypes() {
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"hestia_protocol\":1", "\"hestia_protocol\":2")).isSupported());
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"min_client_protocol\":1", "\"min_client_protocol\":2")).isSupported());
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"max_client_protocol\":1", "\"max_client_protocol\":0")).isSupported());
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"ok\":true", "\"ok\":\"true\"")).isSupported());
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"max_client_protocol\":1", "\"max_client_protocol\":1.5")).isSupported());
    }

    @Test public void rejectsWrongHandshakeIdentity() {
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"server_name\":\"Hermes\"", "\"server_name\":\"Other\"")).isSupported());
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"base\":\"Apollo\"", "\"base\":\"Other\"")).isSupported());
    }

    @Test public void unavailableFeaturesDefaultToFalse() {
        HestiaCapabilities result = HestiaCapabilities.parse(
                VALID.replace("\"virtual_display\":true,", ""));
        assertTrue(result.isSupported());
        assertFalse(result.hasVirtualDisplay());
    }

    @Test public void distinguishesIncompatibleHermesFromLegacyHosts() {
        HestiaCapabilities future = HestiaCapabilities.parse(VALID
                .replace("\"hestia_protocol\":1", "\"hestia_protocol\":2")
                .replace("\"min_client_protocol\":1", "\"min_client_protocol\":2")
                .replace("\"max_client_protocol\":1", "\"max_client_protocol\":2"));
        assertFalse(future.isSupported());
        assertTrue(future.isHermes());
        assertTrue(future.isIncompatibleHermes());
        assertEquals(2, future.getMinClientProtocol());
        assertEquals("0.7.0", future.getServerVersion());
        assertFalse("Incompatible hosts never advertise features", future.hasVirtualDisplay());

        HestiaCapabilities wrongBase = HestiaCapabilities.parse(VALID.replace(
                "\"base\":\"Apollo\"", "\"base\":\"Other\""));
        assertTrue(wrongBase.isIncompatibleHermes());

        assertFalse(HestiaCapabilities.parse("{}").isHermes());
        assertFalse(HestiaCapabilities.parse(VALID.replace(
                "\"server_name\":\"Hermes\"", "\"server_name\":\"Other\"")).isHermes());
        assertTrue(HestiaCapabilities.parse(VALID).isHermes());
        assertFalse(HestiaCapabilities.parse(VALID).isIncompatibleHermes());
    }

    @Test public void readsAdvertisedFrameRates() {
        HestiaCapabilities defaults = HestiaCapabilities.parse(VALID);
        assertArrayEquals(new int[] {30, 40, 45, 60, 90, 120}, defaults.getSupportedFps());
        assertTrue(defaults.supportsFps(60));
        assertFalse(defaults.supportsFps(144));

        HestiaCapabilities custom = HestiaCapabilities.parse(VALID.replace(
                "\"limits\":{}", "\"limits\":{\"supported_fps\":[120,60,\"x\",59.5,144]}"));
        assertArrayEquals(new int[] {60, 120, 144}, custom.getSupportedFps());
    }
}
