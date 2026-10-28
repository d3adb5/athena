package com.limelight.preferences;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class})
public class CollapsiblePreferenceCategoryTest {
    @Test
    public void expandedSettingsCanBeCollapsedRepeatedlyWithoutChangingValues() {
        Context context = new androidx.appcompat.view.ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.AppTheme);
        PreferenceManager manager = new PreferenceManager(context);
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        manager.setPreferences(screen);
        // Inflate the real categories, including their configured expansion limits.
        manager.inflateFromResource(context, R.xml.preferences, screen);
        CollapsiblePreferenceCategory category = screen.findPreference("category_video_settings");
        assertNotNull(category);
        PreferenceGroupAdapter adapter = new PreferenceGroupAdapter(screen);
        int collapsedSize = adapter.getItemCount();
        Preference resolution = screen.findPreference("list_resolution");
        String value = ((androidx.preference.ListPreference) resolution).getValue();
        for (int attempt = 0; attempt < 2; attempt++) {
            Preference expand = findTitle(adapter, context.getString(androidx.preference.R.string.expand_button_title));
            assertNotNull(expand);
            expand.performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(adapter.getItemCount() > collapsedSize);
            Preference collapse = screen.findPreference("category_video_settings_collapse");
            assertTrue(collapse.isVisible());
            collapse.performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(collapse.isVisible());
            assertEquals(collapsedSize, adapter.getItemCount());
            assertEquals(value, ((androidx.preference.ListPreference) resolution).getValue());
        }
    }

    @Test
    public void collapseControlReplacesExpandControlAtSamePosition() {
        Context context = new androidx.appcompat.view.ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.AppTheme);
        PreferenceManager manager = new PreferenceManager(context);
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        manager.setPreferences(screen);
        manager.inflateFromResource(context, R.xml.preferences, screen);
        PreferenceGroupAdapter adapter = new PreferenceGroupAdapter(screen);

        String expandTitle = context.getString(androidx.preference.R.string.expand_button_title);
        int expandedAt = findPositionByTitle(adapter, expandTitle);
        assertTrue(expandedAt >= 0);
        adapter.getItem(expandedAt).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        int collapsedAt = findPositionByKey(adapter, "category_video_settings_collapse");
        assertEquals("The up arrow must replace the down arrow in-place", expandedAt, collapsedAt);
        adapter.getItem(collapsedAt).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("The down arrow must return to the same place",
                expandedAt, findPositionByTitle(adapter, expandTitle));
    }

    private int findPositionByTitle(PreferenceGroupAdapter adapter, String title) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            Preference item = adapter.getItem(i);
            if (title.contentEquals(item.getTitle() == null ? "" : item.getTitle())) {
                return i;
            }
        }
        return -1;
    }

    private int findPositionByKey(PreferenceGroupAdapter adapter, String key) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            Preference item = adapter.getItem(i);
            if (key.equals(item.getKey())) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void restoringExpandedStateKeepsCollapseActionAvailable() {
        Context context = new androidx.appcompat.view.ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.AppTheme);
        PreferenceManager manager = new PreferenceManager(context);
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        manager.setPreferences(screen);
        manager.inflateFromResource(context, R.xml.preferences, screen);
        CollapsiblePreferenceCategory category = screen.findPreference("category_video_settings");
        category.setInitialExpandedChildrenCount(Integer.MAX_VALUE);
        Bundle saved = new Bundle();
        screen.saveHierarchyState(saved);
        category.setInitialExpandedChildrenCount(3);
        screen.restoreHierarchyState(saved);
        assertTrue(screen.findPreference("category_video_settings_collapse").isVisible());
    }

    private Preference findTitle(PreferenceGroupAdapter adapter, String title) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            Preference preference = adapter.getItem(i);
            if (title.contentEquals(preference.getTitle() == null ? "" : preference.getTitle())) {
                return preference;
            }
        }
        return null;
    }
}
