/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class KeyBoardLayoutController {

    private final long timerLongClickTimeout = 300;
    private final Context context;
    private final PreferenceConfiguration prefConfig;
    private FrameLayout frame_layout = null;
    private final Handler handler;
    public boolean shown = false;

    private final LinearLayout keyboardView;
    private static final Set<Integer> MODIFIER_KEY_CODES = new HashSet<>();
    static {
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_RIGHT);
    }

    private static final HashMap<Integer, Runnable> longClickRunnables = new HashMap<>();

    private final BitSet modifierKeyStates = new BitSet();

    public boolean isModifierKeyPressed(int keyCode) {
        return modifierKeyStates.get(keyCode);
    }

    private boolean isModifierKey(int keyCode) {
        return MODIFIER_KEY_CODES.contains(keyCode);
    }

    public KeyBoardLayoutController(FrameLayout layout, final Context context, PreferenceConfiguration prefConfig) {
        this.frame_layout = layout;
        this.context = context;
        this.prefConfig = prefConfig;
        this.keyboardView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.layout_axixi_keyboard, null);
        this.handler = new Handler(Looper.getMainLooper());
        initKeyboard();
    }

    public Handler getHandler() {
        return handler;
    }

    private void initKeyboard(){
        @SuppressLint("ClickableViewAccessibility")
        View.OnTouchListener touchListener = (View v, MotionEvent event) -> {
            int eventAction = event.getAction();
            String tag=(String) v.getTag();
            if (TextUtils.equals("hide", tag)) {
                if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL) {
                    hide();
                }
                return true;
            }

            int keyCode = Integer.parseInt(tag);
            int keyAction;
            boolean _isModifierKey = isModifierKey(keyCode);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (_isModifierKey && isModifierKeyPressed(keyCode)) {
                        modifierKeyStates.clear(keyCode);
                        return true;
                    }

                    keyAction = KeyEvent.ACTION_DOWN;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (_isModifierKey && isModifierKeyPressed(keyCode)) {
                        return true;
                    }

                    keyAction = KeyEvent.ACTION_UP;
                    break;
                default:
                    return false;
            }

            KeyEvent keyEvent = new KeyEvent(keyAction, keyCode);
            keyEvent.setSource(0);
            sendKeyEvent(keyEvent);

            if (_isModifierKey) {
                Runnable longClickRunnable = longClickRunnables.get(keyCode);
                if (longClickRunnable != null) {
                    getHandler().removeCallbacks(longClickRunnable);
                    if (keyAction == KeyEvent.ACTION_DOWN) {
                        getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
                    }
                }
            }

            if (keyAction == KeyEvent.ACTION_DOWN) {
                if (prefConfig.enableKeyboardVibrate) {
                    keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                v.setBackgroundResource(R.drawable.bg_ax_keyboard_button_confirm);
            } else {
                if (prefConfig.enableKeyboardVibrate) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE);
                    } else {
                        keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                }
                v.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
            }
            return true;
        };
        for (int i = 0; i < keyboardView.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) keyboardView.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                View child = keyboardRow.getChildAt(j);
                keyboardRow.getChildAt(j).setOnTouchListener(touchListener);
                String keyTag = (String) child.getTag();
                if (keyTag.equals("hide")) {
                    continue;
                }
                int keycode = Integer.parseInt((String) child.getTag());
                if (isModifierKey(keycode)) {
                    longClickRunnables.put(keycode, () -> {
                        modifierKeyStates.set(keycode);
                        if (prefConfig.enableKeyboardVibrate) {
                            child.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    });
                }
            }
        }
    }

    public void hide(boolean temporary) {
        if (prefConfig.enableKeyboardVibrate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                keyboardView.performHapticFeedback(HapticFeedbackConstants.REJECT);
            } else {
                keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
        keyboardView.setVisibility(View.GONE);
        if (!temporary) {
            shown = false;
        }
    }

    public void hide() {
        hide(false);
    }

    public void show() {
        keyboardView.setVisibility(View.VISIBLE);
        shown = true;
    }

    public void toggleVisibility() {
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
        int height = PreferenceConfiguration.readPreferences(context).oscKeyboardHeight;
        int widthPreference = PreferenceConfiguration.readPreferences(context).oscKeyboardWidth;
        int width = widthPreference == 1000 ? ViewGroup.LayoutParams.MATCH_PARENT : dip2px(context, widthPreference);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, dip2px(context, height));
        params.gravity = Gravity.BOTTOM;
//        params.leftMargin = 20 + buttonSize;
//        params.topMargin = 15;
        keyboardView.setAlpha(PreferenceConfiguration.readPreferences(context).oscKeyboardOpacity / 100f);
        frame_layout.addView(keyboardView, params);
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
    }
}
