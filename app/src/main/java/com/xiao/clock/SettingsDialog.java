package com.xiao.clock;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.xiao.clock.service.ClockAccessibilityService;
import com.xiao.clock.service.KeepAliveService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Lightweight dark-themed configuration dialogs compatible with Android 4.0+ (API 14+).
 */
public class SettingsDialog {

    public interface OnSettingsChangedListener {
        void onSettingsChanged();
        void onWeatherRefreshRequested();
        void onOrientationChanged();
    }

    private interface OnProgressChanged {
        void onChange(int progress);
    }

    private static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        private final OnProgressChanged callback;

        SimpleSeekBarListener(OnProgressChanged callback) {
            this.callback = callback;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && callback != null) {
                callback.onChange(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    @SuppressWarnings("deprecation")
    private static AlertDialog.Builder createDarkBuilder(Activity activity) {
        return new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_DARK);
    }

    private static int dp(Activity activity, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics());
    }

    /**
     * Flowing Multi-Color Gradient Palettes + Circadian + Custom Dual/Solid Color Dialog
     */
    public static void showColorDialog(final Activity activity, final ClockPrefs prefs,
                                       final OnSettingsChangedListener listener) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(makeSectionTitle(activity, "颜色模式"));

        RadioGroup rgMode = new RadioGroup(activity);
        rgMode.setOrientation(RadioGroup.VERTICAL);
        final RadioButton rbFlow = makeRadio(activity, "逐字流光多色渐变（推荐）");
        final RadioButton rbCircadian = makeRadio(activity, "24小时昼夜色温流光渐变");
        final RadioButton rbCustom = makeRadio(activity, "自定义固定纯色");
        rgMode.addView(rbFlow);
        rgMode.addView(rbCircadian);
        rgMode.addView(rbCustom);
        root.addView(rgMode);

        int currentMode = prefs.getColorMode();
        if (currentMode == ClockPrefs.COLOR_MODE_CUSTOM) rbCustom.setChecked(true);
        else if (currentMode == ClockPrefs.COLOR_MODE_CIRCADIAN) rbCircadian.setChecked(true);
        else rbFlow.setChecked(true);

        // Multi-color Gradient Palette Picker
        root.addView(makeSectionTitle(activity, "流光渐变色系（每个数字呈现多色过渡）"));
        RadioGroup rgPalette = new RadioGroup(activity);
        rgPalette.setOrientation(RadioGroup.VERTICAL);
        final RadioButton[] paletteButtons = new RadioButton[ColorEngine.PALETTE_NAMES.length];
        int curPalette = prefs.getGradientPalette();
        for (int i = 0; i < ColorEngine.PALETTE_NAMES.length; i++) {
            paletteButtons[i] = makeRadio(activity, ColorEngine.PALETTE_NAMES[i]);
            rgPalette.addView(paletteButtons[i]);
            if (i == curPalette) paletteButtons[i].setChecked(true);
        }
        root.addView(rgPalette);

        // Gradient Speed
        root.addView(makeSectionTitle(activity, "流光流动速度"));
        RadioGroup rgSpeed = new RadioGroup(activity);
        rgSpeed.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbSlow = makeRadio(activity, "慢速");
        final RadioButton rbNormal = makeRadio(activity, "中速");
        final RadioButton rbFast = makeRadio(activity, "快速");
        rgSpeed.addView(rbSlow);
        rgSpeed.addView(rbNormal);
        rgSpeed.addView(rbFast);
        root.addView(rgSpeed);

        int curSpeed = prefs.getGradientSpeed();
        if (curSpeed == ClockPrefs.SPEED_SLOW) rbSlow.setChecked(true);
        else if (curSpeed == ClockPrefs.SPEED_FAST) rbFast.setChecked(true);
        else rbNormal.setChecked(true);

        // Custom color editor (works for Solid color or Custom Dual Gradient)
        root.addView(makeSectionTitle(activity, "自定义颜色（主色 / 双色渐变副色）"));

        final boolean[] editingSecond = new boolean[]{false};
        LinearLayout toggleRow = new LinearLayout(activity);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button btnEdit1 = new Button(activity);
        final Button btnEdit2 = new Button(activity);
        btnEdit1.setText("编辑主色(色1)");
        btnEdit2.setText("编辑副色(色2)");
        btnEdit1.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnEdit2.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        toggleRow.addView(btnEdit1);
        toggleRow.addView(btnEdit2);
        root.addView(toggleRow);

        final TextView preview = new TextView(activity);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        preview.setBackgroundColor(Color.BLACK);
        root.addView(preview);

        int initColor = prefs.getCustomColor();
        final int[] rgb = new int[]{Color.red(initColor), Color.green(initColor), Color.blue(initColor)};

        final SeekBar sbR = new SeekBar(activity);
        final SeekBar sbG = new SeekBar(activity);
        final SeekBar sbB = new SeekBar(activity);
        sbR.setMax(255);
        sbG.setMax(255);
        sbB.setMax(255);
        sbR.setProgress(rgb[0]);
        sbG.setProgress(rgb[1]);
        sbB.setProgress(rgb[2]);

        final Runnable updatePreview = () -> {
            int c = Color.rgb(rgb[0], rgb[1], rgb[2]);
            preview.setTextColor(c);
            String tag = editingSecond[0] ? "副色" : "主色";
            preview.setText(String.format("%s  12:34  #%02X%02X%02X", tag, rgb[0], rgb[1], rgb[2]));
            if (editingSecond[0]) {
                prefs.setCustomColor2(c);
            } else {
                prefs.setCustomColor(c);
            }
            if (listener != null) listener.onSettingsChanged();
        };
        updatePreview.run();

