package com.xiao.clock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.Calendar;

public class ClockPrefs {

    private static final String PREF_NAME = "retro_clock_prefs";

    // Color modes
    public static final int COLOR_MODE_CUSTOM = 0;
    public static final int COLOR_MODE_FLOW_GRADIENT = 1; // Multi-color flowing shader across every digit
    public static final int COLOR_MODE_CIRCADIAN = 2;     // 24h circadian dual-tone gradient

    // Flowing gradient palettes
    public static final int PALETTE_RAINBOW = 0;
    public static final int PALETTE_AURORA = 1;
    public static final int PALETTE_SUNSET = 2;
    public static final int PALETTE_CYBER = 3;
    public static final int PALETTE_CUSTOM_DUAL = 4;

    // Orientation modes
    public static final int ORIENTATION_AUTO = 0;
    public static final int ORIENTATION_LANDSCAPE = 1;
    public static final int ORIENTATION_PORTRAIT = 2;
    public static final int ORIENTATION_REVERSE_LANDSCAPE = 3;

    // Gradient speed
    public static final int SPEED_SLOW = 0;
    public static final int SPEED_NORMAL = 1;
    public static final int SPEED_FAST = 2;

    // Corner positions
    public static final int CORNER_HIDDEN = 0;
    public static final int CORNER_TOP_LEFT = 1;
    public static final int CORNER_TOP_RIGHT = 2;
    public static final int CORNER_BOTTOM_LEFT = 3;
    public static final int CORNER_BOTTOM_RIGHT = 4;

    // Layout alignment
    public static final int ALIGN_CENTER = 0;
    public static final int ALIGN_LEFT = 1;
    public static final int ALIGN_RIGHT = 2;

    // Date position
    public static final int DATE_BELOW_TIME = 0;
    public static final int DATE_ABOVE_TIME = 1;
    public static final int DATE_HIDDEN = 2;

    // Weather position
    public static final int WEATHER_CENTER = 0;
    public static final int WEATHER_TOP_LEFT = 1;
    public static final int WEATHER_TOP_RIGHT = 2;
    public static final int WEATHER_BOTTOM_LEFT = 3;
    public static final int WEATHER_BOTTOM_RIGHT = 4;
    public static final int WEATHER_HIDDEN = 5;

    // Spacing
    public static final int SPACING_COMPACT = 0;
    public static final int SPACING_NORMAL = 1;
    public static final int SPACING_RELAXED = 2;

    // Font styles
    public static final int FONT_MONO_BOLD = 0;
    public static final int FONT_SANS_BOLD = 1;
    public static final int FONT_SANS_LIGHT = 2;
    public static final int FONT_SERIF_BOLD = 3;

    // Hourly chime modes
    public static final int CHIME_MODE_TTS_WITH_BELL = 0; // 先响内置清脆钟声 + 系统TTS语音报时（无TTS自动仅响钟声）
    public static final int CHIME_MODE_TTS_ONLY = 1;      // 仅系统TTS语音报时（无TTS回退内置钟声）
    public static final int CHIME_MODE_BELL_ONLY = 2;     // 仅内置清脆钟声（离线老机100%可用）

    private final SharedPreferences prefs;

    public ClockPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Color & Gradient ---
    public int getColorMode() {
        return prefs.getInt("color_mode", COLOR_MODE_FLOW_GRADIENT);
    }

    public void setColorMode(int mode) {
        prefs.edit().putInt("color_mode", mode).apply();
    }

    public int getGradientPalette() {
        return prefs.getInt("gradient_palette", PALETTE_RAINBOW);
    }

    public void setGradientPalette(int palette) {
        prefs.edit().putInt("gradient_palette", palette).apply();
    }

    public int getCustomColor() {
        return prefs.getInt("custom_color", Color.rgb(0, 245, 212));
    }

    public void setCustomColor(int color) {
        prefs.edit().putInt("custom_color", color).apply();
    }

    public int getCustomColor2() {
        return prefs.getInt("custom_color_2", Color.rgb(244, 114, 182));
    }

    public void setCustomColor2(int color) {
        prefs.edit().putInt("custom_color_2", color).apply();
    }

    public int getGradientSpeed() {
        return prefs.getInt("gradient_speed", SPEED_NORMAL);
    }

