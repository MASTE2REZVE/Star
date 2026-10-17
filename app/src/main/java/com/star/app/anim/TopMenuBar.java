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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.star.app.PressFeedback;

import java.util.function.Consumer;

public class TopMenuBar extends LinearLayout {

    private static final int BG = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;

    private static final String[] MENUS = {"File", "Edit", "View", "Window", "Help"};
    private static final String[] PLACEHOLDERS = {"New", "Open", "Save", "Export"};

    private Consumer<String> onMenu;
    private Runnable onExport;
    private TextView docName;
    private TextView zoomIndicator;

    public TopMenuBar(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setBackground(new BarBackground(BG, BORDER,
            getResources().getDisplayMetrics().density));
        setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        addLeftItems();
        addRightItems();
    }

    private void addLeftItems() {
        for (final String name : MENUS) {
            TextView item = new TextView(getContext());
            item.setText(name);
            item.setTextSize(12f);
            item.setTextColor(TEXT);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(16), 0, dp(16), 0);
            item.setClickable(true);
            item.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            item.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    showMenu(name, v);
                }
            });
            PressFeedback.attach(item);
            addView(item);
        }

        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addView(spacer);
    }

    private void addRightItems() {
        TextView export = new TextView(getContext());
        export.setText("Export PNG");
        export.setTextSize(12f);
        export.setTextColor(0xFF7CB3FF);
        export.setTypeface(Typeface.MONOSPACE);
        export.setGravity(Gravity.CENTER);
        export.setPadding(dp(12), 0, dp(12), 0);
        export.setClickable(true);
        export.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        export.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (onExport != null) onExport.run();
            }
        });
        PressFeedback.attach(export);
        addView(export);

        docName = new TextView(getContext());
        docName.setText("Untitled 1");
        docName.setTextSize(12f);
        docName.setTextColor(MUTED);
        docName.setTypeface(Typeface.MONOSPACE);
        docName.setGravity(Gravity.CENTER);
        docName.setPadding(dp(12), 0, dp(12), 0);
        docName.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addView(docName);

        zoomIndicator = new TextView(getContext());
        zoomIndicator.setText("100%");
        zoomIndicator.setTextSize(12f);
        zoomIndicator.setTextColor(MUTED);
        zoomIndicator.setTypeface(Typeface.MONOSPACE);
        zoomIndicator.setGravity(Gravity.CENTER);
        zoomIndicator.setPadding(dp(12), 0, dp(12), 0);
        zoomIndicator.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        addView(zoomIndicator);
    }

    private void showMenu(final String name, View anchor) {
        PopupMenu pm = new PopupMenu(getContext(), anchor);
        for (int i = 0; i < PLACEHOLDERS.length; i++) {
            pm.getMenu().add(0, i, i, PLACEHOLDERS[i]);
        }
        pm.setOnMenuItemClickListener(item -> {
            if (onMenu != null) onMenu.accept(name);
            return true;
        });
        pm.show();
    }

    public void setOnMenu(Consumer<String> l) {
        this.onMenu = l;
    }

    public void setOnExport(Runnable l) {
        this.onExport = l;
    }

    public void setDocumentName(String name) {
        if (docName != null && name != null) docName.setText(name);
    }

    public void setZoom(int percent) {
        if (zoomIndicator != null) zoomIndicator.setText(percent + "%");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class BarBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float thickness;

        BarBackground(int fillColor, int borderColor, float density) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            border.setStyle(Paint.Style.FILL);
            border.setColor(borderColor);
            this.thickness = Math.max(1f, density);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b.left, b.top, b.right, b.bottom, fill);
            c.drawRect(b.left, b.bottom - thickness, b.right, b.bottom, border);
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
