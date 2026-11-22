package com.fenglunshi.clock.service;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import com.fenglunshi.clock.ClockPrefs;

/**
 * AccessibilityService that helps preserve clock process survival on older & modern Android ROMs.
 * Does NOT read or collect any window content.
 */
public class ClockAccessibilityService extends AccessibilityService {

    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = true;
        // Ensure keep-alive service is synced when accessibility service connects
        ClockPrefs prefs = new ClockPrefs(this);
        if (prefs.isKeepAliveEnabled() || prefs.isFloatingClockEnabled()) {
            KeepAliveService.syncServiceState(this);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally minimal: no window inspection or data collection
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
