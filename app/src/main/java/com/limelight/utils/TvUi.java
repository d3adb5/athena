package com.limelight.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;

import com.limelight.R;

/** TV browsing UI shared by the phone and TV APK. Streaming input is left untouched. */
public final class TvUi {
    private TvUi() {}

    public static boolean isTv(Context context) {
        PackageManager pm = context.getPackageManager();
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature("amazon.hardware.fire_tv");
    }

    public static int pcLayout(Context context) {
        return isTv(context) ? R.layout.activity_pc_view_tv : R.layout.activity_pc_view;
    }

    public static int appLayout(Context context) {
        return isTv(context) ? R.layout.activity_app_view_tv : R.layout.activity_app_view;
    }

    public static void configureToolbar(View... buttons) {
        for (int i = 0; i < buttons.length; i++) {
            View button = buttons[i];
            button.setFocusable(true);
            button.setNextFocusLeftId(buttons[Math.max(0, i - 1)].getId());
            button.setNextFocusRightId(buttons[Math.min(buttons.length - 1, i + 1)].getId());
            button.setNextFocusDownId(R.id.fragmentView);
        }
    }

    public static void configureGrid(AbsListView grid, int upId, View emptyFocus) {
        if (!isTv(grid.getContext())) {
            return;
        }
        grid.setNextFocusUpId(upId);
        // Override the mobile grid's left shortcut: TV actions live above the grid.
        grid.setNextFocusLeftId(View.NO_ID);
        grid.setSelector(R.drawable.tv_focus_background);
        grid.setDrawSelectorOnTop(true);
        grid.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_MENU || grid.getSelectedView() == null) {
                return false;
            }
            // Remote MENU is an alternative to long-pressing OK. Open once, on release.
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                grid.showContextMenuForChild(grid.getSelectedView());
            }
            return true;
        });
        if (grid.getCount() > 0) {
            grid.requestFocus();
        } else {
            emptyFocus.requestFocus();
        }
    }

    public static void updateAppFocus(View root) {
        if (!isTv(root.getContext())) {
            return;
        }
        View banner = root.findViewById(R.id.hestiaStatusBanner);
        View profiles = root.findViewById(R.id.profilesButton);
        View grid = root.findViewById(R.id.fragmentView);
        boolean hasBanner = banner.getVisibility() == View.VISIBLE;
        profiles.setNextFocusDownId(hasBanner ? banner.getId() : R.id.fragmentView);
        banner.setNextFocusUpId(profiles.getId());
        banner.setNextFocusDownId(R.id.fragmentView);
        if (grid != null) {
            grid.setNextFocusUpId(hasBanner ? banner.getId() : profiles.getId());
        }
    }

    public static void showHelp(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.tv_help_title)
                .setMessage(R.string.tv_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
