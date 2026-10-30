package com.limelight.hestia;

import org.junit.Test;

import static org.junit.Assert.*;

public class HestiaStreamInfoTest {
    @Test public void readsACheckThatPassed() {
        HestiaStreamInfo info = HestiaStreamInfo.parse(new HestiaApiResponse(200,
                "{\"ok\":true,\"readiness\":{\"ok\":true,\"frame_seen\":true,"
                + "\"capture\":\"the detached desktop via KWin screencast\",\"width\":1920,"
                + "\"height\":1080,\"fps\":120,\"encoder\":\"vaapi\","
                + "\"gpu\":\"/dev/dri/renderD128 (amdgpu, AMD 1002:7550)\",\"failure\":\"\","
                + "\"checked_at\":1793400000}}"));
        assertNotNull(info);
        assertTrue(info.isOk());
        assertTrue(info.isFrameSeen());
        assertEquals("Detached desktop via KWin screencast", HestiaStreamInfo.sentenceCase(info.getCapture()));
        assertEquals("VAAPI", HestiaStreamInfo.encoderName(info.getEncoder()));
        assertEquals("AMD 1002:7550 (amdgpu)", HestiaStreamInfo.gpuName(info.getGpu()));
    }

    @Test public void readsACheckThatFailed() {
        HestiaStreamInfo info = HestiaStreamInfo.parse(new HestiaApiResponse(200,
                "{\"ok\":true,\"readiness\":{\"ok\":false,\"frame_seen\":false,"
                + "\"capture\":\"display [0] via kms\",\"failure\":\"Capture failed on display [0] via kms\"}}"));
        assertNotNull(info);
        assertFalse(info.isOk());
        assertEquals("Capture failed on display [0] via kms", info.getFailure());
        assertEquals("", info.getGpu());
    }

    @Test public void saysNothingWithoutACheck() {
        assertNull(HestiaStreamInfo.parse(new HestiaApiResponse(200, "{\"ok\":true,\"readiness\":null}")));
        assertNull(HestiaStreamInfo.parse(new HestiaApiResponse(404, "Not Found")));
        assertNull(HestiaStreamInfo.parse(null));
    }

    @Test public void showsAnUnfamiliarGpuAsWritten() {
        assertEquals("Some GPU", HestiaStreamInfo.gpuName("Some GPU"));
        assertEquals("", HestiaStreamInfo.gpuName(null));
    }
}
