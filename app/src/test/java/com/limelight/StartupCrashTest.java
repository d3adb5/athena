package com.limelight;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfilesManager;
import com.limelight.utils.UiHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.*;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class StartupCrashTest {
    private Context context;


    @Before
    public void setUp() {
        // Reset ProfilesManager instance using reflection since it's package-private
        try {
            java.lang.reflect.Field instanceField = ProfilesManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            // Ignore reflection errors
        }
        context = ApplicationProvider.getApplicationContext();

        // Clean up any existing state
        File profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
    }

    @Test
    public void testNativeLibraryLoadingFailure() {
        // Test startup when native libraries fail to load
        // The shadow should handle this, but let's verify
        try {
            PcView activity = Robolectric.buildActivity(PcView.class).create().get();
            assertNotNull("Activity should handle native library issues", activity);
        } catch (UnsatisfiedLinkError e) {
            fail("Native library loading should be handled by shadow: " + e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception during native library test: " + e.getMessage());
        }
    }

    @Test
    public void testGLSurfaceViewInitialization() {
        // Test GL surface view initialization that happens in PcView.onCreate
        try {
            PcView activity = Robolectric.buildActivity(PcView.class).create().get();

            // Verify activity doesn't crash during GL initialization
            assertNotNull("Activity should survive GL initialization", activity);
            assertFalse("Activity should not be finishing after GL init", activity.isFinishing());
        } catch (Exception e) {
            fail("GL surface view initialization should not crash: " + e.getMessage());
        }
    }

    @Test
    public void testPreferenceConfigurationCrash() {
        // Test PreferenceConfiguration reading that might cause crashes
        try {
            PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
            assertNotNull("PreferenceConfiguration should be readable", config);
        } catch (Exception e) {
            fail("PreferenceConfiguration should not crash: " + e.getMessage());
        }
    }

    @Test
    public void testUiHelperCrash() {
        // Test UiHelper methods that might cause crashes
        try {
            // UiHelper.setLocale requires Activity, so we'll test with a mock activity
            PcView activity = Robolectric.buildActivity(PcView.class).create().get();
            UiHelper.setLocale(activity);
            // Should not crash
        } catch (Exception e) {
            fail("UiHelper.setLocale should not crash: " + e.getMessage());
        }
    }

    @Test
    public void testComputerManagerServiceBinding() {
        // Test service binding that happens during startup
        try {
            PcView activity = Robolectric.buildActivity(PcView.class).create().get();

            // Service binding happens asynchronously, but creation should not crash
            assertNotNull("Activity should handle service binding", activity);
        } catch (Exception e) {
            fail("Service binding should not crash activity creation: " + e.getMessage());
        }
    }

    @Test
    public void testSharedPreferencesCorruption() {
        // Test startup with corrupted shared preferences
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        // Use the real settings key so invalid bitrate is actually parsed.
        editor.putInt("seekbar_bitrate_kbps", -1);
        editor.apply();

        try {
            PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
            assertNotNull("Should handle corrupted preferences", config);
            assertTrue("Invalid bitrate must fall back to a positive default", config.bitrate > 0);
        } catch (Exception e) {
            fail("Should handle corrupted shared preferences gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testMissingRequiredIntentExtras() {
        // Test AppView with missing required intent extras
        Intent intent = new Intent();
        // Missing NAME_EXTRA and UUID_EXTRA

        try {
            AppView activity = Robolectric.buildActivity(AppView.class, intent).create().get();
            assertTrue("An AppView without a host identity must finish", activity.isFinishing());
        } catch (Exception e) {
            fail("Missing intent extras should be handled gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testInvalidUuidInIntent() {
        // Test AppView with invalid UUID
        Intent intent = new Intent();
        intent.putExtra(AppView.NAME_EXTRA, "Test Computer");
        intent.putExtra(AppView.UUID_EXTRA, "invalid-uuid");

        try {
            AppView activity = Robolectric.buildActivity(AppView.class, intent).create().get();
            assertNotNull("Should handle invalid UUID", activity);
        } catch (Exception e) {
            fail("Invalid UUID should be handled gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testFileSystemPermissionDenied() {
        // Test startup when file system access is denied
        // Make profiles directory read-only
        File profilesDir = new File(context.getFilesDir(), "profiles");
        profilesDir.mkdirs();
        assertTrue("Fixture directory must become read-only", profilesDir.setReadOnly());

        try {
            ProfilesManager manager = ProfilesManager.getInstance();
            assertTrue("Empty profiles should load without a file", manager.load(context));
            assertFalse("Permission-denied writes must report failure", manager.save(context));
            assertTrue("A failed write must not invent profiles", manager.getProfiles().isEmpty());
        } catch (Exception e) {
            fail("File permission issues should be handled gracefully: " + e.getMessage());
        } finally {
            // Restore permissions for cleanup
            profilesDir.setWritable(true);
        }
    }

    @Test
    public void testMultipleActivityInstancesStartIndependently() {
        // Activity construction here is sequential; do not claim it tests concurrency.
        try {
            // Create multiple activities simultaneously
            PcView activity1 = Robolectric.buildActivity(PcView.class).create().get();
            PcView activity2 = Robolectric.buildActivity(PcView.class).create().get();

            assertNotNull("First activity should be created", activity1);
            assertNotNull("Second activity should be created", activity2);
            assertNotSame("Two launches must construct separate Activity instances", activity1, activity2);
        } catch (Exception e) {
            fail("Concurrent startup should not cause crashes: " + e.getMessage());
        }
    }

    @Test
    public void testRepeatedCreateDestroyLifecycle() {
        // Exercise Android lifecycle cleanup; memory leak detection requires
        // allocation tracking or real-device instrumentation, not System.gc().
        for (int i = 0; i < 10; i++) {
            org.robolectric.android.controller.ActivityController<PcView> controller =
                    Robolectric.buildActivity(PcView.class).create().start().resume();
            PcView activity = controller.get();
            assertFalse("Activity should start", activity.isFinishing());
            controller.pause().stop().destroy();
            assertTrue("Activity must complete destruction", activity.isDestroyed());
        }
    }

    @Test
    public void testActivityLifecycleTransitions() {
        // Test rapid activity lifecycle transitions that might cause crashes
        try {
            PcView activity = Robolectric.buildActivity(PcView.class)
                .create()
                .start()
                .resume()
                .pause()
                .stop()
                .restart()
                .start()
                .resume()
                .get();

            assertNotNull("Activity should survive lifecycle transitions", activity);
        } catch (Exception e) {
            fail("Activity lifecycle transitions should not crash: " + e.getMessage());
        }
    }

    @Test
    public void testStartupWithSystemUiVisibility() {
        // Test startup with various system UI visibility states
        try {
            PcView activity = Robolectric.buildActivity(PcView.class).create().get();

            // Test various UI visibility changes that might happen during startup
            if (activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
                activity.getWindow().getDecorView().setSystemUiVisibility(0);
            }

            assertNotNull("Activity should handle UI visibility changes", activity);
        } catch (Exception e) {
            fail("System UI visibility changes should not crash: " + e.getMessage());
        }
    }

    @Test
    public void testActivityCreationBeforeAnyHostConnection() {
        // No host session is established in this unit test. Network outage
        // behavior must be tested separately with a disconnected transport.
        try {
            PcView activity = Robolectric.buildActivity(PcView.class).create().get();
            assertNotNull("Activity should initialize without an active host", activity);
            assertFalse("Activity should remain usable", activity.isFinishing());
        } catch (Exception e) {
            fail("Network unavailability should not crash startup: " + e.getMessage());
        }
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursively(c);
                }
            }
        }
        f.delete();
    }
}