package com.star.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Circular icon for the top bar.
 *  MODE_MAIN   — speech bubble glyph (chat model)
 *  MODE_WORKER — cog + spark (worker model). Rotates slowly when active. */
public class WorkerIconView extends View {
    public static final int MODE_MAIN = 0;
    public static final int MODE_WORKER = 1;

    private int mode = MODE_MAIN;
    private boolean active = false;
    private boolean highlighted = false;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private boolean animating = false;
    private float rotation = 0f;
    private long lastTickMs = 0L;

    // One turn every 24 seconds
    private static final float TURN_MS = 24000f;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (lastTickMs > 0) {
                rotation += (now - lastTickMs) / TURN_MS * 360f;
                if (rotation >= 360f) rotation -= 360f;
            }
            lastTickMs = now;
            invalidate();
            if (animating) postOnAnimation(this);
        }
    };

    public WorkerIconView(Context c) {
        super(c);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setMode(int m) { mode = m; invalidate(); }
    public void setActive(boolean a) {
        active = a;
        if (mode == MODE_WORKER) updateAnimation();
        invalidate();
    }
    public void setHighlighted(boolean h) { highlighted = h; invalidate(); }

    private void updateAnimation() {
        boolean want = (mode == MODE_WORKER && active);
        if (want && !animating) {
            animating = true;
            lastTickMs = 0L;
            postOnAnimation(tick);
        } else if (!want && animating) {
            animating = false;
            removeCallbacks(tick);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        animating = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float R = Math.min(w, h) / 2f - dp(3);

        int accent = active ? 0xFFFFB800 : 0xFF8E8E8E;
        if (highlighted) accent = 0xFFFFD24A;

        // background disk
        fill.setColor(active ? 0x22FFB800 : 0xFF1E1E1E);
        cv.drawCircle(cx, cy, R, fill);

        // outer ring
        stroke.setColor(accent);
        stroke.setStrokeWidth(dp(1.4f));
        cv.drawCircle(cx, cy, R, stroke);

        if (mode == MODE_MAIN) drawBubble(cv, cx, cy, R, accent);
        else drawCog(cv, cx, cy, R, accent);
    }

    private void drawBubble(Canvas cv, float cx, float cy, float R, int color) {
        float bw = R * 1.15f, bh = R * 0.95f;
        rect.set(cx - bw / 2, cy - bh / 2 - dp(1), cx + bw / 2, cy + bh / 2 - dp(1));
        stroke.setColor(color);
        stroke.setStrokeWidth(dp(1.6f));
        cv.drawRoundRect(rect, dp(3), dp(3), stroke);

        // tail
        path.reset();
        path.moveTo(cx - bw * 0.15f, cy + bh / 2 - dp(1));
        path.lineTo(cx - bw * 0.28f, cy + bh / 2 + dp(3));
        path.lineTo(cx + bw * 0.02f, cy + bh / 2 - dp(1));
        path.close();
        fill.setColor(color);
        cv.drawPath(path, fill);

        // three dots inside
        float dotR = dp(1.4f);
        float gap = dp(4.2f);
        float dy = cy - dp(1);
        cv.drawCircle(cx - gap, dy, dotR, fill);
        cv.drawCircle(cx, dy, dotR, fill);
        cv.drawCircle(cx + gap, dy, dotR, fill);
    }

    private void drawCog(Canvas cv, float cx, float cy, float R, int color) {
        float outerR = R * 0.62f;
        float innerR = R * 0.30f;
        int teeth = 8;
        float toothDepth = R * 0.20f;
        float toothWidth = (float)(2 * Math.PI / teeth) * 0.42f;

        cv.save();
        cv.rotate(rotation, cx, cy);

        // gear teeth as a star polygon
        path.reset();
        for (int i = 0; i < teeth; i++) {
            double a1 = (Math.PI * 2 / teeth) * i;
            double a2 = a1 + toothWidth / 2;
            double a3 = a1 + (Math.PI * 2 / teeth) - toothWidth / 2;

            float x1 = cx + (float)(Math.cos(a3) * outerR);
            float y1 = cy + (float)(Math.sin(a3) * outerR);
            float x2 = cx + (float)(Math.cos(a1) * (outerR + toothDepth));
            float y2 = cy + (float)(Math.sin(a1) * (outerR + toothDepth));
            float x3 = cx + (float)(Math.cos(a2) * outerR);
            float y3 = cy + (float)(Math.sin(a2) * outerR);

            if (i == 0) path.moveTo(x1, y1);
            else path.lineTo(x1, y1);
            path.lineTo(x2, y2);
            path.lineTo(x3, y3);
        }
        path.close();

        stroke.setColor(color);
        stroke.setStrokeWidth(dp(1.5f));
        cv.drawPath(path, stroke);

        // hub
        fill.setColor(color);
        cv.drawCircle(cx, cy, innerR, fill);

        // hole in the hub
        fill.setColor(0xFF0F0F0F);
        cv.drawCircle(cx, cy, innerR * 0.45f, fill);

        cv.restore();

        // spark on top (does not rotate)
        float sparkX = cx + R * 0.55f;
        float sparkY = cy - R * 0.60f;
        path.reset();
        path.moveTo(sparkX, sparkY - dp(3));
        path.lineTo(sparkX + dp(1.2f), sparkY - dp(0.6f));
        path.lineTo(sparkX + dp(3), sparkY);
        path.lineTo(sparkX + dp(1.2f), sparkY + dp(0.6f));
        path.lineTo(sparkX, sparkY + dp(3));
        path.lineTo(sparkX - dp(1.2f), sparkY + dp(0.6f));
        path.lineTo(sparkX - dp(3), sparkY);
        path.lineTo(sparkX - dp(1.2f), sparkY - dp(0.6f));
        path.close();
        fill.setColor(color);
        cv.drawPath(path, fill);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
