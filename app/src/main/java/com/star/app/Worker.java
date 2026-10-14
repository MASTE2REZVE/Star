package com.star.app;

import org.json.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** One-shot call to a worker model (a free OpenRouter model used as a second brain). */
public class Worker {
    private static final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build();

    public interface Cb {
        void onReply(String text);
        void onError(String msg);
    }

    public static void ask(final Prefs prefs, final String prompt, final Cb cb) {
        final String model = prefs.getWorkerModel();
        if (model == null || model.isEmpty()) {
            cb.onError("No worker model set. Open Settings.");
            return;
        }
        new Thread(() -> {
            try {
                String apiKey = prefs.getApiKey();
                if (apiKey.isEmpty()) { cb.onError("No OpenRouter key"); return; }

                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("stream", false);
                body.put("temperature", 0.3);
                body.put("max_tokens", 4000);

                JSONArray msgs = new JSONArray();
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", prefs.getWorkerSystemPrompt());
                msgs.put(sys);

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", prompt);
                msgs.put(user);
                body.put("messages", msgs);

                Request req = new Request.Builder()
                    .url(prefs.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://github.com/MASTE2REZVE/Star")
                    .header("X-Title", "Star-Worker")
                    .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                    .build();

                Response resp = http.newCall(req).execute();
                String s = resp.body().string();
                if (s.trim().startsWith("data:")) {
                    int i2 = s.indexOf('{');
                    if (i2 >= 0) s = s.substring(i2);
                }
                s = s.replaceAll("[\\u0000-\\u001F]", "").trim();
                int lb = s.lastIndexOf('}');
                if (lb > 0 && lb < s.length() - 1) s = s.substring(0, lb + 1);

                JSONObject j = new JSONObject(s);
                if (j.has("error")) {
                    cb.onError(j.getJSONObject("error").optString("message", "error"));
                    return;
                }
                JSONArray choices = j.optJSONArray("choices");
                if (choices == null || choices.length() == 0) { cb.onError("empty"); return; }
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg == null) { cb.onError("no message"); return; }
                String content = msg.optString("content", "");
                if (content == null) content = "";
                String t = content.trim();
                if (t.isEmpty() || t.equalsIgnoreCase("null")) {
                    cb.onError("worker returned nothing"); return;
                }
                cb.onReply(t);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }
}
