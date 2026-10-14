package com.star.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ModelsActivity extends Activity {
    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1A1A1A;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int GREEN = 0xFF7EE787;
    private static final int GOLD = 0xFFFFB800;
    private static final int RED = 0xFFF85149;

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(28), dp(22), dp(16));

        TextView title = new TextView(this);
        title.setText("Models");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        header.addView(title);

        TextView status = new TextView(this);
        status.setText("Loading…");
        status.setTextSize(12);
        status.setTextColor(MUTED);
        status.setPadding(0, dp(4), 0, 0);
        header.addView(status);
        root.addView(header);

        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(null);
        list.setDividerHeight(dp(6));
        list.setPadding(dp(14), 0, dp(14), dp(14));
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        fetch(prefs, status, list);
    }

    private void fetch(Prefs prefs, TextView status, ListView list) {
        String key = prefs.getApiKey();
        String base = prefs.getBaseUrl();
        if (key.isEmpty()) { status.setText("No API key"); status.setTextColor(RED); return; }

        Request req = new Request.Builder()
            .url(base + "/models")
            .header("Authorization", "Bearer " + key)
            .get().build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call c, IOException e) {
                runOnUiThread(() -> { status.setText("Error: " + e.getMessage()); status.setTextColor(RED); });
            }
            @Override public void onResponse(Call c, Response r) throws IOException {
                String s = r.body().string();
                try {
                    JSONArray arr = new JSONObject(s).getJSONArray("data");
                    List<ModelRow> rows = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject m = arr.getJSONObject(i);
                        String id = m.getString("id");
                        boolean free = id.endsWith(":free")
                            || id.contains("/free")
                            || m.optJSONObject("pricing") != null
                                && "0".equals(m.optJSONObject("pricing").optString("prompt", "-1"));
                        rows.add(new ModelRow(id, free));
                    }
                    rows.sort((a, b) -> {
                        if (a.free != b.free) return a.free ? -1 : 1;
                        return a.id.compareToIgnoreCase(b.id);
                    });

                    runOnUiThread(() -> {
                        status.setText(rows.size() + " models · free first");
                        status.setTextColor(MUTED);

                        BaseAdapter ad = new BaseAdapter() {
                            @Override public int getCount() { return rows.size(); }
                            @Override public Object getItem(int p) { return rows.get(p); }
                            @Override public long getItemId(int p) { return p; }
                            @Override public View getView(int p, View v, ViewGroup parent) {
                                LinearLayout cell = new LinearLayout(ModelsActivity.this);
                                cell.setOrientation(LinearLayout.VERTICAL);
                                cell.setPadding(dp(16), dp(14), dp(16), dp(14));
                                GradientDrawable bg = new GradientDrawable();
                                bg.setCornerRadius(dp(12));
                                bg.setColor(CARD);
                                bg.setStroke(dp(1), BORDER);
                                cell.setBackground(bg);

                                ModelRow row = rows.get(p);
                                String tag = row.free ? " FREE" : " PAID";
                                TextView name = new TextView(ModelsActivity.this);
                                name.setText(row.id);
                                name.setTextSize(14);
                                name.setTextColor(TEXT);
                                cell.addView(name);

                                TextView tagView = new TextView(ModelsActivity.this);
                                tagView.setText(row.free ? "● FREE" : "● PAID");
                                tagView.setTextSize(11);
                                tagView.setTextColor(row.free ? GREEN : GOLD);
                                tagView.setPadding(0, dp(3), 0, 0);
                                cell.addView(tagView);
                                return cell;
                            }
                        };
                        list.setAdapter(ad);
                        list.setOnItemClickListener((par, view, pos, id) -> {
                            ModelRow row = rows.get(pos);
                            prefs.setModel(row.id);
                            Toast.makeText(ModelsActivity.this,
                                "Selected: " + row.id, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { status.setText("Parse error"); status.setTextColor(RED); });
                }
            }
        });
    }

    private static class ModelRow {
        String id; boolean free;
        ModelRow(String id, boolean free) { this.id = id; this.free = free; }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
