package com.star.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.*;

/** Model picker with sort chips: price / coding / smartness / context. */
public class ModelPickerDialog {
    public interface OnPick { void onPick(String modelId); }

    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int GREEN = 0xFF7EE787;
    private static final int ORANGE = 0xFFF0883E;
    private static final int PURPLE = 0xFFA78BFA;
    private static final int RED = 0xFFF85149;

    /** Sort modes. */
    public static final int SORT_PRICE = 0;
    public static final int SORT_CODING = 1;
    public static final int SORT_SMART = 2;
    public static final int SORT_CONTEXT = 3;
    public static final int SORT_BALANCE = 4;

    private static final String[] SORT_LABELS = {
        "Cheapest", "Best coding", "Smartest", "Biggest context", "Balanced"
    };

    
    
    public static void show(final Activity act, final Prefs prefs,
                            final String current, final OnPick onPick) {
        final List<ModelCatalog.Model> all = ModelCatalog.cached();
        if (all == null || all.isEmpty()) {
            Toast.makeText(act, "Loading model list…", Toast.LENGTH_SHORT).show();
            ModelCatalog.refresh(act, prefs, new ModelCatalog.Cb() {
                @Override public void onReady(List<ModelCatalog.Model> l) {
                    act.runOnUiThread(() -> build(act, prefs, current, l, onPick));
                }
                @Override public void onError(String e) {
                    act.runOnUiThread(() -> Toast.makeText(act,
                        "Failed: " + e, Toast.LENGTH_LONG).show());
                }
            });
        } else {
            build(act, prefs, current, all, onPick);
        }
    }

    private static void build(final Activity act, final Prefs prefs,
                              final String current, final List<ModelCatalog.Model> catalog,
                              final OnPick onPick) {

        final int dp = (int) act.getResources().getDisplayMetrics().density;
        final int[] sortMode = { SORT_BALANCE };
        final boolean[] onlyFree = { false };
        final boolean[] onlyTools = { false };

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF161616);
        root.setPadding(dp*16, dp*16, dp*16, dp*8);

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        final Runnable[] rebuild = new Runnable[1];

        // ---- sort chips ----
        TextView sortLabel = tiny(act, "SORT BY");
        root.addView(sortLabel);

