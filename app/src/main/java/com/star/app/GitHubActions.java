package com.star.app;

import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Minimal GitHub REST + Actions API client. */
public class GitHubActions {
    private static final String API = "https://api.github.com";
    private static final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    public static class User { public String login; public String name; public String avatar; }
    public static class RunInfo {
        public long id;
        public String status;
        public String conclusion;
        public String htmlUrl;
        public String name;
        public long startedAt;
        public long updatedAt;
        public long durationMs;   // real value from GitHub API
    }
    public static class Artifact {
        public long id;
        public String name;
        public long size;
        public String url;
    }

    public interface Cb<T> { void onOk(T v); void onErr(String err); }

    private static Request.Builder authed(Prefs prefs) {
        Request.Builder b = new Request.Builder()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Star-Builder");
        String t = prefs.getGitHubToken();
        if (t != null && !t.isEmpty()) b.header("Authorization", "Bearer " + t);
        return b;
    }

    private static JSONObject parse(String body) throws JSONException {
        return new JSONObject(body == null ? "{}" : body);
    }

    private static String requireToken(Prefs prefs) {
        String t = prefs.getGitHubToken();
        if (t == null || t.isEmpty()) return "GitHub token missing. Open Settings.";
        return null;
    }

    // ---------- user ----------
    public static void getUser(final Prefs prefs, final Cb<User> cb) {
        String err = requireToken(prefs);
        if (err != null) { cb.onErr(err); return; }
        new Thread(() -> {
            try {
                Request req = authed(prefs).url(API + "/user").get().build();
                Response resp = http.newCall(req).execute();
                String s = resp.body().string();
                if (!resp.isSuccessful()) { cb.onErr("HTTP " + resp.code() + " " + s); return; }
                JSONObject o = parse(s);
                User u = new User();
                u.login = o.optString("login");
                u.name  = o.optString("name");
                u.avatar = o.optString("avatar_url");
                cb.onOk(u);
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    // ---------- repo file ops ----------
    public static void getFile(final Prefs prefs, final String owner, final String repo,
                               final String path, final Cb<String> cb) {
        new Thread(() -> {
            try {
                Request req = authed(prefs)
                    .url(API + "/repos/" + owner + "/" + repo + "/contents/" + path)
                    .get().build();
                Response resp = http.newCall(req).execute();
                String s = resp.body().string();
                if (resp.code() == 404) { cb.onOk(null); return; }  // missing is fine
                if (!resp.isSuccessful()) { cb.onErr("HTTP " + resp.code()); return; }
                JSONObject o = parse(s);
                String b64 = o.optString("content", "").replaceAll("\\s+", "");
                byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                cb.onOk(new String(bytes, "UTF-8"));
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    /** Write a text file. Creates or updates. */
    public static void putFile(final Prefs prefs, final String owner, final String repo,
                               final String path, final String content, final String message,
                               final Cb<String> cb) {
        new Thread(() -> {
            try {
                // 1. get existing sha (if any)
                String sha = null;
                Request getReq = authed(prefs)
                    .url(API + "/repos/" + owner + "/" + repo + "/contents/" + path)
                    .get().build();
                Response getResp = http.newCall(getReq).execute();
                if (getResp.isSuccessful()) {
                    JSONObject existing = parse(getResp.body().string());
                    sha = existing.optString("sha", null);
                } else {
                    getResp.close();
                }

                // 2. build put body
                JSONObject body = new JSONObject();
                body.put("message", message == null ? "Star build" : message);
                body.put("content", android.util.Base64.encodeToString(
                    content.getBytes("UTF-8"), android.util.Base64.NO_WRAP));
                if (sha != null && !sha.isEmpty()) body.put("sha", sha);

                Request putReq = authed(prefs)
                    .url(API + "/repos/" + owner + "/" + repo + "/contents/" + path)
                    .put(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                    .build();
                Response putResp = http.newCall(putReq).execute();
                String putBody = putResp.body().string();
                if (!putResp.isSuccessful()) { cb.onErr("put HTTP " + putResp.code() + " " + putBody); return; }
                JSONObject r = parse(putBody);
                cb.onOk(r.optJSONObject("content") != null
                    ? r.getJSONObject("content").optString("sha") : "ok");
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    // ---------- workflow dispatch ----------
    public static void dispatchWorkflow(final Prefs prefs, final String owner, final String repo,
                                        final String workflowFile, final JSONObject inputs,
                                        final Cb<Void> cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("ref", "main");
                if (inputs != null) body.put("inputs", inputs);

                Request req = authed(prefs)
                    .url(API + "/repos/" + owner + "/" + repo +
                         "/actions/workflows/" + workflowFile + "/dispatches")
                    .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                    .build();
                Response resp = http.newCall(req).execute();
                if (resp.code() == 204 || resp.isSuccessful()) { cb.onOk(null); return; }
                cb.onErr("HTTP " + resp.code() + " " + resp.body().string());
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    // ---------- runs ----------
    public static void listRuns(final Prefs prefs, final String owner, final String repo,
                                final Cb<List<RunInfo>> cb) {
        new Thread(() -> {
            try {
                Request req = authed(prefs)
                    .url(API + "/repos/" + owner + "/" + repo + "/actions/runs?per_page=10")
                    .get().build();
                Response resp = http.newCall(req).execute();
                if (!resp.isSuccessful()) { cb.onErr("HTTP " + resp.code()); return; }
                JSONObject o = parse(resp.body().string());
                JSONArray arr = o.optJSONArray("workflow_runs");
                List<RunInfo> out = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject r = arr.getJSONObject(i);
                        RunInfo ri = new RunInfo();
                        ri.id = r.optLong("id");
                        ri.status = r.optString("status");
                        ri.conclusion = r.optString("conclusion", null);
                        ri.htmlUrl = r.optString("html_url");
                        ri.name = r.optString("name");
                        ri.startedAt = parseTime(r.optString("run_started_at"));
                        ri.updatedAt = parseTime(r.optString("updated_at"));
                        // run_duration_ms is real. GitHub calculates it.
                        ri.durationMs = r.optLong("run_duration_ms", 0);
                        out.add(ri);
                    }
                }
                cb.onOk(out);
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    public static void listArtifacts(final Prefs prefs, final String owner, final String repo,
                                     final long runId, final Cb<List<Artifact>> cb) {
        new Thread(() -> {
            try {
                Request req = authed(prefs)
                    .url(API + "/repos/" + owner + "/" + repo +
                         "/actions/runs/" + runId + "/artifacts")
                    .get().build();
                Response resp = http.newCall(req).execute();
                if (!resp.isSuccessful()) { cb.onErr("HTTP " + resp.code()); return; }
                JSONObject o = parse(resp.body().string());
                JSONArray arr = o.optJSONArray("artifacts");
                List<Artifact> out = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject a = arr.getJSONObject(i);
                        Artifact ai = new Artifact();
                        ai.id = a.optLong("id");
                        ai.name = a.optString("name");
                        ai.size = a.optLong("size_in_bytes");
                        ai.url = a.optString("archive_download_url");
                        out.add(ai);
                    }
                }
                cb.onOk(out);
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    /** Download artifact zip to a local path. Returns file path on success. */
    public static void downloadArtifact(final Prefs prefs, final String owner, final String repo,
                                        final long artifactId, final String destPath,
                                        final Cb<String> cb) {
        new Thread(() -> {
            try {
                Request req = authed(prefs)
                    .url(API + "/repos/" + owner + "/" + repo +
                         "/actions/artifacts/" + artifactId + "/zip")
                    .get().build();
                Response resp = http.newCall(req).execute();
                if (!resp.isSuccessful()) { cb.onErr("HTTP " + resp.code()); return; }
                InputStream in = resp.body().byteStream();
                FileOutputStream fos = new FileOutputStream(destPath);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close(); in.close();
                cb.onOk(destPath);
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    /** Ensure a repo exists. If not, create it as private. */
    public static void ensureRepo(final Prefs prefs, final String name, final Cb<String> cb) {
        new Thread(() -> {
            try {
                String user = prefs.getGitHubUser();
                if (user == null || user.isEmpty()) { cb.onErr("no github user"); return; }

                // 1. Ask directly: does owner/name exist?
                Request direct = authed(prefs)
                    .url(API + "/repos/" + user + "/" + name)
                    .get().build();
                Response dr = http.newCall(direct).execute();
                if (dr.isSuccessful()) {
                    JSONObject existing = parse(dr.body().string());
                    cb.onOk(existing.optString("full_name", user + "/" + name));
                    return;
                }
                dr.close();

                // 2. Create it
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("private", true);
                body.put("auto_init", true);
                body.put("description", "Star Builder sandbox");
                Request put = authed(prefs).url(API + "/user/repos")
                    .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                    .build();
                Response pr = http.newCall(put).execute();
                String pb = pr.body().string();
                if (pr.code() == 422 && pb.contains("already exists")) {
                    // race or stale cache: treat as success
                    cb.onOk(user + "/" + name);
                    return;
                }
                if (!pr.isSuccessful()) {
                    cb.onErr("create HTTP " + pr.code() + " " + pb);
                    return;
                }
                JSONObject created = new JSONObject(pb);
                cb.onOk(created.optString("full_name", user + "/" + name));
            } catch (Exception e) {
                cb.onErr(e.getMessage());
            }
        }).start();
    }

    public static class BillingInfo {
        public int totalMinutesUsed;
        public int includedMinutes;
        public int paidMinutesUsed;
    }

    /** Real GitHub billing. Uses the billing token if present.
     *  Endpoint: GET /users/{user}/settings/billing/actions
     *  Requires classic token with `user` scope, OR fine-grained token
     *  with permission Plan -> Read only. The latter is the safe option. */
    public static void fetchBilling(final Prefs prefs, final Cb<BillingInfo> cb) {
        new Thread(() -> {
            try {
                String user = prefs.getGitHubUser();
                if (user == null || user.isEmpty()) { cb.onErr("no user"); return; }

                String token = prefs.getBillingToken();
                if (token == null || token.isEmpty()) token = prefs.getGitHubToken();

                Request req = new Request.Builder()
                    .url(API + "/users/" + user + "/settings/billing/actions")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "Star-Builder")
                    .header("Authorization", "Bearer " + token)
                    .get()
                    .build();

                Response resp = http.newCall(req).execute();
                String body = resp.body().string();

                if (!resp.isSuccessful()) {
                    cb.onErr("HTTP " + resp.code() + " " + body);
                    return;
                }

                JSONObject o = new JSONObject(body);
                BillingInfo bi = new BillingInfo();
                bi.totalMinutesUsed = o.optInt("total_minutes_used", 0);
                bi.includedMinutes = o.optInt("included_minutes", 0);
                bi.paidMinutesUsed = o.optInt("total_paid_minutes_used", 0);
                cb.onOk(bi);
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    /** Sum duration of all runs in the current calendar month for a repo. */
    public static void monthlyUsage(final Prefs prefs, final String owner, final String repo,
                                    final Cb<Long> cb) {
        new Thread(() -> {
            try {
                long totalMs = 0L;
                // start of current month in UTC
                java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long monthStart = cal.getTimeInMillis();

                // paginate through runs (max 100 per page)
                for (int page = 1; page <= 5; page++) {
                    String url = API + "/repos/" + owner + "/" + repo +
                        "/actions/runs?per_page=100&page=" + page;
                    Request req = authed(prefs).url(url).get().build();
                    Response resp = http.newCall(req).execute();
                    if (!resp.isSuccessful()) { resp.close(); break; }
                    JSONObject o = parse(resp.body().string());
                    JSONArray arr = o.optJSONArray("workflow_runs");
                    if (arr == null || arr.length() == 0) break;
                    boolean reachedOlder = false;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject r = arr.getJSONObject(i);
                        long started = parseTime(r.optString("run_started_at"));
                        if (started < monthStart) { reachedOlder = true; continue; }
                        totalMs += r.optLong("run_duration_ms", 0);
                    }
                    if (reachedOlder) break;
                }
                cb.onOk(totalMs);
            } catch (Exception e) { cb.onErr(e.getMessage()); }
        }).start();
    }

    private static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .parse(iso).getTime();
        } catch (Exception e) { return 0; }
    }
}
