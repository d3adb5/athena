package com.limelight.hestia;

import java.io.IOException;

/** Hestia v1 endpoint paths (relative to the Hermes web/API HTTPS base). */
public final class HestiaApi {
    private HestiaApi() {}

    public static final String CAPABILITIES = "api/hestia/v1/capabilities";
    public static final String CLIENT_PERMISSIONS = "api/hestia/v1/client/permissions";
    public static final String DIAGNOSTICS = "api/hestia/v1/diagnostics";
    public static final String DISPLAY_STATUS = "api/hestia/v1/display/status";
    public static final String DISPLAY_RECOVER = "api/hestia/v1/display/recover";
    public static final String SESSION_PREPARE = "api/hestia/v1/session/prepare";

    /**
     * Minimal request surface so status evaluation can be unit tested without
     * TLS. Implementations must authenticate with the paired client
     * certificate and pin the host certificate; an HTTP error is returned, not
     * thrown. Only transport failures (timeouts, refused connections, TLS
     * errors) raise IOException.
     */
    public interface Transport {
        HestiaApiResponse get(String path) throws IOException;
    }
}
