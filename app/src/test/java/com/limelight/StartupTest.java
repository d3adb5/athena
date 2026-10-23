package com.limelight;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.profiles.ProfilesManager;

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
public class StartupTest {
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

        // Clean up any existing profiles
        File profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
    }

    @Test
    public void testApplicationStartup() {
        // Test ArtemisApplication creation and initialization
        // Reuse the Robolectric-attached instance; constructing a bare
        // Application leaves ContextWrapper.mBase null.
        ArtemisApplication app = (ArtemisApplication) context;
        app.onCreate();

        // Verify ProfilesManager was initialized
        ProfilesManager manager = ProfilesManager.getInstance();
        assertNotNull("ProfilesManager should be initialized", manager);
        assertNotNull("ProfilesManager should have loaded profiles", manager.getProfiles());
    }

    @Test
    public void testPcViewActivityCreation() {
        // Test PcView activity startup
        PcView activity = Robolectric.buildActivity(PcView.class).create().get();
        assertNotNull("PcView activity should be created", activity);
        assertFalse("Activity should not be finishing", activity.isFinishing());
    }

    @Test
    public void testPcViewActivityWithIntent() {
        // Test PcView with various intent extras that might cause crashes
        Intent intent = new Intent();
        intent.putExtra("hostname", "test.local");
        intent.putExtra("port", 47989);
        intent.putExtra("pin", "1234");
        intent.putExtra("passphrase", "test");

        PcView activity = Robolectric.buildActivity(PcView.class, intent).create().get();
        assertNotNull("PcView activity should be created with intent", activity);
        assertFalse("Activity should not be finishing", activity.isFinishing());
    }

    @Test
    public void testAppViewActivityCreation() {
        // Test AppView activity startup
        Intent intent = new Intent();
        intent.putExtra(AppView.NAME_EXTRA, "Test Computer");
        intent.putExtra(AppView.UUID_EXTRA, "test-uuid-123");

        AppView activity = Robolectric.buildActivity(AppView.class, intent).create().get();
        assertNotNull("AppView activity should be created", activity);
        assertFalse("Activity should not be finishing", activity.isFinishing());
    }

    @Test
    public void testProfilesManagerFileSystemAccess() {
        // Test ProfilesManager file operations that might cause crashes
        ProfilesManager manager = ProfilesManager.getInstance();

        // Test with context that has no file access
        try {
            assertTrue("Loading profiles should succeed", manager.load(context));
            assertTrue("Saving profiles should succeed", manager.save(context));
        } catch (Exception e) {
            fail("ProfilesManager should handle file access gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testInternetPermissionDeclaredInManifest() throws Exception {
        // A runtime permission comparison against >= -1 always passes on
        // Android. Verify the manifest actually declares what streaming needs.
        android.content.pm.PackageInfo info = context.getPackageManager()
                .getPackageInfo(context.getPackageName(),
                        android.content.pm.PackageManager.GET_PERMISSIONS);
        assertNotNull("The app must declare permissions", info.requestedPermissions);
        assertTrue("Internet permission must be in the merged manifest",
                java.util.Arrays.asList(info.requestedPermissions)
                        .contains(android.Manifest.permission.INTERNET));
    }

    @Test
    public void testCorruptedProfilesFile() {
        // Test startup with corrupted profiles file
        File profilesDir = new File(context.getFilesDir(), "profiles");
        profilesDir.mkdirs();
        File profilesFile = new File(profilesDir, "profiles.json");

        try {
            // Write corrupted JSON
            java.io.FileWriter writer = new java.io.FileWriter(profilesFile);
            writer.write("{ corrupted json content");
            writer.close();

            ProfilesManager manager = ProfilesManager.getInstance();
            assertFalse("Malformed JSON must report a failed load", manager.load(context));
            assertTrue("A malformed file must not invent profiles", manager.getProfiles().isEmpty());
        } catch (Exception e) {
            fail("ProfilesManager should handle corrupted files gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testNullContextHandling() {
        // Test ProfilesManager with null context – should be handled gracefully
        ProfilesManager manager = ProfilesManager.getInstance();

        try {
            assertFalse("Null Context must be rejected", manager.load(null));
        } catch (Exception e) {
            fail("Should handle null context gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testLowMemoryCallbackDoesNotCloseActivity() {
        // Verify the Android lifecycle callback, not a synthetic OOM scenario.
        try {
            PcView activity = Robolectric.buildActivity(PcView.class).create().get();
            assertNotNull("Activity should handle low memory conditions", activity);

            activity.onLowMemory();
            assertFalse("Activity should not finish on low memory", activity.isFinishing());
        } catch (OutOfMemoryError e) {
            fail("App should handle low memory gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testConfigurationChanges() {
        // Test startup with configuration changes
        PcView activity = Robolectric.buildActivity(PcView.class).create().start().resume().get();

        try {
            // Simulate configuration change
            android.content.res.Configuration newConfig = new android.content.res.Configuration();
            newConfig.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            activity.onConfigurationChanged(newConfig);

            assertFalse("Activity should survive configuration changes", activity.isFinishing());
        } catch (Exception e) {
            fail("Activity should handle configuration changes gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testActivityCanBeDestroyedBeforeServiceConnectionCompletes() {
        // This checks the lifecycle when no service binder has arrived;
        // it does not claim to simulate Android's binder failure callback.
        org.robolectric.android.controller.ActivityController<PcView> controller =
                Robolectric.buildActivity(PcView.class).create();
        PcView activity = controller.get();
        controller.destroy();
        assertTrue("Activity must finish its destroy lifecycle", activity.isDestroyed());
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