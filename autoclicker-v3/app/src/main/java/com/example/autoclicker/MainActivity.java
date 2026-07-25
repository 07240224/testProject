package com.example.autoclicker;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    static final String PREFS = "auto_clicker_prefs";
    static final String KEY_INTERVAL = "interval_ms";
    static final String KEY_MODE = "operation_mode";
    static final String KEY_HORIZONTAL_DISTANCE = "horizontal_distance_px";
    static final String KEY_HORIZONTAL_COUNT = "horizontal_move_count";
    static final String KEY_VERTICAL_DISTANCE = "vertical_distance_px";
    static final String KEY_VERTICAL_COUNT = "vertical_move_count";
    static final String KEY_SWIPE_DURATION = "swipe_duration_ms";
    static final String KEY_SHOW_ON_CONNECT = "show_on_connect";

    static final String MODE_CLICK = "click";
    static final String MODE_HORIZONTAL = "horizontal";
    static final String MODE_SNAKE = "snake";

    private TextView statusView;
    private EditText intervalInput;
    private EditText horizontalDistanceInput;
    private EditText horizontalCountInput;
    private EditText verticalDistanceInput;
    private EditText verticalCountInput;
    private EditText swipeDurationInput;
    private RadioGroup modeGroup;
    private LinearLayout movementSettings;
    private LinearLayout verticalSettings;
    private SharedPreferences preferences;
    private int clickModeId;
    private int horizontalModeId;
    private int snakeModeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(245, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("自动抓宠连点器");
        title.setTextColor(Color.rgb(30, 35, 42));
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("持续点击发射位置，并可在视角区域进行多段横向或蛇形扫描。");
        subtitle.setTextColor(Color.rgb(88, 96, 105));
        subtitle.setTextSize(15);
        subtitle.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(8);
        root.addView(subtitle, subtitleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Drawables.roundRect(Color.WHITE, dp(16), 0));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(22);
        root.addView(card, cardParams);

        card.addView(sectionLabel("运行模式"));

        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        groupParams.topMargin = dp(7);
        card.addView(modeGroup, groupParams);

        RadioButton clickMode = createRadio("仅连点：红色标记位置持续点击");
        clickModeId = clickMode.getId();
        modeGroup.addView(clickMode);

        RadioButton horizontalMode = createRadio("连点＋横向扫描：多段左右往返");
        horizontalModeId = horizontalMode.getId();
        modeGroup.addView(horizontalMode);

        RadioButton snakeMode = createRadio("连点＋蛇形扫描：横向逐行并上下往返");
        snakeModeId = snakeMode.getId();
        modeGroup.addView(snakeMode);

        String savedMode = normalizeMode(preferences.getString(KEY_MODE, MODE_CLICK));
        if (MODE_SNAKE.equals(savedMode)) {
            modeGroup.check(snakeModeId);
        } else if (MODE_HORIZONTAL.equals(savedMode)) {
            modeGroup.check(horizontalModeId);
        } else {
            modeGroup.check(clickModeId);
        }

        addLabelWithTop(card, "发射点击间隔（毫秒）", 16);
        intervalInput = numberInput(String.valueOf(preferences.getLong(KEY_INTERVAL, 150L)));
        card.addView(intervalInput, inputParams());
        card.addView(hint("纯连点时为两次点击间隔；扫描模式中为点击后开始下一段移动前的等待。范围：50–60000。"), hintParams());

        movementSettings = new LinearLayout(this);
        movementSettings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams movementParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        movementParams.topMargin = dp(16);
        card.addView(movementSettings, movementParams);

        movementSettings.addView(sectionLabel("横向单次移动距离（像素）"));
        horizontalDistanceInput = numberInput(String.valueOf(
                preferences.getInt(KEY_HORIZONTAL_DISTANCE, 120)));
        movementSettings.addView(horizontalDistanceInput, inputParams());
        movementSettings.addView(hint("每次只滑动一小段。范围：20–1500。"), hintParams());

        addLabelWithTop(movementSettings, "横向单程移动次数", 14);
        horizontalCountInput = numberInput(String.valueOf(
                preferences.getInt(KEY_HORIZONTAL_COUNT, 5)));
        movementSettings.addView(horizontalCountInput, inputParams());
        movementSettings.addView(hint("达到该次数后横向反向；蛇形模式中表示每一行的移动段数。范围：1–100。"), hintParams());

        addLabelWithTop(movementSettings, "单次滑动持续时间（毫秒）", 14);
        swipeDurationInput = numberInput(String.valueOf(
                preferences.getLong(KEY_SWIPE_DURATION, 220L)));
        movementSettings.addView(swipeDurationInput, inputParams());
        movementSettings.addView(hint("横向与竖直滑动共用。范围：50–3000。"), hintParams());

        verticalSettings = new LinearLayout(this);
        verticalSettings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams verticalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        verticalParams.topMargin = dp(14);
        movementSettings.addView(verticalSettings, verticalParams);

        verticalSettings.addView(sectionLabel("竖直单次移动距离（像素）"));
        verticalDistanceInput = numberInput(String.valueOf(
                preferences.getInt(KEY_VERTICAL_DISTANCE, 70)));
        verticalSettings.addView(verticalDistanceInput, inputParams());
        verticalSettings.addView(hint("每完成一整行横向扫描后，上下滑动一次。范围：20–1500。"), hintParams());

        addLabelWithTop(verticalSettings, "竖直单程移动次数", 14);
        verticalCountInput = numberInput(String.valueOf(
                preferences.getInt(KEY_VERTICAL_COUNT, 3)));
        verticalSettings.addView(verticalCountInput, inputParams());
        verticalSettings.addView(hint("达到该次数后竖直反向。范围：1–100。"), hintParams());

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> updateSettingsVisibility());
        updateSettingsVisibility();

        Button accessibilityButton = createButton("1. 打开无障碍设置", false);
        accessibilityButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开系统无障碍设置", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams firstButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        firstButtonParams.topMargin = dp(20);
        card.addView(accessibilityButton, firstButtonParams);

        Button showButton = createButton("2. 显示悬浮控制", true);
        showButton.setOnClickListener(v -> showFloatingControls());
        LinearLayout.LayoutParams secondButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        secondButtonParams.topMargin = dp(10);
        card.addView(showButton, secondButtonParams);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        root.addView(statusView, statusParams);

        TextView guideTitle = new TextView(this);
        guideTitle.setText("使用方法");
        guideTitle.setTextColor(Color.rgb(35, 40, 47));
        guideTitle.setTextSize(18);
        guideTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams guideTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        guideTitleParams.topMargin = dp(24);
        root.addView(guideTitle, guideTitleParams);

        TextView guide = new TextView(this);
        guide.setText("① 选择模式并设置参数。\n② 启用无障碍服务后显示悬浮控制。\n③ 红色标记放在精灵球发射键上。\n④ 横向或蛇形模式中，把蓝色标记放在可滑动视角的空白区域。\n⑤ 点击悬浮面板中的“开始”；运行时红色点负责发射，蓝色点负责分段移动视角。\n⑥ 再次点击“停止”后可以重新拖动标记。");
        guide.setTextColor(Color.rgb(75, 82, 90));
        guide.setTextSize(15);
        guide.setLineSpacing(dp(3), 1.2f);
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        guideParams.topMargin = dp(10);
        root.addView(guide, guideParams);

        TextView notice = new TextView(this);
        notice.setText("视角移动通过模拟手指左右、上下滑动实现。游戏中的实际视角方向可能与手指滑动方向相反。应用不会读取或上传屏幕内容。");
        notice.setTextColor(Color.rgb(112, 82, 20));
        notice.setTextSize(13);
        notice.setLineSpacing(0, 1.2f);
        notice.setPadding(dp(14), dp(13), dp(14), dp(13));
        notice.setBackground(Drawables.roundRect(Color.rgb(255, 247, 225), dp(12), Color.rgb(238, 213, 153)));
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noticeParams.topMargin = dp(22);
        root.addView(notice, noticeParams);

        return scrollView;
    }

    private RadioButton createRadio(String text) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.rgb(48, 54, 62));
        return button;
    }

    private void updateSettingsVisibility() {
        int checkedId = modeGroup.getCheckedRadioButtonId();
        boolean movement = checkedId == horizontalModeId || checkedId == snakeModeId;
        movementSettings.setVisibility(movement ? View.VISIBLE : View.GONE);
        verticalSettings.setVisibility(checkedId == snakeModeId ? View.VISIBLE : View.GONE);
    }

    private void showFloatingControls() {
        long interval = readNumber(intervalInput, 50, 60000, "请输入 50–60000");
        if (interval < 0) return;

        int horizontalDistance = (int) readNumber(horizontalDistanceInput, 20, 1500, "请输入 20–1500");
        if (horizontalDistance < 0) return;
        int horizontalCount = (int) readNumber(horizontalCountInput, 1, 100, "请输入 1–100");
        if (horizontalCount < 0) return;
        long duration = readNumber(swipeDurationInput, 50, 3000, "请输入 50–3000");
        if (duration < 0) return;
        int verticalDistance = (int) readNumber(verticalDistanceInput, 20, 1500, "请输入 20–1500");
        if (verticalDistance < 0) return;
        int verticalCount = (int) readNumber(verticalCountInput, 1, 100, "请输入 1–100");
        if (verticalCount < 0) return;

        String mode = selectedMode();
        preferences.edit()
                .putString(KEY_MODE, mode)
                .putLong(KEY_INTERVAL, interval)
                .putInt(KEY_HORIZONTAL_DISTANCE, horizontalDistance)
                .putInt(KEY_HORIZONTAL_COUNT, horizontalCount)
                .putInt(KEY_VERTICAL_DISTANCE, verticalDistance)
                .putInt(KEY_VERTICAL_COUNT, verticalCount)
                .putLong(KEY_SWIPE_DURATION, duration)
                .apply();

        AutoClickAccessibilityService service = AutoClickAccessibilityService.getInstance();
        if (service != null) {
            service.setConfiguration(mode, interval, horizontalDistance, horizontalCount,
                    verticalDistance, verticalCount, duration);
            service.showOverlays();
            Toast.makeText(this, "悬浮控制已显示", Toast.LENGTH_SHORT).show();
        } else {
            preferences.edit().putBoolean(KEY_SHOW_ON_CONNECT, true).apply();
            Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开系统无障碍设置", Toast.LENGTH_SHORT).show();
            }
        }
        updateStatus();
    }

    private String selectedMode() {
        int checked = modeGroup.getCheckedRadioButtonId();
        if (checked == snakeModeId) return MODE_SNAKE;
        if (checked == horizontalModeId) return MODE_HORIZONTAL;
        return MODE_CLICK;
    }

    private String normalizeMode(String value) {
        if (MODE_HORIZONTAL.equals(value) || MODE_SNAKE.equals(value)) return value;
        return MODE_CLICK;
    }

    private long readNumber(EditText input, long min, long max, String rangeError) {
        String text = input.getText().toString().trim();
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            input.setError("请输入整数");
            return -1;
        }
        if (value < min || value > max) {
            input.setError(rangeError);
            return -1;
        }
        return value;
    }

    private void addLabelWithTop(LinearLayout parent, String text, int marginDp) {
        TextView label = sectionLabel(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginDp);
        parent.addView(label, params);
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(40, 45, 52));
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private EditText numberInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(value);
        input.setTextSize(18);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(Drawables.roundRect(Color.rgb(242, 245, 248), dp(10), Color.rgb(210, 216, 222)));
        return input;
    }

    private LinearLayout.LayoutParams inputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(9);
        return params;
    }

    private TextView hint(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(Color.rgb(105, 112, 120));
        hint.setTextSize(13);
        return hint;
    }

    private LinearLayout.LayoutParams hintParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(7);
        return params;
    }

    private void updateStatus() {
        if (statusView == null) return;
        boolean enabled = isServiceEnabled(this);
        if (enabled) {
            statusView.setText("● 无障碍服务已启用");
            statusView.setTextColor(Color.rgb(20, 112, 66));
            statusView.setBackground(Drawables.roundRect(Color.rgb(229, 247, 237), dp(12), Color.rgb(165, 221, 190)));
        } else {
            statusView.setText("● 无障碍服务未启用");
            statusView.setTextColor(Color.rgb(170, 55, 45));
            statusView.setBackground(Drawables.roundRect(Color.rgb(255, 237, 235), dp(12), Color.rgb(240, 183, 177)));
        }
    }

    private static boolean isServiceEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> enabledServices = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        ComponentName expected = new ComponentName(context, AutoClickAccessibilityService.class);
        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            ComponentName actual = new ComponentName(
                    info.getResolveInfo().serviceInfo.packageName,
                    info.getResolveInfo().serviceInfo.name);
            if (expected.equals(actual)) return true;
        }
        return false;
    }

    private Button createButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(36, 83, 135));
        button.setBackground(Drawables.roundRect(
                primary ? Color.rgb(26, 115, 232) : Color.rgb(235, 243, 253),
                dp(12),
                primary ? 0 : Color.rgb(170, 204, 242)));
        return button;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
