package com.star.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class StudioModelsActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF161616;
    private static final int BORDER = 0xFF232323;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int GREEN = 0xFF7EE787;
    private static final int GOLD = 0xFFFFB800;
    private static final int RED = 0xFFF85149;
    private static final int BLUE = 0xFF7CB3FF;

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    private String modality = "text";
    private List<ModelRow> allModels = new ArrayList<>();
    private List<ModelRow> shown = new ArrayList<>();
    private LinearLayout listCol;
    private TextView status;
    private EditText searchBox;
    private String activeFilter = "Tools";

    private Prefs prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        modality = getIntent().getStringExtra("modality");
        if (modality == null) modality = "text";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(20), dp(16), dp(12));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(22);
        back.setTextColor(TEXT);
        back.setPadding(dp(6), dp(6), dp(14), dp(6));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        header.addView(back);

        TextView title = new TextView(this);
        title.setText("Pick " + modalityLabel() + " model");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        root.addView(header);

        // Search
        searchBox = new EditText(this);
        searchBox.setHint("Search models…");
        searchBox.setTextColor(TEXT);
        searchBox.setHintTextColor(0xFF484F58);
        searchBox.setTextSize(14);
        searchBox.setBackground(null);
        searchBox.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setCornerRadius(dp(14));
        sbg.setColor(CARD);
        sbg.setStroke(dp(1), BORDER);
        searchBox.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(16), 0, dp(16), dp(10));
        searchBox.setLayoutParams(slp);
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilters(); }
            public void afterTextChanged(Editable s) {}
        });
        root.addView(searchBox);

        // Filter chips
        ScrollView chipScroll = new ScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(16), 0, dp(16), dp(10));
        chipScroll.addView(chipRow);
        root.addView(chipScroll);

        String[] filters = {"Tools", "All", "Free", "Fast", "Cheap", "Vision"};
        for (String f : filters) {
            TextView chip = filterChip(f, chipRow);
            chipRow.addView(chip);
        }

        // Status
        status = new TextView(this);
        status.setText("Loading…");
        status.setTextSize(11);
        status.setTextColor(MUTED);
        status.setPadding(dp(20), dp(6), dp(20), dp(8));
        root.addView(status);

        // Scrollable list
        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        sv.setClipToPadding(false);

        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(dp(16), dp(4), dp(16), dp(20));
        sv.addView(listCol);
        root.addView(sv);

        setContentView(root);

        fetchModels();
    }

    private String modalityLabel() {
        switch (modality) {
            case "image": return "image";
            case "video": return "video";
            case "audio": return "audio";
            default: return "chat";
        }
    }

    private TextView filterChip(String name, LinearLayout parent) {
        TextView c = new TextView(this);
        c.setText(name);
        c.setTextSize(12);
        c.setTypeface(Typeface.DEFAULT_BOLD);
        boolean isActive = name.equals(activeFilter);
        c.setTextColor(isActive ? TEXT : MUTED);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(isActive ? 0xFF1A2436 : CARD);
        bg.setStroke(dp(1), isActive ? BLUE : BORDER);
        c.setBackground(bg);
        c.setClickable(true);
        c.setTag(name);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        c.setLayoutParams(lp);

        c.setOnClickListener(v -> {
            activeFilter = name;
            // Restyle all chips
            for (int i = 0; i < parent.getChildCount(); i++) {
                TextView ch = (TextView) parent.getChildAt(i);
                String tag = (String) ch.getTag();
                GradientDrawable cbg = new GradientDrawable();
                cbg.setCornerRadius(dp(16));
                if (tag.equals(activeFilter)) {
                    cbg.setColor(0xFF1A2436);
                    cbg.setStroke(dp(1), BLUE);
                    ch.setTextColor(TEXT);
                } else {
                    cbg.setColor(CARD);
                    cbg.setStroke(dp(1), BORDER);
                    ch.setTextColor(MUTED);
                }
                ch.setBackground(cbg);
            }
            applyFilters();
        });
        return c;
    }

    private static class ModelRow {
        String id;
        String name;
        boolean free;
        boolean vision;
        boolean tools;
        double priceIn;   // per M tokens
        double priceOut;  // per M tokens
        int context;
        String description;
    }

    private void fetchModels() {
        String key = prefs.getApiKey();
        String base = prefs.getBaseUrl();
        if (key.isEmpty()) {
            status.setText("No API key in Settings");
            status.setTextColor(RED);
            return;
        }

        String url = base + "/models?output_modalities=" + modality;
        Request req = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + key)
            .get().build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call c, IOException e) {
                runOnUiThread(() -> {
                    status.setText("Error: " + e.getMessage());
                    status.setTextColor(RED);
                });
            }
            @Override public void onResponse(Call c, Response r) throws IOException {
                String s = r.body().string();
                try {
                    JSONArray arr = new JSONObject(s).getJSONArray("data");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject m = arr.getJSONObject(i);
                        ModelRow row = new ModelRow();
                        row.id = m.getString("id");

                        // Friendly name from description or id
                        String desc = m.optString("description", "");
                        row.description = desc.length() > 100
                            ? desc.substring(0, 100) + "…" : desc;
                        row.name = m.optString("name", row.id);
                        if (row.name.isEmpty() || row.name.equals(row.id)) {
                            String[] parts = row.id.split("/");
                            row.name = parts[parts.length - 1];
                        }

                        row.free = row.id.endsWith(":free");
                        row.context = m.optInt("context_length", 0);

                        // Pricing
                        JSONObject pricing = m.optJSONObject("pricing");
                        if (pricing != null) {
                            row.priceIn = parsePrice(pricing.optString("prompt", "0"));
                            row.priceOut = parsePrice(pricing.optString("completion", "0"));
                        }

                        // Capabilities — check supported_parameters array
                        JSONArray params = m.optJSONArray("supported_parameters");
                        if (params != null) {
                            for (int j = 0; j < params.length(); j++) {
                                String p = params.optString(j, "");
                                if ("tools".equals(p) || "tool_choice".equals(p)) {
                                    row.tools = true;
                                }
                            }
                        }
                        JSONObject arch = m.optJSONObject("architecture");
                        if (arch != null) {
                            String inputMods = arch.optString("input_modalities", "");
                            row.vision = inputMods.contains("image");
                        }

                        allModels.add(row);
                    }

                    // Sort: free first, then by price
                    Collections.sort(allModels, (a, b) -> {
                        if (a.free != b.free) return a.free ? -1 : 1;
                        if (a.priceOut != b.priceOut)
                            return Double.compare(a.priceOut, b.priceOut);
                        return a.name.compareToIgnoreCase(b.name);
                    });

                    runOnUiThread(() -> {
                        status.setText(allModels.size() + " models available");
                        applyFilters();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        status.setText("Parse error: " + e.getMessage());
                        status.setTextColor(RED);
                    });
                }
            }
        });
    }

    private double parsePrice(String v) {
        try { return Double.parseDouble(v) * 1_000_000; }
        catch (Exception e) { return 0; }
    }

    private void applyFilters() {
        if (listCol == null) return;
        shown.clear();
        String query = searchBox.getText().toString().toLowerCase().trim();

        for (ModelRow m : allModels) {
            if (!query.isEmpty() &&
                !m.id.toLowerCase().contains(query) &&
                !m.name.toLowerCase().contains(query)) continue;

            switch (activeFilter) {
                case "Free":
                    if (!m.free) continue; break;
                case "Fast":
                    if (!m.id.contains("flash") && !m.id.contains("haiku")
                        && !m.id.contains("groq") && !m.id.contains("turbo")
                        && !m.id.contains("mini") && !m.id.contains("nano")) continue;
                    break;
                case "Cheap":
                    if (!m.free && m.priceOut > 1.0) continue; break;
                case "Vision":
                    if (!m.vision) continue; break;
                case "Tools":
                    if (!m.tools) continue; break;
            }
            shown.add(m);
        }

        renderList();
        status.setText(shown.size() + " shown · " + allModels.size() + " total");
    }

    private void renderList() {
        listCol.removeAllViews();
        for (ModelRow m : shown) listCol.addView(modelCard(m));
    }

    private View modelCard(ModelRow m) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(CARD);
        bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);

        // Row 1: name + price tag
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(m.name);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(TEXT);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row1.addView(name);

        TextView priceTag = new TextView(this);
        if (m.free || (m.priceIn == 0 && m.priceOut == 0)) {
            priceTag.setText("FREE");
            priceTag.setTextColor(GREEN);
            GradientDrawable pbg = new GradientDrawable();
            pbg.setCornerRadius(dp(8));
            pbg.setColor(0x1A7EE787);
            priceTag.setBackground(pbg);
        } else {
            priceTag.setText("$" + fmt(m.priceIn) + " / $" + fmt(m.priceOut));
            priceTag.setTextColor(GOLD);
            GradientDrawable pbg = new GradientDrawable();
            pbg.setCornerRadius(dp(8));
            pbg.setColor(0x1AFFB800);
            priceTag.setBackground(pbg);
        }
        priceTag.setTextSize(10);
        priceTag.setTypeface(Typeface.DEFAULT_BOLD);
        priceTag.setPadding(dp(8), dp(3), dp(8), dp(3));
        row1.addView(priceTag);
        card.addView(row1);

        // Row 2: id
        TextView idView = new TextView(this);
        idView.setText(m.id);
        idView.setTextSize(10);
        idView.setTextColor(0xFF666666);
        idView.setTypeface(Typeface.MONOSPACE);
        idView.setPadding(0, dp(3), 0, dp(6));
        card.addView(idView);

        // Row 3: capability badges + context
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER_VERTICAL);

        if (m.tools) row3.addView(badge("🔧 tools", 0xFF7CB3FF));
        if (m.vision) row3.addView(badge("👁 vision", 0xFFA78BFA));
        if (m.context > 0) row3.addView(badge((m.context/1000) + "K ctx", MUTED));

        card.addView(row3);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        card.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("model_id", m.id);
            result.putExtra("model_name", m.name);
            setResult(RESULT_OK, result);
            finish();
        });
        return card;
    }

    private TextView badge(String text, int color) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(10);
        b.setTextColor(color);
        b.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor((color & 0x00FFFFFF) | 0x1A000000);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private String fmt(double v) {
        if (v < 0.01) return String.format("%.3f", v);
        if (v < 1) return String.format("%.2f", v);
        return String.format("%.0f", v);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
