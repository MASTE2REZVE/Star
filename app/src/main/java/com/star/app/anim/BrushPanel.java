package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.star.app.PressFeedback;

public class BrushPanel extends LinearLayout {

    private static final int BG = 0xFF1E1E1E;
    private static final int CHIP_BG = 0xFF2A2A2A;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;

    private static final String[] PRESETS = {
        "Hard", "Soft", "Ink", "Pencil", "Airbrush", "Marker", "Charcoal", "Blend"
    };

    private static final int MIN_SIZE_DP = 1;
    private static final int MAX_SIZE_DP = 200;

    private final float density;

    private final TextView[] valueLabels = new TextView[3];
    private final FrameLayout[] chips = new FrameLayout[PRESETS.length];
    private final ChipBackground[] chipBgs = new ChipBackground[PRESETS.length];

    private SeekBar sizeBar;
    private SeekBar opacityBar;
    private SeekBar flowBar;

    private int presetIndex;
    private Runnable onChange;

    public BrushPanel(Context c) {
        super(c);
        this.density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        addView(buildPresetStrip());

        sizeBar = addSlider(0, "SIZE", MIN_SIZE_DP, MAX_SIZE_DP, 24);
        opacityBar = addSlider(1, "OPACITY", 0, 100, 100);
        flowBar = addSlider(2, "FLOW", 0, 100, 100);

        selectPreset(0, false);
    }

    private View buildPresetStrip() {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        LinearLayout strip = new LinearLayout(getContext());
        strip.setOrientation(HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(6), 0, dp(6), 0);

        for (int i = 0; i < PRESETS.length; i++) {
            strip.addView(buildChip(i));
        }
        scroll.addView(strip);
        return scroll;
    }

    private View buildChip(final int index) {
        FrameLayout chip = new FrameLayout(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(4), 0, dp(4), 0);
        chip.setLayoutParams(lp);
        chip.setContentDescription(PRESETS[index] + " brush preset");
        chip.setClickable(true);

        ChipBackground bg = new ChipBackground(density);
        chip.setBackground(bg);
        chipBgs[index] = bg;

        View stamp = new View(getContext());
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(24), dp(24));
        slp.gravity = Gravity.CENTER;
        stamp.setLayoutParams(slp);
        stamp.setBackground(new StampDrawable(index));
        chip.addView(stamp);

