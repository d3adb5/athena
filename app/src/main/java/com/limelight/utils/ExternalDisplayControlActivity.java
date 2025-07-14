package com.limelight.utils;

import static com.limelight.StartExternalDisplayControlReceiver.requestFocusToSecondScreen;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.limelight.Game;
import com.limelight.GameMenu;
import com.limelight.R;
import com.limelight.StartExternalDisplayControlReceiver;
import com.limelight.binding.input.GameInputDevice;
import com.limelight.nvstream.NvConnection;

/**
 * A standalone Activity that provides a full-screen touchpad controller
 * and can display the original GameMenu on demand.
 */
public class ExternalDisplayControlActivity extends Activity {

    @SuppressLint("StaticFieldLeak")
    private static ExternalDisplayControlActivity instance;

    private static final String SECONDARY_SCREEN_ACTIVE_CHANNEL_ID = "secondary_screen_active_channel_id";
    public static final int SECONDARY_SCREEN_NOTIFICATION_ID = 1;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    private NvConnection conn;


    private GameInputDevice mInputDevice;
    private GameMenu gameMenu;

    private EditText dummyEditText;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure the Game instance is available before we do anything
        if (Game.instance == null) {
            finish();
            return;
        }
        instance = this;
        // Get necessary instances from the static Game.instance
        this.conn = Game.instance.conn;
        this.mInputDevice = null;

        // ** Instantiate your original GameMenu class **
        // It uses this Activity as its context to work with secondary screen
        this.gameMenu = new GameMenu(Game.instance, conn, this);

        checkNotificationPermissionAndShow();

        // --- Create the UI Programmatically ---

        // 1. Root layout that fills the screen
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(Color.BLACK);
        setContentView(rootLayout);

        // *** NEW: Create a hidden EditText to be the target for the keyboard ***
        dummyEditText = new EditText(this);
        dummyEditText.setLayoutParams(new FrameLayout.LayoutParams(1, 1)); // Zero size = invisible
        dummyEditText.setFocusableInTouchMode(true);
        rootLayout.addView(dummyEditText);
        dummyEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    if (Game.instance != null) {
                        Game.instance.conn.sendUtf8Text((s.charAt(s.length() - 1)) + "");
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 2. The main touchpad view (which is the root layout itself)
        rootLayout.setOnTouchListener((v, event) -> {
            if (Game.instance != null) {
                Game.instance.handleMotionEvent(v, event);
            }
            return true;
        });

        // 3. Container for the top-RIGHT buttons
        LinearLayout topRightButtonContainer = new LinearLayout(this);
        topRightButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        topRightButtonContainer.setGravity(Gravity.END);
        FrameLayout.LayoutParams topRightParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        rootLayout.addView(topRightButtonContainer, topRightParams);

        // 4. Create the Menu button
        ImageButton menuButton = new ImageButton(this);
        menuButton.setImageResource(android.R.drawable.ic_menu_sort_by_size); // Hamburger icon
        menuButton.setBackgroundColor(Color.TRANSPARENT);
        menuButton.setOnClickListener(v -> showGameMenu());
        topRightButtonContainer.addView(menuButton, new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)));

        // 5. Create the Close button
        ImageButton closeButton = new ImageButton(this);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); // 'X' icon
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        closeButton.setOnClickListener(v -> finish());
        topRightButtonContainer.addView(closeButton, new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)));


        // 6. Container for the top-LEFT button
        LinearLayout topLeftButtonContainer = new LinearLayout(this);
        topLeftButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        topLeftButtonContainer.setGravity(Gravity.START);
        FrameLayout.LayoutParams topLeftParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        rootLayout.addView(topLeftButtonContainer, topLeftParams);

        // 7. Create the "Request Focus" button
        ImageButton requestFocusButton = new ImageButton(this);
        // A "send to" or "screen" icon is a good choice
        requestFocusButton.setImageResource(android.R.drawable.ic_menu_send);
        requestFocusButton.setBackgroundColor(Color.TRANSPARENT);
        requestFocusButton.setOnClickListener(v -> {
            requestFocusToSecondScreen();
        });
        topLeftButtonContainer.addView(requestFocusButton, new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)));

        ImageButton toggleKeyboardButton = new ImageButton(this);
        toggleKeyboardButton.setImageResource(android.R.drawable.ic_menu_edit); // A standard icon for input
        toggleKeyboardButton.setBackgroundColor(Color.TRANSPARENT);
        toggleKeyboardButton.setOnClickListener(v -> toggleKeyboard());
        topLeftButtonContainer.addView(toggleKeyboardButton, new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)));

        requestFocusToSecondScreen();
    }
    public static void closeExternalDisplayControl() {
        if(instance != null) instance.finish();
    }

    public static void toggleKeyboardForExternal() {
        if(instance != null) instance.toggleKeyboard();
    }

    public static void toggleGameMenu() {
        if(instance != null) instance.showGameMenu();
    }
    /**
     * Toggles the soft keyboard.
     */
    private void toggleKeyboard() {
        dummyEditText.requestFocus();
        // Now, explicitly toggle the keyboard for that view
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // This is a more reliable way to show/hide the keyboard
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (Game.instance == null) finish();
    }

    @Override
    protected void onResume() {
        if (Game.instance == null) finish();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (Game.instance == null) finish();
        super.onPause();
    }

    private void checkNotificationPermissionAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION
                );
                return;
            }
        }
        showStickyNotification();
    }

    @Override
    protected void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    private void showStickyNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    SECONDARY_SCREEN_ACTIVE_CHANNEL_ID,
                    "SecondScreen is active",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }

        Intent broadcastIntent = new Intent(this, StartExternalDisplayControlReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        @SuppressLint("NotificationTrampoline") Notification notification = new NotificationCompat.Builder(this, SECONDARY_SCREEN_ACTIVE_CHANNEL_ID)
                .setContentTitle("Second Screen is active")
                .setContentText("Touch to open virtual touchpad or make physical keyboard mouse events work again on second screen")
                .setSmallIcon(R.drawable.app_icon)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        notificationManager.notify(SECONDARY_SCREEN_NOTIFICATION_ID, notification);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showStickyNotification();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void showGameMenu() {
        if (gameMenu != null) {
            gameMenu.showMenu(this.mInputDevice);
        }
    }

    @Override
    public void onBackPressed() {
        if (gameMenu != null && gameMenu.isMenuOpen()) {
            gameMenu.hideMenu();
        } else {
            super.onBackPressed();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}