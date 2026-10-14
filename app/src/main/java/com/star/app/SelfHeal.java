package com.star.app;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

public class SelfHeal {

    public static String comp(Context c) {
        return c.getPackageName() + "/" + StarAccessibilityService.class.getName();
    }

    public static boolean isListed(Context c) {
        try {
            String s = Settings.Secure.getString(
                c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return !TextUtils.isEmpty(s) && s.contains(comp(c));
        } catch (Exception e) { return false; }
    }

    public static boolean isEnabled(Context c) {
        try {
            return Settings.Secure.getInt(
                c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
        } catch (Exception e) { return false; }
    }

    /** Force-restart the accessibility service and wait up to `waitMs` for it to come up. */
    public static boolean repair(Context c, long waitMs) {
        String comp = comp(c);

        // Read current list
        String current = "";
        Shell.Result r = Shell.run("settings get secure enabled_accessibility_services");
        if (r.exitCode == 0) {
            current = r.output.trim();
            if (current.equals("null") || current.equals("N")) current = "";
        }

        // Remove Star from list first (forces Android to unbind)
        String withoutStar = removeFromList(current, comp);
        Shell.run("settings put secure enabled_accessibility_services '" + withoutStar + "'");
        SystemClock.sleep(400);

        // Re-add Star (forces rebind)
        String withStar = withoutStar.isEmpty() ? comp : withoutStar + ":" + comp;
        Shell.run("settings put secure enabled_accessibility_services '" + withStar + "'");
        Shell.run("settings put secure accessibility_enabled 1");

        // Poll until service is up
        long start = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - start < waitMs) {
            if (StarAccessibilityService.isRunning()) return true;
            SystemClock.sleep(300);
        }
        return StarAccessibilityService.isRunning();
    }

    private static String removeFromList(String list, String comp) {
        if (list == null || list.isEmpty()) return "";
        String[] parts = list.split(":");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.equals(comp)) continue;
            if (sb.length() > 0) sb.append(":");
            sb.append(p);
        }
        return sb.toString();
    }
}