        chip.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                selectPreset(index, true);
            }
        });
        PressFeedback.attach(chip);

        chips[index] = chip;
        return chip;
    }

    private SeekBar addSlider(final int slot, String label,
                              final int min, final int max, int initial) {
        LinearLayout group = new LinearLayout(getContext());
        group.setOrientation(VERTICAL);
        group.setPadding(dp(8), dp(6), dp(8), dp(2));
        group.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));

        TextView name = new TextView(getContext());
        name.setText(label);
        name.setTextSize(11f);
        name.setTextColor(MUTED);
        name.setTypeface(Typeface.MONOSPACE);
        name.setLetterSpacing(0.15f);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        head.addView(name);

        TextView value = new TextView(getContext());
        value.setTextSize(11f);
        value.setTextColor(TEXT);
        value.setTypeface(Typeface.MONOSPACE);
        value.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(value);
        valueLabels[slot] = value;

        group.addView(head);

        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max);
        bar.setProgress(initial);
        bar.setContentDescription(label + " slider");
        bar.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                clampSize(sb, min);
                updateValue(slot);
                if (fromUser) notifyChange();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        group.addView(bar);

        switch (slot) {
            case 0:  sizeBar = bar;    break;
            case 1:  opacityBar = bar; break;
            default: flowBar = bar;    break;
        }

        addView(group);
        updateValue(slot);
        return bar;
    }

    private void clampSize(SeekBar bar, int min) {
        if (bar == sizeBar && bar.getProgress() < min) {
            bar.setProgress(min);
        }
    }

    private void updateValue(int slot) {
        TextView t = valueLabels[slot];
        if (t == null) return;
        switch (slot) {
            case 0:  t.setText(getSizeDp() + " dp"); break;
            case 1:  t.setText(getOpacity() + "%");   break;
            default: t.setText(getFlow() + "%");      break;
        }
    }

    private void selectPreset(int index, boolean notify) {
        presetIndex = index;
        for (int i = 0; i < chips.length; i++) {
            chipBgs[i].setSelected(i == index);
        }
        if (notify) notifyChange();
    }

    private void notifyChange() {
        if (onChange != null) onChange.run();
    }

    public int getSizeDp() {
        if (sizeBar == null) return MIN_SIZE_DP;
        int v = sizeBar.getProgress();
        if (v < MIN_SIZE_DP) v = MIN_SIZE_DP;
        if (v > MAX_SIZE_DP) v = MAX_SIZE_DP;
        return v;
    }

    public int getOpacity() {
        if (opacityBar == null) return 100;
        return opacityBar.getProgress();
    }

    public int getFlow() {
        if (flowBar == null) return 100;
        return flowBar.getProgress();
    }

    public String getPreset() {
        return PRESETS[presetIndex];
    }

    public void setSizeDp(int sizeDp) {
        sizeBar.setProgress(clamp(sizeDp, MIN_SIZE_DP, MAX_SIZE_DP));
        updateValue(0);
    }

    public void setOpacity(int percent) {
        opacityBar.setProgress(clamp(percent, 0, 100));
        updateValue(1);
    }

    public void setOnChange(Runnable r) {
        this.onChange = r;
    }

    private int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    /** Chip background: rounded 6dp rect, #2A2A2A, accent border when selected. */
    private static class ChipBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float radius;
        private final float thickness;
        private boolean selected;

        ChipBackground(float density) {
            this.radius = 6 * density;
            this.thickness = Math.max(1f, density);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(CHIP_BG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(thickness * 2f);
        }

        void setSelected(boolean s) {
            if (selected != s) {
                selected = s;
                invalidateSelf();
            }
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            rect.set(b.left + thickness, b.top + thickness,
                b.right - thickness, b.bottom - thickness);
            c.drawRoundRect(rect, radius, radius, fill);
            if (selected) {
                border.setColor(ACCENT);
                c.drawRoundRect(rect, radius, radius, border);
            }
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

    /**
     * Sample stamp: a white circle whose edge softness depends on the preset,
     * so each chip previews its brush character (hard edge, soft falloff,
     * thin ring for ink, grain for charcoal, etc.).
     */
    private static class StampDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int preset;
        private Shader shader;

        StampDrawable(int preset) {
            this.preset = preset;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float maxR = Math.min(b.width(), b.height()) / 2f;
            float r = maxR - Math.max(1f, maxR * 0.1f);

            switch (preset) {
                case 0: // Hard
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy, r, paint);
                    break;
                case 1: // Soft
                    paint.setShader(softShader(cx, cy, r, 0f, 1f));
                    c.drawCircle(cx, cy, r, paint);
                    paint.setShader(null);
                    break;
                case 2: // Ink
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1f, r * 0.35f));
                    paint.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy, r * 0.72f, paint);
                    break;
                case 3: // Pencil
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFFBFBFBF);
                    c.drawCircle(cx, cy, r * 0.55f, paint);
                    break;
                case 4: // Airbrush
                    paint.setShader(softShader(cx, cy, r, 0.55f, 1f));
                    c.drawCircle(cx, cy, r, paint);
                    paint.setShader(null);
                    break;
                case 5: // Marker
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xE6FFFFFF);
                    c.drawRect(cx - r * 0.85f, cy - r * 0.4f,
                        cx + r * 0.85f, cy + r * 0.4f, paint);
                    break;
                case 6: // Charcoal
                    paint.setShader(softShader(cx, cy, r, 0.2f, 0.85f));
                    c.drawCircle(cx, cy, r, paint);
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0x66000000);
                    c.drawCircle(cx + r * 0.25f, cy - r * 0.2f, r * 0.25f, paint);
                    break;
                default: // Blend
                    paint.setShader(softShader(cx, cy, r, 0.35f, 1f));
                    c.drawCircle(cx, cy, r, paint);
                    paint.setShader(null);
                    break;
            }
        }

        private Shader softShader(float cx, float cy, float radius,
                                  float innerStop, float startAlpha) {
            int alpha = (int) (startAlpha * 255);
            if (shader == null) {
                shader = new RadialGradient(cx, cy, Math.max(1f, radius),
                    new int[] { (alpha << 24) | 0xFFFFFF, 0x00FFFFFF },
                    new float[] { innerStop, 1f },
                    Shader.TileMode.CLAMP);
            }
            return shader;
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
