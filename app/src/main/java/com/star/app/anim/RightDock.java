package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star.app.PressFeedback;

public class RightDock extends LinearLayout {

    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int CHEVRON = 0xFF8E8E8E;

    private final float density;

    private FrameLayout layersBody;
    private FrameLayout brushBody;
    private FrameLayout colorBody;

    public RightDock(Context c) {
        super(c);
        this.density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackground(new DockBackground(BG, BORDER, density));
        setLayoutParams(new LinearLayout.LayoutParams(
            dp(280), ViewGroup.LayoutParams.MATCH_PARENT));

        layersBody = addSlot("Layers", 4f);
        brushBody = addSlot("Brush", 3f);
        colorBody = addSlot("Color", 3f);
    }

    private FrameLayout addSlot(String label, float weight) {
        LinearLayout slot = new LinearLayout(getContext());
        slot.setOrientation(VERTICAL);
        slot.setBackground(new CardBackground(CARD, BORDER, dp(8), density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, weight);
        lp.setMargins(dp(8), dp(8), dp(8), 0);
        slot.setLayoutParams(lp);

        final FrameLayout body = new FrameLayout(getContext());
        body.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        slot.addView(buildHeader(label, body));
        slot.addView(body);
        addView(slot);
        return body;
    }

    private View buildHeader(String label, final FrameLayout body) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        TextView title = new TextView(getContext());
        title.setText(label.toUpperCase());
        title.setTextSize(11f);
        title.setTextColor(MUTED);
        title.setTypeface(Typeface.MONOSPACE);
        title.setLetterSpacing(0.15f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(10), 0, 0, 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        header.addView(title);

        final TextView chevron = new TextView(getContext());
        chevron.setText("\u25BE");
        chevron.setTextSize(12f);
        chevron.setTextColor(CHEVRON);
        chevron.setGravity(Gravity.CENTER);
        chevron.setPadding(dp(8), 0, dp(8), 0);
        chevron.setContentDescription("Collapse " + label);
        chevron.setClickable(true);
        chevron.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        chevron.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                boolean collapse = body.getVisibility() == VISIBLE;
                body.setVisibility(collapse ? GONE : VISIBLE);
                chevron.animate().rotation(collapse ? -90f : 0f)
                    .setDuration(120).start();
            }
        });
        PressFeedback.attach(chevron);
        header.addView(chevron);

        return header;
    }

    public FrameLayout getLayersBody() {
        return layersBody;
    }

    public FrameLayout getBrushBody() {
        return brushBody;
    }

    public FrameLayout getColorBody() {
        return colorBody;
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    private static class DockBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float thickness;

        DockBackground(int fillColor, int borderColor, float density) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            border.setStyle(Paint.Style.FILL);
            border.setColor(borderColor);
            this.thickness = Math.max(1f, density);
        }

        @Override public void draw(Canvas c) {
            RectF b = new RectF(getBounds());
            c.drawRect(b.left, b.top, b.right, b.bottom, fill);
            c.drawRect(b.left, b.top, b.left + thickness, b.bottom, border);
        }

        @Override public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            border.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            border.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static class CardBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;
        private final float thickness;

        CardBackground(int fillColor, int borderColor, float radius, float density) {
            this.radius = radius;
            this.thickness = Math.max(1f, density);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(thickness);
            border.setColor(borderColor);
        }

        @Override public void draw(Canvas c) {
            RectF b = new RectF(getBounds());
            float inset = thickness / 2f;
            b.inset(inset, inset);
            c.drawRoundRect(b, radius, radius, fill);
            c.drawRoundRect(b, radius, radius, border);
        }

        @Override public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            border.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            border.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
