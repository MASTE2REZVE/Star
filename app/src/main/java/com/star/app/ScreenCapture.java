package com.star.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class ScreenCapture {
    public static volatile String lastError = "";

    public static String captureJpegBase64(Context ctx, int maxSide, int quality) {
        lastError = "";
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) { lastError = "no external files dir"; return null; }
            if (!dir.exists()) dir.mkdirs();
            File png = new File(dir, "live_frame.png");

            // capture as shell user
            String cmd = "screencap -p '" + png.getAbsolutePath() + "' >/dev/null 2>&1; " +
                         "test -s '" + png.getAbsolutePath() + "' && echo OK";
            Shell.Result r = Shell.run(cmd);
            if (r.output == null || !r.output.contains("OK")) {
                lastError = "screencap failed. rc=" + r.exitCode +
                            " out=" + safe(r.output);
                return null;
            }

            long len = png.length();
            Log.d("Star", "capture: path=" + png.getAbsolutePath() + " len=" + len);
            if (len < 1000) { lastError = "png tiny: " + len; return null; }

            byte[] pngBytes = readFully(png);
            if (pngBytes == null) return null;
            if (pngBytes.length < 1000) {
                lastError = "read short: " + pngBytes.length;
                return null;
            }
            Log.d("Star", "capture: read bytes=" + pngBytes.length +
                " head=" + firstHex(pngBytes, 4));

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length, opts);
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0) {
                lastError = "decode bounds failed w=" + w + " h=" + h +
                            " bytes=" + pngBytes.length +
                            " head=" + firstHex(pngBytes, 8);
                return null;
            }
            Log.d("Star", "capture: dims=" + w + "x" + h);

            int scale = 1;
            while ((w / scale) > maxSide || (h / scale) > maxSide) scale *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;
            Bitmap bmp = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length, opts);
            if (bmp == null) { lastError = "bitmap null"; return null; }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            bmp.recycle();
            byte[] bytes = baos.toByteArray();
            Log.d("Star", "capture: jpeg bytes=" + bytes.length);
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            lastError = "ex: " + e.getMessage();
            return null;
        }
    }

    private static byte[] readFully(File f) {
        try {
            InputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } catch (Exception e) {
            lastError = "read ex: " + e.getClass().getSimpleName() + " " + e.getMessage();
            return null;
        }
    }

    private static String firstHex(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++)
            sb.append(String.format("%02x ", b[i]));
        return sb.toString().trim();
    }

    private static String safe(String s) {
        if (s == null) return "(null)";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
