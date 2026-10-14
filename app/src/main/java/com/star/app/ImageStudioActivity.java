package com.star.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import okhttp3.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ImageStudioActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1A1A1A;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int GOLD = 0xFFFFB800;

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build();

    private EditText prompt;
    private ImageView preview;
    private TextView status;
    private LinearLayout historyRow;
    private Spinner modelSpinner;
    private Spinner sizeSpinner;
    private String currentModel = "flux";
    private int currentSize = 1024;
    private final List<File> savedImages = new ArrayList<>();

    private final String[] models = {
        "flux", "turbo", "flux-realism", "flux-anime",
        "flux-3d", "flux-pro", "any-dark"
    };
    private final String[] sizes = {"512 × 512", "768 × 768", "1024 × 1024", "1280 × 720", "720 × 1280"};
    private final int[][] sizeDims = {{512,512}, {768,768}, {1024,1024}, {1280,720}, {720,1280}};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(30));
        scroll.addView(root);

        // Header
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(16));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(22);
        back.setTextColor(TEXT);
        back.setPadding(dp(6), dp(6), dp(14), dp(6));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        topBar.addView(back);

        TextView title = new TextView(this);
        title.setText("Image Studio");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topBar.addView(title);

        TextView badge = new TextView(this);
        badge.setText("FREE");
        badge.setTextSize(10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(0xFF7EE787);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setCornerRadius(dp(10));
        bbg.setColor(0x1A7EE787);
        badge.setBackground(bbg);
        topBar.addView(badge);
        root.addView(topBar);

        // Prompt
        TextView promptLabel = new TextView(this);
        promptLabel.setText("PROMPT");
        promptLabel.setTextSize(10);
        promptLabel.setLetterSpacing(0.15f);
        promptLabel.setTextColor(MUTED);
        promptLabel.setPadding(dp(4), 0, 0, dp(6));
        root.addView(promptLabel);

        prompt = new EditText(this);
        prompt.setHint("a cyberpunk cat on a rooftop, neon lights, 4k");
        prompt.setTextColor(TEXT);
        prompt.setHintTextColor(0xFF484F58);
        prompt.setTextSize(15);
        prompt.setPadding(dp(14), dp(14), dp(14), dp(14));
        prompt.setMinLines(3);
        prompt.setGravity(Gravity.TOP | Gravity.START);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dp(14));
        pbg.setColor(CARD);
        pbg.setStroke(dp(1), BORDER);
        prompt.setBackground(pbg);
        root.addView(prompt);

        // Style chips
        TextView styleLabel = new TextView(this);
        styleLabel.setText("STYLE");
        styleLabel.setTextSize(10);
        styleLabel.setLetterSpacing(0.15f);
        styleLabel.setTextColor(MUTED);
        styleLabel.setPadding(dp(4), dp(16), 0, dp(6));
        root.addView(styleLabel);

        ScrollView styleScroll = new ScrollView(this);
        styleScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        styleScroll.addView(styleRow);
        root.addView(styleScroll);

        String[][] styles = {
            {"Realistic", ", photorealistic, 8k, cinematic lighting, detailed"},
            {"Anime", ", anime style, studio ghibli, vibrant colors, cel shading"},
            {"Cyberpunk", ", cyberpunk, neon lights, rainy, blade runner style"},
            {"Oil Paint", ", oil painting, van gogh style, thick brush strokes"},
            {"3D Render", ", 3d render, octane, blender, soft lighting"},
            {"Minimal", ", minimalist, clean, flat design, simple"},
            {"Fantasy", ", fantasy art, epic, detailed, concept art"},
            {"None", ""}
        };
        for (String[] s : styles) {
            TextView chip = new TextView(this);
            chip.setText(s[0]);
            chip.setTextSize(12);
            chip.setTextColor(TEXT);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            GradientDrawable cbg = new GradientDrawable();
            cbg.setCornerRadius(dp(16));
            cbg.setColor(CARD);
            cbg.setStroke(dp(1), BORDER);
            chip.setBackground(cbg);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                String current = prompt.getText().toString();
                // Strip existing style suffixes
                for (String[] st : styles) {
                    current = current.replace(st[1], "");
                }
                prompt.setText(current.trim() + s[1]);
                prompt.setSelection(prompt.getText().length());
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = dp(8);
            chip.setLayoutParams(clp);
            styleRow.addView(chip);
        }

        // Model + size row
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(16), 0, 0);

        LinearLayout modelCol = new LinearLayout(this);
        modelCol.setOrientation(LinearLayout.VERTICAL);
        modelCol.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ml = new TextView(this);
        ml.setText("MODEL");
        ml.setTextSize(10);
        ml.setLetterSpacing(0.15f);
        ml.setTextColor(MUTED);
        ml.setPadding(dp(4), 0, 0, dp(6));
        modelCol.addView(ml);
        modelSpinner = new Spinner(this);
        ArrayAdapter<String> mad = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, models);
        modelSpinner.setAdapter(mad);
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                currentModel = models[pos];
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        modelCol.addView(modelSpinner);

        LinearLayout sizeCol = new LinearLayout(this);
        sizeCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        scp.leftMargin = dp(10);
        sizeCol.setLayoutParams(scp);
        TextView sl = new TextView(this);
        sl.setText("SIZE");
        sl.setTextSize(10);
        sl.setLetterSpacing(0.15f);
        sl.setTextColor(MUTED);
        sl.setPadding(dp(4), 0, 0, dp(6));
        sizeCol.addView(sl);
        sizeSpinner = new Spinner(this);
        ArrayAdapter<String> sad = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, sizes);
        sizeSpinner.setAdapter(sad);
        sizeSpinner.setSelection(2);
        sizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                currentSize = pos;
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        sizeCol.addView(sizeSpinner);

        row2.addView(modelCol);
        row2.addView(sizeCol);
        root.addView(row2);

        // Generate button
        TextView genBtn = new TextView(this);
        genBtn.setText("⚡  GENERATE");
        genBtn.setTextSize(15);
        genBtn.setTypeface(Typeface.DEFAULT_BOLD);
        genBtn.setTextColor(Color.WHITE);
        genBtn.setGravity(Gravity.CENTER);
        genBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable gbg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xFFFFB800, 0xFFFF8F00});
        gbg.setCornerRadius(dp(14));
        genBtn.setBackground(gbg);
        genBtn.setClickable(true);
        genBtn.setOnClickListener(v -> generate());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(24);
        genBtn.setLayoutParams(glp);
        root.addView(genBtn);

        // Status
        status = new TextView(this);
        status.setText("");
        status.setTextSize(12);
        status.setTextColor(MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        // Preview
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(400));
        plp.topMargin = dp(8);
        preview.setLayoutParams(plp);
        GradientDrawable pvbg = new GradientDrawable();
        pvbg.setCornerRadius(dp(14));
        pvbg.setColor(CARD);
        pvbg.setStroke(dp(1), BORDER);
        preview.setBackground(pvbg);
        root.addView(preview);

        // Save button
        TextView saveBtn = new TextView(this);
        saveBtn.setText("↓  Save to Gallery");
        saveBtn.setTextSize(13);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setTextColor(ACCENT);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setCornerRadius(dp(14));
        sbg.setColor(0xFF0F1A26);
        sbg.setStroke(dp(1), 0xFF2A4A6A);
        saveBtn.setBackground(sbg);
        saveBtn.setClickable(true);
        saveBtn.setVisibility(View.GONE);
        saveBtn.setOnClickListener(v -> saveToGallery());
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sblp.topMargin = dp(10);
        saveBtn.setLayoutParams(sblp);
        root.addView(saveBtn);

        setContentView(scroll);
    }

    private Bitmap lastBitmap = null;

    private void generate() {
        String p = prompt.getText().toString().trim();
        if (p.isEmpty()) {
            Toast.makeText(this, "Enter a prompt first", Toast.LENGTH_SHORT).show();
            return;
        }

        status.setText("Generating…");
        preview.setImageDrawable(null);
        lastBitmap = null;

        int w = sizeDims[currentSize][0];
        int h = sizeDims[currentSize][1];
        int seed = new Random().nextInt(1_000_000);

        try {
            String url = "https://image.pollinations.ai/prompt/"
                + URLEncoder.encode(p, "UTF-8")
                + "?width=" + w
                + "&height=" + h
                + "&seed=" + seed
                + "&model=" + URLEncoder.encode(currentModel, "UTF-8")
                + "&nologo=true"
                + "&enhance=true";

            Request req = new Request.Builder().url(url).get().build();
            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call c, java.io.IOException e) {
                    runOnUiThread(() -> status.setText("Failed: " + e.getMessage()));
                }
                @Override public void onResponse(Call c, Response r) {
                    try (InputStream is = r.body().byteStream()) {
                        Bitmap bm = BitmapFactory.decodeStream(is);
                        if (bm == null) throw new Exception("Decode failed");
                        runOnUiThread(() -> {
                            lastBitmap = bm;
                            preview.setImageBitmap(bm);
                            status.setText("✓ " + bm.getWidth() + " × " + bm.getHeight());
                            // Show save button
                            View parent = (View) preview.getParent();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> status.setText("Error: " + e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            status.setText("Error: " + e.getMessage());
        }
    }

    private void saveToGallery() {
        if (lastBitmap == null) return;
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Star");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "star_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(f);
            lastBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            // Notify gallery
            android.media.MediaScannerConnection.scanFile(this,
                new String[]{f.getAbsolutePath()}, null, null);
            Toast.makeText(this, "Saved: " + f.getAbsolutePath(),
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
