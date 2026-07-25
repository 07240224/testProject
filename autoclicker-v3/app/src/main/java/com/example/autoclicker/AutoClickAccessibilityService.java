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
    private static final long AFTER_MOVE_DELAY_MS = 50L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;

    private TargetMarkerView clickTargetView;
    private TargetMarkerView moveTargetView;
    private LinearLayout panelView;
    private WindowManager.LayoutParams clickTargetParams;
    private WindowManager.LayoutParams moveTargetParams;
    private WindowManager.LayoutParams panelParams;
    private TextView panelStatus;
    private Button startStopButton;

    private boolean running;
    private boolean gestureInFlight;
    private boolean nextActionIsClick = true;
    private boolean nextMoveIsVertical;
    private int runGeneration;

    private int horizontalDirection = 1;
    private int verticalDirection = 1;
    private int horizontalStepsCompleted;
    private int verticalStepsCompleted;

    private String mode = MainActivity.MODE_CLICK;
    private long clickIntervalMs = 150L;
    private int horizontalDistancePx = 120;
    private int horizontalMoveCount = 5;
    private int verticalDistancePx = 70;
    private int verticalMoveCount = 3;
    private long swipeDurationMs = 220L;

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
        mode = normalizeMode(preferences.getString(MainActivity.KEY_MODE, MainActivity.MODE_CLICK));
        clickIntervalMs = clamp(preferences.getLong(MainActivity.KEY_INTERVAL, 150L), 50L, 60000L);
        horizontalDistancePx = (int) clamp(
                preferences.getInt(MainActivity.KEY_HORIZONTAL_DISTANCE, 120), 20, 1500);
        horizontalMoveCount = (int) clamp(
                preferences.getInt(MainActivity.KEY_HORIZONTAL_COUNT, 5), 1, 100);
        verticalDistancePx = (int) clamp(
                preferences.getInt(MainActivity.KEY_VERTICAL_DISTANCE, 70), 20, 1500);
        verticalMoveCount = (int) clamp(
                preferences.getInt(MainActivity.KEY_VERTICAL_COUNT, 3), 1, 100);
        swipeDurationMs = clamp(
                preferences.getLong(MainActivity.KEY_SWIPE_DURATION, 220L), 50L, 3000L);
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

    public void setConfiguration(String newMode,
                                 long intervalMs,
                                 int horizontalDistance,
                                 int horizontalCount,
                                 int verticalDistance,
                                 int verticalCount,
                                 long durationMs) {
        stopRunning();
        mode = normalizeMode(newMode);
        clickIntervalMs = clamp(intervalMs, 50L, 60000L);
        horizontalDistancePx = (int) clamp(horizontalDistance, 20, 1500);
        horizontalMoveCount = (int) clamp(horizontalCount, 1, 100);
        verticalDistancePx = (int) clamp(verticalDistance, 20, 1500);
        verticalMoveCount = (int) clamp(verticalCount, 1, 100);
        swipeDurationMs = clamp(durationMs, 50L, 3000L);

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit()
                .putString(MainActivity.KEY_MODE, mode)
                .putLong(MainActivity.KEY_INTERVAL, clickIntervalMs)
                .putInt(MainActivity.KEY_HORIZONTAL_DISTANCE, horizontalDistancePx)
                .putInt(MainActivity.KEY_HORIZONTAL_COUNT, horizontalMoveCount)
                .putInt(MainActivity.KEY_VERTICAL_DISTANCE, verticalDistancePx)
                .putInt(MainActivity.KEY_VERTICAL_COUNT, verticalMoveCount)
                .putLong(MainActivity.KEY_SWIPE_DURATION, swipeDurationMs)
                .apply();

        syncMoveTargetOverlay();
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
        if (clickTargetView == null) {
            createClickTargetOverlay();
        }
        syncMoveTargetOverlay();
        if (panelView == null) {
            createControlPanel();
        }
        updateMarkerAppearance();
        updatePanelText();
    }

    private void createClickTargetOverlay() {
        clickTargetView = new TargetMarkerView(this);
        clickTargetView.setMarkerType(TargetMarkerView.TYPE_CLICK);
        clickTargetView.setContentDescription("发射连点位置");

        int size = dp(64);
        clickTargetParams = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        clickTargetParams.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        clickTargetParams.x = Math.max(0, (int) (metrics.widthPixels * 0.75f) - size / 2);
        clickTargetParams.y = Math.max(dp(90), (int) (metrics.heightPixels * 0.70f) - size / 2);
        clickTargetView.setOnTouchListener(new DragTouchListener(clickTargetView, clickTargetParams));

        try {
            windowManager.addView(clickTargetView, clickTargetParams);
        } catch (Exception e) {
            clickTargetView = null;
            clickTargetParams = null;
            Toast.makeText(this, "红色发射标记创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void syncMoveTargetOverlay() {
        if (windowManager == null) return;
        boolean movementMode = !MainActivity.MODE_CLICK.equals(mode);
        if (movementMode && moveTargetView == null) {
            createMoveTargetOverlay();
        } else if (!movementMode && moveTargetView != null) {
            try {
                windowManager.removeView(moveTargetView);
            } catch (Exception ignored) {
            }
            moveTargetView = null;
            moveTargetParams = null;
        }
    }

    private void createMoveTargetOverlay() {
        moveTargetView = new TargetMarkerView(this);
        moveTargetView.setMarkerType(MainActivity.MODE_SNAKE.equals(mode)
                ? TargetMarkerView.TYPE_SNAKE : TargetMarkerView.TYPE_HORIZONTAL);
        moveTargetView.setContentDescription("视角滑动起点");

        int size = dp(64);
        moveTargetParams = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        moveTargetParams.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        moveTargetParams.x = Math.max(0, (metrics.widthPixels - size) / 2);
        moveTargetParams.y = Math.max(dp(90), (metrics.heightPixels - size) / 2);
        moveTargetView.setOnTouchListener(new DragTouchListener(moveTargetView, moveTargetParams));

        try {
            windowManager.addView(moveTargetView, moveTargetParams);
        } catch (Exception e) {
            moveTargetView = null;
            moveTargetParams = null;
            Toast.makeText(this, "蓝色视角标记创建失败", Toast.LENGTH_SHORT).show();
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
        dragHandle.setText("自动抓宠  ·  拖动面板");
        dragHandle.setTextColor(Color.WHITE);
        dragHandle.setTextSize(14);
        dragHandle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setPadding(dp(8), dp(6), dp(8), dp(8));
        panelView.addView(dragHandle, new LinearLayout.LayoutParams(dp(250), dp(40)));

        panelStatus = new TextView(this);
        panelStatus.setTextColor(Color.rgb(220, 225, 230));
        panelStatus.setTextSize(12);
        panelStatus.setGravity(Gravity.CENTER);
        panelStatus.setPadding(dp(4), dp(2), dp(4), dp(8));
        panelView.addView(panelStatus, new LinearLayout.LayoutParams(dp(250), dp(48)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        panelView.addView(row, new LinearLayout.LayoutParams(dp(250), dp(48)));

        startStopButton = overlayButton("开始", Color.rgb(33, 150, 83));
        startStopButton.setOnClickListener(v -> {
            if (running) stopRunning();
            else startRunning();
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
        dragHandle.setOnTouchListener(new DragTouchListener(panelView, panelParams));

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
        if (clickTargetView == null || clickTargetParams == null) {
            Toast.makeText(this, "请先显示并放置红色发射标记", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!MainActivity.MODE_CLICK.equals(mode)
                && (moveTargetView == null || moveTargetParams == null)) {
            Toast.makeText(this, "请先显示并放置蓝色视角标记", Toast.LENGTH_SHORT).show();
            return;
        }
        if (running) return;

        running = true;
        gestureInFlight = false;
        nextActionIsClick = true;
        nextMoveIsVertical = false;
        horizontalDirection = 1;
        verticalDirection = 1;
        horizontalStepsCompleted = 0;
        verticalStepsCompleted = 0;
        runGeneration++;

        setTargetsTouchable(false);
        updatePanelText();
        handler.removeCallbacks(actionRunnable);
        handler.post(actionRunnable);
    }

    private void stopRunning() {
        running = false;
        gestureInFlight = false;
        runGeneration++;
        handler.removeCallbacks(actionRunnable);
        setTargetsTouchable(true);
        updatePanelText();
    }

    private final Runnable actionRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || gestureInFlight || clickTargetParams == null || clickTargetView == null) return;

            final int generation = runGeneration;
            final boolean clickAction = MainActivity.MODE_CLICK.equals(mode) || nextActionIsClick;
            final boolean verticalAction = !clickAction && MainActivity.MODE_SNAKE.equals(mode) && nextMoveIsVertical;

            Path path = new Path();
            long duration;

            if (clickAction) {
                float x = clickTargetParams.x + clickTargetParams.width / 2f;
                float y = clickTargetParams.y + clickTargetParams.height / 2f;
                path.moveTo(x, y);
                duration = 1L;
            } else {
                if (moveTargetParams == null || moveTargetView == null) return;
                float startX = moveTargetParams.x + moveTargetParams.width / 2f;
                float startY = moveTargetParams.y + moveTargetParams.height / 2f;
                path.moveTo(startX, startY);
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                if (verticalAction) {
                    float endY = clampFloat(startY + verticalDirection * verticalDistancePx,
                            dp(4), metrics.heightPixels - dp(4));
                    path.lineTo(startX, endY);
                } else {
                    float endX = clampFloat(startX + horizontalDirection * horizontalDistancePx,
                            dp(4), metrics.widthPixels - dp(4));
                    path.lineTo(endX, startY);
                }
                duration = swipeDurationMs;
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
                        finishGesture(generation, clickAction, verticalAction, true);
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        finishGesture(generation, clickAction, verticalAction, false);
                    }
                }, handler);
            } catch (Exception e) {
                accepted = false;
            }

            if (!accepted) {
                finishGesture(generation, clickAction, verticalAction, false);
            }
        }
    };

    private void finishGesture(int generation,
                               boolean clickAction,
                               boolean verticalAction,
                               boolean completed) {
        if (generation != runGeneration) return;
        gestureInFlight = false;
        if (!running) return;

        long delay;
        if (MainActivity.MODE_CLICK.equals(mode)) {
            nextActionIsClick = true;
            delay = clickIntervalMs;
        } else if (clickAction) {
            nextActionIsClick = false;
            delay = clickIntervalMs;
        } else {
            if (completed) {
                advanceMovement(verticalAction);
            }
            nextActionIsClick = true;
            delay = AFTER_MOVE_DELAY_MS;
        }
        updatePanelText();
        handler.postDelayed(actionRunnable, delay);
    }

    private void advanceMovement(boolean verticalAction) {
        if (MainActivity.MODE_HORIZONTAL.equals(mode)) {
            horizontalStepsCompleted++;
            if (horizontalStepsCompleted >= horizontalMoveCount) {
                horizontalStepsCompleted = 0;
                horizontalDirection *= -1;
            }
            return;
        }

        if (!MainActivity.MODE_SNAKE.equals(mode)) return;
        if (verticalAction) {
            verticalStepsCompleted++;
            if (verticalStepsCompleted >= verticalMoveCount) {
                verticalStepsCompleted = 0;
                verticalDirection *= -1;
            }
            nextMoveIsVertical = false;
        } else {
            horizontalStepsCompleted++;
            if (horizontalStepsCompleted >= horizontalMoveCount) {
                horizontalStepsCompleted = 0;
                horizontalDirection *= -1;
                nextMoveIsVertical = true;
            }
        }
    }

    private void updateMarkerAppearance() {
        if (clickTargetView != null) {
            clickTargetView.setMarkerType(TargetMarkerView.TYPE_CLICK);
            clickTargetView.setContentDescription("发射连点位置");
        }
        if (moveTargetView != null) {
            moveTargetView.setMarkerType(MainActivity.MODE_SNAKE.equals(mode)
                    ? TargetMarkerView.TYPE_SNAKE : TargetMarkerView.TYPE_HORIZONTAL);
            moveTargetView.setContentDescription(MainActivity.MODE_SNAKE.equals(mode)
                    ? "蛇形视角滑动起点" : "横向视角滑动起点");
        }
    }

    private void setTargetsTouchable(boolean touchable) {
        setTargetTouchable(clickTargetView, clickTargetParams, touchable);
        setTargetTouchable(moveTargetView, moveTargetParams, touchable);
    }

    private void setTargetTouchable(View target,
                                    WindowManager.LayoutParams params,
                                    boolean touchable) {
        if (target == null || params == null || windowManager == null) return;
        if (touchable) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            target.setAlpha(1f);
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            target.setAlpha(0.55f);
        }
        try {
            windowManager.updateViewLayout(target, params);
        } catch (Exception ignored) {
        }
    }

    private void updatePanelText() {
        if (panelStatus != null) {
            String detail;
            if (MainActivity.MODE_SNAKE.equals(mode)) {
                detail = "蛇形 · 横 " + horizontalStepsCompleted + "/" + horizontalMoveCount
                        + " · 竖 " + verticalStepsCompleted + "/" + verticalMoveCount;
            } else if (MainActivity.MODE_HORIZONTAL.equals(mode)) {
                detail = "横向 · 当前 " + horizontalStepsCompleted + "/" + horizontalMoveCount;
            } else {
                detail = "仅连点 · 间隔 " + clickIntervalMs + " ms";
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
        removeViewQuietly(clickTargetView);
        removeViewQuietly(moveTargetView);
        removeViewQuietly(panelView);
        clickTargetView = null;
        moveTargetView = null;
        panelView = null;
        clickTargetParams = null;
        moveTargetParams = null;
        panelParams = null;
        panelStatus = null;
        startStopButton = null;
    }

    private void removeViewQuietly(View view) {
        if (windowManager == null || view == null) return;
        try {
            windowManager.removeView(view);
        } catch (Exception ignored) {
        }
    }

    private String normalizeMode(String value) {
        if (MainActivity.MODE_HORIZONTAL.equals(value) || MainActivity.MODE_SNAKE.equals(value)) {
            return value;
        }
        return MainActivity.MODE_CLICK;
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
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        private DragTouchListener(View movedView, WindowManager.LayoutParams params) {
            this.movedView = movedView;
            this.params = params;
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
                    updatePanelText();
                    return true;
                default:
                    return false;
            }
        }
    }
}
