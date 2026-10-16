package com.star.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * K-B8 — Video Studio, rebuilt on the Concat engine.
 *
 * Seven stacked sections, all programmatic views on Star's dark palette:
 *   1. Project bar   — name field, New / Save
 *   2. Media strip   — imported clips; tap one to append it to the timeline
 *   3. Timeline      — the clips on the timeline, with up / down reordering
 *   4. Effect row    — opens {@link EffectGridActivity}, applies the pick
 *   5. Transition row— dropdown of fade / wipe / slide / zoom
 *   6. Text row      — words + position + duration, placed as a title clip
 *   7. Export        — video_export, then subscribes to export.progress
 *
 * Everything talks to Concat through {@link ConcatTools}.
 */
public class VideoStudioActivity extends Activity {

    // ---- Star palette ----
    private static final int BG     = 0xFF0F0F0F;
    private static final int CARD   = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int TEXT   = 0xFFECECEC;
    private static final int MUTED  = 0xFF8E8E8E;
    private static final int GREEN  = 0xFF7EE787;
    private static final int ORANGE = 0xFFF0883E;
    private static final int RED    = 0xFFF85149;

    private static final int REQ_EFFECT = 4001;

    // The export event stream the Concat API emits as `export.progress`
    // notifications. The Termux-side bridge relays the latest one here, and
    // a terminal `export.done` marker lands next to it.
    private static final String CONCAT_DIR    = "/sdcard/Star/concat";
    private static final String PROGRESS_FILE = CONCAT_DIR + "/export.progress";
    private static final String DONE_FILE     = CONCAT_DIR + "/export.done";

    // label -> catalogue id (from catalogue.list)
    private static final String[] TRANSITION_LABELS = { "Fade", "Wipe", "Slide", "Zoom" };
    private static final String[] TRANSITION_IDS = {
        "concat.dissolve", "concat.linear-wipe", "concat.slide", "concat.zoom-transition"
    };

    private static final String[] TEXT_POSITIONS = { "Start", "Middle", "End" };
    private static final String[] TEXT_DURATIONS = { "1 second", "2 seconds", "3 seconds", "5 seconds" };
    private static final double[] TEXT_DURATION_VALUES = { 1.0, 2.0, 3.0, 5.0 };

    private final Handler ui = new Handler(Looper.getMainLooper());

    // ---- state ----
    private String projectName = "Untitled";
    private String projectPath = ConcatTools.DEFAULT_LOCATION + "/Untitled";
    private final List<Media> media = new ArrayList<>();
    private final List<Clip> timeline = new ArrayList<>();
    private int selectedClip = -1;
    private String selectedEffectId = null;
    private int selectedTransition = 0;
    private int textPosition = 0;
    private int textDuration = 2;
    private double clipEstimateSec = 3.0;

    private volatile boolean exporting = false;
    private Thread exportThread;

    // ---- views ----
    private EditText nameField;
    private LinearLayout mediaStrip;
    private TextView mediaEmpty;
    private LinearLayout timelineBar;
    private TextView timelineEmpty;
    private TextView effectTv;
    private TextView transitionTv;
    private EditText textField;
    private TextView textPosTv;
    private TextView textDurTv;
    private EditText outputField;
    private TextView exportBtn;
    private ProgressBar exportBar;
    private TextView statusTv;

