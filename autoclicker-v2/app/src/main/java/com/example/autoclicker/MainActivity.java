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
    static final String KEY_SWIPE_DISTANCE = "swipe_distance_px";
    static final String KEY_SWIPE_DURATION = "swipe_duration_ms";
    static final String KEY_SHOW_ON_CONNECT = "show_on_connect";

    static final String MODE_CLICK = "click";
    static final String MODE_HORIZONTAL = "horizontal";

    private TextView statusView;
    private EditText intervalInput;
    private EditText swipeDistanceInput;
    private EditText swipeDurationInput;
    private RadioGroup modeGroup;
    private LinearLayout horizontalSettings;
    private SharedPreferences preferences;

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
        title.setText("自动连点与横向移动");
        title.setTextColor(Color.rgb(30, 35, 42));
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("无需 Root。可选择固定位置连续点击，或在指定区域模拟左右滑动往返。");
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

        TextView modeLabel = sectionLabel("运行模式");
        card.addView(modeLabel);

        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        groupParams.topMargin = dp(7);
        card.addView(modeGroup, groupParams);

        RadioButton clickMode = new RadioButton(this);
        clickMode.setId(View.generateViewId());
        clickMode.setText("连点模式：在红色标记位置持续点击");
        clickMode.setTextSize(15);
        clickMode.setTextColor(Color.rgb(48, 54, 62));
        modeGroup.addView(clickMode);

        RadioButton horizontalMode = new RadioButton(this);
        horizontalMode.setId(View.generateViewId());
        horizontalMode.setText("横向移动：在蓝色标记位置左右滑动");
        horizontalMode.setTextSize(15);
        horizontalMode.setTextColor(Color.rgb(48, 54, 62));
        modeGroup.addView(horizontalMode);

        String savedMode = preferences.getString(KEY_MODE, MODE_CLICK);
        modeGroup.check(MODE_HORIZONTAL.equals(savedMode) ? horizontalMode.getId() : clickMode.getId());

        TextView intervalLabel = sectionLabel("动作间隔（毫秒）");
        LinearLayout.LayoutParams intervalLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        intervalLabelParams.topMargin = dp(16);
        card.addView(intervalLabel, intervalLabelParams);

        intervalInput = numberInput(String.valueOf(preferences.getLong(KEY_INTERVAL, 200L)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        inputParams.topMargin = dp(9);
        card.addView(intervalInput, inputParams);

        TextView intervalHint = hint("连点模式中表示两次点击之间的等待；横向模式中表示两次滑动之间的等待。范围：50–60000。");
        card.addView(intervalHint, hintParams());

        horizontalSettings = new LinearLayout(this);
        horizontalSettings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams horizontalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        horizontalParams.topMargin = dp(12);
        card.addView(horizontalSettings, horizontalParams);

        horizontalSettings.addView(sectionLabel("每次滑动距离（像素）"));
        swipeDistanceInput = numberInput(String.valueOf(preferences.getInt(KEY_SWIPE_DISTANCE, 240)));
        LinearLayout.LayoutParams distanceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        distanceParams.topMargin = dp(9);
        horizontalSettings.addView(swipeDistanceInput, distanceParams);
        horizontalSettings.addView(hint("程序会先向一侧滑动，再以相同距离向另一侧滑动，持续往返。范围：20–1500。"), hintParams());

        TextView durationLabel = sectionLabel("单次滑动持续时间（毫秒）");
        LinearLayout.LayoutParams durationLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        durationLabelParams.topMargin = dp(14);
        horizontalSettings.addView(durationLabel, durationLabelParams);

        swipeDurationInput = numberInput(String.valueOf(preferences.getLong(KEY_SWIPE_DURATION, 300L)));
        LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        durationParams.topMargin = dp(9);
        horizontalSettings.addView(swipeDurationInput, durationParams);
        horizontalSettings.addView(hint("数值越小滑动越快。范围：50–3000。"), hintParams());

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean horizontal = checkedId == horizontalMode.getId();
            horizontalSettings.setVisibility(horizontal ? View.VISIBLE : View.GONE);
        });
        horizontalSettings.setVisibility(MODE_HORIZONTAL.equals(savedMode) ? View.VISIBLE : View.GONE);

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
        showButton.setOnClickListener(v -> showFloatingControls(horizontalMode.getId()));
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
        guide.setText("① 选择“连点模式”或“横向移动”。\n② 设置参数并启用无障碍服务。\n③ 显示悬浮控制，把标记拖到目标位置。\n④ 连点模式：标记放在需要点击的按钮上。\n⑤ 横向模式：标记放在可拖动游戏视角的空白区域。\n⑥ 点击悬浮面板中的“开始”运行，再次点击停止。");
        guide.setTextColor(Color.rgb(75, 82, 90));
        guide.setTextSize(15);
        guide.setLineSpacing(dp(3), 1.2f);
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        guideParams.topMargin = dp(10);
        root.addView(guide, guideParams);

        TextView notice = new TextView(this);
        notice.setText("横向移动通过模拟手指向左、向右滑动实现。游戏中的实际视角方向可能与手指滑动方向相反，这是游戏自身的控制逻辑。应用不会读取或上传屏幕内容。");
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

    private void showFloatingControls(int horizontalRadioId) {
        long interval = readNumber(intervalInput, 50, 60000, "请输入 50–60000");
        if (interval < 0) {
            return;
        }
        int distance = (int) readNumber(swipeDistanceInput, 20, 1500, "请输入 20–1500");
        if (distance < 0) {
            return;
        }
        long duration = readNumber(swipeDurationInput, 50, 3000, "请输入 50–3000");
        if (duration < 0) {
            return;
        }

        String mode = modeGroup.getCheckedRadioButtonId() == horizontalRadioId
                ? MODE_HORIZONTAL : MODE_CLICK;

        preferences.edit()
                .putString(KEY_MODE, mode)
                .putLong(KEY_INTERVAL, interval)
                .putInt(KEY_SWIPE_DISTANCE, distance)
                .putLong(KEY_SWIPE_DURATION, duration)
                .apply();

        AutoClickAccessibilityService service = AutoClickAccessibilityService.getInstance();
        if (service != null) {
            service.setConfiguration(mode, interval, distance, duration);
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
        if (statusView == null) {
            return;
        }
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
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> enabledServices = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        ComponentName expected = new ComponentName(context, AutoClickAccessibilityService.class);
        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) {
                continue;
            }
            ComponentName actual = new ComponentName(
                    info.getResolveInfo().serviceInfo.packageName,
                    info.getResolveInfo().serviceInfo.name);
            if (expected.equals(actual)) {
                return true;
            }
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
