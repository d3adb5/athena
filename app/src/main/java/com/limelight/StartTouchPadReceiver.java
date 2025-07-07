package com.limelight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartTouchPadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Open TouchpadView
        Intent serviceIntent = new Intent(context, TouchPadOverlayService.class);
        context.startService(serviceIntent);
    }
}
