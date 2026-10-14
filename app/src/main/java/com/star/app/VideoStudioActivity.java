package com.star.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoStudioActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int GREEN = 0xFF7EE787;
    private static final int ORANGE = 0xFFF0883E;
    private static final int RED = 0xFFF85149;

    private Prefs prefs;
    private EditText promptField;
    private TextView modeTv, durTv, aspectTv, statusTv, generateBtn;
    private LinearLayout logCol, recentCol;
    private ScrollView logScroll;
    private VideoView player;
    private TextView playerLabel;
    private String lastOutput = null;

    private String mode = "single";
    private int duration = 5;
    private String aspect = "16:9";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        buildUI();
        refreshRecent();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Top bar
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
        title.setText("Video Studio");
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

        // Prompt
        TextView promptLabel = tiny("PROMPT");
        content.addView(promptLabel);

        promptField = new EditText(this);
        promptField.setHint("a cat walking on a beach at sunset, cinematic");
        promptField.setTextColor(TEXT);
        promptField.setHintTextColor(MUTED);
        promptField.setBackground(rounded(CARD, BORDER, 12));
        promptField.setPadding(dp(12), dp(12), dp(12), dp(12));
        promptField.setMinLines(2);
        promptField.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(4);
        plp.bottomMargin = dp(14);
        promptField.setLayoutParams(plp);
        content.addView(promptField);

        // Mode
        content.addView(tiny("MODE"));
        modeTv = rowLine("Single image with motion (Ken Burns)");
        modeTv.setOnClickListener(v -> pickMode());
        content.addView(modeTv);

        // Duration
        content.addView(tiny("DURATION"));
        durTv = rowLine("5 seconds");
        durTv.setOnClickListener(v -> pickDuration());
        content.addView(durTv);

        // Aspect
        content.addView(tiny("ASPECT"));
        aspectTv = rowLine("16:9 landscape");
        aspectTv.setOnClickListener(v -> pickAspect());
        content.addView(aspectTv);

        // Generate button
        generateBtn = new TextView(this);
        generateBtn.setText("Generate video");
        generateBtn.setTextSize(15);
        generateBtn.setTypeface(Typeface.DEFAULT_BOLD);
        generateBtn.setTextColor(GREEN);
        generateBtn.setGravity(Gravity.CENTER);
        generateBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable gb = new GradientDrawable();
        gb.setCornerRadius(dp(14));
        gb.setColor(0x1A7EE787);
        gb.setStroke(dp(1), GREEN);
        generateBtn.setBackground(gb);
        generateBtn.setClickable(true);
        generateBtn.setOnClickListener(v -> generate());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(16);
        generateBtn.setLayoutParams(glp);
        content.addView(generateBtn);

        // Status
        statusTv = new TextView(this);
        statusTv.setText("");
        statusTv.setTextSize(11);
        statusTv.setTypeface(Typeface.MONOSPACE);
        statusTv.setTextColor(MUTED);
        statusTv.setPadding(0, dp(8), 0, dp(8));
        content.addView(statusTv);

        // Log
        logScroll = new ScrollView(this);
        LinearLayout.LayoutParams lslp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        lslp.bottomMargin = dp(14);
        logScroll.setLayoutParams(lslp);
        logScroll.setBackground(rounded(0xFF141414, 0xFF232323, 10));
        logCol = new LinearLayout(this);
        logCol.setOrientation(LinearLayout.VERTICAL);
        logCol.setPadding(dp(12), dp(10), dp(12), dp(10));
        logScroll.addView(logCol);
        logScroll.setVisibility(View.GONE);
        content.addView(logScroll);

        // Player
        playerLabel = tiny("PREVIEW");
        playerLabel.setVisibility(View.GONE);
        content.addView(playerLabel);

        player = new VideoView(this);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        vlp.topMargin = dp(4);
        vlp.bottomMargin = dp(10);
        player.setLayoutParams(vlp);
        player.setBackgroundColor(0xFF000000);
        player.setVisibility(View.GONE);
        content.addView(player);

        // Save/Share buttons
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(6);
        alp.bottomMargin = dp(14);
        actions.setLayoutParams(alp);
        actions.setVisibility(View.GONE);

        TextView saveBtn = smallBtn("Save to Gallery", ACCENT);
        saveBtn.setOnClickListener(v -> saveToGallery());
        actions.addView(saveBtn);

        TextView shareBtn = smallBtn("Share", GREEN);
        shareBtn.setOnClickListener(v -> shareVideo());
        actions.addView(shareBtn);

        // we need a ref to hide/show
        final LinearLayout actionsRef = actions;
        this.actionsBar = actionsRef;
        content.addView(actions);

        // Recent
        content.addView(tiny("RECENT"));
        recentCol = new LinearLayout(this);
        recentCol.setOrientation(LinearLayout.VERTICAL);
        content.addView(recentCol);

        root.addView(mainScroll);
        setContentView(root);
    }

    private LinearLayout actionsBar;

    private TextView tiny(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(10);
        t.setTextColor(MUTED);
        t.setLetterSpacing(0.15f);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView rowLine(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(TEXT);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(rounded(CARD, BORDER, 10));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView smallBtn(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0x12FFFFFF);
        bg.setStroke(dp(1), color);
        t.setBackground(bg);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private GradientDrawable rounded(int fill, int border, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(r));
        g.setColor(fill);
        g.setStroke(dp(1), border);
        return g;
    }

    // ===================== pickers =====================

    private void pickMode() {
        String[] opts = { "Single image with motion (Ken Burns)", "Slideshow (4 images + RIFE smoothing)" };
        new android.app.AlertDialog.Builder(this)
            .setTitle("Mode")
            .setItems(opts, (d, w) -> {
                mode = (w == 0) ? "single" : "slideshow";
                modeTv.setText(opts[w]);
            })
            .show();
    }

    private void pickDuration() {
        String[] opts = { "3 seconds", "5 seconds", "10 seconds" };
        final int[] vals = { 3, 5, 10 };
        new android.app.AlertDialog.Builder(this)
            .setTitle("Duration")
            .setItems(opts, (d, w) -> {
                duration = vals[w];
                durTv.setText(opts[w]);
            })
            .show();
    }

    private void pickAspect() {
        String[] opts = { "16:9 landscape", "9:16 portrait", "1:1 square" };
        new android.app.AlertDialog.Builder(this)
            .setTitle("Aspect ratio")
            .setItems(opts, (d, w) -> {
                aspect = (w == 0) ? "16:9" : (w == 1) ? "9:16" : "1:1";
                aspectTv.setText(opts[w]);
            })
            .show();
    }

    // ===================== generate =====================

    private void generate() {
        String prompt = promptField.getText().toString().trim();
        if (prompt.isEmpty()) {
            toast("Enter a prompt");
            return;
        }
        if (running) {
            toast("Already generating");
            return;
        }
        running = true;
        generateBtn.setText("Generating...");
        generateBtn.setTextColor(ORANGE);
        logCol.removeAllViews();
        logScroll.setVisibility(View.VISIBLE);
        player.setVisibility(View.GONE);
        playerLabel.setVisibility(View.GONE);
        if (actionsBar != null) actionsBar.setVisibility(View.GONE);
        statusTv.setText("Sending request...");

        int w, h;
        if ("16:9".equals(aspect)) { w = 1024; h = 576; }
        else if ("9:16".equals(aspect)) { w = 576; h = 1024; }
        else { w = 768; h = 768; }

        final String reqMode = mode;
        final int rw = w, rh = h;
        final int dur = duration;
        final String p = prompt;
        final int frames = 4;

        new Thread(() -> {
            try {
                org.json.JSONObject req = new org.json.JSONObject();
                req.put("prompt", p);
                req.put("mode", reqMode);
                req.put("duration", dur);
                req.put("width", rw);
                req.put("height", rh);
                req.put("frames", frames);

                Shell.run("rm -f /sdcard/Star/candy/video-result.json /sdcard/Star/candy/video.log");
                String esc = req.toString().replace("'", "'\\''");
                Shell.run("printf '%s' '" + esc + "' > /sdcard/Star/candy/video-request.json");

                long deadline = System.currentTimeMillis() + 15 * 60 * 1000L;
                long lastSize = 0;
                while (System.currentTimeMillis() < deadline) {
                    Shell.Result lg = Shell.run("cat /sdcard/Star/candy/video.log 2>/dev/null");
                    if (lg.output != null) {
                        String full = lg.output;
                        if (full.length() > lastSize) {
                            final String chunk = full.substring((int)lastSize);
                            lastSize = full.length();
                            handler.post(() -> {
                                TextView line = new TextView(this);
                                line.setText(chunk);
                                line.setTextSize(10);
                                line.setTypeface(Typeface.MONOSPACE);
                                line.setTextColor(0xFFB0B0B0);
                                logCol.addView(line);
                                logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
                            });
                        }
                    }
                    Shell.Result res = Shell.run("test -f /sdcard/Star/candy/video-result.json && cat /sdcard/Star/candy/video-result.json");
                    if (res.output != null && !res.output.trim().isEmpty()) {
                        final String out = res.output.trim();
                        handler.post(() -> finishGenerate(out));
                        return;
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                }
                handler.post(() -> {
                    running = false;
                    generateBtn.setText("Generate video");
                    generateBtn.setTextColor(GREEN);
                    statusTv.setText("Timed out");
                });
            } catch (Exception e) {
                handler.post(() -> {
                    running = false;
                    generateBtn.setText("Generate video");
                    generateBtn.setTextColor(GREEN);
                    statusTv.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void finishGenerate(String resultJson) {
        running = false;
        generateBtn.setText("Generate video");
        generateBtn.setTextColor(GREEN);
        try {
            org.json.JSONObject o = new org.json.JSONObject(resultJson);
            if ("ok".equals(o.optString("status"))) {
                lastOutput = o.optString("output");
                statusTv.setText("Done · " + new File(lastOutput).length() / 1024 + " KB");
                refreshRecent();
                playLocal(lastOutput);
            } else {
                statusTv.setText("Failed: " + o.optString("error", "unknown"));
            }
        } catch (Exception e) {
            statusTv.setText("Parse error: " + e.getMessage());
        }
    }

    // ===================== save/share =====================

    private void saveToGallery() {
        if (lastOutput == null) return;
        String name = "star_" + System.currentTimeMillis() + ".mp4";
        String dst = "/sdcard/Movies/Star/" + name;
        Shell.run("mkdir -p /sdcard/Movies/Star");
        Shell.run("cp '" + lastOutput + "' '" + dst + "'");
        Toast.makeText(this, "Saved: " + dst, Toast.LENGTH_LONG).show();
    }

    private void shareVideo() {
        if (lastOutput == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(lastOutput)));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Share video"));
    }

    // ===================== recent =====================

    private void refreshRecent() {
        recentCol.removeAllViews();
        new Thread(() -> {
            Shell.Result r = Shell.run("ls -t /sdcard/Star/candy/video_*.mp4 2>/dev/null | head -10");
            if (r.output == null || r.output.trim().isEmpty()) {
                handler.post(() -> {
                    TextView empty = new TextView(this);
                    empty.setText("No videos yet.");
                    empty.setTextSize(12);
                    empty.setTextColor(MUTED);
                    empty.setPadding(0, dp(8), 0, dp(8));
                    recentCol.addView(empty);
                });
                return;
            }
            final String[] paths = r.output.trim().split("\n");
            handler.post(() -> {
                for (String path : paths) {
                    final String p = path.trim();
                    if (p.isEmpty()) continue;
                    TextView item = new TextView(this);
                    File f = new File(p);
                    item.setText(f.getName() + "  ·  " + (f.length() / 1024) + " KB");
                    item.setTextSize(12);
                    item.setTypeface(Typeface.MONOSPACE);
                    item.setTextColor(TEXT);
                    item.setPadding(dp(12), dp(10), dp(12), dp(10));
                    item.setBackground(rounded(CARD, BORDER, 8));
                    item.setClickable(true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = dp(6);
                    item.setLayoutParams(lp);
                    item.setOnClickListener(v -> {
                        lastOutput = p;
                        playLocal(p);
                        statusTv.setText("Playing: " + new File(p).getName());
                    });
                    recentCol.addView(item);
                }
            });
        }).start();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void playLocal(final String srcPath) {
        new Thread(() -> {
            try {
                File src = new File(srcPath);
                if (!src.exists()) {
                    handler.post(() -> statusTv.setText("File missing: " + srcPath));
                    return;
                }
                // Copy via Shizuku into the app external files dir.
                File extDir = getExternalFilesDir(null);
                if (extDir == null) extDir = getCacheDir();
                if (!extDir.exists()) extDir.mkdirs();
                final File dst = new File(extDir, "preview.mp4");
                if (dst.exists()) dst.delete();

                String cmd = "cp '" + srcPath + "' '" + dst.getAbsolutePath() + "' && " +
                             "chmod 644 '" + dst.getAbsolutePath() + "'";
                Shell.Result r = Shell.run(cmd);
                if (!dst.exists() || dst.length() == 0) {
                    final String err = (r.output == null ? "" : r.output);
                    handler.post(() -> statusTv.setText("Copy failed: " + err));
                    return;
                }
                handler.post(() -> {
                    playerLabel.setVisibility(View.VISIBLE);
                    player.setVisibility(View.VISIBLE);
                    if (actionsBar != null) actionsBar.setVisibility(View.VISIBLE);
                    try {
                        player.stopPlayback();
                        player.setVideoURI(Uri.fromFile(dst));
                        player.setOnPreparedListener(mp -> {
                            mp.setLooping(true);
                            player.start();
                        });
                        player.setOnErrorListener((mp, what, extra) -> {
                            statusTv.setText("Play error " + what + "/" + extra);
                            return true;
                        });
                    } catch (Throwable t) {
                        statusTv.setText("Play ex: " + t.getMessage());
                    }
                });
            } catch (Exception e) {
                handler.post(() -> statusTv.setText("Read fail: " + e.getMessage()));
            }
        }).start();
    }


    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
