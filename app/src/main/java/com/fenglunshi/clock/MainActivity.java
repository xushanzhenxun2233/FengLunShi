package com.fenglunshi.clock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fenglunshi.clock.service.KeepAliveService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Main full-screen pure-black clock Activity compatible with Android 4.0+ (API 14 - API 34).
 * Features per-digit flowing rainbow LinearGradient shaders, corner schedule countdown,
 * customizable layout, local weather, floating clock, and background keep-alive.
 */
public class MainActivity extends Activity implements SettingsDialog.OnSettingsChangedListener {

    private static final long WEATHER_REFRESH_INTERVAL_MS = 30 * 60 * 1000L;
    private static final long HUD_AUTO_HIDE_MS = 6500L;

    private ClockPrefs prefs;
    private WeatherManager weatherManager;
    private HourlyChimeManager chimeManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View rootContainer;
    private LinearLayout clockContainer;
    private LinearLayout timeRow;
    private TextView tvTime;
    private TextView tvSeconds;
    private TextView tvAmPm;
    private TextView tvDateTop;
    private TextView tvDate;
    private TextView tvWeather;
    private TextView tvCornerWeather;
    private View weatherContainer;
    private LinearLayout countdownContainer;
    private TextView tvCountdownTitle;
    private TextView tvCountdownValue;
    private View hudControls;
    private TextView tvHudHint;
    private TextView btnRotate;
    private TextView btnFloating;

    private boolean hudVisible = false;
    private int lastBurnInMinute = -1;
    private String latestWeatherText = "";

