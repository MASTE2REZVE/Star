package com.star.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public class RadialIconView extends View {
    public static final int VOICE = 0;
    public static final int LIVE = 1;
    public static final int SCREEN = 2;
    public static final int HIDE = 3;

    private int type;
    private int accent = 0xFF7CB3FF;
    private boolean highlighted = false;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public RadialIconView(Context c) { super(c); init(); }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void setType(int t) { type = t; invalidate(); }
    public void setAccent(int c) { accent = c; invalidate(); }
    public void setHighlighted(boolean h) { highlighted = h; invalidate(); }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float R = Math.min(w, h) / 2f - dp(1);

        // background with radial tint
        RadialGradient rg = new RadialGradient(cx, cy, Math.max(1, R),
            new int[]{ withAlpha(accent, highlighted ? 0x55 : 0x22), 0xF0101010 },
            new float[]{ 0f, 1f }, Shader.TileMode.CLAMP);
        fill.setShader(rg);
        cv.drawCircle(cx, cy, R, fill);
        fill.setShader(null);

        // outer ring
        stroke.setColor(accent);
        stroke.setStrokeWidth(highlighted ? dp(2.5f) : dp(1.6f));
        cv.drawCircle(cx, cy, R, stroke);

        // halo
        if (highlighted) {
            stroke.setColor(withAlpha(accent, 0x55));
            stroke.setStrokeWidth(dp(6));
            cv.drawCircle(cx, cy, R - dp(1), stroke);
        }

        float s = R * 0.55f;
        fill.setColor(accent);
        stroke.setColor(accent);
        stroke.setStrokeWidth(dp(1.8f));

        switch (type) {
            case VOICE:  drawMic(cv, cx, cy, s); break;
            case LIVE:   drawBroadcast(cv, cx, cy, s); break;
            case SCREEN: drawCamera(cv, cx, cy, s); break;
            case HIDE:   drawEyeSlash(cv, cx, cy, s); break;
        }
    }

    private void drawMic(Canvas cv, float cx, float cy, float s) {
        float bw = s * 0.55f, bh = s * 1.05f;
        rect.set(cx - bw / 2, cy - bh * 0.65f, cx + bw / 2, cy + bh * 0.2f);
        cv.drawRoundRect(rect, bw / 2, bw / 2, stroke);

        rect.set(cx - bw * 0.8f, cy - bh * 0.2f, cx + bw * 0.8f, cy + bh * 0.55f);
        cv.drawArc(rect, 0, 180, false, stroke);

        cv.drawLine(cx, cy + bh * 0.55f, cx, cy + bh * 0.85f, stroke);
        cv.drawLine(cx - bw * 0.55f, cy + bh * 0.85f,
                    cx + bw * 0.55f, cy + bh * 0.85f, stroke);
    }

    private void drawBroadcast(Canvas cv, float cx, float cy, float s) {
        fill.setColor(accent);
        cv.drawCircle(cx, cy, s * 0.22f, fill);

        stroke.setStrokeWidth(dp(1.8f));
        rect.set(cx - s * 0.75f, cy - s * 0.75f, cx + s * 0.3f, cy + s * 0.75f);
        cv.drawArc(rect, 135, 90, false, stroke);
        rect.set(cx - s * 0.3f, cy - s * 0.75f, cx + s * 0.75f, cy + s * 0.75f);
        cv.drawArc(rect, -45, 90, false, stroke);

        stroke.setStrokeWidth(dp(1.2f));
        rect.set(cx - s * 1.1f, cy - s * 1.1f, cx + s * 0.55f, cy + s * 1.1f);
        cv.drawArc(rect, 135, 90, false, stroke);
        rect.set(cx - s * 0.55f, cy - s * 1.1f, cx + s * 1.1f, cy + s * 1.1f);
        cv.drawArc(rect, -45, 90, false, stroke);
    }

    private void drawCamera(Canvas cv, float cx, float cy, float s) {
        float bw = s * 1.2f, bh = s * 0.85f;
        rect.set(cx - bw / 2, cy - bh / 2 + dp(1.5f), cx + bw / 2, cy + bh / 2);
        cv.drawRoundRect(rect, dp(3), dp(3), stroke);

        rect.set(cx - dp(4), cy - bh / 2 - dp(1), cx + dp(4), cy - bh / 2 + dp(3));
        cv.drawRoundRect(rect, dp(1.5f), dp(1.5f), stroke);

        cv.drawCircle(cx, cy + dp(1), s * 0.28f, stroke);
        fill.setColor(accent);
        cv.drawCircle(cx, cy + dp(1), s * 0.13f, fill);
    }

    private void drawEyeSlash(Canvas cv, float cx, float cy, float s) {
        float ew = s * 0.95f, eh = s * 0.5f;
        rect.set(cx - ew, cy - eh * 1.5f, cx + ew, cy + eh * 1.5f);
        stroke.setStrokeWidth(dp(1.8f));
        cv.drawArc(rect, 200, 140, false, stroke);
        cv.drawArc(rect, 20, 140, false, stroke);

        fill.setColor(accent);
        cv.drawCircle(cx, cy, s * 0.22f, fill);

        stroke.setStrokeWidth(dp(2.2f));
        cv.drawLine(cx - s * 0.9f, cy + s * 0.9f, cx + s * 0.9f, cy - s * 0.9f, stroke);
    }

    private int withAlpha(int c, int a) { return (a << 24) | (c & 0x00FFFFFF); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
