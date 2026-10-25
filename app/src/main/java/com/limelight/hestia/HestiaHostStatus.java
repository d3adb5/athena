package com.limelight.hestia;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Health of a paired Hermes host as seen through the Hestia v1 API.
 *
 * The probe turns capabilities, per-client permissions, the host's own
 * preflight checks and the display state into a small list of findings, each
 * with a severity. The UI only has to map {@link Code} values to localised
 * text and {@link Level} values to an icon: it never re-interprets JSON.
 *
 * A non-Hermes host is {@link #legacy()} and gets no indicator at all, so
 * ordinary Moonlight/Sunshine/Apollo servers look exactly as before.
 */
public final class HestiaHostStatus {
    /** Ordered by severity. INFO findings never change the overall level. */
    public enum Level { INFO, OK, WARNING, ERROR }

    public enum Code {
        /** Nothing checked needs attention. */
        READY,
        /** Hermes speaks a Hestia protocol range this client does not. */
        PROTOCOL_INCOMPATIBLE,
        /** The host was Hermes, but its HTTPS API cannot be reached now. */
        API_UNREACHABLE,
        /** The API answered with an error; detail carries the host's message. */
        API_ERROR,
        /** Hermes does not accept this device's pairing certificate. */
        CERTIFICATE_REJECTED,
        /** Neither launch nor view: this client cannot stream at all. */
        NO_STREAM_PERMISSION,
        NO_LAUNCH_PERMISSION,
        NO_LIST_PERMISSION,
        /** detail: comma-separated subset of "keyboard,mouse,gamepad". */
        NO_INPUT_PERMISSION,
        /** No view permission, so diagnostics and display state are hidden. */
        NO_VIEW_PERMISSION,
        /** A diagnostics endpoint failed; checkId names it, detail explains. */
        DIAGNOSTICS_UNAVAILABLE,
        /** One host preflight check; checkId is the host's id, detail its message. */
        PREFLIGHT_CHECK,
        /** Physical monitors still off while nothing is streaming. */
        DISPLAY_LEFT_DISABLED,
        /** detail: the host's termination reason (e.g. "handshake_failed"). */
        LAST_SESSION_ENDED,
        /** detail: number of active sessions. */
        STREAMING,
        /** An app is parked on the host waiting for a client to resume. */
        AWAITING_RECONNECT,
        /** detail: "WIDTHxHEIGHT@FPS backend". */
        VIRTUAL_DISPLAY_ACTIVE,
        /** detail: the host's clipboard diagnostic code. */
        CLIPBOARD_UNAVAILABLE,
    }

    public static final class Item {
        public final Level level;
        public final Code code;
        /** Host check id for PREFLIGHT_CHECK / endpoint for DIAGNOSTICS_UNAVAILABLE. */
        public final String checkId;
        /** Host-provided text or a machine value, depending on {@link #code}. */
        public final String detail;

        Item(Level level, Code code, String checkId, String detail) {
            this.level = level;
            this.code = code;
            this.checkId = checkId != null ? checkId : "";
            this.detail = detail != null ? detail : "";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Item)) {
                return false;
            }
            Item that = (Item) o;
            return level == that.level && code == that.code
                    && checkId.equals(that.checkId) && detail.equals(that.detail);
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, code, checkId, detail);
        }

        @Override
        public String toString() {
            return level + " " + code + (checkId.isEmpty() ? "" : "[" + checkId + "]")
                    + (detail.isEmpty() ? "" : ": " + detail);
        }
    }

    /** A recent session end is only worth surfacing for this long. */
    static final long RECENT_SESSION_END_MS = 10 * 60 * 1000;

    private static final HestiaHostStatus LEGACY = new HestiaHostStatus(
            false, "", Collections.<Item>emptyList(), false, null, null, 0);

    private final boolean hermes;
    private final String serverVersion;
    private final List<Item> items;
    private final Level level;
    private final boolean displayRecoveryOffered;
    private final HestiaCapabilities capabilities;
    private final HestiaPermissions permissions;
    private final long checkedAtMs;

    private HestiaHostStatus(boolean hermes, String serverVersion, List<Item> items,
            boolean displayRecoveryOffered, HestiaCapabilities capabilities,
            HestiaPermissions permissions, long checkedAtMs) {
        this.hermes = hermes;
        this.serverVersion = serverVersion != null ? serverVersion : "";
        this.items = Collections.unmodifiableList(items);
        this.displayRecoveryOffered = displayRecoveryOffered;
        this.capabilities = capabilities;
        this.permissions = permissions;
        this.checkedAtMs = checkedAtMs;

        Level overall = Level.OK;
        for (Item item : items) {
            if (item.level.compareTo(overall) > 0) {
                overall = item.level;
            }
        }
        this.level = overall;
    }

    /** A host without (or no longer with) the Hestia API. */
    public static HestiaHostStatus legacy() {
        return LEGACY;
    }

    /**
     * Query and evaluate a host. Never throws: every failure becomes either
     * {@link #legacy()} (not Hermes) or a finding on a Hermes status.
     *
     * @param knownHermes the host answered as Hermes before, so a transport
     *                    failure is an error rather than "not Hermes"
     */
    public static HestiaHostStatus probe(HestiaApi.Transport transport, boolean knownHermes) {
        long now = System.currentTimeMillis();

        HestiaApiResponse capsResponse;
        try {
            capsResponse = transport.get(HestiaApi.CAPABILITIES);
        } catch (IOException | RuntimeException e) {
            return knownHermes ? failure(Code.API_UNREACHABLE, "", now) : legacy();
        }
        if (!capsResponse.isHttpSuccess()) {
            // 404 means the host is no longer running Hermes (e.g. swapped
            // back to Apollo/Sunshine): show it as a plain host again.
            if (!knownHermes || capsResponse.getHttpCode() == 404) {
                return legacy();
            }
            return failure(Code.API_ERROR, capsResponse.describeError(), now);
        }

        HestiaCapabilities caps = HestiaCapabilities.parse(capsResponse.getBody());
        if (!caps.isHermes()) {
            return legacy();
        }

        Builder builder = new Builder(caps, now);
        if (caps.isIncompatibleHermes()) {
            builder.add(Level.ERROR, Code.PROTOCOL_INCOMPATIBLE, null,
                    caps.getMinClientProtocol() + "-" + caps.getMaxClientProtocol());
            return builder.build();
        }

        HestiaApiResponse permissionsResponse;
        try {
            permissionsResponse = transport.get(HestiaApi.CLIENT_PERMISSIONS);
        } catch (IOException | RuntimeException e) {
            builder.add(Level.ERROR, Code.API_UNREACHABLE, null, null);
            return builder.build();
        }
        if (permissionsResponse.getHttpCode() == 401) {
            builder.add(Level.ERROR, Code.CERTIFICATE_REJECTED, null,
                    permissionsResponse.getErrorMessage());
            return builder.build();
        }
        HestiaPermissions permissions = HestiaPermissions.parse(permissionsResponse);
        if (permissions == null) {
            builder.add(Level.ERROR, Code.API_ERROR, HestiaApi.CLIENT_PERMISSIONS,
                    permissionsResponse.describeError());
            return builder.build();
        }
        builder.permissions = permissions;
        evaluatePermissions(builder, permissions);

        if (!permissions.canViewStreams()) {
            builder.add(Level.INFO, Code.NO_VIEW_PERMISSION, null, null);
            return builder.build();
        }

        HestiaDiagnostics diagnostics = null;
        try {
            HestiaApiResponse response = transport.get(HestiaApi.DIAGNOSTICS);
            diagnostics = HestiaDiagnostics.parse(response);
            if (diagnostics == null) {
                builder.add(Level.WARNING, Code.DIAGNOSTICS_UNAVAILABLE, "diagnostics",
                        response.describeError());
            }
        } catch (IOException | RuntimeException e) {
            builder.add(Level.WARNING, Code.DIAGNOSTICS_UNAVAILABLE, "diagnostics", null);
        }

        HestiaDisplayStatus display = null;
        try {
            HestiaApiResponse response = transport.get(HestiaApi.DISPLAY_STATUS);
            display = HestiaDisplayStatus.parse(response);
            if (display == null) {
                builder.add(Level.WARNING, Code.DIAGNOSTICS_UNAVAILABLE, "display",
                        response.describeError());
            }
        } catch (IOException | RuntimeException e) {
            builder.add(Level.WARNING, Code.DIAGNOSTICS_UNAVAILABLE, "display", null);
        }

        if (diagnostics != null) {
            evaluateDiagnostics(builder, diagnostics);
        }
        if (display != null) {
            evaluateDisplay(builder, caps, permissions, diagnostics, display);
        }
        return builder.build();
    }

    private static void evaluatePermissions(Builder builder, HestiaPermissions p) {
        if (!p.canLaunchApps() && !p.canViewStreams()) {
            builder.add(Level.ERROR, Code.NO_STREAM_PERMISSION, null, null);
        } else if (!p.canLaunchApps()) {
            builder.add(Level.WARNING, Code.NO_LAUNCH_PERMISSION, null, null);
        }
        if (!p.canListApps()) {
            builder.add(Level.WARNING, Code.NO_LIST_PERMISSION, null, null);
        }

        List<String> missingInputs = new ArrayList<>();
        if (!p.canUseKeyboard()) {
            missingInputs.add("keyboard");
        }
        if (!p.canUseMouse()) {
            missingInputs.add("mouse");
        }
        if (!p.canUseGamepad()) {
            missingInputs.add("gamepad");
        }
        if (!missingInputs.isEmpty()) {
            builder.add(Level.WARNING, Code.NO_INPUT_PERMISSION, null, join(missingInputs));
        }
    }

    private static void evaluateDiagnostics(Builder builder, HestiaDiagnostics d) {
        boolean sawFailure = false;
        for (HestiaDiagnostics.Check check : d.getChecks()) {
            Level level;
            switch (check.status) {
                case FAIL:
                    level = Level.ERROR;
                    sawFailure = true;
                    break;
                case WARN:
                    level = Level.WARNING;
                    break;
                case OK:
                    level = Level.OK;
                    break;
                default:
                    level = Level.INFO;
                    break;
            }
            builder.add(level, Code.PREFLIGHT_CHECK, check.id, check.message);
        }
        if (Boolean.FALSE.equals(d.isPreflightReady()) && !sawFailure) {
            builder.add(Level.ERROR, Code.PREFLIGHT_CHECK, "preflight", null);
        }

        if (d.getActiveSessions() > 0) {
            builder.add(Level.INFO, Code.STREAMING, null, Integer.toString(d.getActiveSessions()));
        } else if (d.isAwaitingReconnect()) {
            builder.add(Level.INFO, Code.AWAITING_RECONNECT, null, null);
        }

        String reason = d.getLastTermination();
        if (d.getActiveSessions() == 0 && d.getTotalEnded() > 0 && reason != null
                && d.getMsSinceLastEnd() > 0 && d.getMsSinceLastEnd() < RECENT_SESSION_END_MS) {
            switch (reason) {
                case "handshake_failed":
                case "protocol_error":
                case "permission_revoked":
                    builder.add(Level.WARNING, Code.LAST_SESSION_ENDED, null, reason);
                    break;
                case "client_lost":
                    builder.add(Level.INFO, Code.LAST_SESSION_ENDED, null, reason);
                    break;
                default:
                    // client_quit / server_stopped / unknown are normal endings.
                    break;
            }
        }

        if (Boolean.FALSE.equals(d.isClipboardAvailable())) {
            builder.add(Level.INFO, Code.CLIPBOARD_UNAVAILABLE, null, d.getClipboardDiagnostic());
        }
    }

    private static void evaluateDisplay(Builder builder, HestiaCapabilities caps,
            HestiaPermissions permissions, HestiaDiagnostics diagnostics,
            HestiaDisplayStatus display) {
        if (display.isVirtualDisplayActive()) {
            String mode = display.getWidth() > 0 && display.getHeight() > 0
                    ? display.getWidth() + "x" + display.getHeight()
                        + (display.getFps() > 0 ? "@" + display.getFps() : "")
                    : "";
            builder.add(Level.INFO, Code.VIRTUAL_DISPLAY_ACTIVE, null,
                    (mode + " " + display.getBackend()).trim());
        }

        // Only judge "left disabled" when diagnostics prove nothing is using
        // the virtual display: during a stream or a parked app the physical
        // monitors are meant to be off.
        if (display.isPhysicalDisplayDisabled() && diagnostics != null
                && diagnostics.getActiveSessions() == 0 && !diagnostics.isAwaitingReconnect()) {
            builder.add(Level.WARNING, Code.DISPLAY_LEFT_DISABLED, null, null);
            builder.displayRecoveryOffered = caps.hasDisplayRecovery()
                    && permissions.canRecoverDisplay();
        }
    }

    private static HestiaHostStatus failure(Code code, String detail, long now) {
        Builder builder = new Builder(null, now);
        builder.add(Level.ERROR, code, null, detail);
        return builder.build();
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private static final class Builder {
        final HestiaCapabilities caps;
        final long checkedAtMs;
        final List<Item> items = new ArrayList<>();
        HestiaPermissions permissions;
        boolean displayRecoveryOffered;

        Builder(HestiaCapabilities caps, long checkedAtMs) {
            this.caps = caps;
            this.checkedAtMs = checkedAtMs;
        }

        void add(Level level, Code code, String checkId, String detail) {
            items.add(new Item(level, code, checkId, detail));
        }

        HestiaHostStatus build() {
            boolean needsAttention = false;
            for (Item item : items) {
                if (item.level == Level.WARNING || item.level == Level.ERROR) {
                    needsAttention = true;
                    break;
                }
            }
            if (!needsAttention) {
                // First, so the stable sort below keeps it as the headline.
                items.add(0, new Item(Level.OK, Code.READY, null, null));
            }
            // Most severe first; INFO last. The sort is stable, so host check
            // order is preserved within a level.
            Collections.sort(items, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return rank(b.level) - rank(a.level);
                }
            });
            return new HestiaHostStatus(true, caps != null ? caps.getServerVersion() : "",
                    items, displayRecoveryOffered, caps, permissions, checkedAtMs);
        }

        private static int rank(Level level) {
            // OK findings (passed checks) read better before neutral notes.
            switch (level) {
                case ERROR: return 3;
                case WARNING: return 2;
                case OK: return 1;
                default: return 0;
            }
        }
    }

    /** True when the host identified as Hermes; false for ordinary hosts. */
    public boolean isHermes() { return hermes; }
    /** Overall severity; meaningful only when {@link #isHermes()}. */
    public Level getLevel() { return level; }
    public String getServerVersion() { return serverVersion; }
    public List<Item> getItems() { return items; }

    /** The finding to summarise the host with: the first (most severe) one. */
    public Item getHeadline() {
        return items.isEmpty() ? null : items.get(0);
    }

    public boolean hasCode(Code code) {
        for (Item item : items) {
            if (item.code == code) {
                return true;
            }
        }
        return false;
    }

    /** Display recovery is both needed and allowed for this client. */
    public boolean isDisplayRecoveryOffered() { return displayRecoveryOffered; }

    /** Null when the host could not be identified or was incompatible. */
    public HestiaCapabilities getCapabilities() { return capabilities; }
    /** Null when permissions could not be read. */
    public HestiaPermissions getPermissions() { return permissions; }
    /** Wall-clock time of the probe (not part of equality). */
    public long getCheckedAtMs() { return checkedAtMs; }

    /** Same findings (ignores the probe time), so the UI only redraws on change. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HestiaHostStatus)) {
            return false;
        }
        HestiaHostStatus that = (HestiaHostStatus) o;
        return hermes == that.hermes && displayRecoveryOffered == that.displayRecoveryOffered
                && serverVersion.equals(that.serverVersion) && items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hermes, displayRecoveryOffered, serverVersion, items);
    }

    @Override
    public String toString() {
        if (!hermes) {
            return "legacy";
        }
        return "Hermes " + serverVersion + " " + level + " " + items;
    }
}
