package com.limelight.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.preference.PreferenceManager;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;

import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps one .art file per Steam library game of each host in the folder the user picked
 * (e.g. ROMs/moonlight for a frontend), refreshed whenever an app list arrives.
 */
public final class SteamLibrarySync {
    public static final String FOLDER_PREF = "steam_library_folder";
    private static final String STATE = "steam_library_export";
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    /** After a stream: Ares parks its session as the client leaves, then reloads its library (at most every 10 s). */
    private static final long[] AFTER_STREAM_S = {3, 15};

    private SteamLibrarySync() {}

    public static Uri folder(Context context) {
        String uri = PreferenceManager.getDefaultSharedPreferences(context).getString(FOLDER_PREF, "");
        return uri.isEmpty() ? null : Uri.parse(uri);
    }

    /** After an app list arrived from a host; nothing without a folder. */
    public static void syncAsync(Context context, ComputerDetails computer, String rawAppList) {
        final Context app = context.getApplicationContext();
        if (folder(app) == null || computer.uuid == null) {
            return;
        }
        final String uuid = computer.uuid, name = computer.name;
        EXECUTOR.execute(() -> sync(app, uuid, name, rawAppList, false));
    }

    /**
     * As a stream starts, from any launch path (a frontend's shortcut never opens the app grid): the host's current
     * app list, so games installed or uninstalled since show up. Ares rescans its library on this request.
     */
    public static void refreshAsync(Context context, ComputerDetails computer, String uniqueId) {
        final Context app = context.getApplicationContext();
        if (folder(app) == null || computer.uuid == null) {
            return;
        }
        final String uuid = computer.uuid, name = computer.name;
        final NvHTTP http;
        try {
            http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort, uniqueId,
                    computer.serverCert, PlatformBinding.getCryptoProvider(app));
        } catch (Exception e) {
            LimeLog.warning("Steam library: no address for " + name + ": " + e);
            return;
        }
        EXECUTOR.execute(() -> fetch(app, uuid, name, http));
    }

    /** As a stream ends (disconnect or quit): games installed during it show up without another launch. */
    public static void refreshAfterStream(Context context, String uuid, String name, NvHTTP http) {
        final Context app = context.getApplicationContext();
        if (folder(app) == null || uuid == null || http == null) {
            return;
        }
        for (long delay : AFTER_STREAM_S) {
            EXECUTOR.schedule(() -> fetch(app, uuid, name, http), delay, TimeUnit.SECONDS);
        }
    }

    private static void fetch(Context app, String uuid, String name, NvHTTP http) {
        try {
            sync(app, uuid, name, http.getAppListRaw(), false);
        } catch (Exception e) {
            LimeLog.warning("Steam library: app list from " + name + " failed: " + e);
        }
    }

    /** Every paired host, from its cached app list (a newly picked folder). */
    public static void syncAllAsync(Context context) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            ComputerDatabaseManager db = new ComputerDatabaseManager(app);
            try {
                for (ComputerDetails computer : db.getAllComputers()) {
                    try {
                        String raw = CacheHelper.readInputStreamToString(
                                CacheHelper.openCacheFileForInput(app.getCacheDir(), "applist", computer.uuid));
                        sync(app, computer.uuid, computer.name, raw, true);
                    } catch (Exception e) {
                        LimeLog.info("Steam library: no app list cached for " + computer.name);
                    }
                }
            } finally {
                db.close();
            }
        });
    }

    private static void sync(Context context, String hostUuid, String hostName, String rawAppList, boolean force) {
        Uri tree = folder(context);
        if (tree == null) {
            return;
        }
        Map<String, String> files;
        try {
            List<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(rawAppList));
            files = SteamLibraryExport.files(hostUuid, hostName, apps);
        } catch (Exception e) {
            LimeLog.warning("Steam library: unreadable app list: " + e);
            return;
        }

        SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        String signature = tree + "\n" + files.hashCode();
        if (!force && signature.equals(state.getString("sig:" + hostUuid, null))) {
            return;
        }
        Set<String> previous = state.getStringSet("files:" + hostUuid, new HashSet<>());
        if (files.isEmpty() && previous.isEmpty()) {
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        try {
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            Map<String, Uri> existing = children(resolver, tree);
            int written = 0, removed = 0;
            for (Map.Entry<String, String> file : files.entrySet()) {
                Uri document = existing.get(file.getKey());
                if (document == null) {
                    document = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", file.getKey());
                }
                if (document == null) {
                    continue;
                }
                try (OutputStream out = resolver.openOutputStream(document, "wt")) {
                    out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                }
                written++;
            }
            // Only files this host's export made: anything else in the folder is the user's.
            for (String old : previous) {
                Uri document = existing.get(old);
                if (!files.containsKey(old) && document != null && DocumentsContract.deleteDocument(resolver, document)) {
                    removed++;
                }
            }
            state.edit()
                    .putString("sig:" + hostUuid, signature)
                    .putStringSet("files:" + hostUuid, new HashSet<>(files.keySet()))
                    .apply();
            LimeLog.info("Steam library: " + written + " files for " + hostName + ", " + removed + " removed");
        } catch (Exception e) {
            LimeLog.warning("Steam library: export to " + tree + " failed: " + e);
        }
    }

    private static Map<String, Uri> children(ContentResolver resolver, Uri tree) {
        Map<String, Uri> children = new HashMap<>();
        Uri listing = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        try (Cursor cursor = resolver.query(listing, new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                children.put(cursor.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)));
            }
        }
        return children;
    }
}
