package com.xiao.clock;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.xiao.clock.service.ClockAccessibilityService;

/**
 * Compatibility helper for permissions across Android 4.0 (API 14) to Android 14 (API 34).
 */
public class PermissionHelper {

    public static final int REQ_LOCATION = 1001;
    public static final int REQ_NOTIFICATIONS = 1002;
    public static final int REQ_OVERLAY = 1003;

    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            return Settings.canDrawOverlays(context);
        }
        // On Android 4.0 - 5.1 (API 14-22), SYSTEM_ALERT_WINDOW is granted at install time
        return true;
    }

    public static void openOverlayPermissionSettings(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQ_OVERLAY);
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetailsSettings(activity);
    }

    public static boolean isAccessibilityEnabled(Context context) {
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (accessibilityEnabled != 1) {
                return false;
            }
            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(enabledServices)) {
                return false;
            }
            ComponentName expected = new ComponentName(context, ClockAccessibilityService.class);
            String fullName = expected.flattenToString();
            String shortName = expected.flattenToShortString();
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabledServices);
            while (splitter.hasNext()) {
                String service = splitter.next();
                if (service.equalsIgnoreCase(fullName) || service.equalsIgnoreCase(shortName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void openAccessibilitySettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            Toast.makeText(activity, "请在列表中找到「魈时钟保活辅助服务」并开启", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "无法打开无障碍设置，请手动前往系统设置开启", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean hasLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void requestLocationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQ_LOCATION);
        }
    }

    public static void requestNotificationPermissionIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                        REQ_NOTIFICATIONS);
            }
        }
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    return pm.isIgnoringBatteryOptimizations(context.getPackageName());
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    public static void openBatteryOptimizationSettings(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                activity.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(activity, "当前系统版本无需额外电池优化设置", Toast.LENGTH_SHORT).show();
    }

    public static void openAppDetailsSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    public static void setViewBackground(View view, Drawable drawable) {
        if (Build.VERSION.SDK_INT >= 16) {
            view.setBackground(drawable);
        } else {
            view.setBackgroundDrawable(drawable);
        }
    }
}
