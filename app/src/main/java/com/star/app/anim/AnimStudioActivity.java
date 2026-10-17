package com.star.app.anim;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star.app.R;

import java.util.ArrayList;
import java.util.List;

public class AnimStudioActivity extends Activity {

    private static final int BG = 0xFF0F0F0F;
    private static final int CARD = 0xFF1E1E1E;
    private static final int BAR_BG = 0xFF141414;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int ACCENT = 0xFF7CB3FF;

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int TOTAL_FRAMES = 120;
    private static final int START_FPS = 24;
    private static final int START_FRAME = 12;

    private CanvasHost canvasHost;
    private LayerPanel layerPanel;
    private LayerManager layerManager;
    private FrameManager frameManager;
    private BrushPanel brushPanel;
    private ColorPanel colorPanel;
    private OnionBar onionBar;
    private TimelinePanel timeline;
    private ToolRail toolRail;
    private View colorIndicator;

    private String activeTool = "B";

    private int fps = START_FPS;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(buildTree());
        seedSampleData();
        wireBehaviour();
    }

    private View buildTree() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TopMenuBar topBar = new TopMenuBar(this);
        topBar.setId(R.id.top_bar);
        root.addView(topBar);

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setLayoutParams(new LinearLayout.LayoutParams(MATCH, 0, 1f));

        toolRail = new ToolRail(this);
        toolRail.setId(R.id.tool_rail);
        middle.addView(toolRail);
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH, 1f));

        LinearLayout tabBar = buildTabBar();
        tabBar.setId(R.id.tab_bar);
        center.addView(tabBar, new LinearLayout.LayoutParams(MATCH, dp(32)));

        canvasHost = new CanvasHost(this);
        canvasHost.setId(R.id.canvas_host);
        center.addView(canvasHost, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        middle.addView(center);

        RightDock rightDock = new RightDock(this);
        rightDock.setId(R.id.right_dock);
        layerPanel = new LayerPanel(this);
        brushPanel = new BrushPanel(this);
        colorPanel = new ColorPanel(this);
        rightDock.getLayersBody().addView(layerPanel);
        rightDock.getBrushBody().addView(brushPanel);
        rightDock.getColorBody().addView(colorPanel);
        middle.addView(rightDock);

        root.addView(middle);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setId(R.id.bottom_dock);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(300)));

        onionBar = new OnionBar(this);
        onionBar.setId(R.id.onion_bar);
        bottom.addView(onionBar);

        timeline = new TimelinePanel(this);
        timeline.setId(R.id.timeline_area);
        bottom.addView(timeline, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        root.addView(bottom);

        return root;
    }

    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(new EdgeBackground(BAR_BG, BORDER,
            getResources().getDisplayMetrics().density, true));

        TextView tab = new TextView(this);
        tab.setText("Untitled 1");
        tab.setTextSize(12f);
        tab.setTextColor(ACCENT);
        tab.setTypeface(Typeface.MONOSPACE);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(18), 0, dp(18), 0);
        tab.setLayoutParams(new LinearLayout.LayoutParams(WRAP, MATCH));
        GradientDrawable tabBg = new GradientDrawable();
        tabBg.setColor(CARD);
        float r = dp(6);
        tabBg.setCornerRadii(new float[] { r, r, r, r, 0f, 0f, 0f, 0f });
        tab.setBackground(tabBg);
        bar.addView(tab);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH, 1f));
        bar.addView(spacer);

        colorIndicator = new View(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(18), dp(18));
        ilp.setMargins(0, 0, dp(12), 0);
        colorIndicator.setLayoutParams(ilp);
        colorIndicator.setContentDescription("Current colour");
        colorIndicator.setBackground(colorSquare(0xFF000000));
        bar.addView(colorIndicator);

        return bar;
    }

    private void seedSampleData() {
        layerManager = canvasHost.getLayerManager();
        layerManager.clear();
        layerManager.addLayer("Line Art");
        layerManager.addLayer("Colour");
        LayerManager.Layer bg = layerManager.addLayer("BG Sketch");
        bg.setOpacity(60);
        layerManager.setActiveLayer(0);
        refreshLayers();

        canvasHost.setCanvasSize(1920, 1080);

        timeline.setTotalFrames(TOTAL_FRAMES);
        timeline.setFps(START_FPS);
        frameManager = new FrameManager(TOTAL_FRAMES);
        seedFrames(frameManager);
        timeline.setFrameManager(frameManager);
        timeline.setCurrentFrame(START_FRAME);

        onionBar.setFps(START_FPS);
        onionBar.setFrame(START_FRAME, TOTAL_FRAMES);

        brushPanel.setSizeDp(8);
        brushPanel.setOpacity(100);

        colorPanel.setColor(0xFF000000);
        updateColorIndicator(0xFF000000);
    }

    /** Give sample frames real layer data so the timeline shows keyframes. */
    private void seedFrames(FrameManager frameManager) {
        int[][] keyed = {
            { 0, 24, 48, 72, 96 },  // L1 - line art
            { 0, 48, 96 },          // L2 - colour
            { 0 }                   // BG - background
        };
        for (int frame = 0; frame < TOTAL_FRAMES; frame++) {
            int depth = 0;
            for (int track = 0; track < keyed.length; track++) {
                if (containsFrame(keyed[track], frame)) depth = track + 1;
            }
            LayerManager frameLayers = frameManager.getFrame(frame);
            if (frameLayers == null) frameLayers = frameManager.createFrame(frame);
            while (frameLayers.size() < depth) frameLayers.addLayer();
        }
    }

    private boolean containsFrame(int[] frames, int frame) {
        for (int f : frames) {
            if (f == frame) return true;
        }
        return false;
    }

    private void wireBehaviour() {
        onionBar.setOnPrev(new Runnable() {
            @Override public void run() {
                stepFrame(-1);
            }
        });
        onionBar.setOnNext(new Runnable() {
            @Override public void run() {
                stepFrame(1);
            }
        });
        onionBar.setOnFrameAdvance(frame -> applyFrame(frame));
        onionBar.setOnFps(value -> {
            fps = value > 0 ? value : 1;
            timeline.setFps(fps);
        });

        timeline.setOnFrameChange(frame -> onionBar.setFrame(frame, TOTAL_FRAMES));

        colorPanel.setOnChange(this::updateColorIndicator);

        brushPanel.setOnChange(() -> {
            canvasHost.setBrushRadius(brushPanel.getSizeDp());
            canvasHost.setBrushOpacity(brushPanel.getOpacity());
        });
        canvasHost.setBrushRadius(brushPanel.getSizeDp());
        canvasHost.setBrushOpacity(brushPanel.getOpacity());

        layerPanel.setOnAdd(id -> {
            layerManager.addLayer();
            layerManager.setActiveLayer(layerManager.size() - 1);
            refreshLayers();
        });
        layerPanel.setOnDuplicate(id -> {
            int index = indexOfLayer(id);
            if (index < 0) return;
            layerManager.duplicateLayer(index);
            refreshLayers();
        });
        layerPanel.setOnDelete(id -> {
            int index = indexOfLayer(id);
            if (index < 0) return;
            layerManager.removeLayer(index);
            refreshLayers();
        });
        layerPanel.setOnSelect(id -> {
            int index = indexOfLayer(id);
            if (index < 0) return;
            layerManager.setActiveLayer(index);
        });
        layerPanel.setOnOpacity((id, opacity) -> {
            int index = indexOfLayer(id);
            if (index < 0) return;
            layerManager.getLayers().get(index).setOpacity(opacity);
        });

        toolRail.setOnSelect(code -> {
            if ("U".equals(code)) {
                canvasHost.undo();
                toolRail.setSelected(activeTool);
            } else if ("R".equals(code)) {
                canvasHost.redo();
                toolRail.setSelected(activeTool);
            } else {
                activeTool = code;
            }
        });
    }

    private void refreshLayers() {
        List<LayerPanel.LayerStub> stubs = new ArrayList<>();
        for (LayerManager.Layer layer : layerManager.getLayers()) {
            stubs.add(new LayerPanel.LayerStub(
                String.valueOf(layer.getId()),
                layer.getName(),
                layer.isVisible(),
                layer.isLocked(),
                layer.getOpacity()));
        }
        layerPanel.setLayers(stubs);
        LayerManager.Layer active = layerManager.getActiveLayer();
        layerPanel.setSelected(active != null ? String.valueOf(active.getId()) : null);
    }

    private int indexOfLayer(String id) {
        if (id == null) return -1;
        List<LayerManager.Layer> all = layerManager.getLayers();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void stepFrame(int delta) {
        int frame = (onionBar.getFrame() + delta + TOTAL_FRAMES) % TOTAL_FRAMES;
        applyFrame(frame);
    }

    private void applyFrame(int frame) {
        onionBar.setFrame(frame, TOTAL_FRAMES);
        timeline.setCurrentFrame(frame);
    }

    private void updateColorIndicator(int argb) {
        if (colorIndicator != null) colorIndicator.setBackground(colorSquare(argb));
    }

    private GradientDrawable colorSquare(int argb) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(argb);
        g.setCornerRadius(dp(3));
        g.setStroke(Math.max(1, (int) getResources().getDisplayMetrics().density),
            BORDER);
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class EdgeBackground extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float thickness;
        private final boolean bottom;

        EdgeBackground(int fillColor, int edgeColor, float density, boolean bottom) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);
            edge.setStyle(Paint.Style.FILL);
            edge.setColor(edgeColor);
            this.thickness = Math.max(1f, density);
            this.bottom = bottom;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b.left, b.top, b.right, b.bottom, fill);
            if (bottom) {
                c.drawRect(b.left, b.bottom - thickness, b.right, b.bottom, edge);
            } else {
                c.drawRect(b.left, b.top, b.right, b.top + thickness, edge);
            }
        }

        @Override public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            edge.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            edge.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
