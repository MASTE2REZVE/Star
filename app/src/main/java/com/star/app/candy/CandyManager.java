package com.star.app.candy;

import com.star.app.Shell;
import org.json.*;
import java.io.*;
import java.util.*;

public class CandyManager {
    private static final String DIR = "/sdcard/Star/candy";
    private static final String REGISTRY = DIR + "/registry.json";
    private static final String REQ = DIR + "/build-request.json";
    private static final String RES = DIR + "/build-result.json";
    private static final String LOG = DIR + "/build.log";

    private static final String PKG_REQ = DIR + "/pkg-request.json";
    private static final String PKG_RES = DIR + "/pkg-result.json";
    private static final String PKG_LOG = DIR + "/pkg.log";

    private static final String RUN_REQ = DIR + "/run-request.json";
    private static final String RUN_RES = DIR + "/run-result.json";

    public interface Progress {
        void onLine(String line);
        void onDone(boolean ok, String msg);
    }

    public interface RunCallback {
        void onResult(String stdout, String stderr, int exit);
        void onError(String msg);
    }

    public static void ensureDir() {
        Shell.run("mkdir -p " + DIR);
    }

    /** Debian -> Termux package name map. Returns the input if no map. */
    public static String mapPkgName(String pkg) {
        if (pkg == null) return "";
        String p = pkg.trim().toLowerCase();
        switch (p) {
            case "poppler-utils":  return "poppler";
            case "python3":        return "python";
            case "python3-pip":    return "python-pip";
            case "python3-venv":   return "python";
            case "nodejs":         return "nodejs-lts";
            case "node":           return "nodejs-lts";
            case "npm":            return "nodejs-lts";
            case "python-pil":     return "python-pillow";
            case "libjpeg-dev":    return "libjpeg-turbo";
            case "libpng-dev":     return "libpng";
            case "build-essential": return "build-essential";
        }
        return p;
    }

    private static List<Candy> _cache = null;
    private static long _cacheTime = 0L;

    public static void invalidateCache() { _cache = null; _cacheTime = 0L; }

