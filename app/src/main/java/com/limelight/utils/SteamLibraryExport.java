package com.limelight.utils;

import com.limelight.nvstream.http.NvApp;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The .art launcher files of a host's Steam library games (Ares sends SteamAppId on those apps).
 * Launchers open them with ShortcutTrampoline; [steamappid] lets them fetch Steam's artwork.
 */
public final class SteamLibraryExport {
    public static final String EXTENSION = ".art";

    private SteamLibraryExport() {}

    /** File name -> contents, one per Steam library game, in the app list's order. */
    public static Map<String, String> files(String hostUuid, String hostName, List<NvApp> apps) {
        Map<String, String> files = new LinkedHashMap<>();
        Set<String> lowerNames = new HashSet<>();
        for (NvApp app : apps) {
            if (!app.isSteamLibraryGame() || app.getAppUUID() == null || app.getAppUUID().isEmpty()) {
                continue;
            }
            String base = fileBase(app.getAppName());
            String name = base + EXTENSION;
            if (base.isEmpty() || !lowerNames.add(name.toLowerCase(Locale.ROOT))) {
                name = (base.isEmpty() ? "Steam" : base) + " (" + app.getSteamAppId() + ")" + EXTENSION;
                lowerNames.add(name.toLowerCase(Locale.ROOT));
            }
            files.put(name, contents(hostUuid, hostName, app));
        }
        return files;
    }

    /** [steamappid] first: launchers match the first app id line, and no [app_id] (Ares' id) at all. */
    static String contents(String hostUuid, String hostName, NvApp app) {
        return "# Athena: a Steam game through Ares\n" +
                "[steamappid] " + app.getSteamAppId() + "\n" +
                "[host_uuid] " + hostUuid + "\n" +
                "[host_name] " + oneLine(hostName) + "\n" +
                "[app_uuid] " + app.getAppUUID() + "\n" +
                "[app_name] " + oneLine(app.getAppName()) + "\n";
    }

    /** A file name launchers show as the title: characters storage refuses dropped, trimmed. */
    static String fileBase(String title) {
        String name = oneLine(title).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").replaceAll("\\s+", " ").trim();
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        return name.length() > 120 ? name.substring(0, 120).trim() : name;
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
