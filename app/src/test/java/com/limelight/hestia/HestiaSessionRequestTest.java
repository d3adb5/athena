package com.limelight.hestia;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.*;

public class HestiaSessionRequestTest {
    @Test public void usesExactHermesV1KeysAndEnums() {
        HestiaSessionRequest request = HestiaSessionRequest.create(
                2560, 1600, 120, 28000, 100, true, true, 4, 120, "0.1.0");
        assertNotNull(request);
        JsonObject json = JsonParser.parseString(request.toJson()).getAsJsonObject();
        assertEquals(4, json.size());
        assertEquals(7, json.getAsJsonObject("client").size());
        assertEquals(7, json.getAsJsonObject("stream").size());
        assertEquals(4, json.getAsJsonObject("virtual_display").size());
        assertEquals(2, json.getAsJsonObject("app").size());
        assertEquals("unknown", json.getAsJsonObject("client").get("platform").getAsString());
        assertEquals("hdr", json.getAsJsonObject("stream").get("hdr_mode").getAsString());
        assertEquals(120, json.getAsJsonObject("stream").get("requested_fps").getAsInt());
        assertEquals("auto", json.getAsJsonObject("virtual_display").get("backend").getAsString());
        assertTrue(json.getAsJsonObject("virtual_display").get("enabled").getAsBoolean());
        assertEquals("normal", json.getAsJsonObject("app").get("launch_mode").getAsString());
        assertEquals("4", json.getAsJsonObject("app").get("id").getAsString());
    }

    @Test public void unsupportedFpsOrOversizedRequestNeverPreparesHost() {
        assertNull(HestiaSessionRequest.create(1920,1080,144,20000,100,false,true,1,144,"0.1"));
        assertNull(HestiaSessionRequest.create(9000,1080,60,20000,100,false,true,1,60,"0.1"));
        assertNull(HestiaSessionRequest.create(1920,1080,60,20000,501,false,true,1,60,"0.1"));
        assertNull(HestiaSessionRequest.create(0,1080,60,20000,100,false,true,1,60,"0.1"));
        assertNull(HestiaSessionRequest.create(1920,1080,60,-1,100,false,true,1,60,"0.1"));
    }

    @Test public void sdrAndNoVirtualDisplaySerializeCorrectly() {
        HestiaSessionRequest request = HestiaSessionRequest.create(
                1280, 720, 60, 10000, 100, false, false, 2, 60, "0.1");
        assertNotNull(request);
        JsonObject json = JsonParser.parseString(request.toJson()).getAsJsonObject();
        assertEquals("sdr", json.getAsJsonObject("stream").get("hdr_mode").getAsString());
        assertFalse(json.getAsJsonObject("virtual_display").get("enabled").getAsBoolean());
    }

    @Test public void explainsWhyARequestWasNotBuilt() {
        assertEquals(HestiaSessionRequest.Rejection.NONE,
                HestiaSessionRequest.check(1920, 1080, 60, 20000, 100, 1, 60, "0.1"));
        assertEquals(HestiaSessionRequest.Rejection.FPS_UNSUPPORTED,
                HestiaSessionRequest.check(1920, 1080, 144, 20000, 100, 1, 144, "0.1"));
        assertEquals(HestiaSessionRequest.Rejection.OUT_OF_RANGE,
                HestiaSessionRequest.check(9000, 1080, 60, 20000, 100, 1, 60, "0.1"));
        assertEquals(HestiaSessionRequest.Rejection.OUT_OF_RANGE,
                HestiaSessionRequest.check(1920, 1080, 60, 20000, 0, 1, 60, "0.1"));
        assertEquals(HestiaSessionRequest.Rejection.INVALID,
                HestiaSessionRequest.check(1920, 1080, 60, 0, 100, 1, 60, "0.1"));
        assertEquals(HestiaSessionRequest.Rejection.INVALID,
                HestiaSessionRequest.check(1920, 1080, 60, 20000, 100, 1, 60, ""));
        assertArrayEquals(new int[] {30, 40, 45, 60, 90, 120}, HestiaSessionRequest.supportedFps());
    }
}
