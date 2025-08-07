package com.limelight;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.limelight.utils.ExternalDisplayControlActivity;

public class StartExternalDisplayControlReceiver extends BroadcastReceiver {
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ExternalDisplayControlActivity.instance != null) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            am.moveTaskToFront(ExternalDisplayControlActivity.instance.getTaskId(), 0);
        }
    }

    public static void requestFocusToSecondScreen() {
        if (Game.instance != null) {
            ActivityManager am = (ActivityManager) Game.instance.getSystemService(Context.ACTIVITY_SERVICE);
            am.moveTaskToFront(Game.instance.getTaskId(), 0);
        }
    }
}
