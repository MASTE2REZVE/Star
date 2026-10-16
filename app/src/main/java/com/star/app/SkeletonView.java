package com.star.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;

/**
 * G3 — shared skeleton loader for Models, Sessions, Studio.
 *
 * Mirrors the G2 EmptyStateView pattern: one builder, caller passes its own
 * palette so no new color system is invented (G1 finding F1).
 *
 * Usage (stack screens — Studio tab host, Sessions drawer):
 *
 *   LinearLayout skeleton = SkeletonView.column(ctx, 6, CARD, BORDER, BAR);
 *   contentHost.addView(skeleton);
 *   // data arrives:
 *   contentHost.removeAllViews();
 *   buildRealContent();
 *
 * Usage (ListView screen — Models):
 *
 *   list.setAdapter(new SkeletonView.Adapter(ctx, 6, CARD, BORDER, BAR));
 *   // in onResponse: list.setAdapter(realAdapter);
 *
 * Rules encoded here:
 *  - skeleton shape matches the real row (rounded card, title bar, meta bar)
 *    so the swap is a fill-in, not a layout jump (G1 finding F7);
 *  - shimmer is a single alpha pulse (0.35 <-> 0.9, 900ms, reverse, infinite)
 *    on the bar color only — no gradient sweep, no color invented;
 *  - fixed row count (default 6) — enough to cover a fold, capped so a slow
 *    network never renders an endless wall of bars;
 *  - callers are responsible for removing the skeleton; nothing here holds
 *    state.
 */
public final class SkeletonView {

    public static final int DEFAULT_BG     = 0xFF0F0F0F;
    public static final int DEFAULT_CARD   = 0xFF1A1A1A;
    public static final int DEFAULT_BORDER = 0xFF2A2A2A;
    public static final int DEFAULT_BAR    = 0xFF333333;

    public static final int  DEFAULT_ROWS = 6;
    public static final long SHIMMER_MS   = 900;

    private SkeletonView() {}

    /** One placeholder row: rounded card, a wide title bar, a narrow meta bar. */
    public static View row(Context ctx, int card, int border, int bar, boolean withMeta) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        int p = dp(ctx, 16);
        cell.setPadding(p, dp(ctx, 14), p, dp(ctx, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ctx, 12));
        bg.setColor(card);
        bg.setStroke(dp(ctx, 1), border);
        cell.setBackground(bg);

        cell.addView(barView(ctx, bar, 0.55f, dp(ctx, 13)));   // title-width fraction
        if (withMeta) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 9));
            lp.topMargin = dp(ctx, 9);
            View meta = barView(ctx, bar, 0.30f, dp(ctx, 9));
            meta.setLayoutParams(lp);
            cell.addView(meta);
        }
        shimmer(cell);
        return cell;
    }

    /** A vertical stack of {@code n} placeholder rows, 6dp apart. */
    public static LinearLayout column(Context ctx, int n, int card, int border, int bar) {
        return column(ctx, n, card, border, bar, true);
    }

    public static LinearLayout column(Context ctx, int n, int card, int border, int bar,
                                      boolean withMeta) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < n; i++) {
            View r = row(ctx, card, border, bar, withMeta);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(ctx, 6);
            r.setLayoutParams(lp);
            col.addView(r);
        }
        return col;
    }

    /** Alpha pulse: 0.35 <-> 0.9, 900ms, reverse, infinite. Safe to call twice. */
    public static void shimmer(View v) {
        AlphaAnimation a = new AlphaAnimation(0.35f, 0.9f);
        a.setDuration(SHIMMER_MS);
        a.setRepeatMode(Animation.REVERSE);
        a.setRepeatCount(Animation.INFINITE);
        v.startAnimation(a);
    }

    /** Fixed-count adapter for ListView screens (Models) during fetch. */
    public static final class Adapter extends BaseAdapter {
        private final Context ctx;
        private final int n, card, border, bar;

        public Adapter(Context ctx, int n, int card, int border, int bar) {
            this.ctx = ctx; this.n = n;
            this.card = card; this.border = border; this.bar = bar;
        }

        @Override public int getCount() { return n; }
        @Override public Object getItem(int p) { return p; }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int p, View v, ViewGroup parent) {
            View cell = row(ctx, card, border, bar, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cell.setLayoutParams(lp);
            return cell;
        }
    }

    private static View barView(Context ctx, int bar, float widthFraction, int heightDp) {
        View v = new View(ctx);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(ctx, 4));
        g.setColor(bar);
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightDp);
        lp.weight = widthFraction;
        v.setLayoutParams(lp);
        return v;
    }

    private static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
