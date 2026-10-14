package com.star.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.star.app.candy.*;
import java.util.List;

public class CandyActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1A1A1A;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int GREEN = 0xFF7EE787;
    private static final int ORANGE = 0xFFF0883E;
    private static final int BLUE = 0xFF7CB3FF;
    private static final int RED = 0xFFF85149;
    private static final int DIALOG_BG = 0xFF161616;

    private LinearLayout listCol;
    private TextView status;

    private static class OutputDialog {
        android.app.AlertDialog dialog;
        TextView title;
        TextView body;
        ScrollView scroll;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { buildUI(); }
        catch (Throwable t) {
            Toast.makeText(this, "Candy error: " + t, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(20), dp(16), dp(12));

        TextView back = new TextView(this);
        back.setText("<-");
        back.setTextSize(22);
        back.setTextColor(TEXT);
        back.setPadding(dp(6), dp(6), dp(14), dp(6));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        top.addView(back);

        TextView title = new TextView(this);
        title.setText("Candy Skills");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);
        root.addView(top);

        // Description banner
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dp(12));
        ibg.setColor(0xFF0F1A26);
        ibg.setStroke(dp(1), 0xFF2A4A6A);
        info.setBackground(ibg);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(dp(16), 0, dp(16), dp(12));
        info.setLayoutParams(ilp);

        TextView infoTitle = new TextView(this);
        infoTitle.setText("Build CLI tools from GitHub");
        infoTitle.setTextSize(13);
        infoTitle.setTypeface(Typeface.DEFAULT_BOLD);
        infoTitle.setTextColor(BLUE);
        info.addView(infoTitle);

        TextView infoBody = new TextView(this);
        infoBody.setText("Candy builds on your tablet (fast, RAM limited). " +
            "Builder builds on GitHub servers (slow, no RAM limit). " +
            "Both end with a runnable skill in this list.");
        infoBody.setTextSize(11);
        infoBody.setTextColor(MUTED);
        infoBody.setPadding(0, dp(4), 0, 0);
        infoBody.setLineSpacing(dp(2), 1f);
        info.addView(infoBody);
        root.addView(info);

        TextView addBtn = btn("+ Add repo from GitHub", BLUE, this::addRepo);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(dp(16), 0, dp(16), dp(8));
        addBtn.setLayoutParams(alp);
        root.addView(addBtn);

        TextView pkgBtn = btn("+ Install Termux package", GREEN, this::addPkg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.setMargins(dp(16), 0, dp(16), dp(8));
        pkgBtn.setLayoutParams(plp);
        root.addView(pkgBtn);

        TextView checkBtn = btn("Check for updates", ORANGE, this::checkUpdates);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(16), 0, dp(16), dp(12));
        checkBtn.setLayoutParams(clp);
        root.addView(checkBtn);

        status = new TextView(this);
        status.setTextSize(11);
        status.setTextColor(MUTED);
        status.setPadding(dp(20), 0, dp(20), dp(8));
        root.addView(status);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(dp(16), dp(4), dp(16), dp(20));
        sv.addView(listCol);
        root.addView(sv);

        setContentView(root);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        try { refresh(); } catch (Throwable ignored) {}
        try { CandyManager.prefetchBinaries(); } catch (Throwable ignored) {}
    }

    private void refresh() {
        try {
            listCol.removeAllViews();
            List<Candy> installed = CandyManager.listInstalled();
            status.setText(installed.size() + " skills installed");

            if (installed.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("No skills yet.\n\nTap '+ Install Termux package' for a quick tool (poppler, jq, ffmpeg).\n\nOr tap '+ Add repo from GitHub' to build from source.");
                empty.setTextSize(13);
                empty.setTextColor(MUTED);
                empty.setPadding(dp(16), dp(30), dp(16), dp(16));
                listCol.addView(empty);
                return;
            }
            for (Candy c : installed) listCol.addView(card(c));
        } catch (Throwable t) {
            Toast.makeText(this, "Refresh error: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private View card(Candy c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(c.name == null ? "?" : c.name);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(TEXT);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row1.addView(name);

        TextView tag = new TextView(this);
        String lang = c.language == null ? "?" : c.language.toUpperCase();
        tag.setText(lang);
        tag.setTextSize(10);
        tag.setTextColor(c.isPkg() ? GREEN : BLUE);
        tag.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable tbg = new GradientDrawable();
        tbg.setCornerRadius(dp(8));
        tbg.setColor(c.isPkg() ? 0x1A7EE787 : 0x1A7CB3FF);
        tag.setBackground(tbg);
        row1.addView(tag);
        card.addView(row1);

        TextView repo = new TextView(this);
        repo.setText(c.repo == null ? "" : c.repo);
        repo.setTextSize(10);
        repo.setTextColor(MUTED);
        repo.setPadding(0, dp(4), 0, dp(8));
        card.addView(repo);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        TextView testBtn = smallBtn("Run", GREEN, () -> runSkill(c));
        btns.addView(testBtn);

        if ("rife".equals(c.name)) {
            TextView interpBtn = smallBtn("Interpolate", BLUE, () -> runInterpolate(c));
            btns.addView(interpBtn);
        }

        TextView logBtn = smallBtn("Log", BLUE, () -> showBuildLog(c));
        btns.addView(logBtn);

        TextView removeBtn = smallBtn("Remove", RED, () -> {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Remove " + c.name + "?")
                .setPositiveButton("Remove", (d, w) -> {
                    List<Candy> list = CandyManager.listInstalled();
                    list.removeIf(x -> x.name != null && x.name.equals(c.name));
                    CandyManager.saveRegistry(list);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        btns.addView(removeBtn);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        btns.setLayoutParams(blp);
        card.addView(btns);

        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp2.bottomMargin = dp(8);
        card.setLayoutParams(clp2);
        return card;
    }

    /* ============== RUN ============== */

    private void runSkill(Candy c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView hint = new TextView(this);
        hint.setText("Arguments, space separated.\nExample: -layout input.pdf output.txt");
        hint.setTextSize(11);
        hint.setTextColor(MUTED);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText argInput = new EditText(this);
        argInput.setTextColor(TEXT);
        argInput.setHintTextColor(MUTED);
        argInput.setHint("--help");
        argInput.setText("--help");
        box.addView(argInput);

        new android.app.AlertDialog.Builder(this)
            .setTitle("Run " + c.name)
            .setView(box)
            .setPositiveButton("Run", (d, w) -> {
                String raw = argInput.getText().toString().trim();
                String[] args = raw.isEmpty() ? new String[0] : raw.split("\\s+");
                executeRun(c, args);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void executeRun(Candy c, final String[] args) {
        if (c.isPkg()) {
            final String cmd = (c.installAs == null || c.installAs.isEmpty()) ? c.pkg : c.installAs;
            Toast.makeText(this, "Running " + cmd + "...", Toast.LENGTH_SHORT).show();
            CandyManager.runSkill(cmd, args, new CandyManager.RunCallback() {
                @Override public void onResult(String stdout, String stderr, int exit) {
                    runOnUiThread(() -> {
                        String body = "";
                        if (stdout != null && !stdout.isEmpty()) body += stdout;
                        if (stderr != null && !stderr.isEmpty()) {
                            if (!body.isEmpty()) body += "\n\n[stderr]\n";
                            body += stderr;
                        }
                        if (body.isEmpty()) body = "(no output)";
                        showOutputDialog(cmd + "  (exit " + exit + ")", body);
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> showOutputDialog(cmd + " - error", msg));
                }
            });
        } else {
            String target = "/data/local/tmp/star/bin/" + c.name;
            StringBuilder cl = new StringBuilder(
                "test -x " + target + " || { mkdir -p /data/local/tmp/star/bin; " +
                "cp /sdcard/Star/candy/bin/" + c.name + " " + target + " 2>/dev/null; " +
                "chmod 755 " + target + " 2>/dev/null; }; ");
            cl.append(target);
            for (String a : args) cl.append(" ").append(a);
            Shell.Result r = Shell.run(cl + " 2>&1 | head -200");
            String out = (r.output == null ? "(no output)" : r.output);
            showOutputDialog(c.name, out);
        }
    }

    /* ============== OUTPUT DIALOG (with Copy) ============== */

    private OutputDialog showOutputDialog(String title, String body) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(DIALOG_BG);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(16), dp(14), dp(8), dp(8));

        final TextView tvTitle = new TextView(this);
        tvTitle.setText(title == null ? "" : title);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(TEXT);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tvTitle);

        TextView copyBtn = new TextView(this);
        copyBtn.setText("Copy");
        copyBtn.setTextSize(13);
        copyBtn.setTypeface(Typeface.DEFAULT_BOLD);
        copyBtn.setTextColor(BLUE);
        copyBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        copyBtn.setClickable(true);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setCornerRadius(dp(8));
        cbg.setColor(0x1A7CB3FF);
        copyBtn.setBackground(cbg);
        titleRow.addView(copyBtn);
        root.addView(titleRow);

        final ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svlp.setMargins(dp(8), 0, dp(8), 0);
        sv.setLayoutParams(svlp);

        final TextView tvBody = new TextView(this);
        tvBody.setText(body == null ? "" : body);
        tvBody.setTextSize(12);
        tvBody.setTypeface(Typeface.MONOSPACE);
        tvBody.setTextColor(TEXT);
        tvBody.setTextIsSelectable(true);
        tvBody.setPadding(dp(12), dp(8), dp(12), dp(16));
        sv.addView(tvBody);
        root.addView(sv);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("Close");
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setTextColor(TEXT);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        closeBtn.setClickable(true);
        GradientDrawable clbg = new GradientDrawable();
        clbg.setCornerRadius(dp(10));
        clbg.setColor(0xFF0F1A26);
        clbg.setStroke(dp(1), 0xFF2A4A6A);
        closeBtn.setBackground(clbg);
        LinearLayout.LayoutParams cllp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cllp.setMargins(dp(16), dp(4), dp(16), dp(12));
        closeBtn.setLayoutParams(cllp);
        root.addView(closeBtn);

        final OutputDialog out = new OutputDialog();
        out.title = tvTitle;
        out.body = tvBody;
        out.scroll = sv;
        out.dialog = new android.app.AlertDialog.Builder(this)
            .setView(root)
            .create();

        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("star-output", tvBody.getText()));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
        closeBtn.setOnClickListener(v -> out.dialog.dismiss());

        out.dialog.show();
        if (out.dialog.getWindow() != null) {
            out.dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return out;
    }

    /* ============== ADD REPO ============== */

    private void addRepo() {
        EditText input = new EditText(this);
        input.setHint("https://github.com/user/repo");
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        new android.app.AlertDialog.Builder(this)
            .setTitle("Add repo from GitHub")
            .setView(input)
            .setPositiveButton("Build", (d, w) -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) return;
                if (!url.startsWith("https://github.com/")) {
                    Toast.makeText(this, "Must be a github.com URL", Toast.LENGTH_LONG).show();
                    return;
                }
                startBuild(url);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startBuild(String url) {
        Candy c = new Candy();
        c.name = url.substring(url.lastIndexOf('/') + 1).replace(".git", "");
        c.repo = url;
        c.language = "?";
        c.buildSteps = new String[0];
        c.deps = new String[0];
        c.output = "";
        c.installAs = c.name;
        c.commit = "";
        c.installedAt = "";
        c.status = "installing";
        c.pkg = "";

        final OutputDialog dlg = showOutputDialog("Building " + c.name, "");
        dlg.body.append("-> Sending build request to Termux\n");
        dlg.body.append("-> Repo: " + url + "\n\n");

        CandyManager.install(c, new CandyManager.Progress() {
            @Override public void onLine(String line) {
                runOnUiThread(() -> {
                    dlg.body.append(line);
                    dlg.scroll.post(() -> dlg.scroll.fullScroll(View.FOCUS_DOWN));
                });
            }
            @Override public void onDone(boolean ok, String msg) {
                runOnUiThread(() -> {
                    dlg.body.append("\n" + msg + "\n");
                    dlg.title.setText(ok ? "Build successful" : "Build failed");
                    refresh();
                });
            }
        });
    }

    /* ============== ADD PKG ============== */

    private void addPkg() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView hint = new TextView(this);
        hint.setText("Termux package name. Example: poppler, jq, ffmpeg, imagemagick, yt-dlp.\nDebian names like poppler-utils are mapped automatically.");
        hint.setTextSize(11);
        hint.setTextColor(MUTED);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText pkgInput = new EditText(this);
        pkgInput.setHint("package name (poppler-utils)");
        pkgInput.setTextColor(TEXT);
        pkgInput.setHintTextColor(MUTED);
        box.addView(pkgInput);

        EditText asInput = new EditText(this);
        asInput.setHint("skill name / main binary (optional)");
        asInput.setTextColor(TEXT);
        asInput.setHintTextColor(MUTED);
        box.addView(asInput);

        new android.app.AlertDialog.Builder(this)
            .setTitle("Install Termux package")
            .setView(box)
            .setPositiveButton("Install", (d, w) -> {
                String pkgRaw = pkgInput.getText().toString().trim();
                String as = asInput.getText().toString().trim();
                if (pkgRaw.isEmpty()) return;
                String mapped = CandyManager.mapPkgName(pkgRaw);
                if (as.isEmpty()) as = mapped;
                startPkgInstall(pkgRaw, mapped, as);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startPkgInstall(String pkgRaw, String pkgMapped, String as) {
        final OutputDialog dlg = showOutputDialog("Installing " + pkgMapped, "");
        dlg.body.append("Package: " + pkgRaw + "\n");
        if (!pkgRaw.equals(pkgMapped)) dlg.body.append("Mapped to: " + pkgMapped + "\n");
        dlg.body.append("Skill name: " + as + "\n\n");

        CandyManager.installPkg(pkgMapped, as, new CandyManager.Progress() {
            @Override public void onLine(String line) {
                runOnUiThread(() -> {
                    dlg.body.append(line);
                    dlg.scroll.post(() -> dlg.scroll.fullScroll(View.FOCUS_DOWN));
                });
            }
            @Override public void onDone(boolean ok, String msg) {
                runOnUiThread(() -> {
                    dlg.body.append("\n" + msg + "\n");
                    dlg.title.setText(ok ? "Installed" : "Failed");
                    refresh();
                });
            }
        });
    }

    private void showBuildLog(Candy c) {
        String logPath = "/sdcard/Star/candy/build.log";
        if (c.isPkg()) logPath = "/sdcard/Star/candy/pkg.log";
        Shell.Result r = Shell.run("tail -400 " + logPath + " 2>/dev/null");
        String body = (r.output == null || r.output.trim().isEmpty())
            ? "(log empty or missing: " + logPath + ")"
            : r.output;
        showOutputDialog("Log: " + c.name, body);
    }

    private void checkUpdates() {
        Toast.makeText(this, "Checking...", Toast.LENGTH_SHORT).show();
    }

    private TextView btn(String text, int color, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xFF0F1A26);
        bg.setStroke(dp(1), 0xFF2A4A6A);
        t.setBackground(bg);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private TextView smallBtn(String text, int color, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF0F1A26);
        bg.setStroke(dp(1), 0xFF2A4A6A);
        t.setBackground(bg);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private void runInterpolate(Candy c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView hint = new TextView(this);
        hint.setText("Enter the path to a video or a folder of numbered frames.");
        hint.setTextSize(11);
        hint.setTextColor(MUTED);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText input = new EditText(this);
        input.setHint("/sdcard/Star/video/input.mp4");
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        box.addView(input);

        new android.app.AlertDialog.Builder(this)
            .setTitle("Interpolate with RIFE")
            .setView(box)
            .setPositiveButton("Run", (d, w) -> {
                String path = input.getText().toString().trim();
                if (path.isEmpty()) return;
                doInterpolate(path);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void doInterpolate(String inputPath) {
        final OutputDialog dlg = showOutputDialog("RIFE Interpolate", "");
        dlg.body.append("Input: " + inputPath + "\n\n");

        new Thread(() -> {
            String mode = inputPath.toLowerCase().endsWith(".mp4")
                || inputPath.toLowerCase().endsWith(".mov")
                || inputPath.toLowerCase().endsWith(".mkv")
                ? "video" : "frames";

            String req = "{\"input\":\"" + inputPath.replace("\\","\\\\").replace("\"","\\\"")
                + "\",\"mode\":\"" + mode + "\"}";
            Shell.run("rm -f /sdcard/Star/candy/interpolate-result.json /sdcard/Star/candy/interpolate.log");
            Shell.run("printf '%s' '" + req.replace("'","'\\''") + "' > /sdcard/Star/candy/interpolate-request.json");

            long deadline = System.currentTimeMillis() + 20 * 60 * 1000L;
            long lastSize = 0;
            while (System.currentTimeMillis() < deadline) {
                Shell.Result log = Shell.run("cat /sdcard/Star/candy/interpolate.log 2>/dev/null");
                if (log.output != null) {
                    String full = log.output;
                    if (full.length() > lastSize) {
                        final String chunk = full.substring((int)lastSize);
                        lastSize = full.length();
                        runOnUiThread(() -> {
                            dlg.body.append(chunk);
                            dlg.scroll.post(() -> dlg.scroll.fullScroll(View.FOCUS_DOWN));
                        });
                    }
                }
                Shell.Result res = Shell.run("test -f /sdcard/Star/candy/interpolate-result.json && cat /sdcard/Star/candy/interpolate-result.json");
                if (res.output != null && !res.output.trim().isEmpty()) {
                    final String out = res.output.trim();
                    runOnUiThread(() -> {
                        try {
                            org.json.JSONObject o = new org.json.JSONObject(out);
                            if ("ok".equals(o.optString("status"))) {
                                dlg.body.append("\n\nDone. Output: " + o.optString("output")
                                    + "  (" + o.optInt("frames") + " frames @ " + o.optInt("fps") + "fps)\n");
                                dlg.title.setText("Done");
                            } else {
                                dlg.body.append("\n\nFailed: " + o.optString("error", "unknown") + "\n");
                                dlg.title.setText("Failed");
                            }
                        } catch (Exception e) {
                            dlg.body.append("\n\nraw: " + out + "\n");
                        }
                        dlg.scroll.post(() -> dlg.scroll.fullScroll(View.FOCUS_DOWN));
                    });
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
            }
            runOnUiThread(() -> {
                dlg.body.append("\n\nTimed out (20 min).\n");
                dlg.title.setText("Timeout");
            });
        }).start();
    }


    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
