package com.limelight;

import android.app.Application;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.preferences.HostPorts;
import com.limelight.profiles.ProfilesManager;

public class AthenaApplication extends Application {
    // Kept here: SharedPreferences holds its listeners weakly.
    private final SharedPreferences.OnSharedPreferenceChangeListener portsListener = (prefs, key) -> {
        if (key == null || HostPorts.isPortKey(key)) {
            HostPorts.load(prefs);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        HostPorts.load(prefs);
        prefs.registerOnSharedPreferenceChangeListener(portsListener);
        ProfilesManager profilesManager = ProfilesManager.getInstance();
        if (!profilesManager.load(this)) {
            Toast.makeText(this, R.string.profile_manager_failed_to_load, Toast.LENGTH_LONG).show();
        }
    }
}