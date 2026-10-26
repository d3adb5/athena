package com.limelight.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;

import com.limelight.R;

/** Retains AndroidX expansion behavior and adds a keyboard/D-pad accessible collapse action. */
@SuppressLint("RestrictedApi") // AndroidX 1.2.1's expansion callback is public but library-restricted.
public class CollapsiblePreferenceCategory extends PreferenceCategory {
    private final int collapsedCount;
    private final Preference collapse;

    public CollapsiblePreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        collapsedCount = getInitialExpandedChildrenCount();
        if (collapsedCount == Integer.MAX_VALUE) {
            collapse = null;
            return;
        }
        collapse = new Preference(context);
        collapse.setKey(getKey() + "_collapse");
        collapse.setPersistent(false);
        collapse.setTitle(R.string.settings_show_fewer);
        collapse.setIcon(android.R.drawable.arrow_up_float);
        collapse.setOrder(Integer.MAX_VALUE - 1);
        collapse.setVisible(false);
        collapse.setOnPreferenceClickListener(preference -> {
            setInitialExpandedChildrenCount(collapsedCount);
            // Visibility changes notify the adapter to rebuild the expanded child list.
            collapse.setVisible(false);
            return true;
        });
        setOnExpandButtonClickListener(() -> collapse.setVisible(true));
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        // Adding children requires the manager assigned by the superclass.
        // This also runs during default-value inflation before any screen is displayed.
        if (collapse != null && collapse.getParent() != this) {
            addPreference(collapse);
        }
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        if (collapse != null) {
            collapse.setVisible(getInitialExpandedChildrenCount() == Integer.MAX_VALUE);
        }
    }
}