    // ===================== lifecycle =====================

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUI();
        createProject(projectName);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        exporting = false;
        if (exportThread != null) exportThread.interrupt();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EFFECT && res == RESULT_OK && data != null) {
            String id = data.getStringExtra(EffectGridActivity.EXTRA_EFFECT_ID);
            if (id != null && !id.isEmpty()) {
                selectedEffectId = id;
                effectTv.setText("Effect · " + shortId(id));
                applyEffect(id);
            }
        }
    }

    // ===================== UI =====================

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(topBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(content);

        content.addView(projectBar());
        content.addView(sectionLabel("MEDIA"));
        content.addView(mediaSection());
        content.addView(sectionLabel("TIMELINE"));
        content.addView(timelineSection());
        content.addView(sectionLabel("EFFECT"));
        content.addView(effectSection());
        content.addView(sectionLabel("TRANSITION"));
        content.addView(transitionSection());
        content.addView(sectionLabel("TEXT"));
        content.addView(textSection());
        content.addView(sectionLabel("EXPORT"));
        content.addView(exportSection());

        statusTv = new TextView(this);
        statusTv.setText("");
        statusTv.setTextSize(11);
        statusTv.setTypeface(Typeface.MONOSPACE);
        statusTv.setTextColor(MUTED);
        statusTv.setPadding(0, dp(14), 0, 0);
        content.addView(statusTv);

        root.addView(scroll);
        setContentView(root);
    }

    private View topBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(8));

        TextView back = new TextView(this);
        back.setText("\u2190");
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

        TextView engine = new TextView(this);
        engine.setText("CONCAT");
        engine.setTextSize(10);
        engine.setLetterSpacing(0.2f);
        engine.setTextColor(ACCENT);
        engine.setPadding(dp(8), dp(4), dp(8), dp(4));
        engine.setBackground(pill(0x1A7CB3FF, ACCENT, 20));
        top.addView(engine);

        return top;
    }

    // ---- 1. project bar ----

    private View projectBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        nameField = new EditText(this);
        nameField.setText(projectName);
        nameField.setTextColor(TEXT);
        nameField.setHintTextColor(MUTED);
        nameField.setHint("Project name");
        nameField.setTextSize(13);
        nameField.setSingleLine(true);
        nameField.setBackground(rounded(CARD, BORDER, 10));
        nameField.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        flp.rightMargin = dp(8);
        nameField.setLayoutParams(flp);
        row.addView(nameField);

        TextView newBtn = button("New", ACCENT, false);
        newBtn.setOnClickListener(v -> {
            String n = nameField.getText().toString().trim();
            if (n.isEmpty()) { toast("Enter a project name"); return; }
            projectName = n;
            projectPath = ConcatTools.DEFAULT_LOCATION + "/" + n;
            media.clear();
            timeline.clear();
            selectedClip = -1;
            selectedEffectId = null;
            rebuildMediaStrip();
            rebuildTimeline();
            createProject(projectName);
        });
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.rightMargin = dp(8);
        newBtn.setLayoutParams(nlp);
        row.addView(newBtn);

        TextView saveBtn = button("Save", GREEN, true);
        saveBtn.setOnClickListener(v -> {
            String n = nameField.getText().toString().trim();
            if (n.isEmpty()) { toast("Enter a project name"); return; }
            projectName = n;
            projectPath = ConcatTools.DEFAULT_LOCATION + "/" + n;
            createProject(projectName);
        });
        row.addView(saveBtn);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        return wrap;
    }

    // ---- 2. media strip ----

    private View mediaSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        mediaEmpty = new TextView(this);
        mediaEmpty.setText("No media imported yet.");
        mediaEmpty.setTextSize(12);
        mediaEmpty.setTextColor(MUTED);
        mediaEmpty.setPadding(dp(12), dp(12), dp(12), dp(12));
        mediaEmpty.setBackground(rounded(0xFF141414, BORDER, 10));
        wrap.addView(mediaEmpty);

        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.setVisibility(View.GONE);
        mediaStrip = new LinearLayout(this);
        mediaStrip.setOrientation(LinearLayout.HORIZONTAL);
        mediaStrip.setPadding(0, dp(2), 0, dp(2));
        h.addView(mediaStrip);
        wrap.addView(h);

        TextView importBtn = button("+ Import clip", ACCENT, false);
        importBtn.setOnClickListener(v -> pickMediaFile());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(8);
        importBtn.setLayoutParams(ilp);
        wrap.addView(importBtn);

        mediaScroll = h;
        return wrap;
    }

    private HorizontalScrollView mediaScroll;

    // ---- 3. timeline ----

    private View timelineSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        timelineEmpty = new TextView(this);
        timelineEmpty.setText("Timeline is empty. Tap a media clip to add it.");
        timelineEmpty.setTextSize(12);
        timelineEmpty.setTextColor(MUTED);
        timelineEmpty.setPadding(dp(12), dp(12), dp(12), dp(12));
        timelineEmpty.setBackground(rounded(0xFF141414, BORDER, 10));
        wrap.addView(timelineEmpty);

        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.setVisibility(View.GONE);
        timelineBar = new LinearLayout(this);
        timelineBar.setOrientation(LinearLayout.HORIZONTAL);
        timelineBar.setPadding(0, dp(2), 0, dp(2));
        h.addView(timelineBar);
        wrap.addView(h);

        timelineScroll = h;
        return wrap;
    }

    private HorizontalScrollView timelineScroll;

    // ---- 4. effect ----

    private View effectSection() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        effectTv = button("Choose effect", ACCENT, false);
        effectTv.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        effectTv.setOnClickListener(v -> {
            if (timeline.isEmpty()) { toast("Add a clip first"); return; }
            startActivityForResult(EffectGridActivity.intent(this), REQ_EFFECT);
        });
        row.addView(effectTv);

        TextView clear = button("Clear", MUTED, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(8);
        clear.setLayoutParams(clp);
        clear.setOnClickListener(v -> {
            selectedEffectId = null;
            effectTv.setText("Choose effect");
            setStatus("Effect card cleared (existing chains are untouched).", MUTED);
        });
        row.addView(clear);
        return row;
    }

    // ---- 5. transition ----

    private View transitionSection() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        transitionTv = button("Fade", TEXT, false);
        transitionTv.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        transitionTv.setOnClickListener(v -> pickTransition());
        row.addView(transitionTv);

        TextView apply = button("Apply", GREEN, true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(8);
        apply.setLayoutParams(alp);
        apply.setOnClickListener(v -> applyTransition());
        row.addView(apply);
        return row;
    }

    // ---- 6. text ----

    private View textSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        textField = new EditText(this);
        textField.setHint("Title text");
        textField.setHintTextColor(MUTED);
        textField.setTextColor(TEXT);
        textField.setTextSize(13);
        textField.setSingleLine(true);
        textField.setBackground(rounded(CARD, BORDER, 10));
        textField.setPadding(dp(12), dp(10), dp(12), dp(10));
        wrap.addView(textField);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        row.setLayoutParams(rlp);

        textPosTv = button("Position: Start", TEXT, false);
        textPosTv.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        textPosTv.setOnClickListener(v -> pickTextPosition());
        row.addView(textPosTv);

        textDurTv = button("2 seconds", TEXT, false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dlp.leftMargin = dp(8);
        textDurTv.setLayoutParams(dlp);
        textDurTv.setOnClickListener(v -> pickTextDuration());
        row.addView(textDurTv);
        wrap.addView(row);

        TextView add = button("Add text", GREEN, true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(8);
        add.setLayoutParams(alp);
        add.setOnClickListener(v -> applyText());
        wrap.addView(add);
        return wrap;
    }

    // ---- 7. export ----

    private View exportSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        outputField = new EditText(this);
        outputField.setText("/sdcard/Star/exports/" + projectName + ".mp4");
        outputField.setTextColor(TEXT);
        outputField.setHintTextColor(MUTED);
        outputField.setHint("Output path");
        outputField.setTextSize(12);
        outputField.setTypeface(Typeface.MONOSPACE);
        outputField.setSingleLine(true);
        outputField.setBackground(rounded(CARD, BORDER, 10));
        outputField.setPadding(dp(12), dp(10), dp(12), dp(10));
        wrap.addView(outputField);

        exportBtn = button("Export video", GREEN, true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(8);
        exportBtn.setLayoutParams(blp);
        exportBtn.setOnClickListener(v -> startExport());
        wrap.addView(exportBtn);

        exportBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        exportBar.setMax(100);
        exportBar.setProgress(0);
        exportBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        exportBar.setLayoutParams(plp);
        wrap.addView(exportBar);

        return wrap;
    }

    // ===================== project =====================

    private void createProject(final String name) {
        setStatus("Opening project \u201c" + name + "\u201d\u2026", MUTED);
        new Thread(() -> {
            String r = ConcatTools.video_project_create(name);
            final boolean ok = r != null && r.startsWith("OK");
            ui.post(() -> setStatus(ok
                    ? ("Project ready · " + projectPath)
                    : ("Project error: " + r), ok ? GREEN : RED));
        }).start();
    }

    // ===================== media =====================

    private void pickMediaFile() {
        new Thread(() -> {
            String cmd = "find /sdcard/Movies /sdcard/DCIM/Camera /sdcard/Star /sdcard/Download "
                    + "-maxdepth 3 \\( -iname '*.mp4' -o -iname '*.mov' -o -iname '*.mkv' "
                    + "-o -iname '*.webm' -o -iname '*.m4v' \\) 2>/dev/null | head -60";
            Shell.Result r = Shell.run(cmd);
            final List<String> files = new ArrayList<>();
            if (r != null && r.output != null) {
                for (String line : r.output.split("\n")) {
                    String s = line.trim();
                    if (!s.isEmpty()) files.add(s);
                }
            }
            ui.post(() -> {
                if (files.isEmpty()) {
                    toast("No video files found in the usual folders");
                    return;
                }
                String[] labels = new String[files.size()];
                for (int i = 0; i < files.size(); i++) labels[i] = new File(files.get(i)).getName();
                new AlertDialog.Builder(this)
                        .setTitle("Import clip")
                        .setItems(labels, (d, w) -> importMedia(files.get(w)))
                        .show();
            });
        }).start();
    }

    private void importMedia(final String path) {
        setStatus("Importing " + new File(path).getName() + "\u2026", MUTED);
        new Thread(() -> {
            String r = ConcatTools.video_media_import(projectPath, path);
            final boolean ok = r != null && r.startsWith("OK");
            final String id = ok ? mediaIdFrom(r) : null;
            ui.post(() -> {
                if (!ok) { setStatus("Import failed: " + r, RED); return; }
                media.add(new Media(id == null ? path : id, path, new File(path).getName()));
                rebuildMediaStrip();
                setStatus("Imported " + new File(path).getName(), GREEN);
            });
        }).start();
    }

    private String mediaIdFrom(String result) {
        String raw = result == null ? "" : result;
        int m = raw.indexOf("\"media\":[");
        if (m < 0) m = 0;
        int idAt = raw.indexOf("\"id\":\"", m);
        if (idAt < 0) return null;
        int from = idAt + "\"id\":\"".length();
        int to = raw.indexOf('"', from);
        if (to <= from) return null;
        return raw.substring(from, to);
    }

    private void rebuildMediaStrip() {
        mediaStrip.removeAllViews();
        if (media.isEmpty()) {
            mediaEmpty.setVisibility(View.VISIBLE);
            mediaScroll.setVisibility(View.GONE);
            return;
        }
        mediaEmpty.setVisibility(View.GONE);
        mediaScroll.setVisibility(View.VISIBLE);
        for (final Media m : media) {
            TextView chip = new TextView(this);
            chip.setText(m.name);
            chip.setTextSize(12);
            chip.setTextColor(TEXT);
            chip.setMaxLines(1);
            chip.setSingleLine(true);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setPadding(dp(12), dp(10), dp(12), dp(10));
            chip.setBackground(rounded(CARD, BORDER, 10));
            chip.setClickable(true);
            chip.setOnClickListener(v -> addClip(m));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(140), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);
            mediaStrip.addView(chip);
        }
    }

    // ===================== timeline =====================

    private void addClip(final Media m) {
        final double at = clipEstimateSec * timeline.size();
        setStatus("Adding clip\u2026", MUTED);
        new Thread(() -> {
            String r = ConcatTools.video_add_clip(projectPath, m.id, at);
            final boolean ok = r != null && r.startsWith("OK");
            ui.post(() -> {
                if (!ok) { setStatus("Add clip failed: " + r, RED); return; }
                timeline.add(new Clip(m.id, m.name));
                selectedClip = timeline.size() - 1;
                rebuildTimeline();
                setStatus("Added " + m.name + " at " + fmt(at) + "s", GREEN);
            });
        }).start();
    }

    private void rebuildTimeline() {
        timelineBar.removeAllViews();
        if (timeline.isEmpty()) {
            timelineEmpty.setVisibility(View.VISIBLE);
            timelineScroll.setVisibility(View.GONE);
            return;
        }
        timelineEmpty.setVisibility(View.GONE);
        timelineScroll.setVisibility(View.VISIBLE);
        for (int i = 0; i < timeline.size(); i++) {
            final int index = i;
            timelineBar.addView(clipCard(index));
        }
    }

    private View clipCard(final int index) {
        final Clip c = timeline.get(index);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(10), dp(10), dp(8));
        boolean sel = index == selectedClip;
        card.setBackground(rounded(CARD, sel ? ACCENT : BORDER, 10));
        card.setClickable(true);
        card.setOnClickListener(v -> {
            selectedClip = index;
            rebuildTimeline();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                dp(150), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(8);
        card.setLayoutParams(clp);

        TextView num = new TextView(this);
        num.setText("CLIP " + (index + 1));
        num.setTextSize(9);
        num.setLetterSpacing(0.15f);
        num.setTextColor(sel ? ACCENT : MUTED);
        card.addView(num);

        TextView name = new TextView(this);
        name.setText(c.name);
        name.setTextSize(12);
        name.setTextColor(TEXT);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(2);
        name.setLayoutParams(nlp);
        card.addView(name);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowlp.topMargin = dp(6);
        controls.setLayoutParams(rowlp);

        controls.addView(miniButton("\u2191", index > 0, v -> moveClip(index, index - 1)));
        controls.addView(miniButton("\u2193", index < timeline.size() - 1, v -> moveClip(index, index + 1)));
        controls.addView(miniButton("\u2715", true, v -> removeClip(index)));

        card.addView(controls);
        return card;
    }

    private TextView miniButton(String glyph, boolean enabled, View.OnClickListener onClick) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(enabled ? TEXT : 0xFF555555);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        t.setBackground(rounded(0xFF141414, BORDER, 8));
        t.setClickable(enabled);
        if (enabled) t.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        t.setLayoutParams(lp);
        return t;
    }

    private void moveClip(int from, int to) {
        if (from < 0 || to < 0 || from >= timeline.size() || to >= timeline.size()) return;
        Clip moved = timeline.remove(from);
        timeline.add(to, moved);
        selectedClip = to;
        rebuildTimeline();
        setStatus("Reordered timeline (local \u2014 press re-apply to commit).", MUTED);
    }

    private void removeClip(int index) {
        if (index < 0 || index >= timeline.size()) return;
        timeline.remove(index);
        if (selectedClip >= timeline.size()) selectedClip = timeline.size() - 1;
        rebuildTimeline();
    }

    // ===================== effect =====================

    private void applyEffect(final String effectId) {
        if (timeline.isEmpty()) { toast("Add a clip first"); return; }
        setStatus("Applying effect " + effectId + "\u2026", MUTED);
        new Thread(() -> {
            String r = ConcatTools.video_apply_effect(projectPath, effectId);
            final boolean ok = r != null && r.startsWith("OK");
            ui.post(() -> setStatus(ok
                    ? ("Applied effect · " + shortId(effectId))
                    : ("Effect failed: " + r), ok ? GREEN : RED));
        }).start();
    }

    // ===================== transition =====================

    private void pickTransition() {
        new AlertDialog.Builder(this)
                .setTitle("Transition")
                .setItems(TRANSITION_LABELS, (d, w) -> {
                    selectedTransition = w;
                    transitionTv.setText(TRANSITION_LABELS[w]);
                })
                .show();
    }

    private void applyTransition() {
        if (timeline.isEmpty()) { toast("Add a clip first"); return; }
        final String id = TRANSITION_IDS[selectedTransition];
        setStatus("Adding transition " + TRANSITION_LABELS[selectedTransition] + "\u2026", MUTED);
        new Thread(() -> {
            String r = ConcatTools.video_add_transition(projectPath, id, 0.5);
            final boolean ok = r != null && r.startsWith("OK");
            ui.post(() -> setStatus(ok
                    ? ("Transition applied · " + TRANSITION_LABELS[selectedTransition])
                    : ("Transition failed: " + r), ok ? GREEN : RED));
        }).start();
    }

    // ===================== text =====================

    private void pickTextPosition() {
        new AlertDialog.Builder(this)
                .setTitle("Text position")
                .setItems(TEXT_POSITIONS, (d, w) -> {
                    textPosition = w;
                    textPosTv.setText("Position: " + TEXT_POSITIONS[w]);
                })
                .show();
    }

    private void pickTextDuration() {
        new AlertDialog.Builder(this)
                .setTitle("Text duration")
                .setItems(TEXT_DURATIONS, (d, w) -> {
                    textDuration = w;
                    textDurTv.setText(TEXT_DURATIONS[w]);
                })
                .show();
    }

    private void applyText() {
        final String text = textField.getText().toString().trim();
        if (text.isEmpty()) { toast("Enter some text"); return; }
        final double total = Math.max(clipEstimateSec, clipEstimateSec * timeline.size());
        final double dur = TEXT_DURATION_VALUES[textDuration];
        double start;
        if (textPosition == 0) start = 0.0;
        else if (textPosition == 1) start = Math.max(0.0, (total - dur) / 2.0);
        else start = Math.max(0.0, total - dur);
        final double startSec = start;
        final double endSec = start + dur;

        setStatus("Adding title\u2026", MUTED);
        new Thread(() -> {
            String r = ConcatTools.video_add_text(projectPath, text, startSec, endSec);
            final boolean ok = r != null && r.startsWith("OK");
            ui.post(() -> setStatus(ok
                    ? ("Title added · " + TEXT_POSITIONS[textPosition] + " · " + fmt(dur) + "s")
                    : ("Text failed: " + r), ok ? GREEN : RED));
        }).start();
    }

    // ===================== export =====================

    private void startExport() {
        if (exporting) { toast("Export already running"); return; }
        if (timeline.isEmpty()) { toast("Add a clip first"); return; }
        final String out = outputField.getText().toString().trim();
        if (out.isEmpty()) { toast("Set an output path"); return; }

        exporting = true;
        exportBtn.setText("Exporting\u2026");
        exportBtn.setTextColor(ORANGE);
        exportBar.setVisibility(View.VISIBLE);
        exportBar.setIndeterminate(true);
        setStatus("Starting export\u2026", MUTED);

        exportThread = new Thread(() -> {
            String dir = new File(out).getParent();
            if (dir != null) Shell.run("mkdir -p '" + dir + "'");
            Shell.run("rm -f '" + PROGRESS_FILE + "' '" + DONE_FILE + "'");
            String r = ConcatTools.video_export(projectPath, out);
            if (r == null || r.startsWith("ERR")) {
                finishExport(false, out, r == null ? "no result" : r);
                return;
            }
            setStatus("Export running\u2026", MUTED);
            subscribeExportProgress(out);
        });
        exportThread.start();
    }

    /**
     * Subscribes to the Concat `export.progress` notification stream. The
     * Termux-side bridge mirrors the newest event to {@link #PROGRESS_FILE}
     * and drops {@link #DONE_FILE} when the renderer has written its file, so
     * this polls both and falls back to watching the output file itself.
     */
    private void subscribeExportProgress(String out) {
        final long deadline = System.currentTimeMillis() + 30L * 60L * 1000L;
        long lastSize = -1;
        long stable = 0;

        while (exporting && System.currentTimeMillis() < deadline) {
            String raw = readFile(PROGRESS_FILE);
            if (raw != null && !raw.isEmpty()) {
                int pct = parseExportProgress(raw);
                if (pct >= 0) {
                    final int p = pct;
                    ui.post(() -> {
                        exportBar.setIndeterminate(false);
                        exportBar.setProgress(p);
                    });
                }
            }

            if (fileExists(DONE_FILE)) { finishExport(true, out, null); return; }

            long size = new File(out).exists() ? new File(out).length() : 0;
            if (size > 0 && size == lastSize) {
                stable++;
                if (stable >= 4) { finishExport(true, out, null); return; }
            } else {
                stable = 0;
            }
            lastSize = size;

            try { Thread.sleep(500L); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }

        if (new File(out).exists() && new File(out).length() > 0) {
            finishExport(true, out, null);
        } else {
            finishExport(false, out, exporting ? "export timed out" : "export cancelled");
        }
    }

    private int parseExportProgress(String raw) {
        String line = raw.trim();
        int nl = line.lastIndexOf('\n');
        if (nl >= 0) line = line.substring(nl + 1).trim();
        if (line.isEmpty()) return -1;
        try {
            JSONObject o = new JSONObject(line);
            if (o.has("frame") && o.has("total")) {
                int total = o.optInt("total", 0);
                if (total <= 0) return -1;
                int frame = o.optInt("frame", 0);
                return clampPct((int) Math.round(100.0 * frame / total));
            }
            if (o.has("fraction")) {
                return clampPct((int) Math.round(o.optDouble("fraction", 0.0) * 100.0));
            }
            if (o.has("percent")) return clampPct(o.optInt("percent", 0));
            if (o.has("progress")) {
                double v = o.optDouble("progress", 0.0);
                return clampPct((int) Math.round(v <= 1.0 ? v * 100.0 : v));
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private int clampPct(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private void finishExport(boolean ok, String out, String err) {
        exporting = false;
        final boolean success = ok;
        final String output = out;
        final String error = err;
        ui.post(() -> {
            exportBtn.setText("Export video");
            exportBtn.setTextColor(GREEN);
            if (success) {
                exportBar.setIndeterminate(false);
                exportBar.setProgress(100);
                long bytes = new File(output).length();
                setStatus("Exported \u00b7 " + (bytes / 1024) + " KB \u2192 " + output, GREEN);
            } else {
                exportBar.setIndeterminate(false);
                setStatus("Export failed: " + error, RED);
            }
        });
    }

    // ===================== helpers =====================

    private String readFile(String path) {
        Shell.Result r = Shell.run("cat '" + path + "' 2>/dev/null");
        return (r == null || r.output == null) ? null : r.output.trim();
    }

    private boolean fileExists(String path) {
        Shell.Result r = Shell.run("test -f '" + path + "' && echo 1 || echo 0");
        return r != null && r.output != null && r.output.trim().endsWith("1");
    }

    private String shortId(String id) {
        return id == null ? "" : id.replace("concat.", "");
    }

    private String fmt(double sec) {
        return String.format(Locale.US, "%.1f", sec);
    }

    private void setStatus(String s, int color) {
        if (statusTv != null) {
            statusTv.setText(s);
            statusTv.setTextColor(color);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(10);
        t.setLetterSpacing(0.15f);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(16), 0, dp(6));
        return t;
    }

    private TextView button(String text, int color, boolean filled) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(filled ? 0xFF0F0F0F : color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        if (filled) {
            bg.setColor(color);
        } else {
            bg.setColor(0x1AFFFFFF);
            bg.setStroke(dp(1), color);
        }
        t.setBackground(bg);
        t.setClickable(true);
        return t;
    }

    private GradientDrawable rounded(int fill, int border, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(r));
        g.setColor(fill);
        g.setStroke(dp(1), border);
        return g;
    }

    private GradientDrawable pill(int fill, int border, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(r));
        g.setColor(fill);
        g.setStroke(dp(1), border);
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ===================== model =====================

    private static final class Media {
        final String id;
        final String path;
        final String name;
        Media(String id, String path, String name) {
            this.id = id; this.path = path; this.name = name;
        }
    }

    private static final class Clip {
        final String mediaId;
        final String name;
        Clip(String mediaId, String name) {
            this.mediaId = mediaId; this.name = name;
        }
    }
}
