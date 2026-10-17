package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.star.app.PressFeedback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LayerPanel extends LinearLayout {

    private static final int BG = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int SEL_BG = 0xFF2A2A2A;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int THUMB_BG = 0xFF141414;

    private static final String GLYPH_ADD = "+";
    private static final String GLYPH_COPY = "\u29C9";
    private static final String GLYPH_TRASH = "\u2715";
    private static final String GLYPH_EYE_ON = "\u25C9";
    private static final String GLYPH_EYE_OFF = "\u25CB";
    private static final String GLYPH_LOCK_ON = "\u25A0";
    private static final String GLYPH_LOCK_OFF = "\u25A1";

    private final float density;

    private LinearLayout rows;
    private SeekBar opacityBar;
    private boolean syncing;

    private final List<LayerStub> layers = new ArrayList<>();
    private final List<Row> rowViews = new ArrayList<>();
    private String selectedId;

    private Consumer<String> onSelect;
    private Consumer<String> onAdd;
    private Consumer<String> onDuplicate;
    private Consumer<String> onDelete;

    public LayerPanel(Context c) {
        super(c);
        this.density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        addView(buildTopBar());

        ScrollView scroll = new ScrollView(c);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rows = new LinearLayout(c);
        rows.setOrientation(VERTICAL);
        rows.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(rows);
        addView(scroll);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(new BorderBackground(BG, BORDER, density, false));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        bar.addView(barGlyph(GLYPH_ADD, "Add layer", new OnClickListener() {
            @Override public void onClick(View v) {
                if (onAdd != null) onAdd.accept(null);
            }
        }));
        bar.addView(barGlyph(GLYPH_COPY, "Duplicate layer", new OnClickListener() {
            @Override public void onClick(View v) {
                if (onDuplicate != null) onDuplicate.accept(selectedId);
            }
        }));
        bar.addView(barGlyph(GLYPH_TRASH, "Delete layer", new OnClickListener() {
            @Override public void onClick(View v) {
                if (onDelete != null) onDelete.accept(selectedId);
            }
        }));

        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(spacer);

        opacityBar = new SeekBar(getContext());
        opacityBar.setMax(100);
        opacityBar.setProgress(100);
        opacityBar.setLayoutParams(new LinearLayout.LayoutParams(
            dp(96), dp(20)));
        opacityBar.setContentDescription("Layer opacity");
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (syncing || !fromUser) return;
                LayerStub s = selectedStub();
                if (s != null) s.opacity = progress;
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        bar.addView(opacityBar);

        return bar;
    }

    private TextView barGlyph(String glyph, String desc, OnClickListener l) {
        TextView t = new TextView(getContext());
        t.setText(glyph);
        t.setTextSize(12f);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(desc);
        t.setClickable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(
            dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
        if (l != null) t.setOnClickListener(l);
        PressFeedback.attach(t);
        return t;
    }

    public void setLayers(List<LayerStub> list) {
        layers.clear();
        if (list != null) layers.addAll(list);
        rebuild();
    }

    public void setOnSelect(Consumer<String> l) {
        this.onSelect = l;
    }

    public void setOnAdd(Consumer<String> l) {
        this.onAdd = l;
    }

    public void setOnDuplicate(Consumer<String> l) {
        this.onDuplicate = l;
    }

    public void setOnDelete(Consumer<String> l) {
        this.onDelete = l;
    }

    public void setSelected(String id) {
        if (id == null) {
            selectedId = null;
            for (Row r : rowViews) {
                r.bg.setSelected(false);
            }
            syncOpacity();
            return;
        }
        select(id, false);
        syncOpacity();
    }

    private void rebuild() {
        rows.removeAllViews();
        rowViews.clear();

        if (layers.isEmpty()) {
            selectedId = null;
        } else if (selectedId == null || indexOf(selectedId) < 0) {
            selectedId = layers.get(0).id;
        }

        for (LayerStub stub : layers) {
            rows.addView(buildRow(stub));
        }
        syncOpacity();
    }

    private View buildRow(final LayerStub stub) {
        final LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        row.setClickable(true);

        final RowBackground bg = new RowBackground(density,
            stub.id.equals(selectedId));
        row.setBackground(bg);

        View thumb = new View(getContext());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(32), dp(32));
        tlp.setMargins(dp(10), 0, dp(10), 0);
        thumb.setLayoutParams(tlp);
        thumb.setBackground(new ThumbnailBackground(density));
        row.addView(thumb);

        TextView name = new TextView(getContext());
        name.setText(stub.name);
        name.setTextSize(13f);
        name.setTextColor(TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        row.addView(name);

        final TextView eye = rowGlyph(stub.visible ? GLYPH_EYE_ON : GLYPH_EYE_OFF,
            "Toggle visibility");
        final TextView lock = rowGlyph(stub.locked ? GLYPH_LOCK_ON : GLYPH_LOCK_OFF,
            "Toggle lock");
        eye.setTextColor(stub.visible ? TEXT : MUTED);
        lock.setTextColor(stub.locked ? ACCENT : MUTED);

        eye.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                stub.visible = !stub.visible;
                eye.setText(stub.visible ? GLYPH_EYE_ON : GLYPH_EYE_OFF);
                eye.setTextColor(stub.visible ? TEXT : MUTED);
            }
        });
        lock.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                stub.locked = !stub.locked;
                lock.setText(stub.locked ? GLYPH_LOCK_ON : GLYPH_LOCK_OFF);
                lock.setTextColor(stub.locked ? ACCENT : MUTED);
            }
        });
        row.addView(eye);
        row.addView(lock);

        row.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                select(stub.id, true);
            }
        });
        PressFeedback.attach(row);

        rowViews.add(new Row(stub, bg));
        return row;
    }

    private TextView rowGlyph(String glyph, String desc) {
        TextView t = new TextView(getContext());
        t.setText(glyph);
        t.setTextSize(14f);
        t.setTextColor(MUTED);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(desc);
        t.setClickable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(
            dp(32), ViewGroup.LayoutParams.MATCH_PARENT));
        PressFeedback.attach(t);
        return t;
    }

    private void select(String id, boolean notify) {
        if (id == null || indexOf(id) < 0) return;
        if (id.equals(selectedId)) {
            if (notify && onSelect != null) onSelect.accept(id);
            return;
        }
        selectedId = id;
        for (Row r : rowViews) {
            r.bg.setSelected(r.stub.id.equals(id));
        }
        syncOpacity();
        if (notify && onSelect != null) onSelect.accept(id);
    }

    private void syncOpacity() {
        if (opacityBar == null) return;
        LayerStub s = selectedStub();
        int v = s != null ? s.opacity : 100;
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        syncing = true;
        opacityBar.setProgress(v);
        syncing = false;
    }

    private LayerStub selectedStub() {
        int i = indexOf(selectedId);
        return i < 0 ? null : layers.get(i);
    }

    private int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < layers.size(); i++) {
            if (id.equals(layers.get(i).id)) return i;
        }
        return -1;
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    /** Row model: layer data plus its selectable background. */
    private static class Row {
        final LayerStub stub;
        final RowBackground bg;

        Row(LayerStub stub, RowBackground bg) {
            this.stub = stub;
            this.bg = bg;
        }
    }

    /** Row background: #1E1E1E fill, #2A2A2A bottom border, #7CB3FF accent. */
    private static class RowBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float accentWidth;
        private final float thickness;
        private boolean selected;

        RowBackground(float density, boolean selected) {
            this.selected = selected;
            this.accentWidth = Math.max(1f, 3 * density);
            this.thickness = Math.max(1f, density);
            fill.setStyle(Paint.Style.FILL);
            border.setStyle(Paint.Style.FILL);
            border.setColor(BORDER);
            accent.setStyle(Paint.Style.FILL);
            accent.setColor(ACCENT);
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
            c.drawRect(b.left, b.bottom - thickness, b.right, b.bottom, border);
            if (selected) {
                c.drawRect(b.left, b.top, b.left + accentWidth, b.bottom, accent);
            }
        }

        @Override public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            border.setAlpha(alpha);
            accent.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            border.setColorFilter(cf);
            accent.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    /** 32dp thumbnail placeholder: dark fill with a thin border. */
    private static class ThumbnailBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float thickness;

        ThumbnailBackground(float density) {
            this.thickness = Math.max(1f, density);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(THUMB_BG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(thickness);
            border.setColor(BORDER);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b.left, b.top, b.right, b.bottom, fill);
            float inset = thickness / 2f;
            c.drawRect(b.left + inset, b.top + inset,
                b.right - inset, b.bottom - inset, border);
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

    /** Panel header/footer background with just a bottom border line. */
    private static class BorderBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float thickness;

        BorderBackground(int fillColor, int borderColor, float density, boolean top) {
            this.thickness = Math.max(1f, density);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            border.setStyle(Paint.Style.FILL);
            border.setColor(borderColor);
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

    /** Public layer data stub consumed by setLayers(). */
    public static class LayerStub {
        public String id;
        public String name;
        public boolean visible;
        public boolean locked;
        public int opacity;

        public LayerStub() {
            this.opacity = 100;
            this.visible = true;
        }

        public LayerStub(String id, String name) {
            this();
            this.id = id;
            this.name = name;
        }

        public LayerStub(String id, String name, boolean visible,
                         boolean locked, int opacity) {
            this.id = id;
            this.name = name;
            this.visible = visible;
            this.locked = locked;
            this.opacity = opacity;
        }
    }
}
