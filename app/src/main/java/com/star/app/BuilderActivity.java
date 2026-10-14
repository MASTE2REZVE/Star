package com.star.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class BuilderActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int GREEN = 0xFF7EE787;
    private static final int RED = 0xFFF85149;

    private Prefs prefs;
    private EditText repoField;
    private TextView userTv, sandboxTv, usageTv, statusTv, modelPill, fallbackPill, stopBtn;
    private volatile boolean stopRequested = false;
    private volatile boolean running = false;
    private LinearLayout logCol;
    private ScrollView logScroll;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastWorkflowYaml = null;
    private String lastArtifactGlob = "";
    private String lastOutputType = "";
    private String lastRepoOwner = null;
    private String lastRepoName = null;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        buildUI();
        if (!prefs.getGitHubToken().isEmpty()) connect();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(10));

        TextView back = new TextView(this);
        back.setText("<-");
        back.setTextSize(20);
        back.setTextColor(TEXT);
        back.setPadding(dp(6), dp(6), dp(14), dp(6));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        top.addView(back);

        TextView title = new TextView(this);
        title.setText("Builder");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);
        root.addView(top);

        ScrollView mainScroll = new ScrollView(this);
        mainScroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(20));
        mainScroll.addView(content);

        userTv = row(content, "GitHub: " + (prefs.getGitHubUser().isEmpty() ? "not connected" : prefs.getGitHubUser()));
        sandboxTv = row(content, "Sandbox: " + (prefs.getSandboxRepo().isEmpty() ? "not set" : prefs.getSandboxRepo()));

        usageTv = row(content, "Actions usage: loading...");
        refreshUsage();

        modelPill = row(content, "");
        refreshModelPill();
        modelPill.setOnClickListener(v -> ModelPickerDialog.show(
            BuilderActivity.this, prefs, prefs.getBuilderModel(),
            m -> { prefs.setBuilderModel(m); refreshModelPill(); }));

        fallbackPill = row(content, "");
        refreshFallbackPill();
        fallbackPill.setOnClickListener(v -> ModelPickerDialog.show(
            BuilderActivity.this, prefs, prefs.getBuilderFallback(),
            m -> { prefs.setBuilderFallback(m); refreshFallbackPill(); }));

        stopBtn = btn(content, "Stop", RED, () -> stop());
        stopBtn.setVisibility(View.GONE);

        btn(content, "Connect GitHub", ACCENT, () -> connect());

        content.addView(tiny("TARGET REPO"));
        repoField = new EditText(this);
        repoField.setHint("https://github.com/owner/name");
        repoField.setTextColor(TEXT);
        repoField.setHintTextColor(MUTED);
        repoField.setBackground(rounded(CARD, BORDER, 12));
        repoField.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(4);
        rlp.bottomMargin = dp(14);
        repoField.setLayoutParams(rlp);
        content.addView(repoField);

        btn(content, "Analyze", GREEN, () -> analyze());
        btn(content, "Build", ACCENT, () -> build());

        statusTv = new TextView(this);
        statusTv.setText("Ready");
        statusTv.setTextSize(11);
        statusTv.setTypeface(Typeface.MONOSPACE);
        statusTv.setTextColor(MUTED);
        statusTv.setPadding(0, dp(8), 0, dp(8));
        content.addView(statusTv);

        content.addView(tiny("LOG"));
        logScroll = new ScrollView(this);
        LinearLayout.LayoutParams lslp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        lslp.bottomMargin = dp(14);
        logScroll.setLayoutParams(lslp);
        logScroll.setBackground(rounded(0xFF141414, 0xFF232323, 10));
        logCol = new LinearLayout(this);
        logCol.setOrientation(LinearLayout.VERTICAL);
        logCol.setPadding(dp(12), dp(10), dp(12), dp(10));
        logScroll.addView(logCol);
        content.addView(logScroll);

        root.addView(mainScroll);
        setContentView(root);
    }

    private TextView tiny(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(10);
        t.setTextColor(MUTED);
        t.setLetterSpacing(0.15f);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView row(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(TEXT);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackground(rounded(CARD, BORDER, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        t.setLayoutParams(lp);
        parent.addView(t);
        return t;
    }

    private TextView btn(LinearLayout parent, String text, int color, Runnable on) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x12FFFFFF);
        bg.setStroke(dp(1), color);
        t.setBackground(bg);
        t.setClickable(true);
        t.setOnClickListener(v -> on.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        t.setLayoutParams(lp);
        parent.addView(t);
        return t;
    }

    private GradientDrawable rounded(int fill, int border, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(r));
        g.setColor(fill);
        g.setStroke(dp(1), border);
        return g;
    }

    private void log(String s) {
        handler.post(() -> {
            TextView t = new TextView(this);
            t.setText(s);
            t.setTextSize(10);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextColor(0xFFB0B0B0);
            logCol.addView(t);
            logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void status(String s) { handler.post(() -> statusTv.setText(s)); }

    private void connect() {
        status("Connecting...");
        log("-> GET /user");
        GitHubActions.getUser(prefs, new GitHubActions.Cb<GitHubActions.User>() {
            @Override public void onOk(GitHubActions.User u) {
                prefs.setGitHubUser(u.login);
                handler.post(() -> userTv.setText("GitHub: " + u.login));
                log("OK: " + u.login);
                status("Connected");
                ensureSandbox();
            }
            @Override public void onErr(String e) {
                log("ERR: " + e);
                status("Connect failed");
            }
        });
    }

    private void ensureSandbox() {
        String cur = prefs.getSandboxRepo();
        if (!cur.isEmpty()) {
            log("Sandbox: " + cur);
            handler.post(() -> sandboxTv.setText("Sandbox: " + cur));
            return;
        }
        log("-> creating sandbox star-builds");
        GitHubActions.ensureRepo(prefs, "star-builds", new GitHubActions.Cb<String>() {
            @Override public void onOk(String full) {
                prefs.setSandboxRepo(full);
                handler.post(() -> sandboxTv.setText("Sandbox: " + full));
                log("OK: " + full);
            }
            @Override public void onErr(String e) {
                log("ERR sandbox: " + e);
            }
        });
    }

    private void analyze() {
        String url = repoField.getText().toString().trim();
        if (url.isEmpty()) { toast("Paste a repo URL"); return; }
        final String[] on = parseRepo(url);
        if (on == null) { toast("Bad URL"); return; }

        lastRepoOwner = on[0];
        lastRepoName = on[1];
        retriedStrong = false;

        setRunning(true);
        stopRequested = false;
        status("Analyzing " + on[0] + "/" + on[1] + "...");
        log("-> fetching repo tree");
        log("-> fetching README, manifests");

        new Thread(() -> {
            try {
                String tree = fetchTree(on[0], on[1]);
                String readme = fetchFile(on[0], on[1], "README.md");
                String cargo = fetchFile(on[0], on[1], "Cargo.toml");
                String pkg = fetchFile(on[0], on[1], "package.json");
                String goMod = fetchFile(on[0], on[1], "go.mod");
                String pyproj = fetchFile(on[0], on[1], "pyproject.toml");

                StringBuilder ctx = new StringBuilder();
                ctx.append("Repo: ").append(on[0]).append("/").append(on[1]).append("\n\n");
                ctx.append("Tree:\n").append(tree).append("\n\n");
                if (readme != null) ctx.append("README (first 2000 chars):\n").append(head(readme, 2000)).append("\n\n");
                if (cargo != null) ctx.append("Cargo.toml:\n").append(head(cargo, 2000)).append("\n\n");
                if (pkg != null) ctx.append("package.json:\n").append(head(pkg, 2000)).append("\n\n");
                if (goMod != null) ctx.append("go.mod:\n").append(head(goMod, 1000)).append("\n\n");
                if (pyproj != null) ctx.append("pyproject.toml:\n").append(head(pyproj, 2000)).append("\n\n");

                log("-> calling AI with BuilderExpert prompt");

                String builderModel = prefs.getBuilderModel();
                AIClient.sendMessagesWithModel(prefs, builderModel,
                    BuilderExpert.buildMessages(ctx.toString()),
                    new AIClient.RawCb() {
                        @Override public void onText(String text) {
                            handleAnalysis(text, on[0], on[1]);
                        }
                        @Override public void onError(String err) {
                            log("AI err: " + err);
                            status("AI failed");
                        }
                    });
            } catch (Exception e) {
                log("EX: " + e.getMessage());
                status("Failed");
            }
        }).start();
    }

    private void handleAnalysis(String text, String owner, String name) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("null")
            || t.equals("{}") || t.equals("undefined")) {
            log("weak model returned nothing. retrying with a strong model…");
            retryWithStrongModel(owner, name);
            return;
        }
        log("AI reply preview:");
        log(head(t.replace("\n", " "), 300));
        try {
            String json = extractJson(text);
            org.json.JSONObject o = new org.json.JSONObject(json);
            boolean buildable = o.optBoolean("buildable", false);
            String lang = o.optString("language", "?");
            String outType = o.optString("output_type", "?");
            String yaml = o.optString("workflow_yaml", "");
            double conf = o.optDouble("confidence", 0);

            log("language=" + lang + "  output=" + outType + "  confidence=" + conf);
            log("buildable=" + buildable);

            if (o.has("reasons")) {
                org.json.JSONArray rs = o.optJSONArray("reasons");
                if (rs != null) for (int i = 0; i < rs.length(); i++) log("  reason: " + rs.optString(i));
            }
            if (o.has("warnings")) {
                org.json.JSONArray ws = o.optJSONArray("warnings");
                if (ws != null) for (int i = 0; i < ws.length(); i++) log("  warn: " + ws.optString(i));
            }

            if (!buildable) {
                status("Not buildable");
                return;
            }
            if (yaml.isEmpty()) {
                status("No workflow returned");
                return;
            }

            lastWorkflowYaml = yaml;
            lastArtifactGlob = o.optString("artifact_glob", "");
            lastOutputType = outType;
            status("Ready to build  ·  " + lang + "  ·  " + outType);
            log("OK: workflow generated (" + yaml.length() + " chars)");
            setRunning(false);
            log("-> tap Connect then Analyze again? no - now next batch will build");
        } catch (Exception e) {
            log("Parse failed: " + e.getMessage());
            status("AI output not JSON");
        }
    }

    private boolean retriedStrong = false;

    private void retryWithStrongModel(String owner, String name) {
        if (retriedStrong) {
            status("AI failed on both models");
            log("ERR: fallback also failed");
            return;
        }
        retriedStrong = true;
        String strong = prefs.getBuilderFallback();
        if (strong == null || strong.isEmpty()) strong = "openai/gpt-4o-mini";
        if (strong.equals(prefs.getBuilderModel())) {
            log("fallback is same as primary. skipping retry.");
            status("AI failed");
            return;
        }
        log("-> retry with fallback " + strong);
        try {
            String tree = fetchTree(owner, name);
            String readme = fetchFile(owner, name, "README.md");
            String cargo = fetchFile(owner, name, "Cargo.toml");
            StringBuilder ctx = new StringBuilder();
            ctx.append("Repo: ").append(owner).append("/").append(name).append("\n\n");
            ctx.append("Tree:\n").append(tree).append("\n\n");
            if (readme != null) ctx.append("README:\n").append(head(readme, 2000)).append("\n\n");
            if (cargo != null) ctx.append("Cargo.toml:\n").append(head(cargo, 2000)).append("\n\n");

            AIClient.sendMessagesWithModel(prefs, strong,
                BuilderExpert.buildMessages(ctx.toString()),
                new AIClient.RawCb() {
                    @Override public void onText(String txt) {
                        handleAnalysisFinal(txt, owner, name);
                    }
                    @Override public void onError(String err) {
                        log("retry err: " + err);
                        status("AI failed");
                    }
                });
        } catch (Exception e) {
            log("retry ex: " + e.getMessage());
        }
    }

    private void handleAnalysisFinal(String text, String owner, String name) {
        // Same as handleAnalysis but no more retries
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("null")) {
            status("AI failed twice");
            return;
        }
        handleAnalysisNoRetry(t, owner, name);
    }

    private void handleAnalysisNoRetry(String text, String owner, String name) {
        log("AI reply preview:");
        log(head(text.replace("\n", " "), 300));
        try {
            String json = extractJson(text);
            org.json.JSONObject o = new org.json.JSONObject(json);
            boolean buildable = o.optBoolean("buildable", false);
            String lang = o.optString("language", "?");
            String outType = o.optString("output_type", "?");
            String yaml = o.optString("workflow_yaml", "");
            double conf = o.optDouble("confidence", 0);

            log("language=" + lang + "  output=" + outType + "  confidence=" + conf);
            log("buildable=" + buildable);

            if (o.has("reasons")) {
                org.json.JSONArray rs = o.optJSONArray("reasons");
                if (rs != null) for (int i = 0; i < rs.length(); i++) log("  reason: " + rs.optString(i));
            }
            if (o.has("warnings")) {
                org.json.JSONArray ws = o.optJSONArray("warnings");
                if (ws != null) for (int i = 0; i < ws.length(); i++) log("  warn: " + ws.optString(i));
            }

            if (!buildable) { status("Not buildable"); return; }
            if (yaml.isEmpty()) { status("No workflow returned"); return; }

            lastWorkflowYaml = yaml;
            lastArtifactGlob = o.optString("artifact_glob", "");
            lastOutputType = outType;
            status("Ready to build  ·  " + lang + "  ·  " + outType);
            log("OK: workflow generated (" + yaml.length() + " chars)");
        } catch (Exception e) {
            log("Parse failed: " + e.getMessage());
            status("AI output not JSON");
        }
    }

    private String fetchTree(String owner, String name) {
        try {
            okhttp3.OkHttpClient c = new okhttp3.OkHttpClient();
            okhttp3.Request r = new okhttp3.Request.Builder()
                .url("https://api.github.com/repos/" + owner + "/" + name + "/git/trees/HEAD?recursive=1")
                .header("Authorization", "Bearer " + prefs.getGitHubToken())
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Star-Builder")
                .build();
            okhttp3.Response resp = c.newCall(r).execute();
            if (!resp.isSuccessful()) return "(tree failed: " + resp.code() + ")";
            org.json.JSONObject o = new org.json.JSONObject(resp.body().string());
            org.json.JSONArray arr = o.optJSONArray("tree");
            StringBuilder sb = new StringBuilder();
            if (arr != null) {
                for (int i = 0; i < Math.min(arr.length(), 80); i++) {
                    org.json.JSONObject e = arr.getJSONObject(i);
                    sb.append("  ").append(e.optString("path")).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) { return "(tree ex: " + e.getMessage() + ")"; }
    }

    private String fetchFile(String owner, String name, String path) {
        try {
            okhttp3.OkHttpClient c = new okhttp3.OkHttpClient();
            okhttp3.Request r = new okhttp3.Request.Builder()
                .url("https://raw.githubusercontent.com/" + owner + "/" + name + "/HEAD/" + path)
                .header("User-Agent", "Star-Builder")
                .build();
            okhttp3.Response resp = c.newCall(r).execute();
            if (!resp.isSuccessful()) return null;
            return resp.body().string();
        } catch (Exception e) { return null; }
    }

    private static String head(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static String extractJson(String s) {
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b <= a) return "{}";
        return s.substring(a, b + 1);
    }

    private String[] parseRepo(String url) {
        try {
            url = url.trim().replaceAll("\\.git$", "");
            if (url.startsWith("https://github.com/")) url = url.substring("https://github.com/".length());
            else if (url.startsWith("github.com/")) url = url.substring("github.com/".length());
            String[] p = url.split("/");
            if (p.length < 2) return null;
            return new String[]{ p[0], p[1] };
        } catch (Exception e) { return null; }
    }

    private void build() {
        if (lastWorkflowYaml == null || lastWorkflowYaml.isEmpty()) {
            toast("Analyze first"); return;
        }
        String sandbox = prefs.getSandboxRepo();
        if (sandbox == null || sandbox.isEmpty()) { toast("No sandbox"); return; }
        String[] parts = sandbox.split("/");
        if (parts.length != 2) { toast("Bad sandbox name"); return; }
        final String sbOwner = parts[0], sbRepo = parts[1];

        String workflowFile = "build-" + lastRepoName + ".yml";
        status("Writing workflow...");
        log("-> PUT .github/workflows/" + workflowFile);

        GitHubActions.putFile(prefs, sbOwner, sbRepo,
            ".github/workflows/" + workflowFile, lastWorkflowYaml,
            "star: workflow for " + lastRepoName,
            new GitHubActions.Cb<String>() {
                @Override public void onOk(String sha) {
                    log("OK: workflow written");
                    triggerDispatch(sbOwner, sbRepo, workflowFile);
                }
                @Override public void onErr(String e) {
                    log("ERR put: " + e);
                    status("Write failed");
                }
            });
    }

    private void triggerDispatch(final String sbOwner, final String sbRepo, String workflowFile) {
        status("Triggering...");
        log("-> POST dispatch");
        try {
            org.json.JSONObject inputs = new org.json.JSONObject();
            inputs.put("repo_url", "https://github.com/" + lastRepoOwner + "/" + lastRepoName);
            inputs.put("artifact_glob", lastArtifactGlob);
            inputs.put("output_type", lastOutputType);
            GitHubActions.dispatchWorkflow(prefs, sbOwner, sbRepo,
                workflowFile, inputs, new GitHubActions.Cb<Void>() {
                    @Override public void onOk(Void v) {
                        log("OK: dispatched");
                        status("Running...");
                        pollRun(sbOwner, sbRepo, 0);
                    }
                    @Override public void onErr(String e) {
                        log("ERR dispatch: " + e);
                        status("Trigger failed");
                    }
                });
        } catch (Exception e) {
            log("EX dispatch: " + e.getMessage());
        }
    }

    private void pollRun(final String owner, final String repo, final int attempt) {
        if (attempt > 90) { status("Poll timeout"); return; }
        handler.postDelayed(() -> {
            GitHubActions.listRuns(prefs, owner, repo,
                new GitHubActions.Cb<java.util.List<GitHubActions.RunInfo>>() {
                    @Override public void onOk(java.util.List<GitHubActions.RunInfo> runs) {
                        if (runs.isEmpty()) { pollRun(owner, repo, attempt + 1); return; }
                        GitHubActions.RunInfo r0 = runs.get(0);
                        String line = "run #" + r0.id + "  " + r0.status +
                            (r0.conclusion == null ? "" : "  " + r0.conclusion);
                        status("Build " + r0.status);
                        // only log on change
                        if (!line.equals(lastRunLine)) {
                            lastRunLine = line;
                            log(line);
                        }
                        if ("completed".equals(r0.status)) {
                            if ("success".equals(r0.conclusion)) {
                                log("SUCCESS. fetching artifacts");
                                fetchArtifacts(owner, repo, r0.id);
                            } else {
                                log("FAILED: " + r0.conclusion + "  " + r0.htmlUrl);
                                status("Build " + r0.conclusion);
                            }
                            return;
                        }
                        pollRun(owner, repo, attempt + 1);
                    }
                    @Override public void onErr(String e) {
                        log("poll err: " + e);
                        pollRun(owner, repo, attempt + 1);
                    }
                });
        }, 8000L);
    }

    private String lastRunLine = "";

    private void fetchArtifacts(final String owner, final String repo, final long runId) {
        GitHubActions.listArtifacts(prefs, owner, repo, runId,
            new GitHubActions.Cb<java.util.List<GitHubActions.Artifact>>() {
                @Override public void onOk(java.util.List<GitHubActions.Artifact> arts) {
                    if (arts.isEmpty()) { log("no artifacts"); status("Done"); return; }
                    GitHubActions.Artifact a = arts.get(0);
                    String dest = "/sdcard/Star/built/" + a.name + ".zip";
                    Shell.run("mkdir -p /sdcard/Star/built");
                    log("-> downloading " + a.name + " (" + (a.size / 1024) + " KB)");
                    GitHubActions.downloadArtifact(prefs, owner, repo, a.id, dest,
                        new GitHubActions.Cb<String>() {
                            @Override public void onOk(String path) {
                                log("OK saved: " + path);
                                status("Done: " + path);
                                refreshUsage();
                            }
                            @Override public void onErr(String e) {
                                log("dl err: " + e);
                                status("Download failed");
                            }
                        });
                }
                @Override public void onErr(String e) {
                    log("artifact err: " + e);
                }
            });
    }

    private void refreshFallbackPill() {
        String m0 = prefs.getBuilderFallback();
        if (m0 == null || m0.isEmpty()) m0 = "openai/gpt-4o-mini";
        final String m = m0;
        handler.post(() -> {
            if (fallbackPill != null) fallbackPill.setText("Fallback: " + m);
        });
    }

    private void stop() {
        stopRequested = true;
        running = false;
        log("STOP requested");
        status("Stopped");
        handler.post(() -> {
            if (stopBtn != null) stopBtn.setVisibility(View.GONE);
        });
    }

    private void setRunning(boolean r) {
        running = r;
        handler.post(() -> {
            if (stopBtn != null) stopBtn.setVisibility(r ? View.VISIBLE : View.GONE);
        });
    }

    private void refreshUsage() {
        String sandbox = prefs.getSandboxRepo();

        // 1. try real billing first (needs billing token)
        String billing = prefs.getBillingToken();
        if (billing != null && !billing.isEmpty()) {
            GitHubActions.fetchBilling(prefs, new GitHubActions.Cb<GitHubActions.BillingInfo>() {
                @Override public void onOk(GitHubActions.BillingInfo bi) {
                    int included = bi.includedMinutes > 0 ? bi.includedMinutes : 2000;
                    int used = bi.totalMinutesUsed;
                    int left = Math.max(0, included - used);
                    String line = "Actions: " + used + " min used  ·  " + left +
                        " min left of " + included + " (live billing)";
                    if (bi.paidMinutesUsed > 0) line += "  ·  paid " + bi.paidMinutesUsed + " min";
                    final String f = line;
                    handler.post(() -> usageTv.setText(f));
                }
                @Override public void onErr(String e) {
                    // silently fall back to local sum
                    usageFromLocalSum(sandbox);
                }
            });
            return;
        }

        // 2. fall back to summing our own sandbox runs
        usageFromLocalSum(sandbox);
    }

    private void usageFromLocalSum(String sandbox) {
        if (sandbox == null || sandbox.isEmpty()) {
            handler.post(() -> usageTv.setText("Actions usage: no billing token set"));
            return;
        }
        String[] parts = sandbox.split("/");
        if (parts.length != 2) return;
        GitHubActions.monthlyUsage(prefs, parts[0], parts[1],
            new GitHubActions.Cb<Long>() {
                @Override public void onOk(Long ms) {
                    long sec = ms / 1000L;
                    long min = sec / 60;
                    long rem = sec % 60;
                    long limit = 2000;
                    String line = "Actions: " + min + "m " + rem +
                        "s in sandbox this month  ·  " + (limit - min) +
                        " min left of " + limit + " (sandbox only)";
                    if (limit - min < 0) line = "Actions: " + min + "m " + rem +
                        "s in sandbox  ·  OVER by " + Math.abs(limit - min) + " min";
                    final String f = line;
                    handler.post(() -> usageTv.setText(f));
                }
                @Override public void onErr(String e) {
                    handler.post(() -> usageTv.setText("Actions usage: unavailable"));
                }
            });
    }

    private void refreshModelPill() {
        String m0 = prefs.getBuilderModel();
        if (m0 == null || m0.isEmpty()) m0 = prefs.getModel();
        if (m0 == null || m0.isEmpty()) m0 = "(tap to pick)";
        final String m = m0;
        handler.post(() -> {
            if (modelPill != null) modelPill.setText("Model: " + m);
        });
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
