package com.star.app;

import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;
import java.io.*;
import java.lang.reflect.Method;

public class ShizukuHelper {
    public static final int REQUEST_CODE = 1001;

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder()
                && !Shizuku.isPreV11()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    public static boolean isRunning() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    public static void requestPermission() {
        try { Shizuku.requestPermission(REQUEST_CODE); } catch (Throwable ignored) {}
    }

    /** Grant WRITE_SECURE_SETTINGS to Star via Shizuku (one-time). */
    public static String grantSecureSettings(String pkg) {
        try {
            String cmd = "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS";
            Shell.Result r = run(cmd);
            if (r.exitCode == 0) return "Granted";
            return "Failed: " + r.output;
        } catch (Throwable t) {
            return "Error: " + t.getMessage();
        }
    }

    /** Check if Star already has the permission. */
    public static boolean hasSecureSettings(android.content.Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static Shell.Result run(String cmd) {
        StringBuilder out = new StringBuilder();
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);

            String[] argv = {"sh", "-c", cmd};
            String[] env = {"PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin"};

            Object procObj = newProcess.invoke(null, argv, env, null);
            if (procObj == null) return new Shell.Result("Shizuku returned null process", -1);

            Process proc = (Process) procObj;

            BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader er = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String line;
            while ((line = r.readLine()) != null) out.append(line).append("\n");
            while ((line = er.readLine()) != null) out.append(line).append("\n");

            int code = proc.waitFor();
            return new Shell.Result(out.toString(), code);
        } catch (Throwable t) {
            return new Shell.Result("Shizuku error: " + t.getClass().getSimpleName()
                + ": " + t.getMessage(), -1);
        }
    }
}
