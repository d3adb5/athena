package com.limelight.fallback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.utils.CacheHelper;

import java.io.StringReader;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** The Android side of LocalFallback: settings, installed emulators, the device's networks, the notice. */
public final class LocalFallbackHelper {
    public static final String PREF_EMULATOR = "list_local_fallback";
    public static final String PREF_GAMES = "local_fallback_games";

    private LocalFallbackHelper() {}

    public static Intent launchIntent(LocalEmulator emulator, String steamAppId) {
        return new Intent(emulator.action)
                .setPackage(emulator.packageName)
                .putExtra(LocalEmulator.EXTRA_APP_ID, Integer.parseInt(steamAppId))
                .putExtra(LocalEmulator.EXTRA_GAME_SOURCE, LocalEmulator.SOURCE_STEAM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Installed with the launch action (an older version without it doesn't count). */
    public static boolean isInstalled(Context context, LocalEmulator emulator) {
        Intent probe = new Intent(emulator.action).setPackage(emulator.packageName);
        return context.getPackageManager().resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    public static List<LocalEmulator> installed(Context context) {
        List<LocalEmulator> list = new ArrayList<>();
        for (LocalEmulator e : LocalEmulator.values()) {
            if (isInstalled(context, e)) {
                list.add(e);
            }
        }
        return list;
    }

    /** The chosen emulator if it is still installed, else null (the fallback is then off). */
    public static LocalEmulator chosen(Context context) {
        LocalEmulator e = LocalEmulator.fromKey(prefs(context).getString(PREF_EMULATOR, LocalEmulator.OFF));
        return e != null && isInstalled(context, e) ? e : null;
    }

    public static Set<String> chosenGames(Context context) {
        return new HashSet<>(prefs(context).getStringSet(PREF_GAMES, Collections.emptySet()));
    }

    public static boolean wifiEnabled(Context context) {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            return wifi != null && wifi.isWifiEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static LocalFallback.Network network(Context context, ComputerDetails computer) {
        List<String> device = new ArrayList<>();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network n : cm.getAllNetworks()) {
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) {
                    continue;
                }
                for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof Inet4Address) {
                        device.add(la.getAddress().getHostAddress() + "/" + la.getPrefixLength());
                    }
                }
            }
        }
        List<String> host = new ArrayList<>();
        if (computer.localAddress != null) {
            host.add(computer.localAddress.address);
        }
        if (computer.manualAddress != null) {
            host.add(computer.manualAddress.address);
        }
        LocalFallback.Network result = LocalFallback.classify(device, host);
        LimeLog.info("Local fallback: network " + result + " (device " + device + ", host " + host + ")");
        return result;
    }

    /** Steam app id -> name of every Steam library game in the cached app lists of all hosts, by name. */
    public static TreeMap<String, String> steamGames(Context context) {
        TreeMap<String, String> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ComputerDatabaseManager db = new ComputerDatabaseManager(context);
        try {
            for (ComputerDetails c : db.getAllComputers()) {
                try {
                    String raw = CacheHelper.readInputStreamToString(
                            CacheHelper.openCacheFileForInput(context.getCacheDir(), "applist", c.uuid));
                    for (NvApp app : NvHTTP.getAppListByReader(new StringReader(raw))) {
                        String id = app.getSteamAppId();
                        if (app.isSteamLibraryGame() && !app.isSteamShortcut() && LocalEmulator.validSteamId(id)) {
                            byName.put(app.getAppName() + "\u0000" + id, id);
                        }
                    }
                } catch (Exception e) {
                    // No cached list for this host yet.
                }
            }
        } finally {
            db.close();
        }
        return byName;
    }

    /**
     * The notice: names the reason and the emulator. Away it counts down and starts the game; otherwise it waits for
     * a press. retry runs the shortcut again; done runs after any choice.
     */
    public static void showNotice(Activity activity, LocalEmulator emulator, String steamAppId, String game,
                                  String host, String reason, boolean countdown, Runnable retry, Runnable done) {
        final String title = activity.getString(R.string.local_fallback_title, game);
        final String play = activity.getString(R.string.local_fallback_play, emulator.label);
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        final Runnable launch = () -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            launch(activity, emulator, steamAppId, game, reason);
            done.run();
        };
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(activity.getString(countdown ? R.string.local_fallback_message_away
                        : R.string.local_fallback_message_ask, host, reason, emulator.label))
                .setPositiveButton(play, (d, w) -> launch.run())
                .setNeutralButton(R.string.local_fallback_retry, (d, w) -> {
                    finished[0] = true;
                    retry.run();
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    finished[0] = true;
                    done.run();
                })
                .setOnCancelListener(d -> {
                    finished[0] = true;
                    done.run();
                })
                .create();
        dialog.show();
        if (!countdown) {
            return;
        }
        final Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        button.requestFocus();
        new Runnable() {
            int left = LocalFallback.COUNTDOWN_S;

            @Override
            public void run() {
                if (finished[0] || !dialog.isShowing()) {
                    return;
                }
                if (left <= 0) {
                    dialog.dismiss();
                    launch.run();
                    return;
                }
                button.setText(String.format(Locale.ROOT, "%s (%d)", play, left--));
                handler.postDelayed(this, 1000);
            }
        }.run();
    }

    private static void launch(Context context, LocalEmulator emulator, String steamAppId, String game, String reason) {
        LimeLog.info("Local fallback: " + game + " (" + steamAppId + ") in " + emulator.label + ": " + reason);
        try {
            context.startActivity(launchIntent(emulator, steamAppId));
            Toast.makeText(context, context.getString(R.string.local_fallback_toast, game, emulator.label, reason),
                    Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, context.getString(R.string.local_fallback_missing, emulator.label),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
