package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star.app.PressFeedback;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ToolRail extends LinearLayout {

    private static final int BG = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int SEL_BG = 0xFF2A2A2A;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int TEXT = 0xFFECECEC;

    private static final String[] CODES = {
        "B", "P", "E", "F", "M", "L", "T", "V", "H", "Z", "U", "R"
    };
    private static final String[] NAMES = {
        "Brush", "Pencil", "Eraser", "Fill", "Marquee select", "Lasso",
        "Text", "Move/transform", "Hand (pan)", "Zoom", "Undo", "Redo"
    };

    private final Map<String, FrameLayout> items = new LinkedHashMap<>();
    private final Map<String, RailItemBackground> backgrounds = new LinkedHashMap<>();

    private Consumer<String> onSelect;
    private String selected;

    public ToolRail(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(new RailBackground(BG, BORDER,
            getResources().getDisplayMetrics().density));
        setLayoutParams(new LinearLayout.LayoutParams(
            dp(56), ViewGroup.LayoutParams.MATCH_PARENT));
        buildItems();
    }

    private void buildItems() {
        for (int i = 0; i < CODES.length; i++) {
            final String code = CODES[i];
            if (code.equals("U")) addSeparator();
            addItem(code, NAMES[i]);
        }
    }

    private void addItem(final String code, String name) {
        FrameLayout item = new FrameLayout(getContext());
        item.setContentDescription(name);
        item.setClickable(true);
        item.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        RailItemBackground bg = new RailItemBackground(ACCENT,
            getResources().getDisplayMetrics().density);
        item.setBackground(bg);

        TextView glyph = new TextView(getContext());
        glyph.setText(code);
        glyph.setTextSize(16f);
        glyph.setTextColor(TEXT);
        glyph.setTypeface(Typeface.MONOSPACE);
        glyph.setGravity(Gravity.CENTER);
        glyph.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        item.addView(glyph);

        item.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                select(code, true);
            }
        });
        PressFeedback.attach(item);

        items.put(code, item);
        backgrounds.put(code, bg);
        addView(item);

        if (selected == null) select(code, false);
    }

    private void addSeparator() {
        View sep = new View(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(8), dp(4), dp(8), dp(4));
        sep.setLayoutParams(lp);
        sep.setBackgroundColor(BORDER);
        addView(sep);
    }

    public void setSelected(String code) {
        select(code, false);
    }

    private void select(String code, boolean notify) {
        if (code == null || !items.containsKey(code)) return;
        if (code.equals(selected)) return;

        if (selected != null) {
            RailItemBackground prev = backgrounds.get(selected);
            if (prev != null) prev.setSelected(false);
        }
        selected = code;
        RailItemBackground now = backgrounds.get(code);
        if (now != null) now.setSelected(true);

        if (notify && onSelect != null) onSelect.accept(code);
    }

    public String getSelected() {
        return selected;
    }

    public void setOnSelect(Consumer<String> l) {
        this.onSelect = l;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class RailItemBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float accentWidth;
        private boolean selected;

        RailItemBackground(int accentColor, float density) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(BG);
            accent.setStyle(Paint.Style.FILL);
            accent.setColor(accentColor);
            this.accentWidth = (int) (3 * density + 0.5f);
        }

        void setSelected(boolean s) {
            if (selected != s) {
                selected = s;
                invalidateSelf();
            }
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            fill.setColor(selected ? SEL_BG : BG);
            c.drawRect(b.left, b.top, b.right, b.bottom, fill);
            if (selected) {
                c.drawRect(b.left, b.top, b.left + accentWidth, b.bottom, accent);
            }
        }

        @Override public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            accent.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            accent.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static class RailBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float thickness;

        RailBackground(int fillColor, int borderColor, float density) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            border.setStyle(Paint.Style.FILL);
            border.setColor(borderColor);
            this.thickness = Math.max(1f, density);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b.left, b.top, b.right, b.bottom, fill);
            c.drawRect(b.right - thickness, b.top, b.right, b.bottom, border);
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