    public void setGradientSpeed(int speed) {
        prefs.edit().putInt("gradient_speed", speed).apply();
    }

    // --- Orientation & Display ---
    public int getOrientationMode() {
        return prefs.getInt("orientation_mode", ORIENTATION_AUTO);
    }

    public void setOrientationMode(int mode) {
        prefs.edit().putInt("orientation_mode", mode).apply();
    }

    public boolean is24HourFormat() {
        return prefs.getBoolean("use_24h", true);
    }

    public void set24HourFormat(boolean use24h) {
        prefs.edit().putBoolean("use_24h", use24h).apply();
    }

    public boolean isShowSeconds() {
        return prefs.getBoolean("show_seconds", true);
    }

    public void setShowSeconds(boolean show) {
        prefs.edit().putBoolean("show_seconds", show).apply();
    }

    public boolean isOledBurnInProtect() {
        return prefs.getBoolean("oled_protect", true);
    }

    public void setOledBurnInProtect(boolean enabled) {
        prefs.edit().putBoolean("oled_protect", enabled).apply();
    }

    public int getBrightness() {
        return prefs.getInt("brightness", -1);
    }

    public void setBrightness(int brightness) {
        prefs.edit().putInt("brightness", brightness).apply();
    }

    // --- Custom Layout ---
    public int getLayoutAlign() {
        return prefs.getInt("layout_align", ALIGN_CENTER);
    }

    public void setLayoutAlign(int align) {
        prefs.edit().putInt("layout_align", align).apply();
    }

    public boolean isStackedTime() {
        return prefs.getBoolean("layout_stacked_time", false);
    }

    public void setStackedTime(boolean stacked) {
        prefs.edit().putBoolean("layout_stacked_time", stacked).apply();
    }

    public int getTimeScalePercent() {
        return prefs.getInt("layout_time_scale", 100);
    }

    public void setTimeScalePercent(int percent) {
        prefs.edit().putInt("layout_time_scale", Math.max(60, Math.min(135, percent))).apply();
    }

    public int getDatePosition() {
        return prefs.getInt("layout_date_pos", DATE_BELOW_TIME);
    }

    public void setDatePosition(int pos) {
        prefs.edit().putInt("layout_date_pos", pos).apply();
    }

    public int getWeatherPosition() {
        return prefs.getInt("layout_weather_pos", WEATHER_CENTER);
    }

    public void setWeatherPosition(int pos) {
        prefs.edit().putInt("layout_weather_pos", pos).apply();
    }

    public int getElementSpacing() {
        return prefs.getInt("layout_spacing", SPACING_NORMAL);
    }

    public void setElementSpacing(int spacing) {
        prefs.edit().putInt("layout_spacing", spacing).apply();
    }

    public int getFontStyle() {
        return prefs.getInt("layout_font_style", FONT_MONO_BOLD);
    }

    public void setFontStyle(int style) {
        prefs.edit().putInt("layout_font_style", style).apply();
    }

    // --- Schedule Countdown ---
    public int getCountdownCorner() {
        return prefs.getInt("countdown_corner", CORNER_TOP_RIGHT);
    }

    public void setCountdownCorner(int corner) {
        prefs.edit().putInt("countdown_corner", corner).apply();
    }

    public String getCountdownTitle() {
        return prefs.getString("countdown_title", "新年倒计时");
    }

    public void setCountdownTitle(String title) {
        prefs.edit().putString("countdown_title", title == null ? "" : title.trim()).apply();
    }

