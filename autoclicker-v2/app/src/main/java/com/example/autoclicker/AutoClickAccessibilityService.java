package com.example.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AutoClickAccessibilityService extends AccessibilityService {
    private static volatile AutoClickAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private TargetMarkerView targetView;
    private LinearLayout panelView;
    private WindowManager.LayoutParams targetParams;
    private WindowManager.LayoutParams panelParams;
    private TextView panelStatus;
    private Button startStopButton;

    private boolean running;
    private boolean gestureInFlight;
    private int horizontalDirection = 1;

    private String mode = MainActivity.MODE_CLICK;
    private long actionIntervalMs = 200L;
    private int swipeDistancePx = 240;
    private long swipeDurationMs = 300L;

    public static AutoClickAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadConfiguration();
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        boolean shouldShow = preferences.getBoolean(MainActivity.KEY_SHOW_ON_CONNECT, false);
        if (shouldShow) {
            preferences.edit().putBoolean(MainActivity.KEY_SHOW_ON_CONNECT, false).apply();
            handler.postDelayed(this::showOverlays, 350L);
        }
    }

    private void loadConfiguration() {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        mode = preferences.getString(MainActivity.KEY_MODE, MainActivity.MODE_CLICK);
        actionIntervalMs = clamp(preferences.getLong(MainActivity.KEY_INTERVAL, 200L), 50L, 60000L);
        swipeDistancePx = (int) clamp(preferences.getInt(MainActivity.KEY_SWIPE_DISTANCE, 240), 20, 1500);
        swipeDurationMs = clamp(preferences.getLong(MainActivity.KEY_SWIPE_DURATION, 300L), 50L, 3000L);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不读取事件内容，仅发送用户配置的手势。
    }

    @Override
    public void onInterrupt() {
        stopRunning();
    }

    @Override
    public void onDestroy() {
        stopRunning();
        removeOverlays();
        instance = null;
        super.onDestroy();
    }

    public void setConfiguration(String newMode, long intervalMs, int distancePx, long durationMs) {
        stopRunning();
        mode = MainActivity.MODE_HORIZONTAL.equals(newMode)
                ? MainActivity.MODE_HORIZONTAL : MainActivity.MODE_CLICK;
        actionIntervalMs = clamp(intervalMs, 50L, 60000L);
        swipeDistancePx = (int) clamp(distancePx, 20, 1500);
        swipeDurationMs = clamp(durationMs, 50L, 3000L);
        horizontalDirection = 1;

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit()
                .putString(MainActivity.KEY_MODE, mode)
                .putLong(MainActivity.KEY_INTERVAL, actionIntervalMs)
                .putInt(MainActivity.KEY_SWIPE_DISTANCE, swipeDistancePx)
                .putLong(MainActivity.KEY_SWIPE_DURATION, swipeDurationMs)
                .apply();

        updateMarkerAppearance();
        updatePanelText();
    }

    public void showOverlays() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (windowManager == null) {
            Toast.makeText(this, "无法创建悬浮控件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (targetView == null) {
            createTargetOverlay();
        }
        if (panelView == null) {
            createControlPanel();
        }
        updateMarkerAppearance();
        updatePanelText();
    }

    private void createTargetOverlay() {
        targetView = new TargetMarkerView(this);
        targetView.setContentDescription("自动操作目标位置");

        int size = dp(64);
        targetParams = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        targetParams.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        targetParams.x = Math.max(0, (metrics.widthPixels - size) / 2);
        targetParams.y = Math.max(dp(90), (metrics.heightPixels - size) / 2);
        targetView.setOnTouchListener(new DragTouchListener(targetView, targetParams, true));

        try {
            windowManager.addView(targetView, targetParams);
        } catch (Exception e) {
            targetView = null;
            targetParams = null;
            Toast.makeText(this, "目标标记创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void createControlPanel() {
        panelView = new LinearLayout(this);
        panelView.setOrientation(LinearLayout.VERTICAL);
        panelView.setPadding(dp(12), dp(10), dp(12), dp(12));
        panelView.setBackground(Drawables.roundRect(
                Color.argb(242, 31, 36, 43), dp(15), Color.argb(110, 255, 255, 255)));
        panelView.setElevation(dp(8));

        TextView dragHandle = new TextView(this);
        dragHandle.setText("自动操作  ·  拖动面板");
        dragHandle.setTextColor(Color.WHITE);
        dragHandle.setTextSize(14);
        dragHandle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setPadding(dp(8), dp(6), dp(8), dp(8));
        panelView.addView(dragHandle, new LinearLayout.LayoutParams(dp(230), dp(40)));

        panelStatus = new TextView(this);
        panelStatus.setTextColor(Color.rgb(220, 225, 230));
        panelStatus.setTextSize(13);
        panelStatus.setGravity(Gravity.CENTER);
        panelStatus.setPadding(dp(4), dp(2), dp(4), dp(8));
        panelView.addView(panelStatus, new LinearLayout.LayoutParams(dp(230), dp(42)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        panelView.addView(row, new LinearLayout.LayoutParams(dp(230), dp(48)));

        startStopButton = overlayButton("开始", Color.rgb(33, 150, 83));
        startStopButton.setOnClickListener(v -> {
            if (running) {
                stopRunning();
            } else {
                startRunning();
            }
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        startParams.rightMargin = dp(5);
        row.addView(startStopButton, startParams);

        Button hideButton = overlayButton("隐藏", Color.rgb(85, 91, 99));
        hideButton.setOnClickListener(v -> removeOverlays());
        LinearLayout.LayoutParams hideParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        hideParams.leftMargin = dp(5);
        row.addView(hideButton, hideParams);

        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(12);
        panelParams.y = dp(90);
        dragHandle.setOnTouchListener(new DragTouchListener(panelView, panelParams, false));

        try {
            windowManager.addView(panelView, panelParams);
        } catch (Exception e) {
            panelView = null;
            panelParams = null;
            panelStatus = null;
            startStopButton = null;
            Toast.makeText(this, "控制面板创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private Button overlayButton(String text, int fillColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(Drawables.roundRect(fillColor, dp(11), 0));
        return button;
    }

    private void startRunning() {
        if (targetView == null || targetParams == null) {
            Toast.makeText(this, "请先显示并放置目标标记", Toast.LENGTH_SHORT).show();
            return;
        }
        if (running) {
            return;
        }
        running = true;
        gestureInFlight = false;
        horizontalDirection = 1;
        setTargetTouchable(false);
        updatePanelText();
        handler.removeCallbacks(actionRunnable);
        handler.post(actionRunnable);
    }

    private void stopRunning() {
        running = false;
        gestureInFlight = false;
        handler.removeCallbacks(actionRunnable);
        setTargetTouchable(true);
        updatePanelText();
    }

    private final Runnable actionRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || targetParams == null || targetView == null || gestureInFlight) {
                return;
            }

            float startX = targetParams.x + targetParams.width / 2f;
            float startY = targetParams.y + targetParams.height / 2f;
            Path path = new Path();
            path.moveTo(startX, startY);
            long duration;

            if (MainActivity.MODE_HORIZONTAL.equals(mode)) {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                float endX = clampFloat(startX + horizontalDirection * swipeDistancePx,
                        dp(4), metrics.widthPixels - dp(4));
                if (Math.abs(endX - startX) < dp(18)) {
                    horizontalDirection *= -1;
                    endX = clampFloat(startX + horizontalDirection * swipeDistancePx,
                            dp(4), metrics.widthPixels - dp(4));
                }
                path.lineTo(endX, startY);
                duration = swipeDurationMs;
            } else {
                duration = 1L;
            }

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0L, duration))
                    .build();

            gestureInFlight = true;
            boolean accepted;
            try {
                accepted = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        gestureInFlight = false;
                        if (MainActivity.MODE_HORIZONTAL.equals(mode)) {
                            horizontalDirection *= -1;
                        }
                        scheduleNextAction();
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        gestureInFlight = false;
                        scheduleNextAction();
                    }
                }, handler);
            } catch (Exception e) {
                accepted = false;
            }

            if (!accepted) {
                gestureInFlight = false;
                scheduleNextAction();
            }
        }
    };

    private void scheduleNextAction() {
        if (running) {
            handler.postDelayed(actionRunnable, actionIntervalMs);
        }
    }

    private void updateMarkerAppearance() {
        if (targetView != null) {
            targetView.setHorizontalMode(MainActivity.MODE_HORIZONTAL.equals(mode));
            targetView.setContentDescription(MainActivity.MODE_HORIZONTAL.equals(mode)
                    ? "横向滑动起点" : "连续点击位置");
        }
    }

    private void setTargetTouchable(boolean touchable) {
        if (targetView == null || targetParams == null || windowManager == null) {
            return;
        }
        if (touchable) {
            targetParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            targetView.setAlpha(1f);
        } else {
            targetParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            targetView.setAlpha(0.55f);
        }
        try {
            windowManager.updateViewLayout(targetView, targetParams);
        } catch (Exception ignored) {
        }
    }

    private void updatePanelText() {
        if (panelStatus != null) {
            String detail;
            if (MainActivity.MODE_HORIZONTAL.equals(mode)) {
                detail = "横向 · " + swipeDistancePx + " px / " + swipeDurationMs + " ms";
            } else {
                detail = "连点 · 间隔 " + actionIntervalMs + " ms";
            }
            panelStatus.setText((running ? "运行中 · " : "已停止 · ") + detail);
            panelStatus.setTextColor(running
                    ? Color.rgb(116, 235, 160) : Color.rgb(220, 225, 230));
        }
        if (startStopButton != null) {
            startStopButton.setText(running ? "停止" : "开始");
            startStopButton.setBackground(Drawables.roundRect(
                    running ? Color.rgb(211, 62, 62) : Color.rgb(33, 150, 83),
                    dp(11), 0));
        }
    }

    private void removeOverlays() {
        stopRunning();
        if (windowManager != null && targetView != null) {
            try {
                windowManager.removeView(targetView);
            } catch (Exception ignored) {
            }
        }
        if (windowManager != null && panelView != null) {
            try {
                windowManager.removeView(panelView);
            } catch (Exception ignored) {
            }
        }
        targetView = null;
        targetParams = null;
        panelView = null;
        panelParams = null;
        panelStatus = null;
        startStopButton = null;
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private final View movedView;
        private final WindowManager.LayoutParams params;
        private final boolean target;
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        private DragTouchListener(View movedView, WindowManager.LayoutParams params, boolean target) {
            this.movedView = movedView;
            this.params = params;
            this.target = target;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int proposedX = initialX + Math.round(event.getRawX() - initialTouchX);
                    int proposedY = initialY + Math.round(event.getRawY() - initialTouchY);
                    DisplayMetrics metrics = getResources().getDisplayMetrics();
                    int width = params.width > 0 ? params.width : movedView.getWidth();
                    int height = params.height > 0 ? params.height : movedView.getHeight();
                    params.x = Math.max(-width / 2,
                            Math.min(metrics.widthPixels - width / 2, proposedX));
                    params.y = Math.max(0,
                            Math.min(metrics.heightPixels - Math.max(dp(24), height / 2), proposedY));
                    try {
                        windowManager.updateViewLayout(movedView, params);
                    } catch (Exception ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (target) {
                        updatePanelText();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
