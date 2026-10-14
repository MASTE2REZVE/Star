package com.star.app;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class Shell {
    public static class Result {
        public String output;
        public int exitCode;
        public Result(String o, int c) { output = o; exitCode = c; }
    }

    public static Result run(String cmd) {
        if (ShizukuHelper.isAvailable()) {
            return ShizukuHelper.run(cmd);
        }
        return runLocal(cmd);
    }

    private static Result runLocal(String cmd) {
        StringBuilder out = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) out.append(line).append("\n");
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) { p.destroy(); return new Result(out + "\n[timed out]", -1); }
            return new Result(out.toString(), p.exitValue());
        } catch (Exception e) {
            return new Result("Error: " + e.getMessage(), -1);
        }
    }
}
