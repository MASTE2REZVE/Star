package com.star.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1A1A1A;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int GREEN = 0xFF7EE787;
    private static final int ORANGE = 0xFFF0883E;
    private static final int RED = 0xFFF85149;
    private static final int BLUE = 0xFF7CB3FF;

    private TextView shizuku, access;
    private TextView fixBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(30));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("System Info");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Runtime status of Star's capabilities");
        sub.setTextSize(12);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(4), 0, dp(24));
        root.addView(sub);

        root.addView(infoRow("Device", android.os.Build.MODEL));
        root.addView(infoRow("Android", android.os.Build.VERSION.RELEASE));
        root.addView(infoRow("App version", "v3"));

        shizuku = new TextView(this);
        access = new TextView(this);
        root.addView(infoRowDynamic("Shizuku", shizuku));
        root.addView(infoRowDynamic("Accessibility", access));

        // Quick fix button — the big win
        fixBtn = new TextView(this);
        fixBtn.setTextSize(13);
        fixBtn.setTypeface(Typeface.DEFAULT_BOLD);
        fixBtn.setGravity(Gravity.CENTER);
        fixBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
        fixBtn.setClickable(true);
        updateFixButton();
        fixBtn.setOnClickListener(v -> {
            if (!ShizukuHelper.isRunning()) {
                Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_SHORT).show();
                return;
            }
            fixBtn.setText("Fixing…");
            new Thread(() -> {
                String pkg = getPackageName();
                Shell.run("pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS");
                boolean ok = SelfHeal.repair(MainActivity.this, 6000);
                runOnUiThread(() -> {
                    updateFixButton();
                    refresh();
                    Toast.makeText(this,
                        ok ? "✓ Accessibility restored" : "Still not up — try again in 5s",
                        Toast.LENGTH_LONG).show();
                });
            }).start();
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(24);
        fixBtn.setLayoutParams(flp);
        root.addView(fixBtn);

        // Permissions header
        TextView sec = new TextView(this);
        sec.setText("PERMISSIONS");
        sec.setTextSize(11);
        sec.setTextColor(MUTED);
        sec.setLetterSpacing(0.2f);
        sec.setPadding(dp(4), dp(28), 0, dp(10));
        root.addView(sec);

        root.addView(infoRow("Shell user",
            ShizukuHelper.isAvailable() ? "shell (UID 2000)" : "app"));
        root.addView(infoRow("File access", "/sdcard, /data"));

        root.addView(actionBtn("Request Shizuku Permission", v -> {
            if (!ShizukuHelper.isRunning()) {
                Toast.makeText(this, "Shizuku not running", Toast.LENGTH_SHORT).show();
                return;
            }
            ShizukuHelper.requestPermission();
        }));
        root.addView(actionBtn("Open Accessibility Settings", v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        // Test DSH
        root.addView(actionBtn("🔬  Test DSH engine", v -> testDsh()));
        // Dev: install /sdcard/Star.apk silently
        root.addView(actionBtn("⚡ Install /sdcard/Star.apk", v -> {
            java.io.File apk = new java.io.File("/sdcard/Star.apk");
            if (!apk.exists()) {
                Toast.makeText(this, "No /sdcard/Star.apk found", Toast.LENGTH_LONG).show();
                return;
            }
            if (!ShizukuHelper.isAvailable()) {
                Toast.makeText(this, "Shizuku not active — start it first", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Installing…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                // Copy to shell-accessible cache first
                Shell.run("cp /sdcard/Star.apk /data/local/tmp/Star.apk");
                Shell.Result r = Shell.run("pm install -r -d /data/local/tmp/Star.apk");
                String out = r.output.trim();
                boolean ok = r.exitCode == 0
                    || out.toLowerCase().contains("success");
                final String msg = ok
                    ? "✓ Installed. Close Star and reopen."
                    : "Install failed: " + (out.isEmpty() ? "(no output)" : out);
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
                Shell.run("rm -f /data/local/tmp/Star.apk");
            }).start();
        }));

        setContentView(scroll);

        Shizuku.addRequestPermissionResultListener((code, result) -> refresh());
    }

    private void updateFixButton() {
        boolean ok = ShizukuHelper.hasSecureSettings(this)
            && StarAccessibilityService.isRunning();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (ok) {
            fixBtn.setText("✓ Everything is working");
            fixBtn.setTextColor(GREEN);
            bg.setColor(0x1A7EE787);
            bg.setStroke(dp(1), 0x667EE787);
        } else {
            fixBtn.setText("⚡  FIX PERMISSIONS  (tap to repair)");
            fixBtn.setTextColor(0xFFFFB800);
            bg.setColor(0x1AFFB800);
            bg.setStroke(dp(2), 0xFFFFB800);
        }
        fixBtn.setBackground(bg);
    }

    private void tryHotReload() {
        java.io.File apk = new java.io.File("/sdcard/Star.apk");
        if (!apk.exists() || !ShizukuHelper.isAvailable()) return;
        try {
            long installedTime = getPackageManager()
                .getPackageInfo(getPackageName(), 0).lastUpdateTime;
            if (apk.lastModified() > installedTime + 10000) {
                new Thread(() -> {
                    Shell.run("cp /sdcard/Star.apk /data/local/tmp/Star.apk");
                    Shell.Result r = Shell.run("pm install -r -d /data/local/tmp/Star.apk");
                    boolean ok = r.exitCode == 0
                        || r.output.toLowerCase().contains("success");
                    if (ok) {
                        runOnUiThread(() -> Toast.makeText(this,
                            "✓ New build installed. Restart Star to apply.",
                            Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
        } catch (Exception ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        updateFixButton();
        tryHotReload();
        // Silent auto-heal
        if (!StarAccessibilityService.isRunning()
            && ShizukuHelper.isRunning() && ShizukuHelper.hasPermission()) {
            new Thread(() -> {
                String pkg = getPackageName();
                Shell.run("pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS");
                SelfHeal.repair(MainActivity.this, 4000);
                runOnUiThread(() -> { refresh(); updateFixButton(); });
            }).start();
        }
    }

    private void refresh() {
        if (shizuku == null) return;

        String s;
        int c;
        if (!ShizukuHelper.isRunning()) { s = "Not running"; c = RED; }
        else if (!ShizukuHelper.hasPermission()) { s = "Permission needed"; c = ORANGE; }
        else { s = "Active"; c = GREEN; }
        shizuku.setText(s);
        shizuku.setTextColor(c);

        if (StarAccessibilityService.isRunning()) {
            access.setText("Active");
            access.setTextColor(GREEN);
        } else {
            access.setText("Off");
            access.setTextColor(ORANGE);
        }
    }

    private View actionBtn(String text, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(BLUE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xFF0F1A26);
        bg.setStroke(dp(1), 0xFF2A4A6A);
        t.setBackground(bg);
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private View infoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        row.setBackground(bg);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(MUTED);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(TEXT);
        v.setTypeface(Typeface.MONOSPACE);
        row.addView(v);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private View infoRowDynamic(String label, TextView valueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        row.setBackground(bg);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(MUTED);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        valueView.setTextSize(13);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(valueView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private void testDsh() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("DSH Test");
        b.setMessage("Running…");
        final android.app.AlertDialog d = b.create();
        d.show();

        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            report.append("Bridge alive: ").append(DshClient.isBridgeAlive()).append("\n\n");

            DshClient.Result r = DshClient.run("list files in /sdcard/Star", 90_000);

            report.append("Took: ").append(r.ms).append("ms\n");
            report.append("Answer length: ").append(r.answer == null ? 0 : r.answer.length()).append("\n\n");
            report.append("--- LOG ---\n").append(r.log).append("\n");
            report.append("--- ANSWER ---\n");
            if (r.answer != null && r.answer.length() > 400) {
                report.append(r.answer, 0, 400).append("…");
            } else {
                report.append(r.answer == null ? "(null)" : r.answer);
            }

            final String txt = report.toString();
            runOnUiThread(() -> {
                d.setMessage(txt);
            });
        }).start();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
