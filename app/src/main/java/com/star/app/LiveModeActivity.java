package com.star.app;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class LiveModeActivity extends Activity implements LiveService.Listener {
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
    private LinearLayout transcript;
    private ScrollView scroll;
    private TextView statusTv;
    private TextView startBtn;
    private TextView modelTv;
    private TextView intervalTv;
    private TextView voiceTv;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        buildUI();
        LiveService.listener = this;
        requestNotifPerm();
        if (LiveService.running) {
            startBtn.setText("Stop");
            startBtn.setTextColor(RED);
            statusTv.setText("Running in background");
        } else {
            startBtn.setText("Start watching");
            startBtn.setTextColor(GREEN);
            statusTv.setText("Idle");
        }
    }

    @Override protected void onDestroy() {
        if (LiveService.listener == this) LiveService.listener = null;
        try {
            if (localTts != null) { localTts.stop(); localTts.shutdown(); }
        } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void requestNotifPerm() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
            }
        }
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
        title.setText("Live Mode");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);
        root.addView(top);

        statusTv = new TextView(this);
        statusTv.setText("Idle");
        statusTv.setTextSize(11);
        statusTv.setTypeface(Typeface.MONOSPACE);
        statusTv.setTextColor(MUTED);
        statusTv.setPadding(dp(20), 0, dp(20), dp(10));
        root.addView(statusTv);

        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setPadding(dp(16), 0, dp(16), 0);

        transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(transcript);
        root.addView(scroll);

        LinearLayout ctl = new LinearLayout(this);
        ctl.setOrientation(LinearLayout.VERTICAL);
        ctl.setPadding(dp(16), dp(10), dp(16), dp(20));

        modelTv = rowLine("Vision model: " + prefs.getLiveModel());
        modelTv.setOnClickListener(v -> pickModel());
        ctl.addView(modelTv);

        intervalTv = rowLine("Interval: " + prefs.getLiveIntervalSec() + "s");
        intervalTv.setOnClickListener(v -> pickInterval());
        ctl.addView(intervalTv);

        voiceTv = rowLine("Voice: " + (prefs.getLiveVoice() ? "on" : "off"));
        voiceTv.setOnClickListener(v -> {
            prefs.setLiveVoice(!prefs.getLiveVoice());
            voiceTv.setText("Voice: " + (prefs.getLiveVoice() ? "on" : "off"));
        });
        ctl.addView(voiceTv);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brp.topMargin = dp(10);
        btnRow.setLayoutParams(brp);

        TextView testBtn = btn("Test once", ACCENT, () -> {
            addLine("— test —", MUTED);
            testOnce();
        });
        testBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(testBtn);

        startBtn = btn("Start watching", GREEN, () -> toggle());
        LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp2.leftMargin = dp(8);
        startBtn.setLayoutParams(slp2);
        btnRow.addView(startBtn);

        ctl.addView(btnRow);
        root.addView(ctl);

        setContentView(root);
    }

    private TextView rowLine(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(TEXT);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        t.setBackground(bg);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
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
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        t.setBackground(bg);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private void toggle() {
        if (LiveService.running) {
            LiveService.stop(this);
            startBtn.setText("Start watching");
            startBtn.setTextColor(GREEN);
        } else {
            LiveService.start(this);
            startBtn.setText("Stop");
            startBtn.setTextColor(RED);
        }
    }

    private void testOnce() {
        statusTv.setText("Capturing…");
        new Thread(() -> {
            String b64 = ScreenCapture.captureJpegBase64(getApplicationContext(), 1280, 90);
            if (b64 == null) {
                final String err = ScreenCapture.lastError;
                runOnUiThread(() -> {
                    statusTv.setText("Capture failed");
                    addLine("[error] " + err, RED);
                });
                return;
            }
            AIClient.sendVision(this, prefs, b64, prefs.getLivePrompt(),
                prefs.getLiveModel(), new AIClient.Callback() {
                    @Override public void onReply(String reply) {
                        runOnUiThread(() -> {
                            addLine(reply, TEXT);
                            statusTv.setText("Idle");
                            if (prefs.getLiveVoice()) speakLocally(reply);
                        });
                    }
                    @Override public void onReplyStats(String r, int i, int o, long ms) {}
                    @Override public void onToolCall(String n, String a) {}
                    @Override public void onError(String err) {
                        runOnUiThread(() -> {
                            addLine("[error] " + err, RED);
                            statusTv.setText("Error");
                        });
                    }
                });
        }).start();
    }

    @Override public void onLine(String text, int color) {
        runOnUiThread(() -> addLine(text, color));
    }

    @Override public void onStatus(String status) {
        runOnUiThread(() -> statusTv.setText(status));
    }

    private void addLine(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF141414);
        bg.setStroke(dp(1), 0xFF232323);
        t.setBackground(bg);
        t.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        transcript.addView(t);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void pickInterval() {
        final int[] opts = { 3, 5, 10, 15, 30, 60 };
        String[] labels = new String[opts.length];
        for (int i = 0; i < opts.length; i++) labels[i] = opts[i] + "s";
        new android.app.AlertDialog.Builder(this)
            .setTitle("Interval")
            .setItems(labels, (d, w) -> {
                prefs.setLiveIntervalSec(opts[w]);
                intervalTv.setText("Interval: " + opts[w] + "s");
            })
            .show();
    }

    private void pickModel() {
        java.util.List<ModelCatalog.Model> cached = ModelCatalog.cached();
        if (cached == null || cached.isEmpty()) {
            Toast.makeText(this, "Loading…", Toast.LENGTH_SHORT).show();
            ModelCatalog.refresh(this, prefs, new ModelCatalog.Cb() {
                @Override public void onReady(java.util.List<ModelCatalog.Model> list) {
                    runOnUiThread(() -> showVisionPicker(list));
                }
                @Override public void onError(String e) {
                    runOnUiThread(() -> Toast.makeText(LiveModeActivity.this,
                        "Failed: " + e, Toast.LENGTH_LONG).show());
                }
            });
        } else {
            showVisionPicker(cached);
        }
    }

    private void showVisionPicker(java.util.List<ModelCatalog.Model> all) {
        java.util.List<ModelCatalog.Model> vis = new java.util.ArrayList<>();
        for (ModelCatalog.Model m : all) if (m.vision) vis.add(m);
        if (vis.isEmpty()) { Toast.makeText(this, "no vision models", Toast.LENGTH_SHORT).show(); return; }

        java.util.Collections.sort(vis, (a, b) -> {
            if (a.free != b.free) return a.free ? -1 : 1;
            double pa = a.free ? 0 : a.priceOut;
            double pb = b.free ? 0 : b.priceOut;
            if (pa != pb) return Double.compare(pa, pb);
            return a.id.compareToIgnoreCase(b.id);
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF161616);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];

        TextView recLabel = new TextView(this);
        recLabel.setText("RECOMMENDED");
        recLabel.setTextSize(10);
        recLabel.setTextColor(MUTED);
        recLabel.setLetterSpacing(0.15f);
        recLabel.setPadding(dp(4), 0, dp(4), dp(6));
        root.addView(recLabel);

        String[][] recs = {
            { "openai/gpt-4o-mini",         "$0.60/M  reliable" },
            { "google/gemini-2.0-flash-001","$0.40/M  reliable" },
            { "anthropic/claude-3.5-haiku","$0.80/M  reliable" },
            { "qwen/qwen-2-vl-7b-instruct", "$0.20/M  cheap" }
        };
        for (String[] rec : recs) {
            LinearLayout rrow = new LinearLayout(this);
            rrow.setOrientation(LinearLayout.HORIZONTAL);
            rrow.setGravity(Gravity.CENTER_VERTICAL);
            rrow.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable rbg = new GradientDrawable();
            rbg.setCornerRadius(dp(10));
            rbg.setColor(0x1A7EE787);
            rbg.setStroke(dp(1), GREEN);
            rrow.setBackground(rbg);
            rrow.setClickable(true);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(4);
            rrow.setLayoutParams(rlp);

            TextView rt = new TextView(this);
            rt.setText(rec[0]);
            rt.setTextSize(12);
            rt.setTypeface(Typeface.MONOSPACE);
            rt.setTextColor(TEXT);
            rt.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            rrow.addView(rt);

            TextView rp = new TextView(this);
            rp.setText(rec[1]);
            rp.setTextSize(10);
            rp.setTextColor(GREEN);
            rrow.addView(rp);

            rrow.setOnClickListener(v -> {
                prefs.setLiveModel(rec[0]);
                modelTv.setText("Vision model: " + rec[0]);
                if (ref[0] != null) ref[0].dismiss();
            });
            root.addView(rrow);
        }

        TextView spacer = new TextView(this);
        spacer.setHeight(dp(14));
        root.addView(spacer);

        TextView allLabel = new TextView(this);
        allLabel.setText("ALL VISION MODELS");
        allLabel.setTextSize(10);
        allLabel.setTextColor(MUTED);
        allLabel.setLetterSpacing(0.15f);
        allLabel.setPadding(dp(4), 0, dp(4), dp(6));
        root.addView(allLabel);

        EditText search = new EditText(this);
        search.setHint("search vision models…");
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setBackgroundColor(0xFF1E1E1E);
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(search);

        LinearLayout listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            listCol.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase();
            int shown = 0;
            for (ModelCatalog.Model m : vis) {
                if (!q.isEmpty()
                    && !m.id.toLowerCase().contains(q)
                    && !(m.name != null && m.name.toLowerCase().contains(q))) continue;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(12), dp(8), dp(12));
                row.setClickable(true);

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView name = new TextView(this);
                name.setText(m.name == null ? m.id : m.name);
                name.setTextSize(13);
                name.setTextColor(TEXT);
                name.setMaxLines(1);
                col.addView(name);

                TextView idTv = new TextView(this);
                idTv.setText(m.id);
                idTv.setTextSize(10);
                idTv.setTextColor(MUTED);
                idTv.setTypeface(Typeface.MONOSPACE);
                idTv.setMaxLines(1);
                col.addView(idTv);
                row.addView(col);

                TextView tag = new TextView(this);
                tag.setText(m.priceTag);
                tag.setTextSize(10);
                tag.setTypeface(Typeface.DEFAULT_BOLD);
                tag.setTextColor(m.free ? GREEN : ORANGE);
                tag.setPadding(dp(8), dp(3), dp(8), dp(3));
                GradientDrawable tbg = new GradientDrawable();
                tbg.setCornerRadius(dp(6));
                tbg.setColor(m.free ? 0x1A7EE787 : 0x1AF0883E);
                tag.setBackground(tbg);
                row.addView(tag);

                row.setOnClickListener(v -> {
                    prefs.setLiveModel(m.id);
                    modelTv.setText("Vision model: " + m.id);
                    if (ref[0] != null) ref[0].dismiss();
                });
                listCol.addView(row);
                shown++;
                if (shown >= 100) break;
            }
        };
        rebuild[0].run();
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) { rebuild[0].run(); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        ScrollView sv = new ScrollView(this);
        sv.addView(listCol);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(8);
        sv.setLayoutParams(slp);
        root.addView(sv);

        TextView cancel = new TextView(this);
        cancel.setText("CANCEL");
        cancel.setTextSize(13);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setTextColor(MUTED);
        cancel.setPadding(dp(16), dp(12), dp(16), dp(12));
        cancel.setGravity(Gravity.END);
        cancel.setClickable(true);
        root.addView(cancel);

        ref[0] = new android.app.AlertDialog.Builder(this).setView(root).create();
        if (ref[0].getWindow() != null)
            ref[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        cancel.setOnClickListener(v -> ref[0].dismiss());
        ref[0].show();
        if (ref[0].getWindow() != null) {
            ref[0].getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.92),
                (int)(getResources().getDisplayMetrics().heightPixels * 0.85));
        }
    }

    private android.speech.tts.TextToSpeech localTts;
    private boolean localTtsReady = false;

    private void speakLocally(String text) {
        if (localTts == null) {
            localTts = new android.speech.tts.TextToSpeech(this, status -> {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    try {
                        localTts.setLanguage(java.util.Locale.US);
                        localTts.setSpeechRate(1.0f);
                        if (android.os.Build.VERSION.SDK_INT >= 21) {
                            android.media.AudioAttributes a =
                                new android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build();
                            localTts.setAudioAttributes(a);
                        }
                        localTtsReady = true;
                        localTts.speak(text,
                            android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "local");
                    } catch (Throwable ignored) {}
                }
            });
        } else if (localTtsReady) {
            try {
                localTts.speak(text,
                    android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "local");
            } catch (Throwable ignored) {}
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
