package com.limelight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.TvUi;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "land", shadows = {
        com.limelight.shadows.ShadowMoonBridge.class,
        com.limelight.shadows.ShadowGameManager.class
})
public class TvUiTest {
    private Context context;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        for (String feature : new String[]{PackageManager.FEATURE_TELEVISION,
                PackageManager.FEATURE_LEANBACK, "amazon.hardware.fire_tv"}) {
            Shadows.shadowOf(context.getPackageManager()).setSystemFeature(feature, false);
        }
    }

    @Test
    public void phoneKeepsMobileLayouts() {
        assertFalse(TvUi.isTv(context));
        assertEquals(R.layout.activity_pc_view, TvUi.pcLayout(context));
        assertEquals(R.layout.activity_app_view, TvUi.appLayout(context));
    }

    @Test
    public void largeTabletDoesNotBecomeATv() {
        RuntimeEnvironment.setQualifiers("sw720dp-land");
        assertFalse(TvUi.isTv(context));
    }

    @Test
    public void uiModeAloneSelectsTvLayoutsAndLargeTiles() {
        RuntimeEnvironment.setQualifiers("television-land");
        assertTvDefaults();
    }

    @Test
    public void leanbackFeatureAloneSelectsTvLayoutsAndLargeTiles() {
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        assertTvDefaults();
    }

    @Test
    public void fireTvFeatureAloneSelectsTvLayoutsAndLargeTiles() {
        Shadows.shadowOf(context.getPackageManager()).setSystemFeature("amazon.hardware.fire_tv", true);
        assertTvDefaults();
    }

    @Test
    public void televisionFeatureAloneSelectsTvLayoutsAndLargeTiles() {
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_TELEVISION, true);
        assertTvDefaults();
    }

    private void assertTvDefaults() {
        assertTrue(TvUi.isTv(context));
        assertEquals(R.layout.activity_pc_view_tv, TvUi.pcLayout(context));
        assertEquals(R.layout.activity_app_view_tv, TvUi.appLayout(context));
        assertFalse(PreferenceConfiguration.getDefaultSmallMode(context));
    }

    @Test
    public void samePackageHasPhoneAndTvLauncherEntriesAndOptionalHardware() throws Exception {
        PackageManager pm = context.getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setPackage(context.getPackageName());
            assertTrue(pm.queryIntentActivities(intent, 0).stream()
                    .anyMatch(info -> PcView.class.getName().equals(info.activityInfo.name)));
        }
        PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_CONFIGURATIONS);
        for (String feature : new String[]{PackageManager.FEATURE_TOUCHSCREEN, PackageManager.FEATURE_LEANBACK}) {
            boolean found = false;
            for (FeatureInfo requested : info.reqFeatures) {
                if (feature.equals(requested.name)) {
                    assertEquals(0, requested.flags & FeatureInfo.FLAG_REQUIRED);
                    found = true;
                }
            }
            assertTrue("Missing optional feature: " + feature, found);
        }
        assertEquals(R.drawable.hestia_tv_banner, info.applicationInfo.banner);
        assertNotNull(context.getDrawable(info.applicationInfo.banner));
    }

    @Test
    public void tvLayoutsExposeActionsAndReserveSpaceForGameGrid() {
        Context themed = new androidx.appcompat.view.ContextThemeWrapper(context, R.style.AppTheme);
        View pc = LayoutInflater.from(themed).inflate(R.layout.activity_pc_view_tv, null);
        View settings = pc.findViewById(R.id.settingsButton);
        View help = pc.findViewById(R.id.helpButton);
        View add = pc.findViewById(R.id.manuallyAddPc);
        View profiles = pc.findViewById(R.id.profilesButton);
        TvUi.configureToolbar(settings, help, add, profiles);
        assertEquals(add.getId(), help.getNextFocusRightId());
        assertEquals(help.getId(), add.getNextFocusLeftId());
        assertEquals(R.id.fragmentView, add.getNextFocusDownId());
        assertNotNull(add.getContentDescription());
        assertNotNull(pc.findViewById(R.id.no_pc_found_layout));
        View apps = LayoutInflater.from(themed).inflate(R.layout.activity_app_view_tv, null);
        android.widget.RelativeLayout.LayoutParams grid =
                (android.widget.RelativeLayout.LayoutParams) apps.findViewById(R.id.appFragmentContainer).getLayoutParams();
        assertEquals(R.id.tvNavigationHint, grid.getRule(android.widget.RelativeLayout.ABOVE));
    }

    @Test
    public void hiddenHostStatusDoesNotTrapFocusOnNonHermesHosts() {
        RuntimeEnvironment.setQualifiers("television-land");
        Context themed = new androidx.appcompat.view.ContextThemeWrapper(context, R.style.AppTheme);
        android.widget.RelativeLayout root = (android.widget.RelativeLayout) LayoutInflater.from(themed)
                .inflate(R.layout.activity_app_view_tv, null);
        GridView grid = new GridView(themed);
        grid.setId(R.id.fragmentView);
        ((android.widget.FrameLayout) root.findViewById(R.id.appFragmentContainer)).addView(grid);
        View banner = root.findViewById(R.id.hestiaStatusBanner);
        View profiles = root.findViewById(R.id.profilesButton);
        TvUi.updateAppFocus(root);
        assertEquals(R.id.profilesButton, grid.getNextFocusUpId());
        assertEquals(R.id.fragmentView, profiles.getNextFocusDownId());
        banner.setVisibility(View.VISIBLE);
        TvUi.updateAppFocus(root);
        assertEquals(banner.getId(), grid.getNextFocusUpId());
        assertEquals(banner.getId(), profiles.getNextFocusDownId());
        banner.setVisibility(View.GONE);
        TvUi.updateAppFocus(root);
        assertEquals(R.id.profilesButton, grid.getNextFocusUpId());
    }

    @Test
    public void emptyGridFocusesManualAddAndPopulatedGridSupportsRemoteMenu() {
        RuntimeEnvironment.setQualifiers("television-land");
        org.robolectric.android.controller.ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class);
        Activity activity = controller.get();
        activity.setTheme(R.style.AppTheme);
        controller.setup().visible();
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        Button add = new Button(activity);
        add.setId(R.id.manuallyAddPc);
        root.addView(add);
        GridView grid = new GridView(activity);
        grid.setId(R.id.fragmentView);
        grid.setNumColumns(1);
        root.addView(grid, new LinearLayout.LayoutParams(600, 300));
        activity.setContentView(root);
        TvUi.configureGrid(grid, add.getId(), add);
        assertTrue(add.hasFocus());
        assertFalse(grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)));

        grid.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1,
                new String[]{"Hermes"}));
        root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 800, 600);
        AtomicInteger menus = new AtomicInteger();
        grid.setOnCreateContextMenuListener((menu, view, info) -> {
            menus.incrementAndGet();
            menu.add("Status");
        });
        TvUi.configureGrid(grid, add.getId(), add);
        grid.setSelection(0);
        root.layout(0, 0, 800, 600);
        assertTrue(grid.hasFocus());
        assertNotNull(grid.getSelectedView());
        assertTrue(grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)));
        assertEquals(0, menus.get());
        assertTrue(grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)));
        assertEquals(1, menus.get());
        controller.pause().stop().destroy();
    }
}