    public long getCountdownTargetMillis() {
        long saved = prefs.getLong("countdown_target_ms", 0L);
        if (saved <= 0L) {
            // Default to next Jan 1 00:00:00
            Calendar cal = Calendar.getInstance();
            cal.set(cal.get(Calendar.YEAR) + 1, Calendar.JANUARY, 1, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }
        return saved;
    }

    public void setCountdownTargetMillis(long millis) {
        prefs.edit().putLong("countdown_target_ms", millis).apply();
    }

    // --- Keep-Alive & Services ---
    public boolean isKeepAliveEnabled() {
        return prefs.getBoolean("keep_alive", true);
    }

    public void setKeepAliveEnabled(boolean enabled) {
        prefs.edit().putBoolean("keep_alive", enabled).apply();
    }

    public boolean isBootAutoStart() {
        return prefs.getBoolean("boot_auto_start", true);
    }

    public void setBootAutoStart(boolean enabled) {
        prefs.edit().putBoolean("boot_auto_start", enabled).apply();
    }

    public boolean isAutoOpenOnCharge() {
        return prefs.getBoolean("auto_open_charge", false);
    }

    public void setAutoOpenOnCharge(boolean enabled) {
        prefs.edit().putBoolean("auto_open_charge", enabled).apply();
    }

    public boolean isFloatingClockEnabled() {
        return prefs.getBoolean("floating_clock", false);
    }

    public void setFloatingClockEnabled(boolean enabled) {
        prefs.edit().putBoolean("floating_clock", enabled).apply();
    }

    // --- Weather ---
    public boolean isAutoWeatherLocation() {
        return prefs.getBoolean("weather_auto_loc", true);
    }

    public void setAutoWeatherLocation(boolean auto) {
        prefs.edit().putBoolean("weather_auto_loc", auto).apply();
    }

    public String getWeatherCity() {
        return prefs.getString("weather_city", "");
    }

    public void setWeatherCity(String city) {
        prefs.edit().putString("weather_city", city == null ? "" : city).apply();
    }

    public double getWeatherLat() {
        return Double.longBitsToDouble(prefs.getLong("weather_lat", Double.doubleToRawLongBits(39.9042)));
    }

    public double getWeatherLon() {
        return Double.longBitsToDouble(prefs.getLong("weather_lon", Double.doubleToRawLongBits(116.4074)));
    }

    public void setWeatherCoords(double lat, double lon) {
        prefs.edit()
                .putLong("weather_lat", Double.doubleToRawLongBits(lat))
                .putLong("weather_lon", Double.doubleToRawLongBits(lon))
                .apply();
    }

    public String getCachedWeatherText() {
        return prefs.getString("weather_cache_text", "");
    }

    public void setCachedWeatherText(String text) {
        prefs.edit()
                .putString("weather_cache_text", text == null ? "" : text)
                .putLong("weather_cache_time", System.currentTimeMillis())
                .apply();
    }

    public long getCachedWeatherTime() {
        return prefs.getLong("weather_cache_time", 0L);
    }

    // --- Hourly Chime (整点报时) ---
    public boolean isHourlyChimeEnabled() {
        return prefs.getBoolean("hourly_chime_enabled", true);
    }

    public void setHourlyChimeEnabled(boolean enabled) {
        prefs.edit().putBoolean("hourly_chime_enabled", enabled).apply();
    }

    public boolean isHalfHourChimeEnabled() {
        return prefs.getBoolean("half_hour_chime_enabled", false);
    }

    public void setHalfHourChimeEnabled(boolean enabled) {
        prefs.edit().putBoolean("half_hour_chime_enabled", enabled).apply();
    }

    public int getChimeMode() {
        return prefs.getInt("chime_mode", CHIME_MODE_TTS_WITH_BELL);
    }

    public void setChimeMode(int mode) {
        prefs.edit().putInt("chime_mode", mode).apply();
    }

    public boolean isChimeQuietHoursEnabled() {
        return prefs.getBoolean("chime_quiet_enabled", true);
    }

    public void setChimeQuietHoursEnabled(boolean enabled) {
        prefs.edit().putBoolean("chime_quiet_enabled", enabled).apply();
    }

    public int getChimeQuietStartHour() {
        return prefs.getInt("chime_quiet_start", 23);
    }

    public void setChimeQuietStartHour(int hour) {
        prefs.edit().putInt("chime_quiet_start", Math.max(0, Math.min(23, hour))).apply();
    }

    public int getChimeQuietEndHour() {
        return prefs.getInt("chime_quiet_end", 7);
    }

    public void setChimeQuietEndHour(int hour) {
        prefs.edit().putInt("chime_quiet_end", Math.max(0, Math.min(23, hour))).apply();
    }

    public String getLastChimedKey() {
        return prefs.getString("last_chimed_key", "");
    }

    public void setLastChimedKey(String key) {
        prefs.edit().putString("last_chimed_key", key == null ? "" : key).apply();
    }
}
