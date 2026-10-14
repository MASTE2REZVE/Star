package com.star.app;

import com.star.app.candy.*;
import com.star.app.Prefs;
import org.json.*;
import java.util.*;

/** Runs Candy skills from chat. */
public class SkillRunner {

    public static class DirectCall {
        public Candy skill;
        public String args;
    }

    public static class Result {
        public String name;
        public String output;
        public long ms;
    }

    public static Candy findByName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase();
        if (n.isEmpty()) return null;
        // 1. registry match
        for (Candy c : CandyManager.listInstalled()) {
            if (c.name != null && c.name.toLowerCase().equals(n)) return c;
            if (c.installAs != null && c.installAs.toLowerCase().equals(n)) return c;
        }
        // 2. fallback: any executable file in /sdcard/Star/candy/bin/
        if (n.matches("[a-z0-9_.+-]+")) {
            Shell.Result ls = Shell.run("ls /sdcard/Star/candy/bin/ 2>/dev/null");
            if (ls.output != null) {
                for (String bin : ls.output.split("\n")) {
                    String b = bin.trim();
                    if (b.equalsIgnoreCase(n)) {
                        Candy c = new Candy();
                        c.name = n;
                        c.installAs = n;
                        c.repo = "bin:" + n;
                        c.language = "bin";
                        c.status = "installed";
                        c.pkg = "";
                        return c;
                    }
                }
            }
        }
        return null;
    }

    public static DirectCall parseDirect(String message) {
        return parseDirect(message, null);
    }

    public static DirectCall parseDirect(String message, Prefs prefs) {
        if (message == null) return null;
        String m = message.trim();
        if (m.isEmpty()) return null;

        String original = m;
        boolean explicit = false;

        if (m.startsWith("!")) {
            m = m.substring(1).trim();
            explicit = true;
        } else if (m.toLowerCase().startsWith("run ")) {
            m = m.substring(4).trim();
            explicit = true;
        }
        int sp = m.indexOf(' ');
        String head = (sp < 0) ? m : m.substring(0, sp);
        String rest = (sp < 0) ? "" : m.substring(sp + 1).trim();

        Candy c = findByName(head);
        if (c == null) return null;
        if (prefs != null && !prefs.isSkillEnabled(c.name)) return null;

        if (rest.isEmpty() && !explicit) return null;

        DirectCall dc = new DirectCall();
        dc.skill = c;
        dc.args = rest;
        return dc;
    }

    public static Result run(Candy c, String argString) {
        Result r = new Result();
        r.name = c.name;
        long t0 = System.currentTimeMillis();
        String[] args = splitArgs(argString);
        r.output = runRaw(c, args);
        r.ms = System.currentTimeMillis() - t0;
        return r;
    }

    public static String runRaw(Candy c, String[] args) {
        try {
            if (c.isPkg()) return runPkg(c, args);
            return runRepo(c, args);
        } catch (Exception e) {
            return "skill error: " + e.getMessage();
        }
    }

    private static String runPkg(Candy c, String[] args) {
        String cmd = (c.installAs != null && !c.installAs.isEmpty()) ? c.installAs : c.pkg;
        JSONArray arr = new JSONArray();
        for (String a : args) arr.put(a);
        JSONObject req = new JSONObject();
        try {
            req.put("cmd", cmd);
            req.put("args", arr);
        } catch (Exception e) { return "json error"; }

        Shell.run("rm -f /sdcard/Star/candy/run-result.json");
        String esc = req.toString().replace("'", "'\\''");
        Shell.run("printf '%s' '" + esc + "' > /sdcard/Star/candy/run-request.json");

        long deadline = System.currentTimeMillis() + 120_000L;
        while (System.currentTimeMillis() < deadline) {
            Shell.Result r = Shell.run("test -f /sdcard/Star/candy/run-result.json && cat /sdcard/Star/candy/run-result.json");
            if (r.output != null && !r.output.trim().isEmpty()) {
                try {
                    JSONObject o = new JSONObject(r.output.trim());
                    if ("ok".equals(o.optString("status"))) {
                        String so = o.optString("stdout", "");
                        String se = o.optString("stderr", "");
                        StringBuilder sb = new StringBuilder(so);
                        if (!se.isEmpty()) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(se);
                        }
                        return sb.toString();
                    }
                    return "ERROR: " + o.optString("error", "unknown");
                } catch (Exception e) {
                    return "parse error: " + e.getMessage();
                }
            }
            try { Thread.sleep(300); } catch (Exception ignored) {}
        }
        return "TIMEOUT (120s)";
    }

    private static String runRepo(Candy c, String[] args) {
        String target = "/data/local/tmp/star/bin/" + c.name;
        StringBuilder cl = new StringBuilder(
            "test -x " + target + " || { mkdir -p /data/local/tmp/star/bin; " +
            "cp /sdcard/Star/candy/bin/" + c.name + " " + target + " 2>/dev/null; " +
            "chmod 755 " + target + " 2>/dev/null; }; ");
        cl.append(target);
        for (String a : args) cl.append(" ").append(shellQuote(a));
        cl.append(" 2>&1 | head -500");
        Shell.Result sr = Shell.run(cl.toString());
        return sr.output == null ? "" : sr.output;
    }

    public static String systemPrompt(Prefs prefs) {
        List<Candy> all = CandyManager.listInstalled();
        List<Candy> list = new java.util.ArrayList<>();
        for (Candy c : all) {
            if (prefs == null || prefs.isSkillEnabled(c.name)) list.add(c);
        }
        if (list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("INSTALLED CLI SKILLS.\n");
        sb.append("RULE: if the user message mentions any of these names, you MUST call run_skill. NEVER use run_shell for these. The shell user cannot see Termux binaries.\n");
        sb.append("Use run_skill({\"name\":\"<skill>\",\"args\":\"<args>\"}).\n");
        sb.append("Available skills:\n");
        for (Candy c : list) {
            sb.append("- ").append(c.name);
            if (c.installAs != null && !c.installAs.equals(c.name)) {
                sb.append(" (bin: ").append(c.installAs).append(")");
            }
            sb.append("\n");
        }
        sb.append("Example: run_skill({\"name\":\"rg\",\"args\":\"--version\"})\n");
        return sb.toString();
    }

    private static String[] splitArgs(String s) {
        if (s == null || s.trim().isEmpty()) return new String[0];
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inS = false, inD = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == 39 && !inD) { inS = !inS; }
            else if (ch == '"' && !inS) { inD = !inD; }
            else if (ch == ' ' && !inS && !inD) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        if (s.matches("[A-Za-z0-9_./=:@,+-]+")) return s;
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
