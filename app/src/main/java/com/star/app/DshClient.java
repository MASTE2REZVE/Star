package com.star.app;

public class DshClient {
    public static class Result {
        public String answer;
        public String reasoning;
        public int exitCode;
        public long ms;
        public String log = "";
    }

    private static final String DIR = "/sdcard/Star";
    private static final String TASK_FILE = DIR + "/task.txt";
    private static final String ANSWER_FILE = DIR + "/answer.txt";
    private static final String REASON_FILE = DIR + "/reason.txt";
    private static final String DONE_FILE = DIR + "/.done";
    private static final String LOCK_FILE = DIR + "/.lock";

    public static boolean isAvailable() {
        try {
            Shell.Result r = Shell.run("test -f " + DIR + "/bridge.log && echo yes");
            return r.output != null && r.output.contains("yes");
        } catch (Exception e) { return false; }
    }

    public static boolean isBridgeAlive() {
        try {
            Shell.Result r = Shell.run("pgrep -f star-bridge.sh 2>/dev/null");
            return r.exitCode == 0 && r.output != null && !r.output.trim().isEmpty();
        } catch (Exception e) { return false; }
    }

    public static Result run(String task, long timeoutMs) {
        Result result = new Result();
        long t0 = System.currentTimeMillis();
        StringBuilder log = new StringBuilder();

        try {
            // Clean everything including .done and .lock
            Shell.run("rm -f " + ANSWER_FILE + " " + REASON_FILE + " "
                + DONE_FILE + " " + LOCK_FILE);
            log.append("cleaned\n");

            // Write task
            String safe = task.replace("'", "'\\''");
            Shell.Result write = Shell.run("printf '%s' '" + safe + "' > " + TASK_FILE);
            log.append("write exit=").append(write.exitCode).append("\n");
            if (write.exitCode != 0) {
                result.answer = "Failed to write task";
                result.exitCode = -1;
                result.log = log.toString();
                result.ms = System.currentTimeMillis() - t0;
                return result;
            }

            // Wait for .done file in chunks
            int chunks = (int)Math.max(1, timeoutMs / 20000);
            boolean found = false;
            for (int i = 0; i < chunks && !found; i++) {
                String waitCmd =
                    "i=0; while [ ! -f " + DONE_FILE + " ] && [ $i -lt 18 ]; do " +
                    "sleep 1; i=$((i+1)); done; " +
                    "if [ -f " + DONE_FILE + " ]; then echo FOUND; else echo WAIT; fi";
                Shell.Result wait = Shell.run(waitCmd);
                String out = wait.output == null ? "" : wait.output;
                log.append("chunk").append(i).append("=").append(out.trim()).append("\n");
                if (out.contains("FOUND")) found = true;
            }

            // Read the answer
            if (found) {
                Shell.run("sleep 1");
                Shell.Result a = Shell.run("cat " + ANSWER_FILE + " 2>/dev/null");
                Shell.Result r = Shell.run("cat " + REASON_FILE + " 2>/dev/null");
                result.answer = a.output == null ? "" : a.output.trim();
                result.reasoning = r.output == null ? "" : r.output.trim();
                result.exitCode = result.answer.isEmpty() ? -1 : 0;
                log.append("answer len=").append(result.answer.length()).append("\n");
            } else {
                result.answer = "";
                result.exitCode = -1;
                log.append("timed out\n");
            }
            result.log = log.toString();
            result.ms = System.currentTimeMillis() - t0;
            return result;
        } catch (Exception e) {
            result.answer = "DSH error: " + e.getMessage();
            result.exitCode = -1;
            result.log = log.toString();
            result.ms = System.currentTimeMillis() - t0;
            return result;
        }
    }

    public static String formatReasoning(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("dsh: reasoning:")) {
                s = s.substring("dsh: reasoning:".length()).trim();
            }
            if (!s.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(s);
            }
        }
        String out = sb.toString();
        if (out.length() > 2000) out = out.substring(0, 2000) + "\n…";
        return out;
    }
}
