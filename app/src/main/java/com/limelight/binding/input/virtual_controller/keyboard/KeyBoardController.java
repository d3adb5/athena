/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyBoardController {

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;

    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();

    private Button buttonConfigure = null;

    private Vibrator vibrator;
    private List<keyBoardVirtualControllerElement> elements = new ArrayList<>();

    public KeyBoardController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());

        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.5f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_keyboard_setting);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active) {
                    currentMode = ControllerMode.DisableEnableButtons;
                    showElements();
                    message = "配置模式：启用禁用控件！";
                } else if (currentMode == ControllerMode.DisableEnableButtons) {
                    currentMode = ControllerMode.MoveButtons;
                    showEnabledElements();
                    message = "配置模式：调整控件位置！";
                } else if (currentMode == ControllerMode.MoveButtons) {
                    currentMode = ControllerMode.ResizeButtons;
                    message = "配置模式：调整控件大小！";
                } else {
                    currentMode = ControllerMode.Active;
                    KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context);
                    message = "退出配置模式！";
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                buttonConfigure.invalidate();

                for (keyBoardVirtualControllerElement element : elements) {
                    element.invalidate();
                }
            }
        });

    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }

        buttonConfigure.setVisibility(View.GONE);
    }

    public void show() {
        showEnabledElements();

        buttonConfigure.setVisibility(View.VISIBLE);
    }

    public void showElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }
    }

    public void showEnabledElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(element.enabled ? View.VISIBLE : View.GONE);
        }
    }

    public void switchShowHide() {
        if (buttonConfigure.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    public void removeElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(keyBoardVirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int) (screen.heightPixels * 0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 20 + buttonSize;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);

        // Start with the default layout
        KeyBoardControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        KeyBoardControllerConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
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
        if (PreferenceConfiguration.readPreferences(context).enableKeyboardVibrate && vibrator.hasVibrator()) {
            vibrator.vibrate(10);
        }
    }

    public void sendMouseMove(int x,int y){
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.mouseMove(x,y);
    }

}
