package com.limelight.hestia;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed {@code GET /api/hestia/v1/diagnostics} response: the host's own
 * streaming-readiness preflight plus the bits of runtime state that explain
 * a broken or degraded stream.
 */
public final class HestiaDiagnostics {
    public enum CheckStatus { OK, WARN, FAIL, UNKNOWN }

    public static final class Check {
        public final String id;
        public final CheckStatus status;
        public final String message;

        Check(String id, CheckStatus status, String message) {
            this.id = id;
            this.status = status;
            this.message = message;
        }
    }

    private final Boolean preflightReady;
    private final List<Check> checks;
    private final String encoderName;
    private final boolean encoderHardware;
    private final boolean encoderFellBackToSoftware;
    private final int activeSessions;
    private final boolean awaitingReconnect;
    private final String lastTermination;
    private final long msSinceLastEnd;
    private final long totalEnded;
    private final Boolean clipboardAvailable;
    private final String clipboardDiagnostic;

    private HestiaDiagnostics(Boolean preflightReady, List<Check> checks,
            String encoderName, boolean encoderHardware, boolean encoderFellBackToSoftware,
            int activeSessions, boolean awaitingReconnect, String lastTermination,
            long msSinceLastEnd, long totalEnded,
            Boolean clipboardAvailable, String clipboardDiagnostic) {
        this.preflightReady = preflightReady;
        this.checks = Collections.unmodifiableList(checks);
        this.encoderName = encoderName;
        this.encoderHardware = encoderHardware;
        this.encoderFellBackToSoftware = encoderFellBackToSoftware;
        this.activeSessions = activeSessions;
        this.awaitingReconnect = awaitingReconnect;
        this.lastTermination = lastTermination;
        this.msSinceLastEnd = msSinceLastEnd;
        this.totalEnded = totalEnded;
        this.clipboardAvailable = clipboardAvailable;
        this.clipboardDiagnostic = clipboardDiagnostic;
    }

    /** Returns null unless the response is a successful, well-formed document. */
    public static HestiaDiagnostics parse(HestiaApiResponse response) {
        if (response == null || !response.isOk()) {
            return null;
        }
        JsonObject obj = response.jsonObject();
        if (obj == null) {
            return null;
        }

        Boolean ready = null;
        List<Check> checks = new ArrayList<>();
        if (HestiaCapabilities.isObject(obj, "preflight")) {
            JsonObject preflight = obj.getAsJsonObject("preflight");
            if (HestiaCapabilities.isBoolean(preflight, "ready")) {
                ready = preflight.get("ready").getAsBoolean();
            }
            if (preflight.has("checks") && preflight.get("checks").isJsonArray()) {
                for (JsonElement element : preflight.getAsJsonArray("checks")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject check = element.getAsJsonObject();
                    String id = HestiaCapabilities.string(check, "id");
                    String status = HestiaCapabilities.string(check, "status");
                    String message = HestiaCapabilities.string(check, "message");
                    if (id == null || status == null) {
                        continue;
                    }
                    checks.add(new Check(id, parseStatus(status), message != null ? message : ""));
                }
            }
        }

        String encoderName = null;
        boolean encoderHardware = false;
        boolean fellBack = false;
        int activeSessions = 0;
        boolean awaitingReconnect = false;
        String lastTermination = null;
        long msSinceLastEnd = 0;
        long totalEnded = 0;
        if (HestiaCapabilities.isObject(obj, "runtime")) {
            JsonObject runtime = obj.getAsJsonObject("runtime");
            if (HestiaCapabilities.isObject(runtime, "encoder")) {
                JsonObject encoder = runtime.getAsJsonObject("encoder");
                encoderName = HestiaCapabilities.string(encoder, "name");
                encoderHardware = HestiaCapabilities.isTrue(encoder, "hardware");
                fellBack = HestiaCapabilities.isTrue(encoder, "fell_back_to_software");
            }
            if (HestiaCapabilities.isObject(runtime, "sessions")) {
                JsonObject sessions = runtime.getAsJsonObject("sessions");
                Integer active = HestiaCapabilities.integer(sessions, "active");
                activeSessions = active != null && active > 0 ? active : 0;
                awaitingReconnect = HestiaCapabilities.isTrue(sessions, "awaiting_reconnect");
                lastTermination = HestiaCapabilities.string(sessions, "last_termination");
                Long since = HestiaCapabilities.longValue(sessions, "ms_since_last_end");
                msSinceLastEnd = since != null && since > 0 ? since : 0;
                Long ended = HestiaCapabilities.longValue(sessions, "total_ended");
                totalEnded = ended != null && ended > 0 ? ended : 0;
            }
        }

        Boolean clipboardAvailable = null;
        String clipboardDiagnostic = null;
        if (HestiaCapabilities.isObject(obj, "dependencies")) {
            JsonObject dependencies = obj.getAsJsonObject("dependencies");
            if (HestiaCapabilities.isObject(dependencies, "clipboard")) {
                JsonObject clipboard = dependencies.getAsJsonObject("clipboard");
                if (HestiaCapabilities.isBoolean(clipboard, "available")) {
                    clipboardAvailable = clipboard.get("available").getAsBoolean();
                }
                clipboardDiagnostic = HestiaCapabilities.string(clipboard, "diagnostic");
            }
        }

        return new HestiaDiagnostics(ready, checks, encoderName, encoderHardware, fellBack,
                activeSessions, awaitingReconnect, lastTermination, msSinceLastEnd, totalEnded,
                clipboardAvailable, clipboardDiagnostic);
    }

    private static CheckStatus parseStatus(String status) {
        switch (status) {
            case "ok":
                return CheckStatus.OK;
            case "warn":
                return CheckStatus.WARN;
            case "fail":
                return CheckStatus.FAIL;
            default:
                return CheckStatus.UNKNOWN;
        }
    }

    /** Null when the host did not report a preflight verdict. */
    public Boolean isPreflightReady() { return preflightReady; }
    public List<Check> getChecks() { return checks; }
    public String getEncoderName() { return encoderName; }
    public boolean isEncoderHardware() { return encoderHardware; }
    public boolean didEncoderFallBackToSoftware() { return encoderFellBackToSoftware; }
    public int getActiveSessions() { return activeSessions; }
    public boolean isAwaitingReconnect() { return awaitingReconnect; }
    public String getLastTermination() { return lastTermination; }
    /** Milliseconds since the last session ended; 0 if none ended yet. */
    public long getMsSinceLastEnd() { return msSinceLastEnd; }
    public long getTotalEnded() { return totalEnded; }
    public Boolean isClipboardAvailable() { return clipboardAvailable; }
    public String getClipboardDiagnostic() { return clipboardDiagnostic; }
}