    private final SimpleDateFormat fmt24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat fmt12 = new SimpleDateFormat("hh:mm", Locale.getDefault());
    private final SimpleDateFormat fmt24Stack = new SimpleDateFormat("HH\nmm", Locale.getDefault());
    private final SimpleDateFormat fmt12Stack = new SimpleDateFormat("hh\nmm", Locale.getDefault());
    private final SimpleDateFormat fmtSec = new SimpleDateFormat(":ss", Locale.getDefault());
    private final SimpleDateFormat fmtDate = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);

    private final Runnable clockTickRunnable = () -> {
        updateClockAndColor();
        long delay = (prefs.getColorMode() == ClockPrefs.COLOR_MODE_CUSTOM) ? 500L : 50L;
        handler.postDelayed(this.clockTickRunnable, delay);
    };

    private final Runnable hideHudRunnable = () -> setHudVisible(false);

    private final Runnable weatherAutoRefreshRunnable = () -> {
        refreshWeather();
        handler.postDelayed(this.weatherAutoRefreshRunnable, WEATHER_REFRESH_INTERVAL_MS);
    };

    private static class MainWeatherCallback implements WeatherManager.Callback {
        private final MainActivity activity;

        MainWeatherCallback(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onWeatherUpdated(String displayText) {
            activity.setWeatherDisplayText(displayText);
        }

        @Override
        public void onWeatherError(String message) {
            String cached = activity.prefs.getCachedWeatherText();
            if (!TextUtils.isEmpty(cached)) {
                activity.setWeatherDisplayText(cached);
            } else {
                activity.setWeatherDisplayText(message);
            }
        }
    }

    private final WeatherManager.Callback weatherCallback = new MainWeatherCallback(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindowFlags();

        setContentView(R.layout.activity_main);

        prefs = new ClockPrefs(this);
        weatherManager = new WeatherManager(this, prefs);
        chimeManager = HourlyChimeManager.getInstance(this);

        bindViews();
        applyOrientation();
        applyBrightness();
        applyCustomLayout();
        loadCachedWeather();

        PermissionHelper.requestNotificationPermissionIfNeeded(this);
        KeepAliveService.syncServiceState(this);

        setHudVisible(true);
    }

    @SuppressWarnings("deprecation")
    private void configureWindowFlags() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    private void bindViews() {
        rootContainer = findViewById(R.id.root_container);
        clockContainer = (LinearLayout) findViewById(R.id.clock_container);
        timeRow = (LinearLayout) findViewById(R.id.time_row);
        tvTime = (TextView) findViewById(R.id.tv_time);
        tvSeconds = (TextView) findViewById(R.id.tv_seconds);
        tvAmPm = (TextView) findViewById(R.id.tv_ampm);
        tvDateTop = (TextView) findViewById(R.id.tv_date_top);
        tvDate = (TextView) findViewById(R.id.tv_date);
        tvWeather = (TextView) findViewById(R.id.tv_weather);
        tvCornerWeather = (TextView) findViewById(R.id.tv_corner_weather);
        weatherContainer = findViewById(R.id.weather_container);
        countdownContainer = (LinearLayout) findViewById(R.id.countdown_container);
        tvCountdownTitle = (TextView) findViewById(R.id.tv_countdown_title);
        tvCountdownValue = (TextView) findViewById(R.id.tv_countdown_value);
        hudControls = findViewById(R.id.hud_controls);
        tvHudHint = (TextView) findViewById(R.id.tv_hud_hint);
        btnRotate = (TextView) findViewById(R.id.btn_rotate);
        btnFloating = (TextView) findViewById(R.id.btn_floating);

        rootContainer.setOnClickListener(v -> setHudVisible(!hudVisible));

        View.OnClickListener openWeatherDialog = v ->
                SettingsDialog.showWeatherCityDialog(MainActivity.this, prefs, weatherManager, weatherCallback);
        weatherContainer.setOnClickListener(openWeatherDialog);
        tvCornerWeather.setOnClickListener(openWeatherDialog);

        countdownContainer.setOnClickListener(v ->
                SettingsDialog.showCountdownDialog(MainActivity.this, prefs, MainActivity.this));

        btnRotate.setOnClickListener(v -> {
            toggleLandscapePortrait();
            resetHudAutoHide();
        });

        findViewById(R.id.btn_color).setOnClickListener(v -> {
            SettingsDialog.showColorDialog(MainActivity.this, prefs, MainActivity.this);
            resetHudAutoHide();
        });

        findViewById(R.id.btn_layout).setOnClickListener(v -> {
            SettingsDialog.showLayoutDialog(MainActivity.this, prefs, MainActivity.this);
            resetHudAutoHide();
        });

        findViewById(R.id.btn_countdown).setOnClickListener(v -> {
            SettingsDialog.showCountdownDialog(MainActivity.this, prefs, MainActivity.this);
            resetHudAutoHide();
        });

        findViewById(R.id.btn_chime).setOnClickListener(v -> {
            SettingsDialog.showChimeDialog(MainActivity.this, prefs);
            resetHudAutoHide();
        });

        btnFloating.setOnClickListener(v -> {
            toggleFloatingClock();
            resetHudAutoHide();
        });

        findViewById(R.id.btn_permissions).setOnClickListener(v -> {
            SettingsDialog.showPermissionsDialog(MainActivity.this, prefs, MainActivity.this);
            resetHudAutoHide();
        });

        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            SettingsDialog.showGeneralSettingsDialog(MainActivity.this, prefs, MainActivity.this);
            resetHudAutoHide();
        });

        updateButtonLabels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullscreenMode();
        applyBrightness();
        applyCustomLayout();
        updateButtonLabels();
        adjustTypography();

        handler.removeCallbacks(clockTickRunnable);
        handler.post(clockTickRunnable);

        handler.removeCallbacks(weatherAutoRefreshRunnable);
        long age = System.currentTimeMillis() - prefs.getCachedWeatherTime();
        if (TextUtils.isEmpty(prefs.getCachedWeatherText()) || age > WEATHER_REFRESH_INTERVAL_MS) {
            handler.post(weatherAutoRefreshRunnable);
        } else {
            handler.postDelayed(weatherAutoRefreshRunnable, WEATHER_REFRESH_INTERVAL_MS - age);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTickRunnable);
        handler.removeCallbacks(weatherAutoRefreshRunnable);
        handler.removeCallbacks(hideHudRunnable);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreenMode();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enterFullscreenMode();
        handler.post(() -> {
            applyCustomLayout();
            adjustTypography();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PermissionHelper.REQ_OVERLAY) {
            if (PermissionHelper.hasOverlayPermission(this)) {
                prefs.setFloatingClockEnabled(true);
                KeepAliveService.syncServiceState(this);
                updateButtonLabels();
                Toast.makeText(this, "悬浮窗权限已获取，悬浮时钟已开启", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshWeather();
            }
        }
    }

    private void enterFullscreenMode() {
        try {
            View decor = getWindow().getDecorView();
            if (Build.VERSION.SDK_INT >= 19) {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
            } else {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LOW_PROFILE
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                );
            }
        } catch (Exception ignored) {
        }
    }

    private void applyCustomLayout() {
        // 1. Font style
        Typeface tf = resolveFontTypeface();
        int style = (prefs.getFontStyle() == ClockPrefs.FONT_SANS_LIGHT) ? Typeface.NORMAL : Typeface.BOLD;
        tvTime.setTypeface(tf, style);
        tvSeconds.setTypeface(tf, style);
        tvCountdownValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        // 2. Overall Alignment
        int align = prefs.getLayoutAlign();
        int hGravity;
        if (align == ClockPrefs.ALIGN_LEFT) {
            hGravity = Gravity.LEFT;
        } else if (align == ClockPrefs.ALIGN_RIGHT) {
            hGravity = Gravity.RIGHT;
        } else {
            hGravity = Gravity.CENTER_HORIZONTAL;
        }
        clockContainer.setGravity(Gravity.CENTER_VERTICAL | hGravity);
        timeRow.setGravity(Gravity.BOTTOM | hGravity);
        tvTime.setGravity(hGravity);

        // 3. Date position
        int datePos = prefs.getDatePosition();
        tvDateTop.setVisibility(datePos == ClockPrefs.DATE_ABOVE_TIME ? View.VISIBLE : View.GONE);
        tvDate.setVisibility(datePos == ClockPrefs.DATE_BELOW_TIME ? View.VISIBLE : View.GONE);

        // 4. Weather position
        int wPos = prefs.getWeatherPosition();
        if (wPos == ClockPrefs.WEATHER_CENTER) {
            weatherContainer.setVisibility(View.VISIBLE);
            tvCornerWeather.setVisibility(View.GONE);
        } else if (wPos == ClockPrefs.WEATHER_HIDDEN) {
            weatherContainer.setVisibility(View.GONE);
            tvCornerWeather.setVisibility(View.GONE);
        } else {
            weatherContainer.setVisibility(View.GONE);
            tvCornerWeather.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams wlp = (FrameLayout.LayoutParams) tvCornerWeather.getLayoutParams();
            wlp.gravity = mapCornerGravity(wPos);
            tvCornerWeather.setLayoutParams(wlp);
        }

        // 5. Corner Schedule Countdown position
        int cdCorner = prefs.getCountdownCorner();
        if (cdCorner == ClockPrefs.CORNER_HIDDEN) {
            countdownContainer.setVisibility(View.GONE);
        } else {
            countdownContainer.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams clp = (FrameLayout.LayoutParams) countdownContainer.getLayoutParams();
            clp.gravity = mapCornerGravity(cdCorner);
            countdownContainer.setLayoutParams(clp);
        }

        // 6. Element vertical spacing
        int spaceMode = prefs.getElementSpacing();
        int spaceDp = (spaceMode == ClockPrefs.SPACING_COMPACT) ? 4
                : (spaceMode == ClockPrefs.SPACING_RELAXED ? 22 : 11);
        int spacePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, spaceDp, getResources().getDisplayMetrics());

        LinearLayout.LayoutParams dateTopLp = (LinearLayout.LayoutParams) tvDateTop.getLayoutParams();
        dateTopLp.bottomMargin = spacePx;
        tvDateTop.setLayoutParams(dateTopLp);

        LinearLayout.LayoutParams dateBotLp = (LinearLayout.LayoutParams) tvDate.getLayoutParams();
        dateBotLp.topMargin = spacePx;
        tvDate.setLayoutParams(dateBotLp);

        LinearLayout.LayoutParams weatherLp = (LinearLayout.LayoutParams) weatherContainer.getLayoutParams();
        weatherLp.topMargin = spacePx;
        weatherContainer.setLayoutParams(weatherLp);
    }

    private int mapCornerGravity(int cornerCode) {
        if (cornerCode == ClockPrefs.CORNER_TOP_LEFT) return Gravity.TOP | Gravity.LEFT;
        if (cornerCode == ClockPrefs.CORNER_BOTTOM_LEFT) return Gravity.BOTTOM | Gravity.LEFT;
        if (cornerCode == ClockPrefs.CORNER_BOTTOM_RIGHT) return Gravity.BOTTOM | Gravity.RIGHT;
        return Gravity.TOP | Gravity.RIGHT;
    }

    private Typeface resolveFontTypeface() {
        int style = prefs.getFontStyle();
        if (style == ClockPrefs.FONT_SANS_BOLD) return Typeface.SANS_SERIF;
        if (style == ClockPrefs.FONT_SANS_LIGHT) {
            try {
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            } catch (Exception ignored) {
                return Typeface.SANS_SERIF;
            }
        }
        if (style == ClockPrefs.FONT_SERIF_BOLD) return Typeface.SERIF;
        return Typeface.MONOSPACE;
    }

    private void updateClockAndColor() {
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        boolean use24 = prefs.is24HourFormat();
        boolean showSec = prefs.isShowSeconds();
        boolean stacked = prefs.isStackedTime();

        String timeText;
        if (stacked) {
            timeText = use24 ? fmt24Stack.format(date) : fmt12Stack.format(date);
        } else {
            timeText = use24 ? fmt24.format(date) : fmt12.format(date);
        }
        tvTime.setText(timeText);

        if (showSec) {
            tvSeconds.setVisibility(View.VISIBLE);
            tvSeconds.setText(fmtSec.format(date));
        } else {
            tvSeconds.setVisibility(View.GONE);
        }

        if (use24) {
            tvAmPm.setVisibility(View.GONE);
        } else {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            tvAmPm.setVisibility(View.VISIBLE);
            tvAmPm.setText(cal.get(Calendar.HOUR_OF_DAY) < 12 ? "AM" : "PM");
        }

        String dateStr = fmtDate.format(date);
        tvDate.setText(dateStr);
        tvDateTop.setText(dateStr);

        // Apply per-digit flowing rainbow/neon LinearGradient shaders
        ColorEngine.applyToTextView(tvTime, prefs, now, 0f);
        if (showSec) {
            ColorEngine.applyToTextView(tvSeconds, prefs, now, 0.25f);
        }
        if (tvDate.getVisibility() == View.VISIBLE) {
            ColorEngine.applyToTextView(tvDate, prefs, now, 0.15f);
        }
        if (tvDateTop.getVisibility() == View.VISIBLE) {
            ColorEngine.applyToTextView(tvDateTop, prefs, now, 0.15f);
        }

        int accent = ColorEngine.computeAccentColor(prefs, now);
        tvAmPm.setTextColor(ColorEngine.withAlpha(accent, 195));
        tvWeather.setTextColor(ColorEngine.withAlpha(accent, 190));
        tvCornerWeather.setTextColor(ColorEngine.withAlpha(accent, 210));

        updateCountdown(now, accent);
        applyOledBurnInShift(now);
        if (chimeManager != null) {
            chimeManager.checkAndChimeIfNeeded();
        }
    }

    private void updateCountdown(long now, int accentColor) {
        if (prefs.getCountdownCorner() == ClockPrefs.CORNER_HIDDEN) return;

        String title = prefs.getCountdownTitle();
        tvCountdownTitle.setText(TextUtils.isEmpty(title) ? "日程倒计时" : title);
        tvCountdownTitle.setTextColor(ColorEngine.withAlpha(accentColor, 185));

        long diffMs = prefs.getCountdownTargetMillis() - now;
        String valueText;
        if (diffMs <= 0L) {
            valueText = "已到达";
        } else {
            long totalSec = diffMs / 1000L;
            long days = totalSec / 86400L;
            long hours = (totalSec % 86400L) / 3600L;
            long mins = (totalSec % 3600L) / 60L;
            long secs = totalSec % 60L;
            if (days > 0) {
                valueText = String.format(Locale.CHINA, "%d天 %02d时%02d分", days, hours, mins);
            } else {
                valueText = String.format(Locale.CHINA, "%02d:%02d:%02d", hours, mins, secs);
            }
        }
        tvCountdownValue.setText(valueText);
        ColorEngine.applyToTextView(tvCountdownValue, prefs, now, 0.4f);
    }

    private void applyOledBurnInShift(long now) {
        if (!prefs.isOledBurnInProtect()) {
            clockContainer.setTranslationX(0f);
            clockContainer.setTranslationY(0f);
            return;
        }
        int minute = (int) (now / 60000L);
        if (minute != lastBurnInMinute) {
            lastBurnInMinute = minute;
            double angle = (minute % 12) * (Math.PI / 6.0);
            float dx = (float) (Math.cos(angle) * 10.0);
            float dy = (float) (Math.sin(angle) * 10.0);
            clockContainer.setTranslationX(dx);
            clockContainer.setTranslationY(dy);
        }
    }

    private void adjustTypography() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;
        boolean isLandscape = w > h;
        boolean showSec = prefs.isShowSeconds();
        boolean stacked = prefs.isStackedTime();
        float userScale = prefs.getTimeScalePercent() / 100f;

        float targetWidthRatio = isLandscape ? 0.76f : 0.86f;
        float maxTimeWidth = w * targetWidthRatio;
        float maxTimeHeight = h * (stacked ? (isLandscape ? 0.34f : 0.25f) : (isLandscape ? 0.54f : 0.30f));

        String sample = stacked ? "88" : (showSec ? "88:88:88" : "88:88");
        Paint paint = new Paint();
        paint.setTypeface(Typeface.create(resolveFontTypeface(), Typeface.BOLD));
        paint.setTextSize(100f);
        float measuredAt100 = paint.measureText(sample);

        float effectiveRatio = (!stacked && showSec) ? 0.78f : 1.0f;
        float sizeByWidth = (maxTimeWidth / (measuredAt100 * effectiveRatio)) * 100f;
        float timePx = Math.min(sizeByWidth, maxTimeHeight) * userScale;
        timePx = Math.max(44f, Math.min(timePx, h * 0.68f));

        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, timePx);
        tvSeconds.setTextSize(TypedValue.COMPLEX_UNIT_PX, timePx * 0.38f);
        tvAmPm.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(16f, timePx * 0.16f));

        float datePx = Math.max(20f, Math.min(timePx * 0.23f, h * 0.08f));
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, datePx);
        tvDateTop.setTextSize(TypedValue.COMPLEX_UNIT_PX, datePx);

        float weatherPx = Math.max(16f, Math.min(timePx * 0.16f, h * 0.06f));
        tvWeather.setTextSize(TypedValue.COMPLEX_UNIT_PX, weatherPx);
    }

    private void toggleLandscapePortrait() {
        int currentConfigOri = getResources().getConfiguration().orientation;
        if (currentConfigOri == Configuration.ORIENTATION_LANDSCAPE) {
            prefs.setOrientationMode(ClockPrefs.ORIENTATION_PORTRAIT);
            Toast.makeText(this, "已切换为竖屏", Toast.LENGTH_SHORT).show();
        } else {
            prefs.setOrientationMode(ClockPrefs.ORIENTATION_LANDSCAPE);
            Toast.makeText(this, "已切换为横屏", Toast.LENGTH_SHORT).show();
        }
        applyOrientation();
        updateButtonLabels();
    }

    private void applyOrientation() {
        int mode = prefs.getOrientationMode();
        if (mode == ClockPrefs.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (mode == ClockPrefs.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (mode == ClockPrefs.ORIENTATION_REVERSE_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    private void applyBrightness() {
        int brightness = prefs.getBrightness();
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (brightness < 0) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            lp.screenBrightness = Math.max(0.02f, brightness / 100f);
        }
        getWindow().setAttributes(lp);
    }

    private void toggleFloatingClock() {
        boolean currentlyOn = prefs.isFloatingClockEnabled();
        if (!currentlyOn) {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限以开启桌面悬浮时钟", Toast.LENGTH_LONG).show();
                PermissionHelper.openOverlayPermissionSettings(this);
                return;
            }
            prefs.setFloatingClockEnabled(true);
            KeepAliveService.syncServiceState(this);
            Toast.makeText(this, "悬浮时钟已开启（可拖动位置，点击返回全屏）", Toast.LENGTH_SHORT).show();
        } else {
            prefs.setFloatingClockEnabled(false);
            KeepAliveService.syncServiceState(this);
            Toast.makeText(this, "悬浮时钟已关闭", Toast.LENGTH_SHORT).show();
        }
        updateButtonLabels();
    }

    private void updateButtonLabels() {
        if (btnFloating != null) {
            btnFloating.setText(prefs.isFloatingClockEnabled() ? "悬浮时钟:开" : "悬浮时钟:关");
        }
        if (btnRotate != null) {
            int ori = prefs.getOrientationMode();
            if (ori == ClockPrefs.ORIENTATION_LANDSCAPE) btnRotate.setText("横屏");
            else if (ori == ClockPrefs.ORIENTATION_PORTRAIT) btnRotate.setText("竖屏");
            else if (ori == ClockPrefs.ORIENTATION_REVERSE_LANDSCAPE) btnRotate.setText("反横屏");
            else btnRotate.setText("横竖屏");
        }
    }

    private void setHudVisible(boolean visible) {
        hudVisible = visible;
        int vis = visible ? View.VISIBLE : View.GONE;
        hudControls.setVisibility(vis);
        tvHudHint.setVisibility(vis);
        if (visible) {
            resetHudAutoHide();
        } else {
            handler.removeCallbacks(hideHudRunnable);
            enterFullscreenMode();
        }
    }

    private void resetHudAutoHide() {
        handler.removeCallbacks(hideHudRunnable);
        handler.postDelayed(hideHudRunnable, HUD_AUTO_HIDE_MS);
    }

    private void loadCachedWeather() {
        String cached = prefs.getCachedWeatherText();
        if (!TextUtils.isEmpty(cached)) {
            setWeatherDisplayText(cached);
        } else {
            setWeatherDisplayText(getString(R.string.weather_loading));
        }
    }

    private void setWeatherDisplayText(String text) {
        latestWeatherText = text;
        if (tvWeather != null) tvWeather.setText(text);
        if (tvCornerWeather != null) tvCornerWeather.setText(text);
    }

    private void refreshWeather() {
        if (weatherManager != null) {
            weatherManager.fetchWeather(weatherCallback);
        }
    }

    @Override
    public void onSettingsChanged() {
        applyBrightness();
        applyCustomLayout();
        adjustTypography();
        updateButtonLabels();
        updateClockAndColor();
    }

    @Override
    public void onWeatherRefreshRequested() {
        setWeatherDisplayText(getString(R.string.weather_loading));
        refreshWeather();
    }

    @Override
    public void onOrientationChanged() {
        applyOrientation();
        updateButtonLabels();
        handler.post(() -> {
            applyCustomLayout();
            adjustTypography();
        });
    }
}
