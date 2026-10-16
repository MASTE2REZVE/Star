package com.star.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

/**
 * G2 — shared empty-state builder for Chat, Studio, Models, Sessions.
 *
 * Usage (per screen):
 *
 *   View empty = new EmptyStateView.Builder(ctx)
 *       .glyph("\u2734")                       // one large glyph, or pass a drawable id via .icon()
 *       .title("No models yet")
 *       .body("Add a provider key in Settings and pull the catalogue.")
 *       .cta("Open Settings", v -> startActivity(new Intent(ctx, SettingsActivity.class)))
 *       .build();
 *   container.addView(empty);
 *
 *   // ListView screens (Models): list.setEmptyView(empty); then addView(empty) on the parent.
 *
 * Rules encoded here (from G1 finding F8):
 *  - one glyph + one title + one line of body + at most one action;
 *  - title 22sp bold, body 13sp muted (matches the audited Chat empty state tone);
 *  - CTA is a real 48dp target with the card idiom used everywhere else;
 *  - no color invented: caller passes colors from its own palette (fixes F1 drift
 *    until G9 lands; defaults are the Chat palette #0F0F0F/#7CB3FF).
 */
public final class EmptyStateView {

    public static final int DEFAULT_BG     = 0xFF0F0F0F;
    public static final int DEFAULT_CARD   = 0xFF1A1A1A;
    public static final int DEFAULT_BORDER = 0xFF2A2A2A;
    public static final int DEFAULT_TEXT   = 0xFFECECEC;
    public static final int DEFAULT_MUTED  = 0xFF8E8E8E;
    public static final int DEFAULT_ACCENT = 0xFF7CB3FF;

    private EmptyStateView() {}

    public static final class Builder {
        private final Context ctx;
        private int bg = DEFAULT_BG, card = DEFAULT_CARD, border = DEFAULT_BORDER;
        private int text = DEFAULT_TEXT, muted = DEFAULT_MUTED, accent = DEFAULT_ACCENT;
        private String glyph = "\u2734";
        private int iconRes = 0;
        private String title = "Nothing here yet";
        private String body = "";
        private String ctaText = null;
        private View.OnClickListener cta = null;

        public Builder(Context c) { this.ctx = c; }

        public Builder palette(int bg, int card, int border, int text, int muted, int accent) {
            this.bg = bg; this.card = card; this.border = border;
            this.text = text; this.muted = muted; this.accent = accent;
            return this;
        }
        public Builder glyph(String g) { this.glyph = g; return this; }
        public Builder icon(int drawableRes) { this.iconRes = drawableRes; return this; }
        public Builder title(String t) { this.title = t; return this; }
        public Builder body(String b) { this.body = b; return this; }
        public Builder cta(String label, View.OnClickListener l) { this.ctaText = label; this.cta = l; return this; }

        public View build() {
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setBackgroundColor(bg);
            root.setPadding(dp(32), dp(48), dp(32), dp(48));
            root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Glyph (or vector drawable)
            if (iconRes != 0) {
                android.widget.ImageView iv = new android.widget.ImageView(ctx);
                iv.setImageResource(iconRes);
                int size = dp(64);
                iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                root.addView(iv);
            } else if (glyph != null && !glyph.isEmpty()) {
                TextView g = new TextView(ctx);
                g.setText(glyph);
                g.setTextSize(44);
                g.setTextColor(muted);
                g.setGravity(Gravity.CENTER);
                root.addView(g);
            }

            // Title
            TextView t = new TextView(ctx);
            t.setText(title);
            t.setTextSize(22);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(text);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(16), 0, 0);
            root.addView(t);

            // Body (optional)
            if (body != null && !body.isEmpty()) {
                TextView b = new TextView(ctx);
                b.setText(body);
                b.setTextSize(13);
                b.setTextColor(muted);
                b.setGravity(Gravity.CENTER);
                b.setPadding(dp(16), dp(6), dp(16), 0);
                root.addView(b);
            }

            // CTA (optional) — 48dp target, accent-bordered card idiom
            if (ctaText != null && cta != null) {
                TextView btn = new TextView(ctx);
                btn.setText(ctaText);
                btn.setTextSize(15);
                btn.setTypeface(Typeface.DEFAULT_BOLD);
                btn.setTextColor(accent);
                btn.setGravity(Gravity.CENTER);
                btn.setPadding(dp(24), dp(14), dp(24), dp(14));
                GradientDrawable bgDrawable = new GradientDrawable();
                bgDrawable.setCornerRadius(dp(14));
                bgDrawable.setColor((accent & 0x00FFFFFF) | 0x1A000000); // accent @ 10%
                bgDrawable.setStroke(dp(1), accent);
                btn.setBackground(bgDrawable);
                btn.setClickable(true);
                btn.setOnClickListener(cta);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(24);
                btn.setLayoutParams(lp);
                root.addView(btn);
            }

            return root;
        }

        private int dp(float v) {
            return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
