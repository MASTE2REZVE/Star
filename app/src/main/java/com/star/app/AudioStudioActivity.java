package com.star.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;

import java.io.File;

public class AudioStudioActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int GREEN = 0xFF7EE787;
    private static final int ORANGE = 0xFFF0883E;
    private static final int RED = 0xFFF85149;
    private static final int PURPLE = 0xFFA78BFA;

    private Prefs prefs;
    private EditText textField;
    private TextView voiceTv, speedTv, statusTv, speakBtn, playBtn, saveBtn;
    private LinearLayout recentCol;
    private MediaPlayer player;

    private int voice = 5;   // am_adam default
    private float speed = 1.0f;
    private String lastOutput = null;

    // Kokoro voice names (sid order)
    private static final String[] VOICES = {
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica",
        "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah",
        "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir",
        "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa",
        "bf_alice", "bf_emma", "bf_isabella", "bf_lily",
        "bm_daniel", "bm_fable", "bm_george", "bm_lewis"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        buildUI();
        refreshRecent();
    }

    @Override protected void onDestroy() {
        try { if (player != null) { player.stop(); player.release(); } } catch (Throwable ignored) {}
        super.onDestroy();
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
        title.setText("Audio Studio");
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

        // ---------- TEXT ----------
        content.addView(tiny("TEXT"));
        textField = new EditText(this);
        textField.setHint("Type anything. Star will speak it.");
        textField.setTextColor(TEXT);
        textField.setHintTextColor(MUTED);
        textField.setBackground(rounded(CARD, BORDER, 12));
        textField.setPadding(dp(12), dp(12), dp(12), dp(12));
        textField.setMinLines(3);
        textField.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(4);
        tlp.bottomMargin = dp(14);
        textField.setLayoutParams(tlp);
        content.addView(textField);

        // ---------- VOICE ----------
        content.addView(tiny("VOICE"));
        voiceTv = rowLine("voice: " + VOICES[voice]);
        voiceTv.setOnClickListener(v -> pickVoice());
        content.addView(voiceTv);

        // ---------- SPEED ----------
        content.addView(tiny("SPEED"));
        speedTv = rowLine("1.0x");
        speedTv.setOnClickListener(v -> pickSpeed());
        content.addView(speedTv);

        // ---------- SPEAK ----------
        speakBtn = new TextView(this);
        speakBtn.setText("Speak");
        speakBtn.setTextSize(15);
        speakBtn.setTypeface(Typeface.DEFAULT_BOLD);
        speakBtn.setTextColor(GREEN);
        speakBtn.setGravity(Gravity.CENTER);
        speakBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable sb = new GradientDrawable();
        sb.setCornerRadius(dp(14));
        sb.setColor(0x1A7EE787);
        sb.setStroke(dp(1), GREEN);
        speakBtn.setBackground(sb);
        speakBtn.setClickable(true);
        speakBtn.setOnClickListener(v -> speak());
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sblp.topMargin = dp(14);
        speakBtn.setLayoutParams(sblp);
        content.addView(speakBtn);

        // Status
        statusTv = new TextView(this);
        statusTv.setText("");
        statusTv.setTextSize(11);
        statusTv.setTypeface(Typeface.MONOSPACE);
        statusTv.setTextColor(MUTED);
        statusTv.setPadding(0, dp(8), 0, dp(8));
        content.addView(statusTv);

        // Play / Save row
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(6);
        alp.bottomMargin = dp(14);
        actions.setLayoutParams(alp);

        playBtn = smallBtn("Play", ACCENT);
        playBtn.setOnClickListener(v -> playLast());
        actions.addView(playBtn);

        saveBtn = smallBtn("Save", ORANGE);
        saveBtn.setOnClickListener(v -> saveAudio());
        actions.addView(saveBtn);

        content.addView(actions);

        // ---------- MORE FEATURES ----------
        content.addView(tiny("MORE"));
        content.addView(featureRow("Noise cancellation", "next build", PURPLE));
        content.addView(featureRow("Voice cloning", "next build", PURPLE));
        content.addView(featureRow("Sound effects", "next build", PURPLE));
        content.addView(featureRow("Music generation", "later · large model", MUTED));

        // ---------- RECENT ----------
        content.addView(tiny("RECENT"));
        recentCol = new LinearLayout(this);
        recentCol.setOrientation(LinearLayout.VERTICAL);
        content.addView(recentCol);

        root.addView(mainScroll);
        setContentView(root);
    }

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

    private LinearLayout featureRow(String name, String tag, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(rounded(CARD, BORDER, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        row.setLayoutParams(lp);

        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(13);
        n.setTextColor(TEXT);
        n.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(n);

        TextView t = new TextView(this);
        t.setText(tag);
        t.setTextSize(10);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(color);
        row.addView(t);

        return row;
    }

    private GradientDrawable rounded(int fill, int border, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(r));
        g.setColor(fill);
        g.setStroke(dp(1), border);
        return g;
    }

    // ============ pickers ============
    private void pickVoice() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Voice")
            .setItems(VOICES, (d, w) -> {
                voice = w;
                voiceTv.setText("voice: " + VOICES[voice]);
            })
            .show();
    }

    private void pickSpeed() {
        String[] opts = { "0.7x slow", "0.85x", "1.0x normal", "1.15x", "1.3x fast", "1.5x" };
        final float[] vals = { 0.7f, 0.85f, 1.0f, 1.15f, 1.3f, 1.5f };
        new android.app.AlertDialog.Builder(this)
            .setTitle("Speed")
            .setItems(opts, (d, w) -> {
                speed = vals[w];
                speedTv.setText(String.format("%.2fx", speed));
            })
            .show();
    }

    // ============ speak ============
    private void speak() {
        if (running) { toast("Already speaking"); return; }
        String text = textField.getText().toString().trim();
        if (text.isEmpty()) { toast("Enter text"); return; }

        running = true;
        speakBtn.setText("Generating...");
        speakBtn.setTextColor(ORANGE);
        statusTv.setText("Sending to audio bridge...");

        new Thread(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("text", text);
                req.put("speed", speed);
                req.put("voice", voice);

                Shell.run("rm -f /sdcard/Star/audio-result.json");
                String esc = req.toString().replace("'", "'\\''");
                Shell.run("printf '%s' '" + esc + "' > /sdcard/Star/audio-request.json");

                long deadline = System.currentTimeMillis() + 180_000L;
                while (System.currentTimeMillis() < deadline) {
                    Shell.Result r = Shell.run(
                        "test -f /sdcard/Star/audio-result.json && cat /sdcard/Star/audio-result.json");
                    if (r.output != null && !r.output.trim().isEmpty()) {
                        final String out = r.output.trim();
                        handler.post(() -> finishGenerate(out));
                        return;
                    }
                    try { Thread.sleep(600); } catch (InterruptedException ie) { break; }
                }
                handler.post(() -> {
                    running = false;
                    speakBtn.setText("Speak");
                    speakBtn.setTextColor(GREEN);
                    statusTv.setText("Timed out");
                });
            } catch (Exception e) {
                handler.post(() -> {
                    running = false;
                    speakBtn.setText("Speak");
                    speakBtn.setTextColor(GREEN);
                    statusTv.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void finishGenerate(String resultJson) {
        running = false;
        speakBtn.setText("Speak");
        speakBtn.setTextColor(GREEN);
        try {
            JSONObject o = new JSONObject(resultJson);
            if ("ok".equals(o.optString("status"))) {
                lastOutput = o.optString("output");
                statusTv.setText("Done · " + new File(lastOutput).length() / 1024 + " KB");
                refreshRecent();
                playLast();
            } else {
                statusTv.setText("Failed");
            }
        } catch (Exception e) {
            statusTv.setText("Parse error: " + e.getMessage());
        }
    }

    // ============ play ============
    private void playLast() {
        if (lastOutput == null) {
            // try default output path
            File f = new File("/sdcard/Star/audio_out.wav");
            if (!f.exists()) { toast("Nothing to play"); return; }
            lastOutput = f.getAbsolutePath();
        }
        final String src = lastOutput;

        new Thread(() -> {
            try {
                File extDir = getExternalFilesDir(null);
                if (extDir == null) extDir = getCacheDir();
                if (!extDir.exists()) extDir.mkdirs();
                final File dst = new File(extDir, "audio_preview.wav");
                if (dst.exists()) dst.delete();
                Shell.run("cp '" + src + "' '" + dst.getAbsolutePath() + "' && " +
                          "chmod 644 '" + dst.getAbsolutePath() + "'");

                if (!dst.exists() || dst.length() == 0) {
                    handler.post(() -> statusTv.setText("Copy failed"));
                    return;
                }
                handler.post(() -> {
                    try {
                        if (player != null) { player.stop(); player.release(); player = null; }
                        player = new MediaPlayer();
                        player.setDataSource(dst.getAbsolutePath());
                        player.prepare();
                        player.start();
                        statusTv.setText("Playing...");
                    } catch (Exception e) {
                        statusTv.setText("Play ex: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                handler.post(() -> statusTv.setText("Read fail: " + e.getMessage()));
            }
        }).start();
    }

    private void saveAudio() {
        if (lastOutput == null) { toast("Nothing to save"); return; }
        String name = "star_" + System.currentTimeMillis() + ".wav";
        String dst = "/sdcard/Music/Star/" + name;
        Shell.run("mkdir -p /sdcard/Music/Star");
        Shell.run("cp '" + lastOutput + "' '" + dst + "'");
        Toast.makeText(this, "Saved: " + dst, Toast.LENGTH_LONG).show();
    }

    // ============ recent ============
    private void refreshRecent() {
        recentCol.removeAllViews();
        new Thread(() -> {
            Shell.Result r = Shell.run("ls -t /sdcard/Star/audio_*.wav /sdcard/Star/audio_out.wav 2>/dev/null | head -8");
            if (r.output == null || r.output.trim().isEmpty()) {
                handler.post(() -> {
                    TextView empty = new TextView(this);
                    empty.setText("No audio yet.");
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
                    File f = new File(p);
                    TextView item = new TextView(this);
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
                        playLast();
                    });
                    recentCol.addView(item);
                }
            });
        }).start();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
