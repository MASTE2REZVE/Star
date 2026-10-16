package com.star.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * K-B7 — Effect grid with real Concat preview thumbnails.
 *
 * Reads the Concat catalogue through {@link ConcatBridge#call}, renders one
 * still per effect with {@code concat-cli preview} (cached under
 * {@code /sdcard/Star/effects/<id>.jpg}) and shows them in a 3-column dark
 * grid. Tapping a cell returns the effect id to the caller via setResult.
 */
public class EffectGridActivity extends Activity {

    public static final String EXTRA_EFFECT_ID = "effect_id";

    private static final int BG     = 0xFF0F0F0F;
    private static final int CARD   = 0xFF1A1A1A;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT   = 0xFFECECEC;
    private static final int MUTED  = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int RED    = 0xFFF85149;

    private static final String SD_DIR = "/sdcard/Star/effects";
    private static final String BASE   = SD_DIR + "/_base.jpg";
    private static final String CONCAT  = "/data/data/com.termux/files/home/bin/concat-cli";
    private static final long CATALOGUE_TIMEOUT_MS = 30000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService catalogueIo = Executors.newSingleThreadExecutor();
    private final ExecutorService previewIo = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(64);
    private final Set<String> queued = Collections.synchronizedSet(new HashSet<String>());
    private final AtomicInteger ready = new AtomicInteger(0);
    private final List<Effect> effects = new ArrayList<>();

    private FrameLayout host;
    private TextView status;
    private File localDir;

    public static Intent intent(Context c) {
        return new Intent(c, EffectGridActivity.class);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        File ext = getExternalFilesDir(null);
        if (ext == null) ext = getCacheDir();
        localDir = new File(ext, "effects");
        if (!localDir.exists()) localDir.mkdirs();
        buildUI();
        loadCatalogue();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        catalogueIo.shutdownNow();
        previewIo.shutdownNow();
    }

    // ===================== UI =====================

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

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

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Effects");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        titles.addView(title);

        status = new TextView(this);
        status.setText("Loading\u2026");
        status.setTextSize(11);
        status.setTextColor(MUTED);
        titles.addView(status);
        top.addView(titles);
        root.addView(top);

        host = new FrameLayout(this);
        host.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(host);

        setContentView(root);
    }

    private void showSkeleton() {
        host.removeAllViews();
        status.setText("Loading\u2026");
        status.setTextColor(MUTED);
        LinearLayout sk = SkeletonView.column(this, 6, CARD, BORDER, 0xFF333333);
        sk.setPadding(dp(14), dp(6), dp(14), dp(14));
        host.addView(sk);
    }

    private void showEmpty(String body) {
        host.removeAllViews();
        status.setText("No effects");
        status.setTextColor(MUTED);
        View empty = new EmptyStateView.Builder(this)
                .palette(BG, CARD, BORDER, TEXT, MUTED, ACCENT)
                .glyph("\u2734")
                .title("No effects yet")
                .body(body)
                .build();
        host.addView(empty);
    }

    private void showError(String msg) {
        host.removeAllViews();
        status.setText("Could not load effects");
        status.setTextColor(RED);
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextSize(12);
        t.setTextColor(MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24), dp(24), dp(24), dp(24));
        host.addView(t);
    }

    private void showGrid() {
        host.removeAllViews();
        status.setTextColor(MUTED);

        GridView grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(6));
        grid.setVerticalSpacing(dp(6));
        grid.setPadding(dp(12), dp(6), dp(12), dp(12));
        grid.setClipToPadding(false);
        grid.setBackgroundColor(BG);
        grid.setSelector(android.R.color.transparent);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            Effect e = effects.get(position);
            Intent data = new Intent();
            data.putExtra(EXTRA_EFFECT_ID, e.id);
            setResult(RESULT_OK, data);
            finish();
        });
        host.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateCount();
    }

    private void updateCount() {
        status.setText(ready.get() + "/" + effects.size() + " previews");
    }

    // ===================== data =====================

    private void loadCatalogue() {
        showSkeleton();
        catalogueIo.execute(() -> {
            ConcatBridge.Result r = ConcatBridge.call("catalogue.list", null, CATALOGUE_TIMEOUT_MS);
            if (r == null || !r.ok || r.raw == null) {
                final String err = (r == null || r.error == null) ? "no result" : r.error;
                ui.post(() -> showError(err));
                return;
            }
            final List<Effect> parsed = parse(r.raw);
            if (parsed.isEmpty()) {
                ui.post(() -> showEmpty("The Concat catalogue is empty."));
                return;
            }
            ensureBase();
            ui.post(() -> {
                effects.clear();
                effects.addAll(parsed);
                showGrid();
            });
        });
    }

    private List<Effect> parse(String raw) {
        List<Effect> out = new ArrayList<>();
        try {
            String s = raw.trim();
            JSONArray arr;
            if (s.startsWith("[")) {
                arr = new JSONArray(s);
            } else {
                JSONObject o = new JSONObject(s);
                arr = o.getJSONArray("result");
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                String id = e.optString("id", "");
                if (id.isEmpty()) continue;
                String name = e.optString("name", id);
                String category = e.optString("category", "");
                out.add(new Effect(id, name, category));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void ensureBase() {
        if (fileExists(BASE)) return;
        Shell.run("mkdir -p '" + SD_DIR + "'");
        Shell.run("ffmpeg -y -loglevel error -f lavfi -i testsrc=size=480x270:rate=1 -frames:v 1 '" + BASE + "'");
    }

    private boolean fileExists(String path) {
        Shell.Result r = Shell.run("test -f '" + path + "' && echo 1 || echo 0");
        return r != null && r.output != null && r.output.trim().endsWith("1");
    }

    // ===================== previews =====================

    private void enqueuePreview(final Effect e) {
        if (!queued.add(e.id)) return;
        previewIo.execute(() -> {
            generate(e);
            final boolean ok = new File(localDir, e.id + ".jpg").exists();
            if (ok) ready.incrementAndGet();
            ui.post(this::onPreviewDone);
        });
    }

    private void onPreviewDone() {
        if (adapter != null) adapter.notifyDataSetChanged();
        updateCount();
    }

    private void generate(Effect e) {
        File local = new File(localDir, e.id + ".jpg");
        if (local.exists() && local.length() > 0) return;
        String sdOut = SD_DIR + "/" + e.id + ".jpg";
        if (!fileExists(sdOut)) {
            Shell.run("mkdir -p '" + SD_DIR + "'");
            String cli = fileExists(CONCAT) || shellWhich(CONCAT) ? CONCAT : "concat-cli";
            Shell.run(cli + " preview '" + BASE + "' '" + sdOut + "' --effect '" + e.id + "'");
        }
        if (fileExists(sdOut)) {
            Shell.run("cp -f '" + sdOut + "' '" + local.getAbsolutePath() + "'");
        }
    }

    private boolean shellWhich(String path) {
        Shell.Result r = Shell.run("command -v '" + path + "' 2>/dev/null || true");
        return r != null && r.output != null && r.output.trim().length() > 0;
    }

    private Bitmap decode(File f) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 240
                    && bounds.outHeight / (sample * 2) >= 135) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private void loadThumb(final ImageView iv, final Effect e) {
        final File f = new File(localDir, e.id + ".jpg");
        if (!f.exists()) {
            iv.setImageBitmap(null);
            enqueuePreview(e);
            return;
        }
        Bitmap cached = thumbs.get(e.id);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageBitmap(null);
        iv.setTag(e.id);
        previewIo.execute(() -> {
            final Bitmap bm = decode(f);
            if (bm != null) {
                thumbs.put(e.id, bm);
                ui.post(() -> {
                    if (e.id.equals(iv.getTag())) iv.setImageBitmap(bm);
                });
            }
        });
    }

    // ===================== adapter =====================

    private BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return effects.size(); }
        @Override public Object getItem(int p) { return effects.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int p, View convert, ViewGroup parent) {
            Effect e = effects.get(p);

            LinearLayout cell = new LinearLayout(EffectGridActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);

            ImageView iv = new ImageView(EffectGridActivity.this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(rounded(CARD, BORDER, 10));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
            iv.setLayoutParams(ilp);
            cell.addView(iv);

            TextView name = new TextView(EffectGridActivity.this);
            name.setText(e.name);
            name.setTextSize(11);
            name.setTextColor(MUTED);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(4);
            name.setLayoutParams(tlp);
            cell.addView(name);

            loadThumb(iv, e);
            return cell;
        }
    };

    // ===================== helpers =====================

    private GradientDrawable rounded(int fill, int border, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radius));
        g.setColor(fill);
        g.setStroke(dp(1), border);
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class Effect {
        final String id, name, category;
        Effect(String id, String name, String category) {
            this.id = id; this.name = name; this.category = category;
        }
    }
}
