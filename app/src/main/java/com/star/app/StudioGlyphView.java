package com.star.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Canvas icons. Each type animates on hover, synced to display refresh (90/120fps). */
public class StudioGlyphView extends View {
    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_AUDIO = 2;
    public static final int TYPE_3D    = 3;
    public static final int TYPE_2D    = 4;

    private int type = TYPE_IMAGE;
    private int accent = 0xFFECECEC;
    private boolean highlighted = false;
    private final long startTime = System.currentTimeMillis();
    private boolean animating = false;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            if (animating) postOnAnimation(this);
        }
    };

    public StudioGlyphView(Context c) {
        super(c);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setType(int t) { type = t; invalidate(); }
    public void setAccent(int c) { accent = c; invalidate(); }
    public void setHighlighted(boolean h) { highlighted = h; invalidate(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animating) { animating = true; postOnAnimation(tick); }
    }

    @Override protected void onDetachedFromWindow() {
        animating = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float s = Math.min(w, h) * 0.42f;   // 84% of half
        float t = (System.currentTimeMillis() - startTime) / 1000f;

        fill.setColor(accent);
        stroke.setColor(accent);
        stroke.setStrokeWidth(dp(1.8f));

        switch (type) {
            case TYPE_IMAGE:  drawImage(cv, cx, cy, s, t); break;
            case TYPE_VIDEO:  drawVideo(cv, cx, cy, s, t); break;
            case TYPE_AUDIO:  drawAudio(cv, cx, cy, s, t); break;
            case TYPE_3D:     draw3D(cv, cx, cy, s, t); break;
            case TYPE_2D:     draw2D(cv, cx, cy, s, t); break;
        }
    }

    private void drawImage(Canvas cv, float cx, float cy, float s, float t) {
        float bw = s * 1.9f, bh = s * 1.5f;
        rect.set(cx - bw/2, cy - bh/2, cx + bw/2, cy + bh/2);
        cv.drawRoundRect(rect, dp(2.5f), dp(2.5f), stroke);

        // sun blinks on hover
        float sunR = s * 0.16f * (highlighted ? 1.15f : 1f);
        int sunAlpha = 255;
        if (highlighted) {
            float flash = 0.5f + 0.5f * (float)Math.sin(t * 6f);
            sunAlpha = (int)(80 + 175 * flash);
        }
        fill.setColor(withAlpha(accent, sunAlpha));
        cv.drawCircle(cx + bw*0.28f, cy - bh*0.28f, sunR, fill);
        fill.setColor(accent);

        // mountain 1
        path.reset();
        path.moveTo(cx - bw*0.44f, cy + bh*0.42f);
        path.lineTo(cx - bw*0.08f, cy - bh*0.05f);
        path.lineTo(cx + bw*0.20f, cy + bh*0.42f);
        path.close();
        cv.drawPath(path, fill);

        // mountain 2
        path.reset();
        path.moveTo(cx - bw*0.02f, cy + bh*0.42f);
        path.lineTo(cx + bw*0.20f, cy + bh*0.16f);
        path.lineTo(cx + bw*0.44f, cy + bh*0.42f);
        path.close();
        cv.drawPath(path, fill);
    }

    private void drawVideo(Canvas cv, float cx, float cy, float s, float t) {
        float bw = s * 2.0f, bh = s * 1.4f;
        rect.set(cx - bw/2, cy - bh/2, cx + bw/2, cy + bh/2);
        cv.drawRoundRect(rect, dp(3), dp(3), stroke);

        // play triangle pulses on hover
        float p = highlighted ? (1f + 0.18f * (float)Math.sin(t * 5f)) : 1f;
        path.reset();
        path.moveTo(cx - s*0.30f * p, cy - s*0.48f * p);
        path.lineTo(cx + s*0.50f * p, cy);
        path.lineTo(cx - s*0.30f * p, cy + s*0.48f * p);
        path.close();
        cv.drawPath(path, fill);
    }

    private void drawAudio(Canvas cv, float cx, float cy, float s, float t) {
        float[] base = { 0.40f, 0.85f, 1.10f, 0.70f, 0.35f };
        float bw = s * 0.18f;
        float gap = s * 0.34f;
        float x0 = cx - gap * 2;
        for (int i = 0; i < 5; i++) {
            float h = s * base[i];
            if (highlighted) {
                float phase = t * 9f + i * 0.8f;
                h = s * base[i] * (0.55f + 0.75f * (float)Math.abs(Math.sin(phase)));
            } else {
                // subtle idle breathing
                float phase = t * 2.2f + i * 0.9f;
                h = s * base[i] * (0.95f + 0.07f * (float)Math.sin(phase));
            }
            rect.set(x0 + i*gap - bw/2, cy - h, x0 + i*gap + bw/2, cy + h);
            cv.drawRoundRect(rect, bw/2, bw/2, fill);
        }
    }

    private void draw3D(Canvas cv, float cx, float cy, float s, float t) {
        float angle = highlighted ? (float)(Math.sin(t * 2.6f) * 10.0) : 0f;
        cv.save();
        cv.rotate(angle, cx, cy);

        // top face (solid)
        path.reset();
        path.moveTo(cx, cy - s*1.00f);
        path.lineTo(cx + s*0.78f, cy - s*0.55f);
        path.lineTo(cx, cy - s*0.10f);
        path.lineTo(cx - s*0.78f, cy - s*0.55f);
        path.close();
        cv.drawPath(path, fill);

        // left face (stroke)
        path.reset();
        path.moveTo(cx - s*0.78f, cy - s*0.55f);
        path.lineTo(cx, cy - s*0.10f);
        path.lineTo(cx, cy + s*1.00f);
        path.lineTo(cx - s*0.78f, cy + s*0.55f);
        path.close();
        cv.drawPath(path, stroke);

        // right face (stroke)
        path.reset();
        path.moveTo(cx + s*0.78f, cy - s*0.55f);
        path.lineTo(cx, cy - s*0.10f);
        path.lineTo(cx, cy + s*1.00f);
        path.lineTo(cx + s*0.78f, cy + s*0.55f);
        path.close();
        cv.drawPath(path, stroke);

        cv.restore();
    }

    private void draw2D(Canvas cv, float cx, float cy, float s, float t) {
        float tilt = highlighted ? (float)(Math.sin(t * 3.2f) * 9.0) : 0f;

        cv.save();
        cv.rotate(-18 - tilt, cx, cy);
        rect.set(cx - s*0.85f, cy - s*0.72f, cx + s*0.85f, cy + s*0.72f);
        cv.drawRoundRect(rect, dp(2.5f), dp(2.5f), stroke);
        cv.restore();

        cv.save();
        cv.rotate(18 + tilt, cx, cy);
        rect.set(cx - s*0.85f, cy - s*0.72f, cx + s*0.85f, cy + s*0.72f);
        cv.drawRoundRect(rect, dp(2.5f), dp(2.5f), stroke);
        cv.restore();

        fill.setColor(accent);
        cv.drawCircle(cx, cy, s * 0.14f, fill);
    }

    private int withAlpha(int c, int a) {
        if (a > 255) a = 255; if (a < 0) a = 0;
        return (a << 24) | (c & 0x00FFFFFF);
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
