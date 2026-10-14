package com.star.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class PillIconView extends View {
    public static final int TYPE_AGENT = 0;
    public static final int TYPE_SKILLS = 1;
    public static final int TYPE_MULTI = 2;

    public static final int MM_SINGLE = 0;
    public static final int MM_PARALLEL = 1;
    public static final int MM_MIX = 2;
    public static final int MM_CHAIN = 3;

    public static final int SK_NONE = 0;
    public static final int SK_OFF = 1;
    public static final int SK_SOME = 2;
    public static final int SK_ALL = 3;

    private int type;
    private boolean agentOn;
    private int skillState = SK_ALL;
    private int skillCount;
    private int multiMode = MM_SINGLE;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public PillIconView(Context c) { super(c); init(); }
    public PillIconView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void setAgent(boolean on) { type = TYPE_AGENT; agentOn = on; invalidate(); }
    public void setSkills(int state, int count) {
        type = TYPE_SKILLS; skillState = state; skillCount = count; invalidate();
    }
    public void setMulti(int mode) { type = TYPE_MULTI; multiMode = mode; invalidate(); }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - dp(2);

        int fg = 0xFF8E8E8E;
        int bd = 0xFF2A2A2A;
        boolean hot = false;

        if (type == TYPE_AGENT) {
            if (agentOn) { fg = 0xFF7EE787; bd = fg; hot = true; }
        } else if (type == TYPE_SKILLS) {
            if (skillState == SK_NONE) { fg = 0xFF8E8E8E; bd = 0xFF2A2A2A; }
            else if (skillState == SK_OFF) { fg = 0xFFF85149; bd = fg; hot = true; }
            else if (skillState == SK_SOME) { fg = 0xFFF0883E; bd = fg; hot = true; }
            else { fg = 0xFF7EE787; bd = fg; hot = true; }
        } else {
            if (multiMode == MM_SINGLE) { fg = 0xFF8E8E8E; bd = 0xFF2A2A2A; }
            else if (multiMode == MM_PARALLEL) { fg = 0xFF7CB3FF; bd = fg; hot = true; }
            else if (multiMode == MM_MIX) { fg = 0xFFA78BFA; bd = fg; hot = true; }
            else { fg = 0xFFF0883E; bd = fg; hot = true; }
        }

        // background
        fill.setColor(hot ? withAlpha(fg, 0x22) : 0xFF1E1E1E);
        cv.drawCircle(cx, cy, r, fill);

        // border
        stroke.setColor(bd);
        stroke.setStrokeWidth(dp(hot ? 1.6f : 1.2f));
        cv.drawCircle(cx, cy, r, stroke);

        // glyph
        fill.setColor(fg);
        stroke.setColor(fg);
        if (type == TYPE_AGENT) drawAgent(cv, cx, cy, fg);
        else if (type == TYPE_SKILLS) drawSkills(cv, cx, cy, fg);
        else drawMulti(cv, cx, cy, fg);
    }

    private void drawAgent(Canvas cv, float cx, float cy, int color) {
        float hw = dp(9), hh = dp(7.5f);
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh);
        stroke.setStrokeWidth(dp(1.6f));
        cv.drawRoundRect(rect, dp(4), dp(4), stroke);

        // antenna
        cv.drawLine(cx, cy - hh, cx, cy - hh - dp(3), stroke);
        cv.drawCircle(cx, cy - hh - dp(3.8f), dp(1.2f), fill);

        // eyes
        float eyeR = agentOn ? dp(1.9f) : dp(1.4f);
        float eyeY = cy - dp(1.5f);
        cv.drawCircle(cx - dp(3.5f), eyeY, eyeR, fill);
        cv.drawCircle(cx + dp(3.5f), eyeY, eyeR, fill);

        // mouth
        stroke.setStrokeWidth(agentOn ? dp(1.6f) : dp(1.2f));
        if (agentOn) {
            rect.set(cx - dp(3.5f), cy + dp(2), cx + dp(3.5f), cy + dp(5));
            cv.drawArc(rect, 0, 180, false, stroke);
        } else {
            cv.drawLine(cx - dp(3), cy + dp(3.5f), cx + dp(3), cy + dp(3.5f), stroke);
        }
    }

    private void drawSkills(Canvas cv, float cx, float cy, int color) {
        // three horizontal bars (list)
        float bw = dp(11), bh = dp(2.2f), gap = dp(3.4f);
        float y0 = cy - gap;
        fill.setColor(color);
        for (int i = 0; i < 3; i++) {
            float y = y0 + i * gap;
            rect.set(cx - bw / 2, y - bh / 2, cx + bw / 2, y + bh / 2);
            cv.drawRoundRect(rect, bh / 2, bh / 2, fill);
        }

        if (skillState == SK_OFF) {
            // big X
            stroke.setColor(color);
            stroke.setStrokeWidth(dp(2f));
            cv.drawLine(cx - dp(6), cy - dp(6), cx + dp(6), cy + dp(6), stroke);
            cv.drawLine(cx + dp(6), cy - dp(6), cx - dp(6), cy + dp(6), stroke);
        } else if (skillState == SK_SOME) {
            // small badge with count at bottom-right
            float bx = cx + dp(6), by = cy + dp(6);
            fill.setColor(color);
            cv.drawCircle(bx, by, dp(6), fill);
            text.setColor(0xFF0F0F0F);
            text.setTextSize(dp(8));
            cv.drawText(String.valueOf(skillCount), bx, by + dp(3), text);
        }
    }

    private void drawMulti(Canvas cv, float cx, float cy, int color) {
        fill.setColor(color);
        stroke.setColor(color);
        stroke.setStrokeWidth(dp(1.6f));
        if (multiMode == MM_SINGLE) {
            cv.drawCircle(cx, cy, dp(3.2f), fill);
        } else if (multiMode == MM_PARALLEL) {
            cv.drawLine(cx - dp(3), cy - dp(6), cx - dp(3), cy + dp(6), stroke);
            cv.drawLine(cx + dp(3), cy - dp(6), cx + dp(3), cy + dp(6), stroke);
        } else if (multiMode == MM_MIX) {
            stroke.setStyle(Paint.Style.STROKE);
            cv.drawCircle(cx - dp(2.6f), cy, dp(4.8f), stroke);
            cv.drawCircle(cx + dp(2.6f), cy, dp(4.8f), stroke);
            stroke.setStyle(Paint.Style.STROKE);
        } else {
            // chain: 3 dots + lines
            cv.drawCircle(cx - dp(5.5f), cy, dp(1.9f), fill);
            cv.drawCircle(cx, cy, dp(1.9f), fill);
            cv.drawCircle(cx + dp(5.5f), cy, dp(1.9f), fill);
            stroke.setStrokeWidth(dp(1.4f));
            cv.drawLine(cx - dp(3.6f), cy, cx - dp(1.9f), cy, stroke);
            cv.drawLine(cx + dp(1.9f), cy, cx + dp(3.6f), cy, stroke);
        }
    }

    private int withAlpha(int c, int a) { return (a << 24) | (c & 0x00FFFFFF); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
