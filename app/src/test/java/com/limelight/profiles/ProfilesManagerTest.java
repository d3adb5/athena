package com.limelight.profiles;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class ProfilesManagerTest {
    private Context context;
    private ProfilesManager manager;
    private File profilesDir;


    @Before
    public void setUp() {
        ProfilesManager.instance = null; // Reset the singleton before each test
        context = ApplicationProvider.getApplicationContext();
        profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
        manager = ProfilesManager.getInstance();
        manager.load(context);
    }

    @After
    public void tearDown() {
        deleteRecursively(profilesDir);
    }

    @Test
    public void addAndRetrieveProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Test", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());
        assertEquals(p.getUuid(), manager.getProfiles().get(0).getUuid());
    }

    @Test
    public void setActivePersists() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Active", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        manager.setActive(p.getUuid());
        // Reload from disk to verify persistence
        ProfilesManager.instance = null; // Simulate a new process, not a second reference to the singleton
        ProfilesManager fresh = ProfilesManager.getInstance();
        assertNotSame(manager, fresh);
        assertTrue("Saved profiles should reload from disk", fresh.load(context));
        assertNotNull(fresh.getActive());
        assertEquals(p.getUuid(), fresh.getActive().getUuid());
    }

    @Test
    public void updateAndSaveProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Original", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        p.setName("Updated");
        manager.update(p);

        ProfilesManager.instance = null; // Simulate a new process, not a second reference to the singleton
        ProfilesManager fresh = ProfilesManager.getInstance();
        assertNotSame(manager, fresh);
        assertTrue("Saved profiles should reload from disk", fresh.load(context));
        assertEquals("Updated", fresh.getProfiles().get(0).getName());
    }

    @Test
    public void deleteProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "ToDelete", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());

        manager.delete(p.getUuid());
        assertEquals(0, manager.getProfiles().size());
    }

    @Test
    public void deleteActiveProfile_resetsActive() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "ActiveToDelete", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        manager.setActive(p.getUuid());
        assertNotNull(manager.getActive());

        manager.delete(p.getUuid());
        assertNull(manager.getActive());

        // Verify persistence
        ProfilesManager.instance = null; // Simulate a new process, not a second reference to the singleton
        ProfilesManager fresh = ProfilesManager.getInstance();
        assertNotSame(manager, fresh);
        assertTrue("Saved profiles should reload from disk", fresh.load(context));
        assertNull(fresh.getActive());
    }

    @Test
    public void atomicSaveProducesCommittedJson() throws Exception {
        SettingsProfile profile = new SettingsProfile(UUID.randomUUID(), "Committed",
                System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(profile);
        assertTrue(manager.save(context));

        File saved = new File(profilesDir, "profiles.json");
        assertTrue("The committed profile file must exist", saved.isFile());
        assertFalse("An incomplete pending write must not remain",
                new File(profilesDir, "profiles.json.new").exists());

        ProfilesManager.instance = null;
        ProfilesManager reloaded = ProfilesManager.getInstance();
        assertTrue(reloaded.load(context));
        assertEquals(1, reloaded.getProfiles().size());
        assertEquals(profile.getUuid(), reloaded.getProfiles().get(0).getUuid());
    }

    @Test
    public void failedWriteLeavesLastCommittedProfilesReadable() {
        SettingsProfile first = new SettingsProfile(UUID.randomUUID(), "First",
                System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(first);
        assertTrue(manager.save(context));

        // A directory at AtomicFile's temporary filename deterministically
        // prevents opening the new file without modifying the committed JSON.
        File blockedWrite = new File(profilesDir, "profiles.json.new");
        assertTrue("Create an unwritable pending-file path", blockedWrite.mkdir());
        try {
            assertFalse("A blocked save must report failure", manager.save(context));
        } finally {
            assertTrue("Remove the artificial failure", blockedWrite.delete());
        }

        ProfilesManager.instance = null;
        ProfilesManager reloaded = ProfilesManager.getInstance();
        assertTrue("The previous committed state must still load", reloaded.load(context));
        assertEquals(1, reloaded.getProfiles().size());
        assertEquals(first.getUuid(), reloaded.getProfiles().get(0).getUuid());
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}