    public static List<Candy> listInstalled() {
        long now = System.currentTimeMillis();
        if (_cache != null && now - _cacheTime < 3000L) return _cache;
        List<Candy> out = new ArrayList<>();
        try {
            Shell.Result r = Shell.run("cat " + REGISTRY + " 2>/dev/null");
            if (r.output == null || r.output.trim().isEmpty()) return out;
            JSONArray arr = new JSONArray(r.output);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Candy c = new Candy();
                c.name = o.optString("name");
                c.repo = o.optString("repo");
                c.language = o.optString("language");
                c.commit = o.optString("commit");
                c.installedAt = o.optString("installed");
                c.status = o.optString("status");
                c.installAs = o.optString("install_as");
                c.pkg = o.optString("pkg");
                out.add(c);
            }
        } catch (Exception ignored) {}
        _cache = out;
        _cacheTime = System.currentTimeMillis();
        return out;
    }

    public static void saveRegistry(List<Candy> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Candy c : list) arr.put(new JSONObject(c.toJson()));
            String json = arr.toString(2);
            String esc = json.replace("'", "'\\''");
            Shell.run("printf '%s' '" + esc + "' > " + REGISTRY);
        } catch (Exception ignored) {}
        invalidateCache();
    }

    /* ================== REPO BUILD (existing) ================== */

    public static void install(Candy c, Progress p) {
        new Thread(() -> {
            try {
                ensureDir();
                Shell.run("rm -f " + REQ + " " + RES + " " + LOG);

                final String buildId = java.util.UUID.randomUUID().toString();
                String req = "{\"id\":\"" + buildId + "\",\"repo\":\"" + esc(c.repo) + "\",\"name\":\"" + esc(c.name) + "\"}";
                String escaped = req.replace("'", "'\\''");
                Shell.run("printf '%s' '" + escaped + "' > " + REQ);

                long deadline = System.currentTimeMillis() + 30 * 60 * 1000L;
                long lastLogSize = 0;
                boolean done = false;
                while (System.currentTimeMillis() < deadline && !done) {
                    Shell.Result logR = Shell.run("cat " + LOG + " 2>/dev/null");
                    if (logR.output != null) {
                        String full = logR.output;
                        if (full.length() < lastLogSize) lastLogSize = 0;
                        if (full.length() > lastLogSize) {
                            p.onLine(full.substring((int)lastLogSize));
                            lastLogSize = full.length();
                        }
                    }
                    Shell.Result resR = Shell.run("test -f " + RES + " && cat " + RES + " 2>/dev/null");
                    if (resR.output != null && resR.output.contains("\"" + buildId + "\"")) {
                        done = true;
                        Thread.sleep(400);
                        Shell.Result finalLog = Shell.run("cat " + LOG + " 2>/dev/null");
                        if (finalLog.output != null && finalLog.output.length() > lastLogSize) {
                            p.onLine(finalLog.output.substring((int)lastLogSize));
                        }
                    }
                    Thread.sleep(1000);
                }

                if (done) {
                    Shell.Result statusR = Shell.run("cat " + RES);
                    String statusJson = statusR.output == null ? "" : statusR.output;
                    if (statusJson.contains("failed")) {
                        p.onDone(false, "Build failed — see log");
                        return;
                    }
                    if (statusJson.contains("library")) {
                        p.onDone(false, "Compiled but no binary — this is a library, not a CLI");
                        return;
                    }
                    List<Candy> list = listInstalled();
                    list.removeIf(x -> x.name != null && x.name.equals(c.name));
                    c.installedAt = new java.text.SimpleDateFormat("yyyy-MM-dd")
                        .format(new java.util.Date());
                    c.status = "installed";
                    try {
                        org.json.JSONObject rj = new org.json.JSONObject(statusJson);
                        String lg = rj.optString("language", "");
                        if (lg != null && !lg.isEmpty()) c.language = lg;
                        else if (c.language == null || c.language.isEmpty()) c.language = "rust";
                    } catch (Exception e) {
                        if (c.language == null || c.language.isEmpty()) c.language = "rust";
                    }
                    if (c.installAs == null) c.installAs = c.name;
                    if (c.pkg == null) c.pkg = "";
                    list.add(c);
                    saveRegistry(list);
                    p.onDone(true, "Installed: " + c.name);
                } else {
                    p.onDone(false, "Timed out (10 min)");
                }
            } catch (Exception e) {
                p.onDone(false, "Error: " + e.getMessage());
            }
        }).start();
    }

    /* ================== TERMUX PACKAGE INSTALL ================== */

    public static void installPkg(String pkg, String installAs, Progress p) {
        new Thread(() -> {
            try {
                ensureDir();
                Shell.run("rm -f " + PKG_REQ + " " + PKG_RES);

                final String buildId = java.util.UUID.randomUUID().toString();
                String req = "{\"id\":\"" + buildId + "\",\"pkg\":\"" + esc(pkg) + "\",\"install_as\":\"" + esc(installAs) + "\"}";
                String escaped = req.replace("'", "'\\''");
                Shell.run("printf '%s' '" + escaped + "' > " + PKG_REQ);

                long deadline = System.currentTimeMillis() + 30 * 60 * 1000L;
                long lastSize = 0;
                boolean done = false;

                while (System.currentTimeMillis() < deadline && !done) {
                    Shell.Result full = Shell.run("cat " + PKG_LOG + " 2>/dev/null");
                    if (full.output != null && full.output.length() > lastSize) {
                        p.onLine(full.output.substring((int)lastSize));
                        lastSize = full.output.length();
                    }
                    Shell.Result resR = Shell.run("test -f " + PKG_RES + " && cat " + PKG_RES);
                    if (resR.output != null && resR.output.contains("\"" + buildId + "\"")) {
                        done = true;
                        Shell.Result fl = Shell.run("cat " + PKG_LOG + " 2>/dev/null");
                        if (fl.output != null && fl.output.length() > lastSize) {
                            p.onLine(fl.output.substring((int)lastSize));
                        }
                        JSONObject o = new JSONObject(resR.output.trim());
                        String status = o.optString("status");
                        if ("installed".equals(status)) {
                            List<Candy> list = listInstalled();
                            list.removeIf(x -> x.name != null && x.name.equals(installAs));
                            Candy c = new Candy();
                            c.name = installAs;
                            c.repo = "termux:" + pkg;
                            c.language = "pkg";
                            c.installAs = installAs;
                            c.pkg = pkg;
                            c.status = "installed";
                            c.installedAt = new java.text.SimpleDateFormat("yyyy-MM-dd")
                                .format(new java.util.Date());
                            list.add(c);
                            saveRegistry(list);
                            p.onDone(true, "Installed: " + pkg + " as " + installAs);
                        } else {
                            p.onDone(false, "Install failed: " + o.optString("error", "unknown"));
                        }
                    }
                    Thread.sleep(1000);
                }
                if (!done) p.onDone(false, "Timed out (10 min)");
            } catch (Exception e) {
                p.onDone(false, "Error: " + e.getMessage());
            }
        }).start();
    }

    /* ================== RUN A PKG SKILL ================== */

    public static void runSkill(String cmd, String[] args, RunCallback cb) {
        new Thread(() -> {
            try {
                ensureDir();
                Shell.run("rm -f " + RUN_RES);

                StringBuilder a = new StringBuilder("[");
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) a.append(",");
                        a.append("\"").append(esc(args[i])).append("\"");
                    }
                }
                a.append("]");

                String req = "{\"cmd\":\"" + esc(cmd) + "\",\"args\":" + a + "}";
                String escaped = req.replace("'", "'\\''");
                Shell.run("printf '%s' '" + escaped + "' > " + RUN_REQ);

                long deadline = System.currentTimeMillis() + 5 * 60 * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    Shell.Result r = Shell.run("test -f " + RUN_RES + " && cat " + RUN_RES);
                    if (r.output != null && !r.output.trim().isEmpty()) {
                        JSONObject o = new JSONObject(r.output.trim());
                        if ("ok".equals(o.optString("status"))) {
                            cb.onResult(o.optString("stdout"), o.optString("stderr"), o.optInt("exit"));
                        } else {
                            cb.onError(o.optString("error", "unknown"));
                        }
                        return;
                    }
                    Thread.sleep(500);
                }
                cb.onError("Timed out (5 min)");
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    /** Copy all candy binaries to /data/local/tmp/star/bin once. Fast Run later. */
    public static void prefetchBinaries() {
        new Thread(() -> {
            try {
                Shell.run("mkdir -p /data/local/tmp/star/bin");
                Shell.run("cp -f /sdcard/Star/candy/bin/* /data/local/tmp/star/bin/ 2>/dev/null; chmod 755 /data/local/tmp/star/bin/* 2>/dev/null");
            } catch (Exception ignored) {}
        }).start();
    }

    /** Fast lookup by name or install_as. Uses the cached registry. */
    public static Candy findByName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase();
        if (n.isEmpty()) return null;
        for (Candy c : listInstalled()) {
            if (c.name != null && c.name.toLowerCase().equals(n)) return c;
            if (c.installAs != null && c.installAs.toLowerCase().equals(n)) return c;
        }
        return null;
    }

    public static String checkUpdate(Candy c) {
        return "not-checked";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
