package com.limelight.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;

import com.limelight.R;

/** Retains AndroidX expansion behavior and adds a keyboard/D-pad accessible collapse action. */
@SuppressLint("RestrictedApi") // AndroidX 1.2.1's expansion callback is public but library-restricted.
public class CollapsiblePreferenceCategory extends PreferenceCategory {
    private final int collapsedCount;
    private final CollapseActionPreference collapse;

    /** Keeps keyboard/TV focus on the expander when the adapter replaces the row. */
    private static final class CollapseActionPreference extends Preference {
        private WeakReference<View> row = new WeakReference<>(null);
        private boolean focusOnNextBind;

        CollapseActionPreference(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            View item = holder.itemView;
            row = new WeakReference<>(item);
            if (focusOnNextBind) {
                focusOnNextBind = false;
                item.post(() -> {
                    if (item.isAttachedToWindow()) {
                        item.requestFocus();
                    }
                });
            }
        }

        View getBoundRow() {
            return row.get();
        }

        void focusAfterExpansion() {
            focusOnNextBind = true;
        }
    }

    public CollapsiblePreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        collapsedCount = getInitialExpandedChildrenCount();
        if (collapsedCount == Integer.MAX_VALUE) {
            collapse = null;
            return;
        }
        collapse = new CollapseActionPreference(context);
        collapse.setKey(getKey() + "_collapse");
        collapse.setPersistent(false);
        collapse.setTitle(R.string.settings_show_fewer);
        collapse.setIcon(R.drawable.ic_expand_less);
        // The generated AndroidX expand row appears directly after the first N items.
        // Keep the collapse row at that same position instead of the category's end.
        collapse.setOrder(collapsedCount);
        collapse.setVisible(false);
        collapse.setOnPreferenceClickListener(preference -> {
            View row = collapse.getBoundRow();
            RecyclerView list = row != null && row.getParent() instanceof RecyclerView
                    ? (RecyclerView) row.getParent() : null;
            int position = list != null ? list.getChildAdapterPosition(row) : RecyclerView.NO_POSITION;
            boolean restoreFocus = row != null && row.hasFocus();

            setInitialExpandedChildrenCount(collapsedCount);
            // Visibility changes notify the adapter to rebuild the expanded child list.
            collapse.setVisible(false);

            // Collapsing removes the focused item from RecyclerView. Without restoring
            // focus, Android TV often jumps all the way to the toolbar's Back button.
            if (restoreFocus && list != null && position != RecyclerView.NO_POSITION) {
                list.post(() -> {
                    RecyclerView.Adapter<?> adapter = list.getAdapter();
                    if (adapter == null || adapter.getItemCount() == 0) {
                        return;
                    }
                    int target = Math.min(position, adapter.getItemCount() - 1);
                    list.scrollToPosition(target);
                    list.post(() -> {
                        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(target);
                        if (holder != null) {
                            holder.itemView.requestFocus();
                        }
                    });
                });
            }
            return true;
        });
        setOnExpandButtonClickListener(() -> {
            collapse.focusAfterExpansion();
            collapse.setVisible(true);
        });
    }

    private int nextChildOrder;

    @Override
    public boolean addPreference(Preference preference) {
        if (preference != collapse) {
            // AndroidX assigns 0, 1, 2, ... to XML children. Reserve slot N
            // for the collapse control; otherwise child N also gets order N,
            // and a title-based tie can put the control in the wrong place.
            if (collapsedCount != Integer.MAX_VALUE
                    && preference.getOrder() == Integer.MAX_VALUE) {
                int order = nextChildOrder;
                preference.setOrder(order < collapsedCount ? order : order + 1);
            }
            nextChildOrder++;
        }
        return super.addPreference(preference);
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