        LinearLayout sortRow = new LinearLayout(act);
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        final List<TextView> sortBtns = new ArrayList<>();
        for (int i = 0; i < SORT_LABELS.length; i++) {
            final int idx = i;
            TextView b = chip(act, SORT_LABELS[i]);
            sortBtns.add(b);
            b.setOnClickListener(v -> {
                sortMode[0] = idx;
                styleChips(sortBtns, idx);
                if (rebuild[0] != null) rebuild[0].run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp*6;
            b.setLayoutParams(lp);
            sortRow.addView(b);
        }
        HorizontalScrollView hsv = new HorizontalScrollView(act);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(sortRow);
        root.addView(hsv);
        styleChips(sortBtns, sortMode[0]);

        // ---- filter row ----
        LinearLayout filterRow = new LinearLayout(act);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, dp*10, 0, dp*6);

        final TextView freeChip = chip(act, "Free only");
        final TextView toolsChip = chip(act, "Has tools");
        filterRow.addView(freeChip);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.leftMargin = dp*6;
        toolsChip.setLayoutParams(flp);
        filterRow.addView(toolsChip);
        root.addView(filterRow);

        // ---- search ----
        EditText search = new EditText(act);
        search.setHint("search…");
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setBackgroundColor(0xFF1E1E1E);
        search.setPadding(dp*12, dp*10, dp*12, dp*10);
        root.addView(search);

        // ---- list ----
        final LinearLayout listCol = new LinearLayout(act);
        listCol.setOrientation(LinearLayout.VERTICAL);

        rebuild[0] = () -> {
            listCol.removeAllViews();
            List<ModelCatalog.Model> list = new ArrayList<>();
            for (ModelCatalog.Model m : catalog) {
                if (onlyFree[0] && !m.free) continue;
                if (onlyTools[0] && !m.tools) continue;
                list.add(m);
            }

            final int sm = sortMode[0];
            Collections.sort(list, (a, b) -> {
                switch (sm) {
                    case SORT_PRICE:
                        return Double.compare(a.priceOut, b.priceOut);
                    case SORT_CODING: {
                        // models with benchmark data first
                        boolean ha = a.codingIndex >= 0, hb = b.codingIndex >= 0;
                        if (ha != hb) return ha ? -1 : 1;
                        if (ha && hb) {
                            int cmp = Double.compare(b.codingIndex, a.codingIndex);
                            if (cmp != 0) return cmp;
                        }
                        return Double.compare(a.priceOut, b.priceOut);
                    }
                    case SORT_SMART: {
                        boolean ha = a.intelligenceIndex >= 0, hb = b.intelligenceIndex >= 0;
                        if (ha != hb) return ha ? -1 : 1;
                        if (ha && hb) {
                            int cmp = Double.compare(b.intelligenceIndex, a.intelligenceIndex);
                            if (cmp != 0) return cmp;
                        }
                        return Double.compare(a.priceOut, b.priceOut);
                    }
                    case SORT_CONTEXT:
                        return Integer.compare(b.contextLength, a.contextLength);
                    case SORT_BALANCE:
                    default: {
                        boolean ha = a.intelligenceIndex >= 0, hb = b.intelligenceIndex >= 0;
                        if (ha != hb) return ha ? -1 : 1;
                        double pa = a.free ? 0.01 : a.priceOut + 0.01;
                        double pb = b.free ? 0.01 : b.priceOut + 0.01;
                        double qa = (a.intelligenceIndex >= 0 ? a.intelligenceIndex : 50) / Math.log(pa + 2.0);
                        double qb = (b.intelligenceIndex >= 0 ? b.intelligenceIndex : 50) / Math.log(pb + 2.0);
                        return Double.compare(qb, qa);
                    }
                }
            });

            String q = search.getText().toString().trim().toLowerCase();
            int shown = 0;
            for (ModelCatalog.Model m : list) {
                if (!q.isEmpty() && !m.id.toLowerCase().contains(q)) continue;
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp*10, dp*12, dp*10, dp*12);
                row.setClickable(true);

                LinearLayout col = new LinearLayout(act);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView name = new TextView(act);
                name.setText(m.id);
                name.setTextSize(12);
                name.setTypeface(Typeface.MONOSPACE);
                name.setTextColor(TEXT);
                name.setMaxLines(2);
                col.addView(name);

                // stat line
                StringBuilder stats = new StringBuilder();
                int ctx = m.contextLength / 1000;
                if (ctx > 0) stats.append(ctx).append("k ctx");
                if (m.tools) stats.append("  ·  tools");
                if (m.vision) stats.append("  ·  vision");
                if (m.codingIndex >= 0) {
                    stats.append("  ·  code ").append((int) m.codingIndex);
                }
                if (m.intelligenceIndex >= 0) {
                    stats.append("  ·  int ").append((int) m.intelligenceIndex);
                }
                if (m.codingIndex < 0 && m.intelligenceIndex < 0) {
                    stats.append("  ·  no benchmark");
                }
                TextView stat = new TextView(act);
                stat.setText(stats.toString());
                stat.setTextSize(9);
                stat.setTextColor(MUTED);
                col.addView(stat);
                row.addView(col);

                LinearLayout priceCol = new LinearLayout(act);
                priceCol.setOrientation(LinearLayout.VERTICAL);
                priceCol.setGravity(Gravity.END);
                LinearLayout.LayoutParams pclp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                pclp.leftMargin = dp*8;
                priceCol.setLayoutParams(pclp);

                int color = m.free ? GREEN : ORANGE;
                if (m.free) {
                    TextView tag = new TextView(act);
                    tag.setText("FREE");
                    tag.setTextSize(11);
                    tag.setTypeface(Typeface.DEFAULT_BOLD);
                    tag.setTextColor(color);
                    tag.setPadding(dp*8, dp*3, dp*8, dp*3);
                    GradientDrawable tbg = new GradientDrawable();
                    tbg.setCornerRadius(dp*6);
                    tbg.setColor(0x1A7EE787);
                    tag.setBackground(tbg);
                    priceCol.addView(tag);
                } else {
                    // real input price
                    TextView pin = new TextView(act);
                    pin.setText("in  $" + fmt(m.priceIn) + "/M");
                    pin.setTextSize(10);
                    pin.setTypeface(Typeface.MONOSPACE);
                    pin.setTextColor(ORANGE);
                    priceCol.addView(pin);

                    // real output price
                    TextView pout = new TextView(act);
                    pout.setText("out $" + fmt(m.priceOut) + "/M");
                    pout.setTextSize(10);
                    pout.setTypeface(Typeface.MONOSPACE);
                    pout.setTextColor(ORANGE);
                    priceCol.addView(pout);

                    // combined = in + out per million tokens
                    TextView both = new TextView(act);
                    both.setText("sum $" + fmt(m.priceIn + m.priceOut) + "/M");
                    both.setTextSize(9);
                    both.setTypeface(Typeface.MONOSPACE);
                    both.setTextColor(MUTED);
                    priceCol.addView(both);

                    // cache read price (real, from OpenRouter)
                    if (m.priceCacheRead >= 0) {
                        TextView cache = new TextView(act);
                        cache.setText("cache $" + fmt(m.priceCacheRead) + "/M");
                        cache.setTextSize(9);
                        cache.setTypeface(Typeface.MONOSPACE);
                        cache.setTextColor(0xFF7EE787); // green
                        priceCol.addView(cache);
                    }
                }
                row.addView(priceCol);

                row.setOnClickListener(v -> {
                    onPick.onPick(m.id);
                    if (ref[0] != null) ref[0].dismiss();
                });
                row.setOnLongClickListener(v -> {
                    showProviders(act, prefs, m);
                    return true;
                });
                listCol.addView(row);
                shown++;
                if (shown >= 120) break;
            }
            if (shown == 0) {
                TextView empty = new TextView(act);
                empty.setText("No models match.");
                empty.setTextColor(MUTED);
                empty.setPadding(dp*8, dp*20, dp*8, dp*20);
                listCol.addView(empty);
            }
        };

