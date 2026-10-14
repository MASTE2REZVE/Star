package com.star.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

public class StudioActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int GREEN = 0xFF7EE787;
    private static final int ORANGE = 0xFFF0883E;
    private static final int PURPLE = 0xFFA78BFA;

    private LinearLayout tabStrip, contentHost;
    private int activeTab = 0;

    private static final String[] TABS = { "Image", "Video", "Audio", "3D", "2D" };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUI();
        selectTab(0);
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
        title.setText("Studio");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);
        root.addView(top);

        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(12), 0, dp(12), dp(10));
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(tabStrip);
        root.addView(hsv);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(dp(16), 0, dp(16), dp(24));
        sv.addView(contentHost);
        root.addView(sv);

        setContentView(root);
        buildTabs();
    }

    private void buildTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(TABS[i]);
            t.setTextSize(13);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(16), dp(8), dp(16), dp(8));
            t.setClickable(true);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(idx == activeTab ? 0x1A7CB3FF : CARD);
            bg.setStroke(dp(1), idx == activeTab ? ACCENT : BORDER);
            t.setBackground(bg);
            t.setTextColor(idx == activeTab ? ACCENT : MUTED);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> selectTab(idx));
            tabStrip.addView(t);
        }
    }

    private void selectTab(int idx) {
        activeTab = idx;
        buildTabs();
        contentHost.removeAllViews();
        switch (idx) {
            case 0: buildImageTab(); break;
            case 1: buildVideoTab(); break;
            case 2: buildAudioTab(); break;
            case 3: build3DTab(); break;
            case 4: build2DTab(); break;
        }
    }

    private void buildImageTab() {
        sectionTitle("IMAGE");
        description("Generate stills, edit photos, upscale, backgrounds.");
        openBtn("Open Image Studio", GREEN, () -> {
            try { startActivity(new Intent(this, ImageStudioActivity.class)); }
            catch (Throwable t) { toast("Not installed"); }
        });
        featureGrid(new String[][]{
            { "Text to image",       "free · Pollinations" },
            { "Style transfer",      "free" },
            { "Upscale",             "free" },
            { "Remove background",   "free" },
            { "Image to image",      "OpenRouter paid" },
            { "Inpaint / outpaint",  "coming" },
        });
    }

    private void buildVideoTab() {
        sectionTitle("VIDEO");
        description("Text to video, image to video, interpolate, upscale.");
        openBtn("Open Video Studio", ORANGE, () -> {
            try { startActivity(new Intent(this, VideoStudioActivity.class)); }
            catch (Throwable t) { toast("Not installed"); }
        });
        featureGrid(new String[][]{
            { "Single image motion", "free · Ken Burns" },
            { "Slideshow + RIFE",    "free · 24fps" },
            { "Frame interpolation", "free · RIFE" },
            { "Text to real video",  "OpenRouter paid" },
            { "Image to video",      "OpenRouter paid" },
            { "Video upscale",       "coming" },
        });
    }

    private void buildAudioTab() {
        sectionTitle("AUDIO");
        description("Speech, voice cloning, sound effects, music, cleanup.");
        openBtn("Open Audio Studio", GREEN, () -> {
            try { startActivity(new Intent(this, AudioStudioActivity.class)); }
            catch (Throwable t) { toast("Not installed"); }
        });
        featureGrid(new String[][]{
            { "Text to speech",       "working · Kokoro" },
            { "Voice cloning",        "next build" },
            { "Speech to text",       "free · Android" },
            { "Noise cancellation",   "next build" },
            { "Sound effects",        "next build" },
            { "Music generation",     "later · large" },
        });
        noticeBox("Working now",
            "Text to speech with the Kokoro model. Studio quality, offline. More audio tools coming.");
    }

    private void build3DTab() {
        sectionTitle("3D");
        description("Models, meshes, materials, rigs. Export to game engines.");
        featureGrid(new String[][]{
            { "Mesh from text",       "paid · Meshy / Trellis" },
            { "Mesh from image",      "paid · Trellis" },
            { "Auto rigging",         "paid · Mixamo / RigNet" },
            { "Manual rigging",       "free · Blender remote" },
            { "PBR materials",        "free · procedural" },
            { "GLB / OBJ export",     "free" },
        });
        noticeBox("In development",
            "3D needs GPU compute the tablet does not have. We will use cloud APIs for mesh generation and run PBR locally.");
    }

    private void build2DTab() {
        sectionTitle("2D");
        description("Sprites, tilesets, animation, cut-out characters.");
        featureGrid(new String[][]{
            { "Sprite sheets",         "free · procedural" },
            { "Tilesets / levels",     "free · WFC" },
            { "Cut-out animation",     "free · Synctoon" },
            { "Procedural characters", "free · animdsl" },
            { "Math animation",        "free · Fluxion" },
            { "Walk cycle",            "free" },
        });
        noticeBox("In development",
            "The anime path. Synctoon, animdsl and Sprout will run in Termux. Ask to start when ready.");
    }

    private void sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11);
        t.setTextColor(MUTED);
        t.setLetterSpacing(0.2f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(8), 0, dp(6));
        contentHost.addView(t);
    }

    private void description(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(TEXT);
        t.setPadding(0, 0, 0, dp(12));
        contentHost.addView(t);
    }

    private void openBtn(String text, int color, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x12FFFFFF);
        bg.setStroke(dp(1), color);
        t.setBackground(bg);
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        t.setLayoutParams(lp);
        contentHost.addView(t);
    }

    private void featureGrid(String[][] items) {
        for (String[] it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(rounded(CARD, BORDER));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);

            TextView name = new TextView(this);
            name.setText(it[0]);
            name.setTextSize(13);
            name.setTextColor(TEXT);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            TextView tag = new TextView(this);
            tag.setText(it[1]);
            tag.setTextSize(10);
            tag.setTypeface(Typeface.MONOSPACE);
            boolean paid = it[1].contains("paid");
            boolean coming = it[1].contains("coming") || it[1].contains("next") || it[1].contains("later");
            tag.setTextColor(coming ? MUTED : (paid ? ORANGE : GREEN));
            row.addView(tag);

            contentHost.addView(row);
        }
    }

    private void noticeBox(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x1AA78BFA);
        bg.setStroke(dp(1), PURPLE);
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        box.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(PURPLE);
        t.setLetterSpacing(0.15f);
        box.addView(t);

        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setPadding(0, dp(6), 0, 0);
        box.addView(b);

        contentHost.addView(box);
    }

    private GradientDrawable rounded(int fill, int border) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(fill);
        g.setStroke(dp(1), border);
        return g;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
