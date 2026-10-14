package com.star.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

public class AccessibilityWatchdog {
    private static final String TAG = "StarWatchdog";
    private static final long CHECK_INTERVAL = 15000L; // 15s

    private final Context ctx;
    private final Handler handler;
    private boolean running = false;

    public AccessibilityWatchdog(Context c) {
        ctx = c.getApplicationContext();
        handler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        if (running) return;
        running = true;
        handler.postDelayed(tick, CHECK_INTERVAL);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try { check(); } catch (Exception e) { Log.w(TAG, "check failed", e); }
            if (running) handler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    private void check() {
        String comp = ctx.getPackageName() + "/" + StarAccessibilityService.class.getName();

        // Read current state
        String current = Settings.Secure.getString(
            ctx.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        int enabledFlag = Settings.Secure.getInt(
            ctx.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_ENABLED, 0);

        boolean present = !TextUtils.isEmpty(current) && current.contains(comp);
        if (present && enabledFlag == 1) return;  // all good

        // Try to re-enable
        try {
            String updated = present ? current : (TextUtils.isEmpty(current) ? comp : current + ":" + comp);
            Settings.Secure.putString(
                ctx.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated);
            Settings.Secure.putInt(
                ctx.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i(TAG, "Re-enabled accessibility service");
        } catch (SecurityException se) {
            // No WRITE_SECURE_SETTINGS yet — fall back to restarting service
            Log.w(TAG, "No WRITE_SECURE_SETTINGS, cannot auto-fix");
        }
    }

    /** Returns true if Star has the privileged permission it needs. */
    public static boolean hasWriteSecureSettings(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
