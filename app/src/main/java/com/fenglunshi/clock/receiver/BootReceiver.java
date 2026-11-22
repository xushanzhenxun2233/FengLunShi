package com.fenglunshi.clock.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.fenglunshi.clock.ClockPrefs;
import com.fenglunshi.clock.MainActivity;
import com.fenglunshi.clock.service.KeepAliveService;

/**
 * Handles boot auto-start and app update restart on Android 4.0+ (API 14+).
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            ClockPrefs prefs = new ClockPrefs(context);
            if (prefs.isKeepAliveEnabled() || prefs.isFloatingClockEnabled()) {
                KeepAliveService.syncServiceState(context);
            }

            if (prefs.isBootAutoStart()) {
                Intent launch = new Intent(context, MainActivity.class);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try {
                    context.startActivity(launch);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
