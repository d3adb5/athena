package com.limelight.grid;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.hestia.HestiaHostStatus;
import com.limelight.hestia.HestiaStatusUi;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public void populateView(View parentView, ImageView imgView, RelativeLayout gridMask, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        imgView.setImageResource(R.drawable.ic_computer);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            imgView.setAlpha(1.0f);
        }
        else {
            imgView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            prgView.setVisibility(View.VISIBLE);
        }
        else {
            prgView.setVisibility(View.INVISIBLE);
        }

        txtView.setText(obj.details.name);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            txtView.setAlpha(1.0f);
        }
        else {
            txtView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.OFFLINE) {
            overlayView.setImageResource(R.drawable.ic_pc_offline);
            overlayView.setAlpha(0.4f);
            overlayView.setVisibility(View.VISIBLE);
        }
        // We must check if the status is exactly online and unpaired
        // to avoid colliding with the loading spinner when status is unknown
        else if (obj.details.state == ComputerDetails.State.ONLINE &&
                obj.details.pairState == PairingManager.PairState.NOT_PAIRED) {
            overlayView.setImageResource(R.drawable.ic_lock);
            overlayView.setAlpha(1.0f);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }

        populateHestiaStatus(parentView, imgView, obj);
    }

    /**
     * Hermes hosts get a badge on the computer icon and a coloured status
     * line under the name, so a broken host is visible before tapping it.
     * Other hosts keep the original look.
     */
    private void populateHestiaStatus(View parentView, ImageView imgView, PcView.ComputerObject obj) {
        ImageView badge = parentView.findViewById(R.id.grid_hestia_badge);
        TextView label = parentView.findViewById(R.id.grid_hestia_text);
        if (badge == null || label == null) {
            return;
        }

        HestiaHostStatus status = obj.details.hestiaStatus;
        if (!HestiaStatusUi.shouldShow(obj.details) || status == null) {
            badge.setVisibility(View.GONE);
            label.setVisibility(View.GONE);
            imgView.setContentDescription(obj.details.name);
            return;
        }

        String text = HestiaStatusUi.badgeLabel(context, status);
        badge.setImageResource(HestiaStatusUi.iconFor(status.getLevel()));
        badge.setVisibility(View.VISIBLE);
        label.setText(text);
        label.setTextColor(HestiaStatusUi.colorFor(context, status.getLevel()));
        label.setVisibility(View.VISIBLE);
        imgView.setContentDescription(obj.details.name + ", " + text);
    }
}