        freeChip.setOnClickListener(v -> {
            onlyFree[0] = !onlyFree[0];
            styleFilterChip(freeChip, onlyFree[0]);
            rebuild[0].run();
        });
        toolsChip.setOnClickListener(v -> {
            onlyTools[0] = !onlyTools[0];
            styleFilterChip(toolsChip, onlyTools[0]);
            rebuild[0].run();
        });

        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) { rebuild[0].run(); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        ScrollView sv = new ScrollView(act);
        sv.addView(listCol);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp*10;
        sv.setLayoutParams(slp);
        root.addView(sv);

        TextView cancel = new TextView(act);
        cancel.setText("CANCEL");
        cancel.setTextSize(12);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setTextColor(MUTED);
        cancel.setPadding(dp*16, dp*12, dp*16, dp*12);
        cancel.setGravity(Gravity.END);
        cancel.setClickable(true);
        root.addView(cancel);

        ref[0] = new android.app.AlertDialog.Builder(act)
            .setView(root).create();
        if (ref[0].getWindow() != null) {
            ref[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        cancel.setOnClickListener(v -> ref[0].dismiss());
        ref[0].show();
        if (ref[0].getWindow() != null) {
            ref[0].getWindow().setLayout(
                (int)(act.getResources().getDisplayMetrics().widthPixels * 0.94),
                (int)(act.getResources().getDisplayMetrics().heightPixels * 0.88));
        }

        rebuild[0].run();
    }

    private static void styleChips(List<TextView> chips, int active) {
        for (int i = 0; i < chips.size(); i++) {
            styleChip(chips.get(i), i == active);
        }
    }

    private static void styleChip(TextView t, boolean on) {
        t.setTextColor(on ? ACCENT : MUTED);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius((int)(14 * t.getResources().getDisplayMetrics().density));
        g.setColor(on ? 0x1A7CB3FF : CARD);
        g.setStroke((int)(1 * t.getResources().getDisplayMetrics().density), on ? ACCENT : BORDER);
        t.setBackground(g);
    }

    private static void styleFilterChip(TextView t, boolean on) {
        t.setTextColor(on ? GREEN : MUTED);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius((int)(14 * t.getResources().getDisplayMetrics().density));
        g.setColor(on ? 0x1A7EE787 : CARD);
        g.setStroke((int)(1 * t.getResources().getDisplayMetrics().density), on ? GREEN : BORDER);
        t.setBackground(g);
    }

    private static TextView chip(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding((int)(14 * a.getResources().getDisplayMetrics().density),
                     (int)(8 * a.getResources().getDisplayMetrics().density),
                     (int)(14 * a.getResources().getDisplayMetrics().density),
                     (int)(8 * a.getResources().getDisplayMetrics().density));
        t.setClickable(true);
        return t;
    }

    /** Format a price-per-million with enough precision to be useful. */
    private static String fmt(double v) {
        if (v <= 0) return "0.00";
        if (v < 0.01) return String.format(java.util.Locale.US, "%.4f", v);
        if (v < 1)    return String.format(java.util.Locale.US, "%.3f", v);
        if (v < 100)  return String.format(java.util.Locale.US, "%.2f", v);
        return String.format(java.util.Locale.US, "%.0f", v);
    }

    private static void showProviders(final Activity act, final Prefs prefs,
                                      final ModelCatalog.Model m) {
        final int dp = (int) act.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0F0F);
        root.setPadding(dp*12, dp*14, dp*12, dp*8);

        TextView title = new TextView(act);
        title.setText(m.id);
        title.setTextSize(13);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextColor(TEXT);
        root.addView(title);

        final String pinned = prefs.getModelProvider(m.id);
        String subText = "Live prices per provider";
        if (pinned != null && !pinned.isEmpty()) {
            subText += "  ·  pinned: " + pinned;
        } else {
            subText += "  ·  no provider pinned yet";
        }
        TextView sub = new TextView(act);
        sub.setText(subText);
        sub.setTextSize(10);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp*2, 0, dp*10);
        root.addView(sub);

        // ---- Header ----
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp*6, dp*6, dp*6, dp*6);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setCornerRadius(dp*6);
        hbg.setColor(0xFF1A1A1A);
        header.setBackground(hbg);

        addCellL(act, header, "PROVIDER",   35f, MUTED, 9, false);
        addCellR(act, header, "IN",         14f, MUTED, 9, true);
        addCellR(act, header, "OUT",        14f, MUTED, 9, true);
        addCellR(act, header, "CACHE",      14f, MUTED, 9, true);
        addCellR(act, header, "UP%",         8f, MUTED, 9, true);
        addCellR(act, header, "PICKED",     15f, MUTED, 9, true);
        root.addView(header);

        // ---- List ----
        final LinearLayout listCol = new LinearLayout(act);
        listCol.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(act);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp*4;
        sv.setLayoutParams(slp);
        sv.addView(listCol);
        root.addView(sv);

        TextView loading = new TextView(act);
        loading.setText("Loading providers…");
        loading.setTextColor(MUTED);
        loading.setTextSize(12);
        loading.setPadding(dp*8, dp*20, dp*8, dp*20);
        listCol.addView(loading);

        TextView cancel = new TextView(act);
        cancel.setText("CLOSE");
        cancel.setTextSize(12);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setTextColor(ACCENT);
        cancel.setPadding(dp*16, dp*12, dp*16, dp*12);
        cancel.setGravity(Gravity.END);
        cancel.setClickable(true);
        root.addView(cancel);

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        ref[0] = new android.app.AlertDialog.Builder(act).setView(root).create();
        if (ref[0].getWindow() != null) {
            ref[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        cancel.setOnClickListener(v -> ref[0].dismiss());

        ref[0].show();
        if (ref[0].getWindow() != null) {
            ref[0].getWindow().setLayout(
                (int)(act.getResources().getDisplayMetrics().widthPixels * 0.98),
                (int)(act.getResources().getDisplayMetrics().heightPixels * 0.85));
        }

        ModelCatalog.fetchProviders(prefs, m.id, true, new ModelCatalog.ProviderCb() {
            @Override public void onOk(final java.util.List<ModelCatalog.Provider> providers) {
                act.runOnUiThread(() -> {
                    listCol.removeAllViews();
                    if (providers.isEmpty()) {
                        TextView empty = new TextView(act);
                        empty.setText("No providers listed.");
                        empty.setTextColor(MUTED);
                        empty.setPadding(dp*8, dp*20, dp*8, dp*20);
                        listCol.addView(empty);
                        return;
                    }

                    double cheapestIn = Double.MAX_VALUE;
                    for (ModelCatalog.Provider pv : providers) {
                        if (pv.priceIn > 0 && pv.priceIn < cheapestIn) cheapestIn = pv.priceIn;
                    }

                    for (int i = 0; i < providers.size(); i++) {
                        final ModelCatalog.Provider pv = providers.get(i);
                        final boolean cheapest = (pv.priceIn > 0
                            && Math.abs(pv.priceIn - cheapestIn) < 1e-9);
                        final boolean isPicked = (pinned != null && !pinned.isEmpty()
                            && pinned.equalsIgnoreCase(pv.name));

                        LinearLayout row = new LinearLayout(act);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setPadding(dp*6, dp*10, dp*6, dp*10);
                        row.setClickable(true);

                        GradientDrawable rbg = new GradientDrawable();
                        rbg.setCornerRadius(dp*8);
                        // Pick highlight = blue accent. Cheapest but not picked = neutral.
                        if (isPicked) {
                            rbg.setColor(0x1A7CB3FF);
                            rbg.setStroke(dp*1, ACCENT);
                        } else if (i % 2 == 0) {
                            rbg.setColor(0xFF141414);
                        } else {
                            rbg.setColor(0xFF0F0F0F);
                        }
                        row.setBackground(rbg);

                        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                        rowLp.bottomMargin = dp*4;
                        row.setLayoutParams(rowLp);

                        // PROVIDER column
                        LinearLayout pcol = new LinearLayout(act);
                        pcol.setOrientation(LinearLayout.VERTICAL);
                        LinearLayout.LayoutParams pclp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 35f);
                        pcol.setLayoutParams(pclp);

                        String disp = (pv.providerName != null && !pv.providerName.isEmpty())
                            ? pv.providerName : pv.name;

                        // name + inline badges
                        LinearLayout nameRow = new LinearLayout(act);
                        nameRow.setOrientation(LinearLayout.HORIZONTAL);
                        nameRow.setGravity(Gravity.CENTER_VERTICAL);

                        TextView pn = new TextView(act);
                        pn.setText(disp);
                        pn.setTextSize(12);
                        pn.setTypeface(Typeface.DEFAULT_BOLD);
                        pn.setTextColor(isPicked ? ACCENT : TEXT);
                        nameRow.addView(pn);

                        if (cheapest) {
                            TextView cb = new TextView(act);
                            cb.setText(" cheapest input ");
                            cb.setTextSize(9);
                            cb.setTypeface(Typeface.DEFAULT_BOLD);
                            cb.setTextColor(GREEN);
                            cb.setPadding(dp*4, dp*1, dp*4, dp*1);
                            GradientDrawable cbg = new GradientDrawable();
                            cbg.setCornerRadius(dp*4);
                            cbg.setColor(0x1A7EE787);
                            cb.setBackground(cbg);
                            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                            clp.leftMargin = dp*6;
                            cb.setLayoutParams(clp);
                            nameRow.addView(cb);
                        }
                        pcol.addView(nameRow);

                        // meta line
                        String meta = "";
                        if (pv.context > 0) meta += (pv.context / 1000) + "k ctx";
                        if (pv.quantization != null && !pv.quantization.isEmpty()) {
                            if (!meta.isEmpty()) meta += "  ·  ";
                            meta += pv.quantization;
                        }
                        if (!meta.isEmpty()) {
                            TextView pm = new TextView(act);
                            pm.setText(meta);
                            pm.setTextSize(9);
                            pm.setTypeface(Typeface.MONOSPACE);
                            pm.setTextColor(MUTED);
                            pm.setPadding(0, dp*2, 0, 0);
                            pcol.addView(pm);
                        }
                        row.addView(pcol);

                        // Prices
                        addCellR(act, row, "$" + fmt(pv.priceIn),  14f, 0xFFFFB800, 12, true);
                        addCellR(act, row, "$" + fmt(pv.priceOut), 14f, TEXT,      11, true);
                        String cacheTxt = pv.cacheRead >= 0
                            ? "$" + fmt(pv.cacheRead) : "—";
                        addCellR(act, row, cacheTxt, 14f,
                            pv.cacheRead >= 0 ? GREEN : MUTED, 11, true);

                        // UP% — real value from uptime_last_30m (0-100)
                        String upTxt;
                        int upColor;
                        if (pv.uptime < 0) {
                            upTxt = "—"; upColor = MUTED;
                        } else {
                            upTxt = String.format(java.util.Locale.US, "%.0f", pv.uptime);
                            if (pv.uptime >= 99) upColor = GREEN;
                            else if (pv.uptime >= 90) upColor = ORANGE;
                            else upColor = RED;
                        }
                        addCellR(act, row, upTxt, 8f, upColor, 10, true);

                        // PICKED indicator
                        TextView pk = new TextView(act);
                        pk.setText(isPicked ? "✓ yes" : "pick");
                        pk.setTextSize(10);
                        pk.setTypeface(Typeface.DEFAULT_BOLD);
                        pk.setTextColor(isPicked ? ACCENT : MUTED);
                        pk.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                        pk.setPadding(dp*2, 0, dp*2, 0);
                        LinearLayout.LayoutParams pklp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 15f);
                        pk.setLayoutParams(pklp);
                        row.addView(pk);

                        row.setOnClickListener(v -> {
                            if (isPicked) {
                                // unpin
                                prefs.setModelProvider(m.id, "");
                                Toast.makeText(act,
                                    "Unpinned " + disp, Toast.LENGTH_SHORT).show();
                            } else {
                                prefs.setModelProvider(m.id, pv.name);
                                Toast.makeText(act,
                                    "Pinned to " + disp, Toast.LENGTH_SHORT).show();
                            }
                            if (ref[0] != null) ref[0].dismiss();
                        });
                        listCol.addView(row);
                    }
                });
            }
            @Override public void onError(final String err) {
                act.runOnUiThread(() -> {
                    listCol.removeAllViews();
                    TextView e = new TextView(act);
                    e.setText("Failed: " + err);
                    e.setTextColor(RED);
                    e.setTextSize(12);
                    e.setPadding(dp*8, dp*20, dp*8, dp*20);
                    listCol.addView(e);
                });
            }
        });
    }

    /** Right-aligned numeric cell. */
    private static void addCellR(Activity act, LinearLayout parent, String text,
                                 float weight, int color, int textSize, boolean mono) {
        int dp = (int) act.getResources().getDisplayMetrics().density;
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(textSize);
        if (mono) t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(color);
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setPadding(dp*2, 0, dp*2, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        t.setLayoutParams(lp);
        parent.addView(t);
    }

    /** Left-aligned text cell. */
    private static void addCellL(Activity act, LinearLayout parent, String text,
                                 float weight, int color, int textSize, boolean mono) {
        int dp = (int) act.getResources().getDisplayMetrics().density;
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(textSize);
        if (mono) t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(color);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.setPadding(dp*2, 0, dp*2, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        t.setLayoutParams(lp);
        parent.addView(t);
    }

    private static TextView tiny(Activity a, String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(10);
        t.setTextColor(MUTED);
        t.setLetterSpacing(0.15f);
        t.setPadding(0, 0, 0, (int)(6 * a.getResources().getDisplayMetrics().density));
        return t;
    }
}
