package com.xiao.clock;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Renders vivid multi-color flowing LinearGradient shaders across every clock digit
 * (Android API 1 - API 34 compatible), plus circadian dual-tone gradients and custom colors.
 */
public class ColorEngine {

    public static final int[] PRESET_COLORS = new int[]{
            0xFFFFFFFF, // 纯白
            0xFF00F5D4, // 霓虹青
            0xFF38BDF8, // 赛博蓝
            0xFF4ADE80, // 翡翠绿
            0xFFFBBF24, // 琥珀金
            0xFFFB923C, // 暖日橙
            0xFFFF3B6B, // 霓虹红
            0xFFC084FC, // 极光紫
            0xFFF472B6  // 樱花粉
    };

    public static final String[] PALETTE_NAMES = new String[]{
            "流光彩虹（经典七彩）",
            "北极光（青蓝紫粉）",
            "日落霓虹（红橙金粉）",
            "赛博朋克（青蓝玫红）",
            "自定义双色渐变"
    };

    private static final int[] RAINBOW_STOPS = new int[]{
            0xFFFF3B7F, // 玫红
            0xFFFF9E00, // 亮橙
            0xFFFFEA00, // 明黄
            0xFF00F5A0, // 荧光绿
            0xFF00D9F5, // 冰蓝
            0xFF8B5CF6, // 霓虹紫
            0xFFFF3B7F  // 首尾无缝衔接
    };

    private static final int[] AURORA_STOPS = new int[]{
            0xFF00F5D4,
            0xFF38BDF8,
            0xFF818CF8,
            0xFFC084FC,
            0xFFF472B6,
            0xFF00F5D4
    };

    private static final int[] SUNSET_STOPS = new int[]{
            0xFFFF2E63,
            0xFFFF6B35,
            0xFFFFD166,
            0xFFFF8FAB,
            0xFFC084FC,
            0xFFFF2E63
    };

    private static final int[] CYBER_STOPS = new int[]{
            0xFF00F5D4,
            0xFF00BBF9,
            0xFF9B5DE5,
            0xFFF15BB5,
            0xFF00F5D4
    };

    private static final Matrix SHADER_MATRIX = new Matrix();

    /**
     * Applies a multi-color flowing LinearGradient shader directly to the TextView paint
     * so every single digit displays a smooth rainbow/neon gradient across its strokes.
     */
    public static void applyToTextView(TextView tv, ClockPrefs prefs, long nowMillis, float phaseOffset) {
        if (tv == null) return;
        int mode = prefs.getColorMode();

        if (mode == ClockPrefs.COLOR_MODE_CUSTOM) {
            tv.getPaint().setShader(null);
            tv.setTextColor(prefs.getCustomColor());
            tv.invalidate();
            return;
        }

        int width = tv.getWidth();
        int height = tv.getHeight();
        if (width <= 0) {
            // View not laid out yet; fallback to primary color until layout completes
            tv.getPaint().setShader(null);
            tv.setTextColor(computeAccentColor(prefs, nowMillis));
            return;
        }

        int[] colors = getActiveGradientColors(prefs, nowMillis);
        // Span width slightly narrower than full text width so each digit shows rich color transitions
        float spanWidth = Math.max(width * 0.72f, 180f);
        float spanHeight = Math.max(height * 0.35f, 40f);

        LinearGradient shader = new LinearGradient(
                0f, 0f, spanWidth, spanHeight,
                colors,
                null,
                Shader.TileMode.REPEAT
        );

        long cycleMs = getCycleDurationMs(prefs.getGradientSpeed());
        float progress = ((nowMillis % cycleMs) / (float) cycleMs) + phaseOffset;
        float translateX = (progress % 1.0f) * spanWidth;

        SHADER_MATRIX.reset();
        SHADER_MATRIX.setTranslate(translateX, 0f);
        shader.setLocalMatrix(SHADER_MATRIX);

        tv.getPaint().setShader(shader);
        // Set base textColor to white so shader renders at full brightness
        tv.setTextColor(0xFFFFFFFF);
        tv.invalidate();
    }

    /**
     * Returns the current multi-stop color array for the selected gradient mode/palette.
     */
    public static int[] getActiveGradientColors(ClockPrefs prefs, long nowMillis) {
        int mode = prefs.getColorMode();
        if (mode == ClockPrefs.COLOR_MODE_CIRCADIAN) {
            int c1 = computeCircadianColor(nowMillis);
            int c2 = computeCircadianColor(nowMillis + 3 * 3600 * 1000L);
            return new int[]{c1, c2, c1};
        }
        int palette = prefs.getGradientPalette();
        switch (palette) {
            case ClockPrefs.PALETTE_AURORA:
                return AURORA_STOPS;
            case ClockPrefs.PALETTE_SUNSET:
                return SUNSET_STOPS;
            case ClockPrefs.PALETTE_CYBER:
                return CYBER_STOPS;
            case ClockPrefs.PALETTE_CUSTOM_DUAL:
                int c1 = prefs.getCustomColor();
                int c2 = prefs.getCustomColor2();
                return new int[]{c1, c2, c1};
            case ClockPrefs.PALETTE_RAINBOW:
            default:
                return RAINBOW_STOPS;
        }
    }

    /**
     * Computes a harmonized single accent color (for secondary labels like weather/countdown).
     */
    public static int computeAccentColor(ClockPrefs prefs, long nowMillis) {
        int mode = prefs.getColorMode();
        if (mode == ClockPrefs.COLOR_MODE_CUSTOM) {
            return prefs.getCustomColor();
        }
        if (mode == ClockPrefs.COLOR_MODE_CIRCADIAN) {
            return computeCircadianColor(nowMillis);
        }
        int[] stops = getActiveGradientColors(prefs, nowMillis);
        long cycleMs = getCycleDurationMs(prefs.getGradientSpeed());
        float progress = (nowMillis % cycleMs) / (float) cycleMs;
        float scaled = progress * (stops.length - 1);
        int idx = Math.min((int) scaled, stops.length - 2);
        float frac = scaled - idx;
        return lerpColor(stops[idx], stops[idx + 1], frac);
    }

    private static long getCycleDurationMs(int speed) {
        if (speed == ClockPrefs.SPEED_SLOW) return 18000L;
        if (speed == ClockPrefs.SPEED_FAST) return 4000L;
        return 9000L;
    }

    private static int computeCircadianColor(long nowMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        float hour = cal.get(Calendar.HOUR_OF_DAY)
                + cal.get(Calendar.MINUTE) / 60f
                + cal.get(Calendar.SECOND) / 3600f;

        float[] hours = new float[]{0f, 5f, 7.5f, 12f, 16.5f, 19f, 21.5f, 24f};
        int[] colors = new int[]{
                0xFF38BDF8,
                0xFF818CF8,
                0xFFFBBF24,
                0xFFE0F2FE,
                0xFF4ADE80,
                0xFFFB923C,
                0xFFC084FC,
                0xFF38BDF8
        };

        for (int i = 0; i < hours.length - 1; i++) {
            if (hour >= hours[i] && hour <= hours[i + 1]) {
                float t = (hour - hours[i]) / (hours[i + 1] - hours[i]);
                return lerpColor(colors[i], colors[i + 1], t);
            }
        }
        return colors[0];
    }

    public static int lerpColor(int c1, int c2, float t) {
        int r = (int) (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t);
        int g = (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t);
        int b = (int) (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t);
        return Color.rgb(r, g, b);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
