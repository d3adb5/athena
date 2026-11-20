package com.limelight.fallback;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.utils.CacheHelper;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** The Android side of LocalFallback: settings, installed emulators, the Wi-Fi name, the notice. */
public final class LocalFallbackHelper {
    public static final String PREF_EMULATOR = "list_local_fallback";
    public static final String PREF_GAMES = "local_fallback_games";
    public static final String PREF_HOME_WIFI = "local_fallback_home_wifi";
    private static final String PROP_FORCE_PLACE = "debug.athena.fallback_net";

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

    /** The fallback is on (an emulator is chosen, installed or not): off keeps Athena as it was. */
    public static boolean isOn(Context context) {
        return !LocalEmulator.OFF.equals(prefs(context).getString(PREF_EMULATOR, LocalEmulator.OFF));
    }

    public static String homeWifi(Context context) {
        String name = prefs(context).getString(PREF_HOME_WIFI, "");
        return name == null ? "" : name.trim();
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean locationOn(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return lm.isLocationEnabled();
        }
        return lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /** Why Android hides the Wi-Fi name, as the notice says it, or null when nothing on Athena's side explains it. */
    public static String unreadableReason(Context context) {
        if (!hasLocationPermission(context)) {
            return context.getString(R.string.local_fallback_reason_no_permission);
        }
        if (!locationOn(context)) {
            return context.getString(R.string.local_fallback_reason_location_off);
        }
        return context.getString(R.string.local_fallback_reason_unreadable);
    }

    @SuppressWarnings("deprecation")
    public static LocalFallback.Place place(Context context) {
        boolean wifi = false, any = false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null) {
                    continue;
                }
                any = true;
                wifi |= caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            }
        }
        String ssid = null;
        if (wifi) {
            try {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm == null ? null : wm.getConnectionInfo();
                ssid = info == null ? null : info.getSSID();
            } catch (RuntimeException e) {
                ssid = null;
            }
        }
        LocalFallback.Place place = LocalFallback.place(homeWifi(context), ssid, wifi, any);
        LocalFallback.Place forced = forcedPlace();
        if (forced != null) {
            LimeLog.warning("Local fallback: Wi-Fi check forced to " + forced + " by " + PROP_FORCE_PLACE);
            place = forced;
        }
        // The SSID is the user's own home name or a place: never logged.
        LimeLog.info("Local fallback: " + place + " (wifi " + wifi + ", network " + any + ")");
        return place;
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

    /** Away, and the game can't be played here: names the host and the game instead of the usual error. */
    public static void showNotInstalled(Activity activity, String host, String game, String reason, Runnable retry,
                                        Runnable done) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.conn_error_title)
                .setMessage(activity.getString(R.string.local_fallback_not_installed, host, game, reason))
                .setPositiveButton(android.R.string.ok, (d, w) -> done.run())
                .setNeutralButton(R.string.local_fallback_retry, (d, w) -> retry.run())
                .setOnCancelListener(d -> done.run())
                .show();
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

    // For tests: adb shell setprop debug.athena.fallback_net away|home (empty to clear).
    private static LocalFallback.Place forcedPlace() {
        try {
            String value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, PROP_FORCE_PLACE);
            return value == null || value.isEmpty() ? null
                    : LocalFallback.Place.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
