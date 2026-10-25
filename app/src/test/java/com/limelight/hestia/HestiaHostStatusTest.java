package com.limelight.hestia;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HestiaHostStatusTest {
    static final String CAPS =
            "{\"ok\":true,\"server_name\":\"Hermes\",\"base\":\"Apollo\","
            + "\"server_version\":\"0.6.0\",\"hestia_protocol\":1,"
            + "\"min_client_protocol\":1,\"max_client_protocol\":1,"
            + "\"compatibility\":{\"gamestream\":true,\"moonlight\":true,\"sunshine\":true},"
            + "\"limits\":{\"max_width\":7680,\"max_height\":4320,\"max_fps\":120,"
            + "\"supported_fps\":[30,40,45,60,90,120],\"supported_codecs\":[\"h264\"]},"
            + "\"features\":{\"virtual_display\":true,\"clipboard_sync\":true,"
            + "\"display_recovery\":true,\"server_commands\":true}}";

    static final String ALL_PERMISSIONS =
            "{\"ok\":true,\"client_id\":\"abc\",\"permissions\":{"
            + "\"view_streams\":true,\"list_apps\":true,\"launch_apps\":true,"
            + "\"keyboard_input\":true,\"mouse_input\":true,\"gamepad_input\":true,"
            + "\"virtual_display\":true,\"server_commands\":true,"
            + "\"clipboard_sync\":true,\"display_recovery\":true}}";

    // Hermes's PERM::_default for every device paired after the first one.
    static final String DEFAULT_PERMISSIONS =
            "{\"ok\":true,\"client_id\":\"abc\",\"permissions\":{"
            + "\"view_streams\":true,\"list_apps\":true,\"launch_apps\":false,"
            + "\"keyboard_input\":false,\"mouse_input\":false,\"gamepad_input\":false,"
            + "\"virtual_display\":false,\"server_commands\":false,"
            + "\"clipboard_sync\":false,\"display_recovery\":false}}";

    static final String HEALTHY_DIAGNOSTICS =
            "{\"ok\":true,\"runtime\":{"
            + "\"encoder\":{\"name\":\"vaapi\",\"hardware\":true,\"fell_back_to_software\":false},"
            + "\"sessions\":{\"active\":0,\"streaming\":false,\"last_termination\":\"client_quit\","
            + "\"awaiting_reconnect\":false,\"ms_since_last_end\":5000,\"total_ended\":1}},"
            + "\"preflight\":{\"ready\":true,\"checks\":["
            + "{\"id\":\"encoder\",\"status\":\"ok\",\"message\":\"Hardware encoder in use: vaapi.\"},"
            + "{\"id\":\"display\",\"status\":\"ok\",\"message\":\"A capturable display is available.\"}]},"
            + "\"dependencies\":{\"clipboard\":{\"available\":true,\"diagnostic\":\"ready\"}}}";

    static final String IDLE_DISPLAY =
            "{\"ok\":true,\"active\":false,"
            + "\"virtual_display\":{\"enabled\":false,\"name\":\"\",\"backend\":\"none\","
            + "\"width\":0,\"height\":0,\"fps\":0,\"hdr\":false},"
            + "\"physical_display\":{\"disabled_during_stream\":false,\"saved_primary\":\"\","
            + "\"recovery_state_exists\":false},"
            + "\"desktop\":{\"environment\":\"wayland\",\"kscreen_available\":true,\"kscreen_output\":\"\"}}";

    static final String STUCK_DISPLAY =
            "{\"ok\":true,\"active\":true,"
            + "\"virtual_display\":{\"enabled\":true,\"name\":\"DVI-I-1\",\"backend\":\"evdi\","
            + "\"width\":2560,\"height\":1600,\"fps\":120,\"hdr\":false},"
            + "\"physical_display\":{\"disabled_during_stream\":true,\"saved_primary\":\"DP-1\","
            + "\"recovery_state_exists\":true},"
            + "\"desktop\":{\"environment\":\"wayland\",\"kscreen_available\":true,\"kscreen_output\":\"DVI-I-1\"}}";

    /** Scripted host: path -> response, or IOException when absent. */
    static final class FakeHost implements HestiaApi.Transport {
        final Map<String, HestiaApiResponse> responses = new HashMap<>();
        int requests;

        FakeHost respond(String path, int code, String body) {
            responses.put(path, new HestiaApiResponse(code, body));
            return this;
        }

        static FakeHost healthy() {
            return new FakeHost()
                    .respond(HestiaApi.CAPABILITIES, 200, CAPS)
                    .respond(HestiaApi.CLIENT_PERMISSIONS, 200, ALL_PERMISSIONS)
                    .respond(HestiaApi.DIAGNOSTICS, 200, HEALTHY_DIAGNOSTICS)
                    .respond(HestiaApi.DISPLAY_STATUS, 200, IDLE_DISPLAY);
        }

        @Override
        public HestiaApiResponse get(String path) throws IOException {
            requests++;
            HestiaApiResponse response = responses.get(path);
            if (response == null) {
                throw new SocketTimeoutException(path);
            }
            return response;
        }
    }

    static String error(String code, String message) {
        return "{\"ok\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
    }

    @Test public void healthyHostIsReady() {
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy(), false);
        assertTrue(status.isHermes());
        assertEquals(HestiaHostStatus.Level.OK, status.getLevel());
        assertEquals(HestiaHostStatus.Code.READY, status.getHeadline().code);
        assertEquals("0.6.0", status.getServerVersion());
        assertFalse(status.isDisplayRecoveryOffered());
        // Passed host checks are kept so the dialog can show what was verified.
        int passedChecks = 0;
        for (HestiaHostStatus.Item item : status.getItems()) {
            if (item.code == HestiaHostStatus.Code.PREFLIGHT_CHECK) {
                assertEquals(HestiaHostStatus.Level.OK, item.level);
                passedChecks++;
            }
        }
        assertEquals(2, passedChecks);
        // A normal quit is not reported.
        assertFalse(status.hasCode(HestiaHostStatus.Code.LAST_SESSION_ENDED));
    }

    @Test public void legacyHostsGetNoIndicator() {
        // Plain Sunshine/Apollo: no endpoint, timeouts, or a non-Hermes document.
        assertFalse(HestiaHostStatus.probe(new FakeHost()
                .respond(HestiaApi.CAPABILITIES, 404, "Not Found"), false).isHermes());
        assertFalse(HestiaHostStatus.probe(new FakeHost(), false).isHermes());
        assertFalse(HestiaHostStatus.probe(new FakeHost()
                .respond(HestiaApi.CAPABILITIES, 200, "{\"ok\":true,\"server_name\":\"Apollo\"}"),
                false).isHermes());
        assertFalse(HestiaHostStatus.probe(new FakeHost()
                .respond(HestiaApi.CAPABILITIES, 500, "oops"), false).isHermes());
    }

    @Test public void knownHermesBecomingUnreachableIsAnError() {
        HestiaHostStatus status = HestiaHostStatus.probe(new FakeHost(), true);
        assertTrue(status.isHermes());
        assertEquals(HestiaHostStatus.Level.ERROR, status.getLevel());
        assertEquals(HestiaHostStatus.Code.API_UNREACHABLE, status.getHeadline().code);

        // ...but a 404 means it is no longer Hermes at all.
        assertFalse(HestiaHostStatus.probe(new FakeHost()
                .respond(HestiaApi.CAPABILITIES, 404, ""), true).isHermes());
    }

    @Test public void incompatibleProtocolIsAnErrorAndStopsProbing() {
        FakeHost host = FakeHost.healthy().respond(HestiaApi.CAPABILITIES, 200,
                CAPS.replace("\"hestia_protocol\":1", "\"hestia_protocol\":2")
                    .replace("\"min_client_protocol\":1", "\"min_client_protocol\":2")
                    .replace("\"max_client_protocol\":1", "\"max_client_protocol\":2"));
        HestiaHostStatus status = HestiaHostStatus.probe(host, false);
        assertTrue(status.isHermes());
        assertEquals(HestiaHostStatus.Level.ERROR, status.getLevel());
        assertEquals(HestiaHostStatus.Code.PROTOCOL_INCOMPATIBLE, status.getHeadline().code);
        assertEquals("2-2", status.getHeadline().detail);
        assertEquals("No privileged endpoint may be called", 1, host.requests);
    }

    @Test public void rejectedCertificateAsksForRepair() {
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy().respond(
                HestiaApi.CLIENT_PERMISSIONS, 401,
                error("unauthorized", "A paired client certificate is required")), false);
        assertEquals(HestiaHostStatus.Level.ERROR, status.getLevel());
        assertEquals(HestiaHostStatus.Code.CERTIFICATE_REJECTED, status.getHeadline().code);
        assertEquals("A paired client certificate is required", status.getHeadline().detail);
    }

    @Test public void defaultPermissionsWarnAboutLaunchAndInput() {
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy().respond(
                HestiaApi.CLIENT_PERMISSIONS, 200, DEFAULT_PERMISSIONS), false);
        assertEquals(HestiaHostStatus.Level.WARNING, status.getLevel());
        assertTrue(status.hasCode(HestiaHostStatus.Code.NO_LAUNCH_PERMISSION));
        assertFalse(status.hasCode(HestiaHostStatus.Code.READY));
        boolean sawInput = false;
        for (HestiaHostStatus.Item item : status.getItems()) {
            if (item.code == HestiaHostStatus.Code.NO_INPUT_PERMISSION) {
                assertEquals("keyboard,mouse,gamepad", item.detail);
                sawInput = true;
            }
        }
        assertTrue(sawInput);
        assertNotNull(status.getPermissions());
        assertFalse(status.getPermissions().canLaunchApps());
    }

    @Test public void noLaunchAndNoViewMeansNoStreaming() {
        String none = DEFAULT_PERMISSIONS.replace("\"view_streams\":true", "\"view_streams\":false");
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy().respond(
                HestiaApi.CLIENT_PERMISSIONS, 200, none), false);
        assertEquals(HestiaHostStatus.Level.ERROR, status.getLevel());
        assertEquals(HestiaHostStatus.Code.NO_STREAM_PERMISSION, status.getHeadline().code);
        // Without view the diagnostics endpoints would only answer 403.
        assertTrue(status.hasCode(HestiaHostStatus.Code.NO_VIEW_PERMISSION));
        assertFalse(status.hasCode(HestiaHostStatus.Code.PREFLIGHT_CHECK));
    }

    @Test public void failingPreflightCheckIsAnError() {
        String diagnostics = HEALTHY_DIAGNOSTICS
                .replace("\"ready\":true", "\"ready\":false")
                .replace("{\"id\":\"encoder\",\"status\":\"ok\",\"message\":\"Hardware encoder in use: vaapi.\"}",
                        "{\"id\":\"encoder\",\"status\":\"fail\",\"message\":\"No usable video encoder was found.\"}");
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy().respond(
                HestiaApi.DIAGNOSTICS, 200, diagnostics), false);
        assertEquals(HestiaHostStatus.Level.ERROR, status.getLevel());
        HestiaHostStatus.Item headline = status.getHeadline();
        assertEquals(HestiaHostStatus.Code.PREFLIGHT_CHECK, headline.code);
        assertEquals("encoder", headline.checkId);
        assertEquals("No usable video encoder was found.", headline.detail);
        // Exactly one error: ready=false is explained by the failing check.
        int errors = 0;
        for (HestiaHostStatus.Item item : status.getItems()) {
            if (item.level == HestiaHostStatus.Level.ERROR) {
                errors++;
            }
        }
        assertEquals(1, errors);
    }

    @Test public void softwareEncodingWarns() {
        String diagnostics = HEALTHY_DIAGNOSTICS.replace(
                "{\"id\":\"encoder\",\"status\":\"ok\",\"message\":\"Hardware encoder in use: vaapi.\"}",
                "{\"id\":\"encoder\",\"status\":\"warn\",\"message\":\"Using software encoding.\"}");
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy().respond(
                HestiaApi.DIAGNOSTICS, 200, diagnostics), false);
        assertEquals(HestiaHostStatus.Level.WARNING, status.getLevel());
        assertEquals("encoder", status.getHeadline().checkId);
    }

    @Test public void physicalDisplayLeftOffOffersRecovery() {
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy().respond(
                HestiaApi.DISPLAY_STATUS, 200, STUCK_DISPLAY), false);
        assertEquals(HestiaHostStatus.Level.WARNING, status.getLevel());
        assertEquals(HestiaHostStatus.Code.DISPLAY_LEFT_DISABLED, status.getHeadline().code);
        assertTrue(status.isDisplayRecoveryOffered());
        assertTrue(status.hasCode(HestiaHostStatus.Code.VIRTUAL_DISPLAY_ACTIVE));
    }

    @Test public void physicalDisplayOffDuringStreamIsNormal() {
        String streaming = HEALTHY_DIAGNOSTICS.replace("\"active\":0", "\"active\":1");
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.DIAGNOSTICS, 200, streaming)
                .respond(HestiaApi.DISPLAY_STATUS, 200, STUCK_DISPLAY), false);
        assertEquals(HestiaHostStatus.Level.OK, status.getLevel());
        assertTrue(status.hasCode(HestiaHostStatus.Code.STREAMING));
        assertFalse(status.isDisplayRecoveryOffered());

        String parked = HEALTHY_DIAGNOSTICS.replace("\"awaiting_reconnect\":false",
                "\"awaiting_reconnect\":true");
        status = HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.DIAGNOSTICS, 200, parked)
                .respond(HestiaApi.DISPLAY_STATUS, 200, STUCK_DISPLAY), false);
        assertEquals(HestiaHostStatus.Level.OK, status.getLevel());
        assertTrue(status.hasCode(HestiaHostStatus.Code.AWAITING_RECONNECT));
    }

    @Test public void recoveryNotOfferedWithoutPermission() {
        String noRecovery = ALL_PERMISSIONS.replace("\"display_recovery\":true",
                "\"display_recovery\":false");
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.CLIENT_PERMISSIONS, 200, noRecovery)
                .respond(HestiaApi.DISPLAY_STATUS, 200, STUCK_DISPLAY), false);
        assertTrue(status.hasCode(HestiaHostStatus.Code.DISPLAY_LEFT_DISABLED));
        assertFalse(status.isDisplayRecoveryOffered());
    }

    @Test public void recentFailedSessionWarns() {
        String failed = HEALTHY_DIAGNOSTICS.replace("\"last_termination\":\"client_quit\"",
                "\"last_termination\":\"handshake_failed\"");
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.DIAGNOSTICS, 200, failed), false);
        assertEquals(HestiaHostStatus.Level.WARNING, status.getLevel());
        assertEquals(HestiaHostStatus.Code.LAST_SESSION_ENDED, status.getHeadline().code);
        assertEquals("handshake_failed", status.getHeadline().detail);

        // Old failures are not resurfaced forever.
        String old = failed.replace("\"ms_since_last_end\":5000", "\"ms_since_last_end\":3600000");
        status = HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.DIAGNOSTICS, 200, old), false);
        assertEquals(HestiaHostStatus.Level.OK, status.getLevel());
    }

    @Test public void diagnosticsFailureDegradesToWarning() {
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.DIAGNOSTICS, 500,
                        error("internal_error", "boom")), false);
        assertEquals(HestiaHostStatus.Level.WARNING, status.getLevel());
        HestiaHostStatus.Item headline = status.getHeadline();
        assertEquals(HestiaHostStatus.Code.DIAGNOSTICS_UNAVAILABLE, headline.code);
        assertEquals("diagnostics", headline.checkId);
        assertEquals("boom (internal_error, HTTP 500)", headline.detail);
    }

    @Test public void clipboardUnavailableIsOnlyInformational() {
        String noClipboard = HEALTHY_DIAGNOSTICS.replace(
                "\"available\":true,\"diagnostic\":\"ready\"",
                "\"available\":false,\"diagnostic\":\"wl_clipboard_missing\"");
        HestiaHostStatus status = HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.DIAGNOSTICS, 200, noClipboard), false);
        assertEquals(HestiaHostStatus.Level.OK, status.getLevel());
        assertTrue(status.hasCode(HestiaHostStatus.Code.CLIPBOARD_UNAVAILABLE));
    }

    @Test public void equalityIgnoresProbeTime() throws Exception {
        HestiaHostStatus first = HestiaHostStatus.probe(FakeHost.healthy(), false);
        Thread.sleep(2);
        HestiaHostStatus second = HestiaHostStatus.probe(FakeHost.healthy(), false);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, HestiaHostStatus.probe(FakeHost.healthy()
                .respond(HestiaApi.DISPLAY_STATUS, 200, STUCK_DISPLAY), false));
        assertEquals(HestiaHostStatus.legacy(), HestiaHostStatus.probe(new FakeHost(), false));
    }
}
