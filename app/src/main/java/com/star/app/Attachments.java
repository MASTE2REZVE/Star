package com.star.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import java.io.InputStream;

public class Attachments {
    public static final long MAX_BYTES = 4_000_000; // 4 MB

    public static String getFileName(Context c, Uri uri) {
        String name = null;
        try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        if (name == null) name = uri.getLastPathSegment();
        return name;
    }

    public static String getMime(Context c, Uri uri) {
        String m = c.getContentResolver().getType(uri);
        return m == null ? "application/octet-stream" : m;
    }

    public static long getSize(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static boolean isImage(String mime) {
        return mime != null && mime.startsWith("image/");
    }

    /** Encode file bytes as a data URL usable in OpenAI vision format. */
    public static String toDataUrl(Context c, Uri uri) {
        try {
            String mime = getMime(c, uri);
            InputStream is = c.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int n, total = 0;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES + 1) { is.close(); return null; }
                bos.write(buf, 0, n);
            }
            is.close();
            if (total > MAX_BYTES) return null;
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            return "data:" + mime + ";base64," + b64;
        } catch (Exception e) {
            return null;
        }
    }
}
