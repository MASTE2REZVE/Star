package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star.app.PressFeedback;

import java.util.function.IntConsumer;

public class ColorPanel extends LinearLayout {

    private static final int BG = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;

    private static final int[] SWATCHES = {
        0xFF000000, // black
        0xFFFFFFFF, // white
        0xFF1E1E1E, // grey dark
        0xFF555555, // grey mid
        0xFFAAAAAA, // grey light
        0xFFE53935, // red
        0xFFF57C00, // orange
        0xFFFDD835, // yellow
        0xFF43A047, // green
        0xFF00ACC1, // cyan
        0xFF1E88E5, // blue
        0xFF8E24AA, // purple
        0xFFEC407A, // pink
        0xFF795548, // brown
        0xFFFFCC9E, // skin
        0x00000000  // transparent
    };

    private final float density;

    private final WheelView wheel;
    private final TextView hexLabel;
    private final TextView readingLabel;

    private int alpha = 0xFF;
    private IntConsumer onChange;

    public ColorPanel(Context c) {
        super(c);
        this.density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);

        wheel = new WheelView(c);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(200), dp(200));
        wheel.setLayoutParams(wlp);
        wheel.setOnWheelChange(new Runnable() {
            @Override public void run() {
                updateReadings();
                notifyChange();
            }
        });

        FrameLayout wheelHolder = new FrameLayout(c);
        wheelHolder.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(dp(200), dp(200));
        center.gravity = Gravity.CENTER_HORIZONTAL;
        wheelHolder.addView(wheel, center);
        addView(wheelHolder);

        LinearLayout readings = new LinearLayout(c);
        readings.setOrientation(VERTICAL);
        readings.setPadding(dp(10), dp(4), dp(10), dp(6));

        hexLabel = new TextView(c);
        hexLabel.setTextSize(12f);
        hexLabel.setTextColor(TEXT);
        hexLabel.setTypeface(Typeface.MONOSPACE);
        readings.addView(hexLabel);

        readingLabel = new TextView(c);
        readingLabel.setTextSize(11f);
        readingLabel.setTextColor(MUTED);
        readingLabel.setTypeface(Typeface.MONOSPACE);
        readingLabel.setPadding(0, dp(3), 0, 0);
        readings.addView(readingLabel);
        addView(readings);

        addView(buildSwatchStrip());

        setColor(0xFF7CB3FF);
    }

    private View buildSwatchStrip() {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout strip = new LinearLayout(getContext());
        strip.setOrientation(HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(8), 0, dp(8), 0);

        for (int i = 0; i < SWATCHES.length; i++) {
            strip.addView(buildSwatch(SWATCHES[i]));
        }
        scroll.addView(strip);
        return scroll;
    }

    private View buildSwatch(final int argb) {
        View v = new View(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
        lp.setMargins(dp(3), 0, dp(3), 0);
        v.setLayoutParams(lp);
        v.setClickable(true);

        if (Color.alpha(argb) == 0) {
            v.setBackground(new CheckerDrawable(density, 4 * density));
            v.setContentDescription("transparent colour swatch");
        } else {
            GradientDrawable g = new GradientDrawable();
            g.setColor(argb);
            g.setCornerRadius(4 * density);
            g.setStroke(dp(1), BORDER);
            v.setBackground(g);
            v.setContentDescription(String.format("colour swatch #%06X",
                argb & 0xFFFFFF));
        }

        v.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                setColor(argb);
                notifyChange();
            }
        });
        PressFeedback.attach(v);
        return v;
    }

    public int getColor() {
        float[] hsv = new float[] { wheel.getHue(), wheel.getSat(), wheel.getValue() };
        return Color.HSVToColor(alpha, hsv);
    }

    public void setColor(int argb) {
        alpha = Color.alpha(argb);
        float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);
        wheel.setHSV(hsv[0], hsv[1], hsv[2]);
        updateReadings();
    }

    public void setOnChange(IntConsumer l) {
        this.onChange = l;
    }

    private void notifyChange() {
        if (onChange != null) onChange.accept(getColor());
    }

    private void updateReadings() {
        int color = getColor();
        hexLabel.setText(String.format("#%08X", color));
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        readingLabel.setText(String.format("RGB %d,%d,%d   HSV %d,%d,%d",
            r, g, b,
            Math.round(wheel.getHue()),
            Math.round(wheel.getSat() * 100f),
            Math.round(wheel.getValue() * 100f)));
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    /** HSV wheel: a hue ring around an interactive saturation/value triangle. */
    private static class WheelView extends View {
        private static final int MODE_NONE = 0;
        private static final int MODE_HUE = 1;
        private static final int MODE_SV = 2;

        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint triPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triPath = new Path();
        private final float[] av = new float[2];
        private final float[] bv = new float[2];
        private final float[] cv = new float[2];
        private final float[] marker = new float[2];
        private final float density;

        private float hue = 214f;
        private float sat = 1f;
        private float val = 1f;

        private int mode = MODE_NONE;
        private Runnable onWheelChange;

        WheelView(Context c) {
            super(c);
            density = getResources().getDisplayMetrics().density;
            markerPaint.setStyle(Paint.Style.FILL);
            markerPaint.setColor(0xFFFFFFFF);
            markerStroke.setStyle(Paint.Style.STROKE);
            markerStroke.setStrokeWidth(Math.max(1f, dp(2)));
            markerStroke.setColor(0x99000000);
            setContentDescription("HSV colour wheel");
        }

        void setOnWheelChange(Runnable r) {
            this.onWheelChange = r;
        }

        float getHue() { return hue; }
        float getSat() { return sat; }
        float getValue() { return val; }

        void setHSV(float h, float s, float v) {
            hue = norm(h);
            sat = clamp01(s);
            val = clamp01(v);
            invalidate();
        }

        private float dp(float v) {
            return v * density;
        }

        private float norm(float deg) {
            float d = deg % 360f;
            return d < 0f ? d + 360f : d;
        }

        private float clamp01(float v) {
            return v < 0f ? 0f : (v > 1f ? 1f : v);
        }

        private float cx() { return getWidth() / 2f; }
        private float cy() { return getHeight() / 2f; }

        private float outerR() {
            return Math.min(getWidth(), getHeight()) / 2f - dp(3);
        }

        private float ringWidth() {
            return outerR() * 0.26f;
        }

        private float innerR() {
            return outerR() - ringWidth();
        }

        private void computeTriangle() {
            float r = innerR() - dp(6);
            float base = (float) Math.toRadians(hue);
            place(av, r, base);
            place(bv, r, base + Math.toRadians(120));
            place(cv, r, base + Math.toRadians(240));
        }

        private void place(float[] out, float r, double angle) {
            out[0] = cx() + r * (float) Math.cos(angle);
            out[1] = cy() + r * (float) Math.sin(angle);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int[] colors = new int[13];
            for (int i = 0; i < 12; i++) {
                colors[i] = Color.HSVToColor(new float[] { i * 30f, 1f, 1f });
            }
            colors[12] = colors[0];
            ringPaint.setShader(new SweepGradient(cx(), cy(), colors, null));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(ringWidth());
            ringPaint.setAntiAlias(true);
            canvas.drawCircle(cx(), cy(), innerR() + ringWidth() / 2f, ringPaint);

            computeTriangle();
            triPath.reset();
            triPath.moveTo(av[0], av[1]);
            triPath.lineTo(bv[0], bv[1]);
            triPath.lineTo(cv[0], cv[1]);
            triPath.close();

            int hueColor = Color.HSVToColor(new float[] { hue, 1f, 1f });
            triPaint.setStyle(Paint.Style.FILL);
            triPaint.setShader(new LinearGradient(av[0], av[1], bv[0], bv[1],
                hueColor, 0xFFFFFFFF, Shader.TileMode.CLAMP));
            canvas.drawPath(triPath, triPaint);

            float mx = (av[0] + bv[0]) / 2f;
            float my = (av[1] + bv[1]) / 2f;
            triPaint.setShader(new LinearGradient(mx, my, cv[0], cv[1],
                0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawPath(triPath, triPaint);
            triPaint.setShader(null);

            computeMarker();
            float mr = dp(7);
            canvas.drawCircle(marker[0], marker[1], mr, markerStroke);
            markerPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(marker[0], marker[1], mr - dp(2), markerPaint);
            markerPaint.setColor(Color.HSVToColor(new float[] { hue, sat, val }));
            canvas.drawCircle(marker[0], marker[1], mr - dp(3.5f), markerPaint);
        }

        private void computeMarker() {
            float wa = sat * val;
            float wb = (1f - sat) * val;
            float wc = 1f - val;
            marker[0] = av[0] * wa + bv[0] * wb + cv[0] * wc;
            marker[1] = av[1] * wa + bv[1] * wb + cv[1] * wc;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX() - cx();
            float y = e.getY() - cy();
            float r = (float) Math.hypot(x, y);

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (r <= innerR() + ringWidth()) {
                        if (r >= innerR()) mode = MODE_HUE;
                        else if (insideTriangle(e.getX(), e.getY())) mode = MODE_SV;
                        else mode = MODE_NONE;
                    } else {
                        mode = MODE_NONE;
                    }
                    if (mode == MODE_NONE) return false;
                    applyTouch(x, y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mode != MODE_NONE) {
                        applyTouch(x, y);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mode != MODE_NONE) mode = MODE_NONE;
                    return true;
                default:
                    return false;
            }
        }

        private void applyTouch(float x, float y) {
            if (mode == MODE_HUE) {
                float deg = (float) Math.toDegrees(Math.atan2(y, x));
                hue = norm(deg);
            } else if (mode == MODE_SV) {
                computeTriangle();
                float[] w = barycentric(x, y);
                if (w != null) {
                    float wa = Math.max(0f, w[0]);
                    float wb = Math.max(0f, w[1]);
                    float total = wa + wb;
                    if (total <= 0f) {
                        sat = 0f;
                        val = 0f;
                    } else {
                        sat = clamp01(wa / total);
                        val = clamp01(total);
                    }
                }
            }
            invalidate();
            if (onWheelChange != null) onWheelChange.run();
        }

        private boolean insideTriangle(float x, float y) {
            computeTriangle();
            float[] w = barycentric(x, y);
            if (w == null) return false;
            float eps = -0.02f;
            return w[0] >= eps && w[1] >= eps && w[2] >= eps;
        }

        private float[] barycentric(float px, float py) {
            float x1 = av[0], y1 = av[1];
            float x2 = bv[0], y2 = bv[1];
            float x3 = cv[0], y3 = cv[1];
            float denom = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
            if (Math.abs(denom) < 1e-6f) return null;
            float wa = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / denom;
            float wb = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / denom;
            float wc = 1f - wa - wb;
            return new float[] { wa, wb, wc };
        }
    }

    /** Checkerboard used behind the transparent swatch. */
    private static class CheckerDrawable extends Drawable {
        private final Paint paint = new Paint();
        private final float cell;
        private final float radius;

        CheckerDrawable(float density, float radiusPx) {
            this.cell = 6 * density;
            this.radius = radiusPx;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            Path clip = new Path();
            clip.addRoundRect(new android.graphics.RectF(b), radius, radius,
                Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            paint.setColor(0xFFFFFFFF);
            c.drawRect(b, paint);
            paint.setColor(0xFFCCCCCC);
            int cols = (int) Math.ceil(b.width() / cell);
            int rows = (int) Math.ceil(b.height() / cell);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if ((row + col) % 2 == 0) {
                        float left = b.left + col * cell;
                        float top = b.top + row * cell;
                        c.drawRect(left, top, left + cell, top + cell, paint);
                    }
                }
            }
            c.restore();
        }

        @Override public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
