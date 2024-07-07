/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class KeyBoardLayoutController {

    private final ControllerHandler controllerHandler;
    private final Context context;
    private FrameLayout frame_layout = null;
    private Vibrator vibrator;
    private LinearLayout keyboardView;

    public KeyBoardLayoutController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.keyboardView= (LinearLayout) LayoutInflater.from(context).inflate(R.layout.layout_axixi_keyboard,null);
        initKeyboard();
    }

    private void initKeyboard(){
        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 处理按下事件
                        String tag=(String) v.getTag();
                        if(TextUtils.equals("hide",tag)){
                            return true;
                        }
                        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, Integer.parseInt(tag));
                        keyEvent.setSource(0);
                        sendKeyEvent(keyEvent);
                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button_confirm);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 处理释放事件
                        String tag2=(String) v.getTag();
                        if(TextUtils.equals("hide",tag2)){
                            hide();
                            return true;
                        }
                        KeyEvent keyUP = new KeyEvent(KeyEvent.ACTION_UP, Integer.parseInt(tag2));
                        keyUP.setSource(0);
                        sendKeyEvent(keyUP);
                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                        return true;
                }
                return false;
            }
        };
        for (int i = 0; i < keyboardView.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) keyboardView.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                keyboardRow.getChildAt(j).setOnTouchListener(touchListener);
            }
        }
    }

    public void hide() {
        keyboardView.setVisibility(View.GONE);
    }

    public void show() {
        keyboardView.setVisibility(View.VISIBLE);
    }

    public void switchShowHide() {
        if (keyboardView.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    public void refreshLayout() {
        frame_layout.removeView(keyboardView);
//        DisplayMetrics screen = context.getResources().getDisplayMetrics();
//        (int)(screen.heightPixels/0.4)
        int height=PreferenceConfiguration.readPreferences(context).oscKeyboardHeight;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dip2px(context,height));
        params.gravity= Gravity.BOTTOM;
//        params.leftMargin = 20 + buttonSize;
//        params.topMargin = 15;
        keyboardView.setAlpha(PreferenceConfiguration.readPreferences(context).oscKeyboardOpacity/100f);
        frame_layout.addView(keyboardView,params);

    }

    public int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public void sendKeyEvent(KeyEvent keyEvent) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        //1-鼠标 0-按键 2-摇杆 3-十字键
        if (keyEvent.getSource() == 1) {
            Game.instance.mouseButtonEvent(keyEvent.getKeyCode(), KeyEvent.ACTION_DOWN == keyEvent.getAction());
        } else {
            Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
        }
//        if (PreferenceConfiguration.readPreferences(context).enableKeyboardVibrate && vibrator.hasVibrator()) {
//            vibrator.vibrate(10);
//        }
    }
}
