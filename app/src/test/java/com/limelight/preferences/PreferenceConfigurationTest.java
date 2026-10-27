package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.shadows.ShadowMoonBridge;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {ShadowMoonBridge.class})
public class PreferenceConfigurationTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    @Test
    public void readPreferencesLoadsMicrophoneSettings() {
        prefs.edit()
                .putBoolean(PreferenceConfiguration.ENABLE_MICROPHONE_PREF_STRING, true)
                .putString(PreferenceConfiguration.MICROPHONE_DEVICE_PREF_STRING, "7")
                .commit();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context, prefs);

        assertTrue(config.enableMicrophone);
        assertEquals(7, config.microphoneDeviceId);
    }

    @Test
    public void readPreferencesResetsInvalidMicrophoneDeviceSelection() {
        prefs.edit()
                .putString(PreferenceConfiguration.MICROPHONE_DEVICE_PREF_STRING, "invalid")
                .commit();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context, prefs);

        assertEquals(0, config.microphoneDeviceId);
        assertEquals("0", prefs.getString(PreferenceConfiguration.MICROPHONE_DEVICE_PREF_STRING, null));
    }

    @Test
    public void readPreferencesMigratesNoiseSuppressionCheckbox() {
        prefs.edit().putBoolean("checkbox_mic_noise_suppress", true).commit();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context, prefs);

        assertTrue(config.micNoiseSuppress);
        assertNull(config.micDeepFilter);
        assertEquals("standard", prefs.getString(PreferenceConfiguration.MIC_NOISE_SUPPRESS_PREF_STRING, null));
        assertFalse(prefs.contains("checkbox_mic_noise_suppress"));
    }

    @Test
    public void readPreferencesUsesMumlaDeepFilterDefaults() {
        prefs.edit().putString(PreferenceConfiguration.MIC_NOISE_SUPPRESS_PREF_STRING, "deepfilter").commit();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context, prefs);

        assertFalse(config.micNoiseSuppress);
        assertNotNull(config.micDeepFilter);
        assertEquals(100f, config.micDeepFilter.attenLimitDb, 0f);
        assertEquals(0f, config.micDeepFilter.postFilterBeta, 0f);
        assertEquals(-15f, config.micDeepFilter.minDbThresh, 0f);
        assertEquals(35f, config.micDeepFilter.maxDbErbThresh, 0f);
        assertEquals(35f, config.micDeepFilter.maxDbDfThresh, 0f);
    }

    @Test
    public void readPreferencesLoadsDeepFilterSettings() {
        prefs.edit()
                .putString(PreferenceConfiguration.MIC_NOISE_SUPPRESS_PREF_STRING, "deepfilter")
                .putInt(PreferenceConfiguration.MIC_DF_ATTEN_LIMIT_PREF_STRING, 30)
                .putInt(PreferenceConfiguration.MIC_DF_POST_FILTER_PREF_STRING, 20)
                .putInt(PreferenceConfiguration.MIC_DF_MIN_DB_PREF_STRING, -10)
                .putInt(PreferenceConfiguration.MIC_DF_MAX_ERB_DB_PREF_STRING, 30)
                .putInt(PreferenceConfiguration.MIC_DF_MAX_DF_DB_PREF_STRING, 20)
                .commit();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context, prefs);

        assertEquals(30f, config.micDeepFilter.attenLimitDb, 0f);
        assertEquals(0.02f, config.micDeepFilter.postFilterBeta, 1e-6f);
        assertEquals(-10f, config.micDeepFilter.minDbThresh, 0f);
        assertEquals(30f, config.micDeepFilter.maxDbErbThresh, 0f);
        assertEquals(20f, config.micDeepFilter.maxDbDfThresh, 0f);
    }
}
