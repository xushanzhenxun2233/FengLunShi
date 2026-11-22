package com.fenglunshi.clock.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import com.fenglunshi.clock.ClockPrefs;
import com.fenglunshi.clock.FloatingClockManager;
import com.fenglunshi.clock.HourlyChimeManager;
import com.fenglunshi.clock.MainActivity;
import com.fenglunshi.clock.R;

/**
 * Foreground keep-alive service (API 14 - API 34).
 * Manages persistent notification, floating clock overlay, charger auto-launch, and restart heartbeat.
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "retro_clock_keep_alive";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_SYNC = "com.fenglunshi.clock.ACTION_SYNC_SERVICE";

    private ClockPrefs prefs;
    private FloatingClockManager floatingClockManager;
    private BroadcastReceiver systemEventReceiver;
    private final Handler chimeHandler = new Handler(Looper.getMainLooper());
    private final Runnable chimeCheckRunnable = () -> {
        HourlyChimeManager.getInstance(KeepAliveService.this).checkAndChimeIfNeeded();
        chimeHandler.postDelayed(this.chimeCheckRunnable, 10000L);
    };

    public static void syncServiceState(Context context) {
        ClockPrefs prefs = new ClockPrefs(context);
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_SYNC);
        if (prefs.isKeepAliveEnabled() || prefs.isFloatingClockEnabled()) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            } catch (Exception ignored) {
            }
        } else {
            try {
                context.stopService(intent);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new ClockPrefs(this);
        floatingClockManager = new FloatingClockManager(this);
        startAsForeground();
        registerSystemReceiver();
        HourlyChimeManager.getInstance(this).initTtsIfNeeded();
        chimeHandler.removeCallbacks(chimeCheckRunnable);
        chimeHandler.post(chimeCheckRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (prefs == null) {
            prefs = new ClockPrefs(this);
        }
        startAsForeground();

        if (prefs.isFloatingClockEnabled()) {
            floatingClockManager.show();
        } else {
            floatingClockManager.hide();
        }

        if (!prefs.isKeepAliveEnabled() && !prefs.isFloatingClockEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (prefs != null && (prefs.isKeepAliveEnabled() || prefs.isFloatingClockEnabled())) {
            scheduleRestart();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        chimeHandler.removeCallbacks(chimeCheckRunnable);
        if (floatingClockManager != null) {
            floatingClockManager.hide();
        }
        unregisterSystemReceiver();
        if (prefs != null && (prefs.isKeepAliveEnabled() || prefs.isFloatingClockEnabled())) {
            scheduleRestart();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAsForeground() {
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.notif_channel_desc));
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(R.drawable.ic_stat_clock)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= 16) {
            builder.setPriority(Notification.PRIORITY_LOW);
            return builder.build();
        } else {
            return builder.getNotification();
        }
    }

    private static class SystemEventReceiver extends BroadcastReceiver {
        private final ClockPrefs prefs;

        SystemEventReceiver(ClockPrefs prefs) {
            this.prefs = prefs;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                if (prefs != null && prefs.isAutoOpenOnCharge()) {
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

    private void registerSystemReceiver() {
        if (systemEventReceiver != null) return;
        systemEventReceiver = new SystemEventReceiver(prefs);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(systemEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(systemEventReceiver, filter);
            }
        } catch (Exception ignored) {
        }
    }

    private void unregisterSystemReceiver() {
        if (systemEventReceiver != null) {
            try {
                unregisterReceiver(systemEventReceiver);
            } catch (Exception ignored) {
            }
            systemEventReceiver = null;
        }
    }

    private void scheduleRestart() {
        try {
            Intent restartIntent = new Intent(getApplicationContext(), KeepAliveService.class);
            restartIntent.setAction(ACTION_SYNC);
            int piFlags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= 23) {
                piFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getService(getApplicationContext(), 2001, restartIntent, piFlags);
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 3000, pi);
            }
        } catch (Exception ignored) {
        }
    }
}
