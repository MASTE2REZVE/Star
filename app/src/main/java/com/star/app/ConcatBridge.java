package com.star.app;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import android.util.Base64;
import android.util.Log;

public class ConcatBridge {

    private static final String TAG = "ConcatBridge";
    private static final String DIR = "/sdcard/Star/concat";
    private static final String REQ = DIR + "/request.json";
    private static final String RES = DIR + "/result.json";
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static class Result {
        public boolean ok;
        public String raw;
        public String error;
    }

    public static Result call(String method, String paramsJson, long timeoutMs) {
        Result r = new Result();
        try {
            int id = COUNTER.incrementAndGet();
            if (paramsJson == null || paramsJson.length() == 0) paramsJson = "{}";
            if (method == null) method = "";

            String request = "{\"jsonrpc\":\"2.0\",\"id\":" + id
                    + ",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
            String b64 = Base64.encodeToString(request.getBytes("UTF-8"), Base64.NO_WRAP);

            Shell.run("rm -f " + RES);
            Shell.run("sh -c 'echo " + b64 + " | base64 -d > " + REQ + "'");

            long reqMtime = mtime(REQ);
            long budget = timeoutMs > 0 ? timeoutMs : 0;
            long deadline = System.currentTimeMillis() + budget;

            String json = null;
            while (System.currentTimeMillis() <= deadline) {
                Shell.Result cr = Shell.run("cat " + RES + " 2>/dev/null");
                if (cr != null && cr.output != null) {
                    String out = cr.output.trim();
                    if (out.startsWith("{") && mtime(RES) >= reqMtime) {
                        json = out;
                        break;
                    }
                }
                try {
                    Thread.sleep(120L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            Shell.run("rm -f " + RES);

            if (json != null) {
                r.ok = true;
                r.raw = json;
                return r;
            }
            r.ok = false;
            r.error = "timeout waiting for result.json after " + budget + "ms";
            return r;
        } catch (Throwable t) {
            Log.w(TAG, "call failed: " + method, t);
            r.ok = false;
            r.raw = null;
            r.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            return r;
        }
    }

    private static long mtime(String path) {
        try {
            Shell.Result r = Shell.run("stat -c %Y '" + path + "' 2>/dev/null");
            if (r != null && r.output != null) {
                String s = r.output.trim();
                if (s.length() > 0) return Long.parseLong(s);
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }
}
