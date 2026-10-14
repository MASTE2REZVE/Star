package com.star.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

public class Updater {
    public interface Callback {
        void onResult(String msg);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build();

    // Checks a JSON manifest like:
    // { "versionCode": 2, "versionName": "1.1", "apk": "https://.../Star.apk", "notes": "..." }
    public static void checkAndUpdate(Activity act, Prefs prefs, Callback cb) {
        String manifestUrl = prefs.getUpdateUrl();
        if (manifestUrl.isEmpty()) { cb.onResult("No update URL set."); return; }

        Request req = new Request.Builder().url(manifestUrl).get().build();
        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call c, IOException e) {
                act.runOnUiThread(() -> cb.onResult("Check failed: " + e.getMessage()));
            }
            @Override public void onResponse(Call c, Response r) throws IOException {
                String s = r.body().string();
                try {
                    JSONObject j = new JSONObject(s);
                    int remoteCode = j.getInt("versionCode");
                    String apkUrl = j.getString("apk");
                    String versionName = j.optString("versionName", "");

                    int localCode = act.getPackageManager()
                        .getPackageInfo(act.getPackageName(), 0).versionCode;

                    if (remoteCode <= localCode) {
                        act.runOnUiThread(() -> cb.onResult("Already up to date (v" + localCode + ")"));
                        return;
                    }

                    act.runOnUiThread(() -> cb.onResult(
                        "Update found: v" + versionName + ". Downloading..."));
                    downloadAndInstall(act, apkUrl, cb);
                } catch (Exception e) {
                    act.runOnUiThread(() -> cb.onResult("Parse failed: " + s));
                }
            }
        });
    }

    private static void downloadAndInstall(Activity act, String url, Callback cb) {
        Request req = new Request.Builder().url(url).get().build();
        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call c, IOException e) {
                act.runOnUiThread(() -> cb.onResult("Download failed: " + e.getMessage()));
            }
            @Override public void onResponse(Call c, Response r) throws IOException {
                if (!r.isSuccessful()) {
                    act.runOnUiThread(() -> cb.onResult("HTTP " + r.code()));
                    return;
                }

                File out = new File(act.getExternalFilesDir(null), "Star-update.apk");
                try (InputStream in = r.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                }

                act.runOnUiThread(() -> {
                    cb.onResult("Downloaded. Tap Install when prompted.");
                    installApk(act, out);
                });
            }
        });
    }

    private static void installApk(Context ctx, File apk) {
        // Try silent install via Shizuku first
        if (ShizukuHelper.isAvailable()) {
            new Thread(() -> {
                String p = apk.getAbsolutePath().replace("'", "'\\''");
                Shell.Result r = Shell.run("pm install -r '" + p + "'");
                boolean ok = r.exitCode == 0
                    || r.output.contains("Success")
                    || r.output.toLowerCase().contains("success");
                if (!ok) {
                    ((android.app.Activity) ctx).runOnUiThread(() -> systemInstall(ctx, apk));
                } else {
                    ((android.app.Activity) ctx).runOnUiThread(() ->
                        android.widget.Toast.makeText(ctx,
                            "Installed silently. Reopen Star.", android.widget.Toast.LENGTH_LONG).show());
                }
            }).start();
            return;
        }
        systemInstall(ctx, apk);
    }

    private static void systemInstall(Context ctx, File apk) {
        Uri uri = FileProvider.getUriForFile(ctx, "com.star.app.fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
