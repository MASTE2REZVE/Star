package com.star.app;

// G4 patch — shared inline error card for every network activity.
// Today every network failure renders as one red status line
// ("Error: <msg>" / "Failed: <msg>" / "Parse error" — G1 finding F9:
// "errors as status text only"). The card gives the failure a shape:
// red-tinted card, "!" glyph, plain-language title, raw message in muted
// mono, and one 48dp "Retry" CTA that re-runs the exact call that failed.
// Dismissible via a small "✕" so a transient error doesn't block the screen.
//
// Integration pattern per activity (see the per-activity patch files):
//  1. onCreate: build the card once (GONE) above the content, e.g.
//        errorCard = new ErrorCardView.Builder(this)
//            .title("Couldn't load models")
//            .onRetry(() -> fetch())
//            .build();
//        root.addView(errorCard, 1);
//  2. In every error path (onFailure / onError / parse catch), call:
//        runOnUiThread(() ->
//            ErrorCardView.show(errorCard, "Network error", e.getMessage()));
//  3. In the success path, hide it:
//        ErrorCardView.hide(errorCard);
//
// javac-verified against ~/android-sdk/android.jar.

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

class ErrorCardView {

    // Palette — matches the app's error red (0xFFF85149) on the app bg family.
    public static final int CARD   = 0xFF2A1416;   // red-tinted card
    public static final int BORDER = 0xFFF85149;   // error red
    public static final int TEXT   = 0xFFECECEC;
    public static final int MUTED  = 0xFFA08080;   // warm muted for the raw msg
    public static final int DIM    = 0xFF6E6060;

    /** Fluent builder — one card per activity. */
    public static class Builder {
        private final Context ctx;
        private String title = "Something went wrong";
        private String message;
        private String retryLabel = "Retry";
        private Runnable onRetry;
        private boolean dismissible = true;

        public Builder(Context ctx) { this.ctx = ctx; }

        public Builder title(String t) { this.title = t; return this; }
        public Builder message(String m) { this.message = m; return this; }
        public Builder retryLabel(String l) { this.retryLabel = l; return this; }
        public Builder onRetry(Runnable r) { this.onRetry = r; return this; }
        public Builder dismissible(boolean d) { this.dismissible = d; return this; }

        public LinearLayout build() {
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackground(shape(CARD, BORDER, dp(ctx, 1), dp(ctx, 12)));
            card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 12), dp(ctx, 14));
            card.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 2));
            card.setLayoutParams(lp);

            // "!" glyph badge — 32dp circle, red fill @ 15%
            TextView badge = new TextView(ctx);
            badge.setText("!");
            badge.setTextSize(16);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(BORDER);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(shape(0x26F85149, 0, 0, dp(ctx, 16)));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                dp(ctx, 32), dp(ctx, 32));
            blp.setMargins(0, 0, dp(ctx, 12), 0);
            badge.setLayoutParams(blp);
            card.addView(badge);

            // Text column: title + raw message
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView t = new TextView(ctx);
            t.setText(title);
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(TEXT);
            col.addView(t);

            if (message != null && !message.isEmpty()) {
                col.addView(messageView(ctx, message));
            }

            // Action row: Retry (48dp target) + optional dismiss
            LinearLayout actions = new LinearLayout(ctx);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.setMargins(0, dp(ctx, 10), 0, 0);
            actions.setLayoutParams(alp);

            if (onRetry != null) {
                TextView retry = new TextView(ctx);
                retry.setText(retryLabel);
                retry.setTextSize(14);
                retry.setTypeface(Typeface.DEFAULT_BOLD);
                retry.setTextColor(BORDER);
                retry.setGravity(Gravity.CENTER);
                retry.setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12));
                retry.setBackground(shape(0x1AF85149, BORDER, dp(ctx, 1), dp(ctx, 10)));
                retry.setClickable(true);
                retry.setOnClickListener(v -> {
                    card.setVisibility(View.GONE);
                    onRetry.run();
                });
                actions.addView(retry);
            }

            if (dismissible) {
                TextView close = new TextView(ctx);
                close.setText("✕");
                close.setTextSize(14);
                close.setTextColor(DIM);
                close.setGravity(Gravity.CENTER);
                close.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 8), dp(ctx, 12));
                close.setClickable(true);
                close.setOnClickListener(v -> card.setVisibility(View.GONE));
                LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                xlp.setMargins(dp(ctx, 8), 0, 0, 0);
                close.setLayoutParams(xlp);
                actions.addView(close);
            }

            col.addView(actions);
            card.addView(col);
            return card;
        }
    }

    /** Show a pre-built card with a fresh title/message. Call from error paths. */
    public static void show(LinearLayout card, String title, String message) {
        update(card, title, message);
        card.setVisibility(View.VISIBLE);
    }

    /** Swap title + message without rebuilding the card. */
    public static void update(LinearLayout card, String title, String message) {
        try {
            LinearLayout col = (LinearLayout) card.getChildAt(1);
            ((TextView) col.getChildAt(0)).setText(title);
            boolean hasMsg = col.getChildCount() > 2;
            if (message != null && !message.isEmpty()) {
                if (hasMsg) {
                    ((TextView) col.getChildAt(1)).setText(message);
                } else {
                    col.addView(messageView(card.getContext(), message), 1);
                }
            } else if (hasMsg) {
                col.removeViewAt(1);
            }
        } catch (Throwable ignored) { /* card shape differs — ignore */ }
    }

    public static void hide(LinearLayout card) {
        if (card != null) card.setVisibility(View.GONE);
    }

    private static TextView messageView(Context ctx, String message) {
        TextView m = new TextView(ctx);
        m.setText(message);
        m.setTextSize(12);
        m.setTypeface(Typeface.MONOSPACE);
        m.setTextColor(MUTED);
        m.setPadding(0, dp(ctx, 4), 0, 0);
        m.setMaxLines(4);
        return m;
    }

    private static GradientDrawable shape(int fill, int strokeColor, int strokeWidth, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(fill);
        if (strokeColor != 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
