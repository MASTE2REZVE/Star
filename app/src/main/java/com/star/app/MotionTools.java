package com.star.app;

import java.io.File;
import java.io.FileWriter;

/**
 * LLM-callable wrappers around the motion-graphics renderers under
 * /sdcard/Star/motion (Remotion + Motion Canvas).
 *
 * Every method writes a small JSON job file to /sdcard/Star/motion/tmp/ and
 * shells out to the matching ~/bin/*_gen.sh launcher, then returns either
 * "OK: <path>" or "ERR: <message>".
 */
public class MotionTools {

    public static final String MOTION_DIR = "/sdcard/Star/motion";
    private static final String TMP_DIR = MOTION_DIR + "/tmp";

    private MotionTools() {}

    public static String generateTitleCard(String text, int durationSec, String outputPath) {
        try {
            String title = (text == null || text.length() == 0) ? "Title" : text;
            String job = "{\"type\":\"titleCard\",\"text\":" + jsonString(title)
                    + ",\"durationSec\":" + Math.max(1, durationSec) + "}";
            writeTemp("titlecard", job);
            String out = resolveOut(outputPath, MOTION_DIR + "/out.mp4");
            return runMotion(title, out);
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String generateExplainer(String scriptJson, String outputPath) {
        try {
            String job = (scriptJson == null || scriptJson.length() == 0) ? "{}" : scriptJson;
            writeTemp("explainer", job);
            String out = resolveOut(outputPath, MOTION_DIR + "/explainer.mp4");
            String title = firstTitle(job, "Explainer");
            return runMotion(title, out);
        } catch (Throwable t) {
            return err(t);
        }
    }

    public static String listTemplates() {
        return "[\"TitleCard\"]";
    }

    private static String runMotion(String titleArg, String outputPath) {
        String cmd = "~/bin/motion_gen.sh \"" + escapeShell(titleArg) + "\" \"" + escapeShell(outputPath) + "\"";
        Shell.Result r = Shell.run(cmd);
        if (r == null) {
            return "ERR: no result from Shell.run";
        }
        String out = (r.output == null) ? "" : r.output.trim();
        if (r.exitCode == 0 && new File(outputPath).exists()) {
            return "OK: " + outputPath;
        }
        String msg = out.length() > 0 ? out : ("exit code " + r.exitCode);
        return "ERR: " + msg;
    }

    private static String resolveOut(String outputPath, String fallback) {
        return (outputPath == null || outputPath.length() == 0) ? fallback : outputPath;
    }

    private static String firstTitle(String json, String fallback) {
        try {
            int i = json.indexOf("\"title\"");
            if (i >= 0) {
                int c = json.indexOf(':', i);
                int q1 = json.indexOf('"', c + 1);
                int q2 = json.indexOf('"', q1 + 1);
                if (q1 >= 0 && q2 > q1) {
                    return json.substring(q1 + 1, q2);
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static void writeTemp(String name, String body) throws Exception {
        File dir = new File(TMP_DIR);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IllegalStateException("cannot create " + TMP_DIR);
        }
        File f = new File(dir, name + "-" + System.currentTimeMillis() + ".json");
        FileWriter w = new FileWriter(f);
        try {
            w.write(body == null ? "" : body);
        } finally {
            w.close();
        }
    }

    private static String jsonString(String s) {
        return "\"" + escapeJson(s) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeShell(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`");
    }

    private static String err(Throwable t) {
        String msg = (t == null) ? "unknown error" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        return "ERR: " + msg;
    }
}
