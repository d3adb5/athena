package com.limelight.hestia;

import org.junit.Test;

import static org.junit.Assert.*;

public class HestiaApiResponseTest {
    @Test public void readsHermesErrorBodies() {
        HestiaApiResponse response = new HestiaApiResponse(403,
                "{\"ok\":false,\"error\":{\"code\":\"permission_denied\","
                + "\"message\":\"This client does not have the required permission\"}}");
        assertFalse(response.isHttpSuccess());
        assertFalse(response.isOk());
        assertEquals("permission_denied", response.getErrorCode());
        assertEquals("This client does not have the required permission", response.getErrorMessage());
        assertEquals("This client does not have the required permission (permission_denied, HTTP 403)",
                response.describeError());
    }

    @Test public void toleratesNonJsonBodies() {
        HestiaApiResponse response = new HestiaApiResponse(502, "<html>Bad gateway</html>");
        assertNull(response.jsonObject());
        assertNull(response.getErrorCode());
        assertEquals("HTTP 502", response.describeError());
        assertFalse(new HestiaApiResponse(200, null).isOk());
    }

    @Test public void okRequiresBothHttpSuccessAndOkField() {
        assertTrue(new HestiaApiResponse(200, "{\"ok\":true}").isOk());
        assertFalse(new HestiaApiResponse(200, "{\"ok\":false}").isOk());
        assertFalse(new HestiaApiResponse(200, "{\"ok\":\"true\"}").isOk());
        assertFalse(new HestiaApiResponse(500, "{\"ok\":true}").isOk());
    }
}
