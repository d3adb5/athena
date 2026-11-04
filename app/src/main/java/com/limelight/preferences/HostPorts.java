package com.limelight.preferences;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Ports Athena connects to on a host, from Settings > Host ports. 0 means automatic: the port the host names
 * (serverinfo, RTSP), else the well-known default. Applies to every host. Read on any thread.
 */
public final class HostPorts {
    public static final String HTTP = "port_http";
    public static final String HTTPS = "port_https";
    public static final String HESTIA_API = "port_hestia_api";
    public static final String RTSP = "port_rtsp";
    public static final String VIDEO = "port_video";
    public static final String CONTROL = "port_control";
    public static final String AUDIO = "port_audio";
    public static final String MIC = "port_mic";
    public static final String WOL = "ports_wol";

    public static final int DEFAULT_HTTP = 47989;
    public static final String DEFAULT_WOL = "9, 47009";
    // Hermes serves the Hestia API on its web UI port, six above GameStream HTTPS (47984 -> 47990).
    public static final int HESTIA_API_OFFSET = 6;

    private static volatile HostPorts current = new HostPorts(DEFAULT_HTTP, 0, 0, 0, 0, 0, 0, 0,
            parseList(DEFAULT_WOL));

    public final int http, https, hestiaApi, rtsp, video, control, audio, mic;
    private final int[] wol;

    HostPorts(int http, int https, int hestiaApi, int rtsp, int video, int control, int audio, int mic, int[] wol) {
        this.http = http;
        this.https = https;
        this.hestiaApi = hestiaApi;
        this.rtsp = rtsp;
        this.video = video;
        this.control = control;
        this.audio = audio;
        this.mic = mic;
        this.wol = wol;
    }

    public static HostPorts get() {
        return current;
    }

    public static void load(SharedPreferences prefs) {
        current = from(prefs);
    }

    public static HostPorts from(SharedPreferences prefs) {
        int http = port(prefs, HTTP);
        return new HostPorts(http == 0 ? DEFAULT_HTTP : http, port(prefs, HTTPS), port(prefs, HESTIA_API),
                port(prefs, RTSP), port(prefs, VIDEO), port(prefs, CONTROL), port(prefs, AUDIO), port(prefs, MIC),
                wolOrDefault(parseList(prefs.getString(WOL, DEFAULT_WOL))));
    }

    public static boolean isPortKey(String key) {
        return key != null && (key.startsWith("port_") || key.equals(WOL));
    }

    /** The Hestia API port: the setting, else the host's HTTPS port plus six. */
    public int hestiaApiFor(int hostHttpsPort) {
        return hestiaApi != 0 ? hestiaApi : hostHttpsPort + HESTIA_API_OFFSET;
    }

    /** UDP ports for Wake-on-LAN that are tried as they are (not offset by the host's HTTP port). */
    public int[] wolPorts() {
        return wol.clone();
    }

    /** A port setting as typed: blank = 0 (automatic), else 1-65535; -1 when invalid. */
    public static int parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            int port = Integer.parseInt(text.trim());
            return port >= 0 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "9, 47009" -> {9, 47009}; null when any entry is not a port. */
    public static int[] parseList(String text) {
        List<Integer> ports = new ArrayList<>();
        if (text != null) {
            for (String part : text.split("[,\\s]+")) {
                if (part.isEmpty()) {
                    continue;
                }
                int port = parse(part);
                if (port <= 0) {
                    return null;
                }
                ports.add(port);
            }
        }
        int[] out = new int[ports.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ports.get(i);
        }
        return out;
    }

    private static int[] wolOrDefault(int[] ports) {
        return ports != null ? ports : parseList(DEFAULT_WOL);
    }

    private static int port(SharedPreferences prefs, String key) {
        int port = parse(prefs.getString(key, ""));
        return Math.max(port, 0);
    }
}