        btnEdit1.setOnClickListener(v -> {
            editingSecond[0] = false;
            int c = prefs.getCustomColor();
            rgb[0] = Color.red(c);
            rgb[1] = Color.green(c);
            rgb[2] = Color.blue(c);
            sbR.setProgress(rgb[0]);
            sbG.setProgress(rgb[1]);
            sbB.setProgress(rgb[2]);
            updatePreview.run();
        });

        btnEdit2.setOnClickListener(v -> {
            editingSecond[0] = true;
            int c = prefs.getCustomColor2();
            rgb[0] = Color.red(c);
            rgb[1] = Color.green(c);
            rgb[2] = Color.blue(c);
            sbR.setProgress(rgb[0]);
            sbG.setProgress(rgb[1]);
            sbB.setProgress(rgb[2]);
            rbFlow.setChecked(true);
            paletteButtons[ClockPrefs.PALETTE_CUSTOM_DUAL].setChecked(true);
            prefs.setColorMode(ClockPrefs.COLOR_MODE_FLOW_GRADIENT);
            prefs.setGradientPalette(ClockPrefs.PALETTE_CUSTOM_DUAL);
            updatePreview.run();
        });

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        int swatchSize = dp(activity, 34);
        int swatchMargin = dp(activity, 5);

        for (int i = 0; i < ColorEngine.PRESET_COLORS.length; i++) {
            final int preset = ColorEngine.PRESET_COLORS[i];
            View swatch = new View(activity);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(preset);
            gd.setStroke(dp(activity, 1), 0x88FFFFFF);
            PermissionHelper.setViewBackground(swatch, gd);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            lp.setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin);
            swatch.setLayoutParams(lp);
            swatch.setOnClickListener(v -> {
                rgb[0] = Color.red(preset);
                rgb[1] = Color.green(preset);
                rgb[2] = Color.blue(preset);
                sbR.setProgress(rgb[0]);
                sbG.setProgress(rgb[1]);
                sbB.setProgress(rgb[2]);
                if (!editingSecond[0] && prefs.getColorMode() != ClockPrefs.COLOR_MODE_FLOW_GRADIENT) {
                    rbCustom.setChecked(true);
                    prefs.setColorMode(ClockPrefs.COLOR_MODE_CUSTOM);
                }
                updatePreview.run();
            });
            if (i < 5) row1.addView(swatch);
            else row2.addView(swatch);
        }
        root.addView(row1);
        root.addView(row2);

        root.addView(makeLabel(activity, "红 (R)"));
        root.addView(sbR);
        root.addView(makeLabel(activity, "绿 (G)"));
        root.addView(sbG);
        root.addView(makeLabel(activity, "蓝 (B)"));
        root.addView(sbB);

        SeekBar.OnSeekBarChangeListener rgbListener = new SimpleSeekBarListener(p -> {
            rgb[0] = sbR.getProgress();
            rgb[1] = sbG.getProgress();
            rgb[2] = sbB.getProgress();
            updatePreview.run();
        });
        sbR.setOnSeekBarChangeListener(rgbListener);
        sbG.setOnSeekBarChangeListener(rgbListener);
        sbB.setOnSeekBarChangeListener(rgbListener);

        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == rbCustom.getId()) {
                prefs.setColorMode(ClockPrefs.COLOR_MODE_CUSTOM);
            } else if (checkedId == rbCircadian.getId()) {
                prefs.setColorMode(ClockPrefs.COLOR_MODE_CIRCADIAN);
            } else {
                prefs.setColorMode(ClockPrefs.COLOR_MODE_FLOW_GRADIENT);
            }
            if (listener != null) listener.onSettingsChanged();
        });

        rgPalette.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < paletteButtons.length; i++) {
                if (checkedId == paletteButtons[i].getId()) {
                    prefs.setGradientPalette(i);
                    rbFlow.setChecked(true);
                    prefs.setColorMode(ClockPrefs.COLOR_MODE_FLOW_GRADIENT);
                    if (listener != null) listener.onSettingsChanged();
                    break;
                }
            }
        });

        rgSpeed.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == rbSlow.getId()) {
                prefs.setGradientSpeed(ClockPrefs.SPEED_SLOW);
            } else if (checkedId == rbFast.getId()) {
                prefs.setGradientSpeed(ClockPrefs.SPEED_FAST);
            } else {
                prefs.setGradientSpeed(ClockPrefs.SPEED_NORMAL);
            }
            if (listener != null) listener.onSettingsChanged();
        });

        createDarkBuilder(activity)
                .setTitle("彩虹流光与颜色设置")
                .setView(scroll)
                .setPositiveButton("完成", null)
                .show();
    }

    /**
     * Full Custom Layout Settings Dialog
     */
    public static void showLayoutDialog(final Activity activity, final ClockPrefs prefs,
                                        final OnSettingsChangedListener listener) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // Alignment
        root.addView(makeSectionTitle(activity, "整体对齐方式"));
        RadioGroup rgAlign = new RadioGroup(activity);
        rgAlign.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbCenter = makeRadio(activity, "居中");
        final RadioButton rbLeft = makeRadio(activity, "左对齐");
        final RadioButton rbRight = makeRadio(activity, "右对齐");
        rgAlign.addView(rbCenter);
        rgAlign.addView(rbLeft);
        rgAlign.addView(rbRight);
        root.addView(rgAlign);
        int curAlign = prefs.getLayoutAlign();
        if (curAlign == ClockPrefs.ALIGN_LEFT) rbLeft.setChecked(true);
        else if (curAlign == ClockPrefs.ALIGN_RIGHT) rbRight.setChecked(true);
        else rbCenter.setChecked(true);
        rgAlign.setOnCheckedChangeListener((g, id) -> {
            if (id == rbLeft.getId()) prefs.setLayoutAlign(ClockPrefs.ALIGN_LEFT);
            else if (id == rbRight.getId()) prefs.setLayoutAlign(ClockPrefs.ALIGN_RIGHT);
            else prefs.setLayoutAlign(ClockPrefs.ALIGN_CENTER);
            if (listener != null) listener.onSettingsChanged();
        });

        // Stacked vs Horizontal Time
        CheckBox cbStack = makeCheckBox(activity, "时间上下堆叠大字（小时在上，分钟在下）", prefs.isStackedTime());
        cbStack.setOnCheckedChangeListener((b, checked) -> {
            prefs.setStackedTime(checked);
            if (listener != null) listener.onSettingsChanged();
        });
        root.addView(cbStack);

        // Time Scale Slider (60% - 135%)
        final TextView tvScale = makeSectionTitle(activity, "时间字号缩放：" + prefs.getTimeScalePercent() + "%");
        root.addView(tvScale);
        SeekBar sbScale = new SeekBar(activity);
        sbScale.setMax(75); // 60 + 0..75 = 60..135
        sbScale.setProgress(prefs.getTimeScalePercent() - 60);
        sbScale.setOnSeekBarChangeListener(new SimpleSeekBarListener(p -> {
            int pct = 60 + p;
            prefs.setTimeScalePercent(pct);
            tvScale.setText("时间字号缩放：" + pct + "%");
            if (listener != null) listener.onSettingsChanged();
        }));
        root.addView(sbScale);

        // Font Style
        root.addView(makeSectionTitle(activity, "时钟字体风格"));
        RadioGroup rgFont = new RadioGroup(activity);
        rgFont.setOrientation(RadioGroup.VERTICAL);
        final RadioButton rbF0 = makeRadio(activity, "等宽粗体（经典数字表，防抖动）");
        final RadioButton rbF1 = makeRadio(activity, "现代无衬线粗体");
        final RadioButton rbF2 = makeRadio(activity, "极简纤细现代体");
        final RadioButton rbF3 = makeRadio(activity, "优雅衬线粗体");
        rgFont.addView(rbF0);
        rgFont.addView(rbF1);
        rgFont.addView(rbF2);
        rgFont.addView(rbF3);
        root.addView(rgFont);
        int curFont = prefs.getFontStyle();
        if (curFont == ClockPrefs.FONT_SANS_BOLD) rbF1.setChecked(true);
        else if (curFont == ClockPrefs.FONT_SANS_LIGHT) rbF2.setChecked(true);
        else if (curFont == ClockPrefs.FONT_SERIF_BOLD) rbF3.setChecked(true);
        else rbF0.setChecked(true);
        rgFont.setOnCheckedChangeListener((g, id) -> {
            if (id == rbF1.getId()) prefs.setFontStyle(ClockPrefs.FONT_SANS_BOLD);
            else if (id == rbF2.getId()) prefs.setFontStyle(ClockPrefs.FONT_SANS_LIGHT);
            else if (id == rbF3.getId()) prefs.setFontStyle(ClockPrefs.FONT_SERIF_BOLD);
            else prefs.setFontStyle(ClockPrefs.FONT_MONO_BOLD);
            if (listener != null) listener.onSettingsChanged();
        });

        // Date Position
        root.addView(makeSectionTitle(activity, "日期显示位置"));
        RadioGroup rgDate = new RadioGroup(activity);
        rgDate.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbDateBelow = makeRadio(activity, "时间下方");
        final RadioButton rbDateAbove = makeRadio(activity, "时间上方");
        final RadioButton rbDateHide = makeRadio(activity, "隐藏");
        rgDate.addView(rbDateBelow);
        rgDate.addView(rbDateAbove);
        rgDate.addView(rbDateHide);
        root.addView(rgDate);
        int curDatePos = prefs.getDatePosition();
        if (curDatePos == ClockPrefs.DATE_ABOVE_TIME) rbDateAbove.setChecked(true);
        else if (curDatePos == ClockPrefs.DATE_HIDDEN) rbDateHide.setChecked(true);
        else rbDateBelow.setChecked(true);
        rgDate.setOnCheckedChangeListener((g, id) -> {
            if (id == rbDateAbove.getId()) prefs.setDatePosition(ClockPrefs.DATE_ABOVE_TIME);
            else if (id == rbDateHide.getId()) prefs.setDatePosition(ClockPrefs.DATE_HIDDEN);
            else prefs.setDatePosition(ClockPrefs.DATE_BELOW_TIME);
            if (listener != null) listener.onSettingsChanged();
        });

        // Weather Position
        root.addView(makeSectionTitle(activity, "当地天气显示位置"));
        RadioGroup rgWeather = new RadioGroup(activity);
        rgWeather.setOrientation(RadioGroup.VERTICAL);
        final RadioButton rbWCenter = makeRadio(activity, "主区域下方（默认）");
        final RadioButton rbWTL = makeRadio(activity, "屏幕左上角");
        final RadioButton rbWTR = makeRadio(activity, "屏幕右上角");
        final RadioButton rbWBL = makeRadio(activity, "屏幕左下角");
        final RadioButton rbWBR = makeRadio(activity, "屏幕右下角");
        final RadioButton rbWHide = makeRadio(activity, "隐藏天气");
        rgWeather.addView(rbWCenter);
        rgWeather.addView(rbWTL);
        rgWeather.addView(rbWTR);
        rgWeather.addView(rbWBL);
        rgWeather.addView(rbWBR);
        rgWeather.addView(rbWHide);
        root.addView(rgWeather);
        int curWPos = prefs.getWeatherPosition();
        if (curWPos == ClockPrefs.WEATHER_TOP_LEFT) rbWTL.setChecked(true);
        else if (curWPos == ClockPrefs.WEATHER_TOP_RIGHT) rbWTR.setChecked(true);
        else if (curWPos == ClockPrefs.WEATHER_BOTTOM_LEFT) rbWBL.setChecked(true);
        else if (curWPos == ClockPrefs.WEATHER_BOTTOM_RIGHT) rbWBR.setChecked(true);
        else if (curWPos == ClockPrefs.WEATHER_HIDDEN) rbWHide.setChecked(true);
        else rbWCenter.setChecked(true);
        rgWeather.setOnCheckedChangeListener((g, id) -> {
            if (id == rbWTL.getId()) prefs.setWeatherPosition(ClockPrefs.WEATHER_TOP_LEFT);
            else if (id == rbWTR.getId()) prefs.setWeatherPosition(ClockPrefs.WEATHER_TOP_RIGHT);
            else if (id == rbWBL.getId()) prefs.setWeatherPosition(ClockPrefs.WEATHER_BOTTOM_LEFT);
            else if (id == rbWBR.getId()) prefs.setWeatherPosition(ClockPrefs.WEATHER_BOTTOM_RIGHT);
            else if (id == rbWHide.getId()) prefs.setWeatherPosition(ClockPrefs.WEATHER_HIDDEN);
            else prefs.setWeatherPosition(ClockPrefs.WEATHER_CENTER);
            if (listener != null) listener.onSettingsChanged();
        });

        // Spacing
        root.addView(makeSectionTitle(activity, "元素上下间距"));
        RadioGroup rgSpace = new RadioGroup(activity);
        rgSpace.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbS0 = makeRadio(activity, "紧凑");
        final RadioButton rbS1 = makeRadio(activity, "适中");
        final RadioButton rbS2 = makeRadio(activity, "宽松");
        rgSpace.addView(rbS0);
        rgSpace.addView(rbS1);
        rgSpace.addView(rbS2);
        root.addView(rgSpace);
        int curSpace = prefs.getElementSpacing();
        if (curSpace == ClockPrefs.SPACING_COMPACT) rbS0.setChecked(true);
        else if (curSpace == ClockPrefs.SPACING_RELAXED) rbS2.setChecked(true);
        else rbS1.setChecked(true);
        rgSpace.setOnCheckedChangeListener((g, id) -> {
            if (id == rbS0.getId()) prefs.setElementSpacing(ClockPrefs.SPACING_COMPACT);
            else if (id == rbS2.getId()) prefs.setElementSpacing(ClockPrefs.SPACING_RELAXED);
            else prefs.setElementSpacing(ClockPrefs.SPACING_NORMAL);
            if (listener != null) listener.onSettingsChanged();
        });

        createDarkBuilder(activity)
                .setTitle("时钟布局自定义")
                .setView(scroll)
                .setPositiveButton("完成", null)
                .setNeutralButton("恢复默认", (dialog, which) -> {
                    prefs.setLayoutAlign(ClockPrefs.ALIGN_CENTER);
                    prefs.setStackedTime(false);
                    prefs.setTimeScalePercent(100);
                    prefs.setFontStyle(ClockPrefs.FONT_MONO_BOLD);
                    prefs.setDatePosition(ClockPrefs.DATE_BELOW_TIME);
                    prefs.setWeatherPosition(ClockPrefs.WEATHER_CENTER);
                    prefs.setElementSpacing(ClockPrefs.SPACING_NORMAL);
                    if (listener != null) listener.onSettingsChanged();
                })
                .show();
    }

    /**
     * Corner Schedule Countdown Settings Dialog
     */
    public static void showCountdownDialog(final Activity activity, final ClockPrefs prefs,
                                           final OnSettingsChangedListener listener) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(makeSectionTitle(activity, "显示角落位置"));
        RadioGroup rgCorner = new RadioGroup(activity);
        rgCorner.setOrientation(RadioGroup.VERTICAL);
        final RadioButton rbTR = makeRadio(activity, "屏幕右上角（默认）");
        final RadioButton rbTL = makeRadio(activity, "屏幕左上角");
        final RadioButton rbBL = makeRadio(activity, "屏幕左下角");
        final RadioButton rbBR = makeRadio(activity, "屏幕右下角");
        final RadioButton rbHide = makeRadio(activity, "隐藏倒计时");
        rgCorner.addView(rbTR);
        rgCorner.addView(rbTL);
        rgCorner.addView(rbBL);
        rgCorner.addView(rbBR);
        rgCorner.addView(rbHide);
        root.addView(rgCorner);

        int curCorner = prefs.getCountdownCorner();
        if (curCorner == ClockPrefs.CORNER_TOP_LEFT) rbTL.setChecked(true);
        else if (curCorner == ClockPrefs.CORNER_BOTTOM_LEFT) rbBL.setChecked(true);
        else if (curCorner == ClockPrefs.CORNER_BOTTOM_RIGHT) rbBR.setChecked(true);
        else if (curCorner == ClockPrefs.CORNER_HIDDEN) rbHide.setChecked(true);
        else rbTR.setChecked(true);

        root.addView(makeSectionTitle(activity, "日程标题"));
        final EditText etTitle = new EditText(activity);
        etTitle.setSingleLine(true);
        etTitle.setTextColor(Color.WHITE);
        etTitle.setHintTextColor(0x88FFFFFF);
        etTitle.setHint("例如：高考 / 下班 / 放假 / 新年");
        etTitle.setText(prefs.getCountdownTitle());
        root.addView(etTitle);

        root.addView(makeSectionTitle(activity, "目标日期与时间（格式：yyyy-MM-dd HH:mm）"));
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        final EditText etDateTime = new EditText(activity);
        etDateTime.setSingleLine(true);
        etDateTime.setTextColor(Color.WHITE);
        etDateTime.setHintTextColor(0x88FFFFFF);
        etDateTime.setHint("2027-01-01 00:00");
        etDateTime.setText(fmt.format(new Date(prefs.getCountdownTargetMillis())));
        root.addView(etDateTime);

        root.addView(makeSectionTitle(activity, "快捷时间预设"));
        LinearLayout presetRow1 = new LinearLayout(activity);
        presetRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout presetRow2 = new LinearLayout(activity);
        presetRow2.setOrientation(LinearLayout.HORIZONTAL);

        Button b1h = makeSmallBtn(activity, "+1小时", v -> {
            etDateTime.setText(fmt.format(new Date(System.currentTimeMillis() + 3600L * 1000L)));
            if (TextUtils.isEmpty(etTitle.getText().toString().trim())) etTitle.setText("专注倒计时");
        });
        Button bOff = makeSmallBtn(activity, "今天18:00", v -> {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 18);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
            etDateTime.setText(fmt.format(c.getTime()));
            etTitle.setText("距离下班");
        });
        Button bTom = makeSmallBtn(activity, "明天09:00", v -> {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 9);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            etDateTime.setText(fmt.format(c.getTime()));
            etTitle.setText("明日日程");
        });
        presetRow1.addView(b1h);
        presetRow1.addView(bOff);
        presetRow1.addView(bTom);

        Button b7d = makeSmallBtn(activity, "+7天", v -> {
            etDateTime.setText(fmt.format(new Date(System.currentTimeMillis() + 7L * 86400000L)));
        });
        Button b30d = makeSmallBtn(activity, "+30天", v -> {
            etDateTime.setText(fmt.format(new Date(System.currentTimeMillis() + 30L * 86400000L)));
        });
        Button bNY = makeSmallBtn(activity, "明年元旦", v -> {
            Calendar c = Calendar.getInstance();
            c.set(c.get(Calendar.YEAR) + 1, Calendar.JANUARY, 1, 0, 0, 0);
            etDateTime.setText(fmt.format(c.getTime()));
            etTitle.setText((c.get(Calendar.YEAR)) + "年元旦");
        });
        presetRow2.addView(b7d);
        presetRow2.addView(b30d);
        presetRow2.addView(bNY);

        root.addView(presetRow1);
        root.addView(presetRow2);

        createDarkBuilder(activity)
                .setTitle("角落日程倒计时设置")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    int checkedId = rgCorner.getCheckedRadioButtonId();
                    if (checkedId == rbTL.getId()) prefs.setCountdownCorner(ClockPrefs.CORNER_TOP_LEFT);
                    else if (checkedId == rbBL.getId()) prefs.setCountdownCorner(ClockPrefs.CORNER_BOTTOM_LEFT);
                    else if (checkedId == rbBR.getId()) prefs.setCountdownCorner(ClockPrefs.CORNER_BOTTOM_RIGHT);
                    else if (checkedId == rbHide.getId()) prefs.setCountdownCorner(ClockPrefs.CORNER_HIDDEN);
                    else prefs.setCountdownCorner(ClockPrefs.CORNER_TOP_RIGHT);

                    String title = etTitle.getText().toString().trim();
                    prefs.setCountdownTitle(TextUtils.isEmpty(title) ? "日程倒计时" : title);

                    String dtStr = etDateTime.getText().toString().trim();
                    try {
                        Date parsed = fmt.parse(dtStr);
                        if (parsed != null) {
                            prefs.setCountdownTargetMillis(parsed.getTime());
                        }
                    } catch (Exception e) {
                        Toast.makeText(activity, "日期格式应为 yyyy-MM-dd HH:mm，已保留原时间", Toast.LENGTH_LONG).show();
                    }
                    if (listener != null) listener.onSettingsChanged();
                })
                .show();
    }

    /**
     * Hourly Chime Settings Dialog (System TTS + Built-in Synthesized Bell + Quiet Hours + Test)
     */
    public static void showChimeDialog(final Activity activity, final ClockPrefs prefs) {
        final HourlyChimeManager chimeManager = HourlyChimeManager.getInstance(activity);
        chimeManager.initTtsIfNeeded();

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        String ttsStatus;
        int ttsColor;
        if (chimeManager.isTtsReady() && chimeManager.isTtsChineseAvailable()) {
            ttsStatus = "语音引擎状态：系统中文 TTS 已就绪";
            ttsColor = 0xFF4ADE80;
        } else if (chimeManager.isTtsReady()) {
            ttsStatus = "语音引擎状态：系统 TTS 已就绪（未检测到中文包，可配合内置钟声）";
            ttsColor = 0xFFFBBF24;
        } else {
            ttsStatus = "语音引擎状态：未检测到系统 TTS，将自动使用内置清脆钟声";
            ttsColor = 0xFFFBBF24;
        }
        TextView tvStatus = makeLabel(activity, ttsStatus);
        tvStatus.setTextColor(ttsColor);
        tvStatus.setPadding(0, 0, 0, dp(activity, 8));
        root.addView(tvStatus);

        Button btnTest = new Button(activity);
        btnTest.setText("立即试听报时效果");
        btnTest.setOnClickListener(v -> chimeManager.testChimeNow(activity));
        root.addView(btnTest);

        root.addView(makeSectionTitle(activity, "报时开关"));
        CheckBox cbEnable = makeCheckBox(activity, "开启整点报时（每小时 :00 自动播报）", prefs.isHourlyChimeEnabled());
        cbEnable.setOnCheckedChangeListener((b, checked) -> prefs.setHourlyChimeEnabled(checked));
        root.addView(cbEnable);

        CheckBox cbHalf = makeCheckBox(activity, "同时开启半点报时（每小时 :30 提示）", prefs.isHalfHourChimeEnabled());
        cbHalf.setOnCheckedChangeListener((b, checked) -> prefs.setHalfHourChimeEnabled(checked));
        root.addView(cbHalf);

        root.addView(makeSectionTitle(activity, "报时声音方式"));
        RadioGroup rgMode = new RadioGroup(activity);
        rgMode.setOrientation(RadioGroup.VERTICAL);
        final RadioButton rbBellTts = makeRadio(activity, "内置清脆钟声 + 系统TTS语音（推荐）");
        final RadioButton rbTtsOnly = makeRadio(activity, "仅系统TTS语音（无TTS自动回退钟声）");
        final RadioButton rbBellOnly = makeRadio(activity, "仅内置清脆钟声（纯离线，老机100%可用）");
        rgMode.addView(rbBellTts);
        rgMode.addView(rbTtsOnly);
        rgMode.addView(rbBellOnly);
        root.addView(rgMode);

        int curMode = prefs.getChimeMode();
        if (curMode == ClockPrefs.CHIME_MODE_TTS_ONLY) rbTtsOnly.setChecked(true);
        else if (curMode == ClockPrefs.CHIME_MODE_BELL_ONLY) rbBellOnly.setChecked(true);
        else rbBellTts.setChecked(true);

        rgMode.setOnCheckedChangeListener((g, id) -> {
            if (id == rbTtsOnly.getId()) prefs.setChimeMode(ClockPrefs.CHIME_MODE_TTS_ONLY);
            else if (id == rbBellOnly.getId()) prefs.setChimeMode(ClockPrefs.CHIME_MODE_BELL_ONLY);
            else prefs.setChimeMode(ClockPrefs.CHIME_MODE_TTS_WITH_BELL);
        });

        root.addView(makeSectionTitle(activity, "夜间免打扰"));
        final CheckBox cbQuiet = makeCheckBox(activity,
                String.format(Locale.CHINA, "夜间静音时段（%02d:00 ~ %02d:00 不报时）",
                        prefs.getChimeQuietStartHour(), prefs.getChimeQuietEndHour()),
                prefs.isChimeQuietHoursEnabled());
        cbQuiet.setOnCheckedChangeListener((b, checked) -> prefs.setChimeQuietHoursEnabled(checked));
        root.addView(cbQuiet);

        LinearLayout qRow = new LinearLayout(activity);
        qRow.setOrientation(LinearLayout.HORIZONTAL);
        qRow.addView(makeSmallBtn(activity, "23:00~07:00", v -> {
            prefs.setChimeQuietStartHour(23);
            prefs.setChimeQuietEndHour(7);
            cbQuiet.setText("夜间静音时段（23:00 ~ 07:00 不报时）");
        }));
        qRow.addView(makeSmallBtn(activity, "22:00~08:00", v -> {
            prefs.setChimeQuietStartHour(22);
            prefs.setChimeQuietEndHour(8);
            cbQuiet.setText("夜间静音时段（22:00 ~ 08:00 不报时）");
        }));
        qRow.addView(makeSmallBtn(activity, "00:00~06:00", v -> {
            prefs.setChimeQuietStartHour(0);
            prefs.setChimeQuietEndHour(6);
            cbQuiet.setText("夜间静音时段（00:00 ~ 06:00 不报时）");
        }));
        root.addView(qRow);

        createDarkBuilder(activity)
                .setTitle("整点语音报时设置")
                .setView(scroll)
                .setPositiveButton("完成", null)
                .show();
    }

    /**
     * Permissions & Background Keep-Alive Dialog
     */
    public static void showPermissionsDialog(final Activity activity, final ClockPrefs prefs,
                                             final OnSettingsChangedListener listener) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        boolean hasOverlay = PermissionHelper.hasOverlayPermission(activity);
        boolean hasAccessibility = PermissionHelper.isAccessibilityEnabled(activity)
                || ClockAccessibilityService.isRunning();
        boolean hasBattery = PermissionHelper.isIgnoringBatteryOptimizations(activity);
        boolean hasLocation = PermissionHelper.hasLocationPermission(activity);

        root.addView(makeSectionTitle(activity, "系统权限申请"));

        root.addView(makePermRow(activity,
                "悬浮窗权限（桌面悬浮时钟 / 后台拉起）",
                hasOverlay ? "已授权" : "未授权",
                hasOverlay ? 0xFF4ADE80 : 0xFFFBBF24,
                "去授权",
                v -> PermissionHelper.openOverlayPermissionSettings(activity)));

        root.addView(makePermRow(activity,
                "无障碍辅助服务（强化后台保活防清理）",
                hasAccessibility ? "已开启" : "未开启",
                hasAccessibility ? 0xFF4ADE80 : 0xFFFBBF24,
                "去开启",
                v -> PermissionHelper.openAccessibilitySettings(activity)));

        root.addView(makePermRow(activity,
                "忽略电池优化（防止休眠杀后台）",
                hasBattery ? "已加入白名单" : "未设置",
                hasBattery ? 0xFF4ADE80 : 0xFFFBBF24,
                "去设置",
                v -> PermissionHelper.openBatteryOptimizationSettings(activity)));

        root.addView(makePermRow(activity,
                "位置权限（当地天气自动定位）",
                hasLocation ? "已授权" : "未授权",
                hasLocation ? 0xFF4ADE80 : 0xFFFBBF24,
                "申请",
                v -> PermissionHelper.requestLocationPermission(activity)));

        root.addView(makeSectionTitle(activity, "后台保活与自启动开关"));

        CheckBox cbKeepAlive = makeCheckBox(activity, "开启前台保活服务（通知栏常驻防杀）", prefs.isKeepAliveEnabled());
        cbKeepAlive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setKeepAliveEnabled(isChecked);
            KeepAliveService.syncServiceState(activity);
            if (listener != null) listener.onSettingsChanged();
        });
        root.addView(cbKeepAlive);

        CheckBox cbBoot = makeCheckBox(activity, "开机自动启动时钟", prefs.isBootAutoStart());
        cbBoot.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.setBootAutoStart(isChecked));
        root.addView(cbBoot);

        CheckBox cbCharge = makeCheckBox(activity, "插上电源/充电时自动打开时钟", prefs.isAutoOpenOnCharge());
        cbCharge.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.setAutoOpenOnCharge(isChecked));
        root.addView(cbCharge);

        TextView tip = makeLabel(activity,
                "提示：在国产定制系统（如MIUI/EMUI/ColorOS）或老旧安卓设备上，建议同时在系统「自启动管理」中允许本应用自启动，并开启无障碍与悬浮窗权限以达到最佳保活效果。");
        tip.setTextColor(0x99FFFFFF);
        tip.setPadding(0, dp(activity, 8), 0, 0);
        root.addView(tip);

        createDarkBuilder(activity)
                .setTitle("权限与后台保活中心")
                .setView(scroll)
                .setPositiveButton("确定", null)
                .show();
    }

    /**
     * Weather City Picker & Auto-Location Dialog
     */
    public static void showWeatherCityDialog(final Activity activity, final ClockPrefs prefs,
                                             final WeatherManager weatherManager,
                                             final WeatherManager.Callback callback) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        final AlertDialog dialog = createDarkBuilder(activity)
                .setTitle("当地天气与城市设置")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .create();

        Button btnAuto = new Button(activity);
        btnAuto.setText("自动定位当前城市并刷新天气");
        btnAuto.setOnClickListener(v -> {
            prefs.setAutoWeatherLocation(true);
            Toast.makeText(activity, "正在定位并获取天气...", Toast.LENGTH_SHORT).show();
            weatherManager.fetchWeather(callback);
            dialog.dismiss();
        });
        root.addView(btnAuto);

        root.addView(makeSectionTitle(activity, "搜索自定义城市"));
        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText etCity = new EditText(activity);
        etCity.setHint("输入城市名，如：大理 / 三亚");
        etCity.setSingleLine(true);
        etCity.setTextColor(Color.WHITE);
        etCity.setHintTextColor(0x88FFFFFF);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etCity.setLayoutParams(etLp);
        searchRow.addView(etCity);

        Button btnSearch = new Button(activity);
        btnSearch.setText("搜索");
        btnSearch.setOnClickListener(v -> {
            String q = etCity.getText().toString().trim();
            if (TextUtils.isEmpty(q)) {
                Toast.makeText(activity, "请输入城市名称", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(activity, "正在搜索 " + q + "...", Toast.LENGTH_SHORT).show();
            weatherManager.searchAndSelectCity(q, callback);
            dialog.dismiss();
        });
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        root.addView(makeSectionTitle(activity, "常用城市快速选择"));
        for (int i = 0; i < WeatherManager.PRESET_CITIES.length; i += 4) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 4 && (i + j) < WeatherManager.PRESET_CITIES.length; j++) {
                final WeatherManager.CityPreset city = WeatherManager.PRESET_CITIES[i + j];
                Button b = new Button(activity);
                b.setText(city.name);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                b.setLayoutParams(blp);
                b.setOnClickListener(v -> {
                    prefs.setAutoWeatherLocation(false);
                    prefs.setWeatherCity(city.name);
                    prefs.setWeatherCoords(city.lat, city.lon);
                    Toast.makeText(activity, "已切换至 " + city.name + "，正在更新天气...", Toast.LENGTH_SHORT).show();
                    weatherManager.fetchWeather(callback);
                    dialog.dismiss();
                });
                row.addView(b);
            }
            root.addView(row);
        }

        dialog.show();
    }

    /**
     * General Clock Settings Dialog (Orientation, 24h, Seconds, OLED Burn-in, Brightness)
     */
    public static void showGeneralSettingsDialog(final Activity activity, final ClockPrefs prefs,
                                                 final OnSettingsChangedListener listener) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(makeSectionTitle(activity, "屏幕方向"));
        RadioGroup rgOri = new RadioGroup(activity);
        rgOri.setOrientation(RadioGroup.VERTICAL);
        final RadioButton rbAuto = makeRadio(activity, "自动跟随重力感应");
        final RadioButton rbLand = makeRadio(activity, "锁定横屏");
        final RadioButton rbPort = makeRadio(activity, "锁定竖屏");
        final RadioButton rbRevLand = makeRadio(activity, "锁定反向横屏（适配充电线方向）");
        rgOri.addView(rbAuto);
        rgOri.addView(rbLand);
        rgOri.addView(rbPort);
        rgOri.addView(rbRevLand);
        root.addView(rgOri);

        int ori = prefs.getOrientationMode();
        if (ori == ClockPrefs.ORIENTATION_LANDSCAPE) rbLand.setChecked(true);
        else if (ori == ClockPrefs.ORIENTATION_PORTRAIT) rbPort.setChecked(true);
        else if (ori == ClockPrefs.ORIENTATION_REVERSE_LANDSCAPE) rbRevLand.setChecked(true);
        else rbAuto.setChecked(true);

        rgOri.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == rbLand.getId()) prefs.setOrientationMode(ClockPrefs.ORIENTATION_LANDSCAPE);
            else if (checkedId == rbPort.getId()) prefs.setOrientationMode(ClockPrefs.ORIENTATION_PORTRAIT);
            else if (checkedId == rbRevLand.getId()) prefs.setOrientationMode(ClockPrefs.ORIENTATION_REVERSE_LANDSCAPE);
            else prefs.setOrientationMode(ClockPrefs.ORIENTATION_AUTO);
            if (listener != null) listener.onOrientationChanged();
        });

        root.addView(makeSectionTitle(activity, "显示选项"));

        CheckBox cb24 = makeCheckBox(activity, "使用 24 小时制", prefs.is24HourFormat());
        cb24.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.set24HourFormat(isChecked);
            if (listener != null) listener.onSettingsChanged();
        });
        root.addView(cb24);

        CheckBox cbSec = makeCheckBox(activity, "显示秒数 (:ss)", prefs.isShowSeconds());
        cbSec.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setShowSeconds(isChecked);
            if (listener != null) listener.onSettingsChanged();
        });
        root.addView(cbSec);

        CheckBox cbOled = makeCheckBox(activity, "OLED 防烧屏微移保护（每分钟微调位置）", prefs.isOledBurnInProtect());
        cbOled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setOledBurnInProtect(isChecked);
            if (listener != null) listener.onSettingsChanged();
        });
        root.addView(cbOled);

        final TextView tvBright = makeSectionTitle(activity,
                prefs.getBrightness() < 0 ? "屏幕亮度：跟随系统" : ("屏幕亮度：" + prefs.getBrightness() + "%"));
        root.addView(tvBright);

        SeekBar sbBright = new SeekBar(activity);
        sbBright.setMax(100);
        sbBright.setProgress(prefs.getBrightness() < 0 ? 0 : prefs.getBrightness());
        sbBright.setOnSeekBarChangeListener(new SimpleSeekBarListener(progress -> {
            if (progress == 0) {
                prefs.setBrightness(-1);
                tvBright.setText("屏幕亮度：跟随系统");
            } else {
                prefs.setBrightness(progress);
                tvBright.setText("屏幕亮度：" + progress + "%");
            }
            if (listener != null) listener.onSettingsChanged();
        }));
        root.addView(sbBright);
        TextView brightHint = makeLabel(activity, "提示：滑到最左侧(0)恢复跟随系统亮度");
        brightHint.setTextColor(0x88FFFFFF);
        root.addView(brightHint);

        createDarkBuilder(activity)
                .setTitle("时钟更多设置")
                .setView(scroll)
                .setPositiveButton("完成", null)
                .setNeutralButton("刷新天气", (dialog, which) -> {
                    if (listener != null) listener.onWeatherRefreshRequested();
                })
                .show();
    }

    private static Button makeSmallBtn(Activity activity, String text, View.OnClickListener l) {
        Button b = new Button(activity);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        b.setOnClickListener(l);
        return b;
    }

    private static TextView makeSectionTitle(Activity activity, String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(0xFF00F5D4);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setPadding(0, dp(activity, 10), 0, dp(activity, 4));
        return tv;
    }

    private static TextView makeLabel(Activity activity, String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return tv;
    }

    private static RadioButton makeRadio(Activity activity, String text) {
        RadioButton rb = new RadioButton(activity);
        rb.setText(text);
        rb.setTextColor(Color.WHITE);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return rb;
    }

    private static CheckBox makeCheckBox(Activity activity, String text, boolean checked) {
        CheckBox cb = new CheckBox(activity);
        cb.setText(text);
        cb.setTextColor(Color.WHITE);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cb.setChecked(checked);
        return cb;
    }

    private static View makePermRow(Activity activity, String title, String status, int statusColor,
                                    String btnText, View.OnClickListener clickListener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(activity, 6), 0, dp(activity, 6));

        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        left.addView(tvTitle);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText(status);
        tvStatus.setTextColor(statusColor);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        left.addView(tvStatus);

        Button btn = new Button(activity);
        btn.setText(btnText);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setOnClickListener(clickListener);

        row.addView(left);
        row.addView(btn);
        return row;
    }
}
