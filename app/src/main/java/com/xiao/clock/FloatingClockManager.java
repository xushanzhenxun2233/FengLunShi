package com.xiao.clock;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Manages the draggable pure-black floating mini-clock overlay (API 14 - API 34).
 */
public class FloatingClockManager {

    private final Context appContext;
    private final ClockPrefs prefs;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View floatingView;
    private TextView tvTime;
    private TextView tvSub;
    private WindowManager.LayoutParams layoutParams;
    private boolean isShowing = false;

    private final SimpleDateFormat fmt24Sec = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat fmt24NoSec = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat fmt12Sec = new SimpleDateFormat("hh:mm:ss", Locale.getDefault());
    private final SimpleDateFormat fmt12NoSec = new SimpleDateFormat("hh:mm", Locale.getDefault());
    private final SimpleDateFormat fmtSub = new SimpleDateFormat("MM/dd EEE", Locale.CHINA);

    private final Runnable tickRunnable = () -> {
        if (!isShowing || tvTime == null) return;
        updateDisplay();
        handler.postDelayed(this.tickRunnable, 250);
    };

    public FloatingClockManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = new ClockPrefs(appContext);
        this.windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShowing() {
        return isShowing;
    }

    public boolean show() {
        if (isShowing) return true;
        if (!PermissionHelper.hasOverlayPermission(appContext)) {
            return false;
        }
        try {
            LayoutInflater inflater = LayoutInflater.from(appContext);
            floatingView = inflater.inflate(R.layout.view_floating_clock, null);
            tvTime = (TextView) floatingView.findViewById(R.id.tv_float_time);
            tvSub = (TextView) floatingView.findViewById(R.id.tv_float_sub);
            tvTime.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

            int windowType;
            if (Build.VERSION.SDK_INT >= 26) {
                windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                windowType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            layoutParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.x = 40;
            layoutParams.y = 120;

            setupTouchDrag();

            try {
                windowManager.addView(floatingView, layoutParams);
            } catch (Exception e) {
                // Fallback for certain Android 4.x / 5.x ROMs
                if (Build.VERSION.SDK_INT < 26) {
                    layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                    windowManager.addView(floatingView, layoutParams);
                } else {
                    throw e;
                }
            }

            isShowing = true;
            updateDisplay();
            handler.removeCallbacks(tickRunnable);
            handler.post(tickRunnable);
            return true;
        } catch (Exception e) {
            floatingView = null;
            isShowing = false;
            return false;
        }
    }

    public void hide() {
        handler.removeCallbacks(tickRunnable);
        if (isShowing && floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {
            }
        }
        floatingView = null;
        tvTime = null;
        tvSub = null;
        isShowing = false;
    }

    private void updateDisplay() {
        long now = System.currentTimeMillis();
        Date d = new Date(now);
        boolean use24 = prefs.is24HourFormat();
        boolean showSec = prefs.isShowSeconds();

        String timeStr;
        if (use24) {
            timeStr = showSec ? fmt24Sec.format(d) : fmt24NoSec.format(d);
        } else {
            timeStr = showSec ? fmt12Sec.format(d) : fmt12NoSec.format(d);
        }
        tvTime.setText(timeStr);
        tvSub.setText(fmtSub.format(d));

        ColorEngine.applyToTextView(tvTime, prefs, now, 0f);
        int accentColor = ColorEngine.computeAccentColor(prefs, now);
        tvSub.setTextColor(ColorEngine.withAlpha(accentColor, 195));
    }

    private void setupTouchDrag() {
        floatingView.setOnTouchListener(new DragTouchListener(this));
    }

    private static class DragTouchListener implements View.OnTouchListener {
        private final FloatingClockManager owner;
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;
        private long touchDownTime;
        private boolean moved;

        DragTouchListener(FloatingClockManager owner) {
            this.owner = owner;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (owner.layoutParams == null || owner.windowManager == null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = owner.layoutParams.x;
                    initialY = owner.layoutParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    touchDownTime = System.currentTimeMillis();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        moved = true;
                    }
                    owner.layoutParams.x = initialX + (int) dx;
                    owner.layoutParams.y = initialY + (int) dy;
                    try {
                        owner.windowManager.updateViewLayout(owner.floatingView, owner.layoutParams);
                    } catch (Exception ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && (System.currentTimeMillis() - touchDownTime) < 350) {
                        Intent intent = new Intent(owner.appContext, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        try {
                            owner.appContext.startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;
            }
            return false;
        }
    }
}
