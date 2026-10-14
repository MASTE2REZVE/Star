package com.star.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class BubbleFaceView extends View {
    public static final int EYE = 1;
    public static final int LISTEN = 2;

    private int mode = EYE;
    private int accent = 0xFF7EE787;
    private float blink = 0f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public BubbleFaceView(Context c) {
        super(c);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setMode(int m) { mode = m; invalidate(); }
    public void setAccent(int c) { accent = c; invalidate(); }
    public void setBlink(float b) {
        blink = Math.max(0f, Math.min(1f, b));
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float s = Math.min(w, h) / 2f;
        fill.setColor(accent);
        stroke.setColor(accent);
        if (mode == EYE) drawEye(cv, cx, cy, s);
        else drawListen(cv, cx, cy, s);
    }

    private void drawEye(Canvas cv, float cx, float cy, float s) {
        float lidTop = s * 0.85f * (1f - blink);
        float lidBot = s * 0.85f * (1f - blink);
        float ew = s * 1.05f;

        path.reset();
        path.moveTo(cx - ew, cy);
        path.quadTo(cx, cy - lidTop, cx + ew, cy);
        path.quadTo(cx, cy + lidBot, cx - ew, cy);
        path.close();
        cv.drawPath(path, fill);

        if (blink < 0.9f) {
            Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
            pupil.setColor(0xFF0F0F0F);
            float pr = s * 0.30f * (1f - blink);
            cv.drawCircle(cx, cy, pr, pupil);
            Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
            shine.setColor(0xFFFFFFFF);
            cv.drawCircle(cx - s * 0.10f, cy - s * 0.10f, s * 0.06f, shine);
        }
    }

    private void drawListen(Canvas cv, float cx, float cy, float s) {
        // solid dot on the left, three arcs opening to the right
        fill.setColor(accent);
        cv.drawCircle(cx - s * 0.55f, cy, s * 0.22f, fill);

        stroke.setColor(accent);
        stroke.setStrokeWidth(dp(1.6f));
        rect.set(cx - s * 0.35f, cy - s * 0.55f, cx + s * 0.65f, cy + s * 0.55f);
        cv.drawArc(rect, -50, 100, false, stroke);

        stroke.setStrokeWidth(dp(1.4f));
        rect.set(cx - s * 0.35f, cy - s * 0.85f, cx + s * 1.05f, cy + s * 0.85f);
        cv.drawArc(rect, -45, 90, false, stroke);

        stroke.setStrokeWidth(dp(1.2f));
        rect.set(cx - s * 0.35f, cy - s * 1.15f, cx + s * 1.35f, cy + s * 1.15f);
        cv.drawArc(rect, -40, 80, false, stroke);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
