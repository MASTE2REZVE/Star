package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.function.IntConsumer;

public class TimelinePanel extends LinearLayout {

    private static final int PANEL_BG = 0xFF0F0F0F;
    private static final int ROW_BG = 0xFF1E1E1E;
    private static final int GUTTER_BG = 0xFF141414;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int CELL_KEY_BG = 0xFF2A2A2A;
    private static final int ACCENT = 0xFF7CB3FF;
    private static final int TICK = 0xFF555555;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int PLAYHEAD = 0xFFF85149;

    private static final int RULER_DP = 22;
    private static final int TRACK_DP = 44;
    private static final int GUTTER_DP = 40;
    private static final int CELL_DP = 12;

    private static final String[] TRACK_LABELS = { "L1", "L2", "BG" };

    private final float density;
    private final int gutterPx;
    private final int cellPx;

    private final TimelineRuler ruler;
    private final LinearLayout tracksContainer;
    private final TrackRow[] trackRows = new TrackRow[TRACK_LABELS.length];
    private final PlayheadView playhead;

    private int totalFrames = 60;
    private int fps = 24;
    private int currentFrame = 0;

    private IntConsumer onFrameChange;

    private FrameManager frameManager;

    public TimelinePanel(Context c) {
        super(c);
        this.density = getResources().getDisplayMetrics().density;
        this.gutterPx = dp(GUTTER_DP);
        this.cellPx = dp(CELL_DP);

        setOrientation(VERTICAL);
        setBackgroundColor(PANEL_BG);

        FrameLayout content = new FrameLayout(c);
        content.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(VERTICAL);
        column.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        ruler = new TimelineRuler(c);
        ruler.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(RULER_DP)));
        column.addView(ruler);

        tracksContainer = new LinearLayout(c);
        tracksContainer.setOrientation(VERTICAL);
        tracksContainer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < TRACK_LABELS.length; i++) {
            TrackRow row = new TrackRow(c, i);
            row.setOnCellTap(frame -> setFrameInternal(frame, true));
            row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TRACK_DP)));
            tracksContainer.addView(row);
            trackRows[i] = row;
        }
        column.addView(tracksContainer);

        content.addView(column);

        playhead = new PlayheadView(c);
        playhead.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(playhead);

        addView(content);
        ruler.setTotalFrames(totalFrames);
    }

    public void setTotalFrames(int frames) {
        totalFrames = Math.max(1, frames);
        if (currentFrame > totalFrames - 1) currentFrame = totalFrames - 1;
        if (currentFrame < 0) currentFrame = 0;
        ruler.setTotalFrames(totalFrames);
        for (int i = 0; i < tracksContainer.getChildCount(); i++) {
            View v = tracksContainer.getChildAt(i);
            if (v instanceof TrackRow) ((TrackRow) v).setTotalFrames(totalFrames);
        }
        playhead.update();
    }

    public void setFps(int fps) {
        this.fps = fps > 0 ? fps : 1;
    }

    public void setCurrentFrame(int frame) {
        setFrameInternal(frame, false);
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void setOnFrameChange(IntConsumer l) {
        this.onFrameChange = l;
    }

    public void setKeyframes(int trackIndex, int... frames) {
        if (trackIndex < 0 || trackIndex >= trackRows.length) return;
        TrackRow row = trackRows[trackIndex];
        if (row != null) row.setKeyframes(frames);
    }

    /** Attach the frame store; every track row reads its keyframe data from it. */
    public void setFrameManager(FrameManager frameManager) {
        this.frameManager = frameManager;
        for (TrackRow row : trackRows) {
            if (row != null) row.setFrameManager(frameManager);
        }
        if (frameManager != null) setTotalFrames(frameManager.size());
    }

    private void setFrameInternal(int frame, boolean notify) {
        int f = frame;
        if (f < 0) f = 0;
        if (f > totalFrames - 1) f = totalFrames - 1;
        boolean changed = f != currentFrame;
        currentFrame = f;
        playhead.update();
        if (changed && notify && onFrameChange != null) onFrameChange.accept(f);
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    /** Full-height draggable red playhead line positioned by frame. */
    private class PlayheadView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float widthPx;

        PlayheadView(Context c) {
            super(c);
            line.setColor(PLAYHEAD);
            line.setStyle(Paint.Style.FILL);
            widthPx = Math.max(1f, 2f * density);
            setContentDescription("Timeline playhead");
        }

        void update() {
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = frameLeftPx(currentFrame) + cellPx / 2f;
            canvas.drawRect(x - widthPx / 2f, 0f, x + widthPx / 2f,
                getHeight(), line);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    int f = Math.round((e.getX() - gutterPx) / (float) cellPx);
                    setFrameInternal(f, true);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }
    }

    private int frameLeftPx(int frame) {
        return gutterPx + frame * cellPx;
    }

    /** Ruler strip: per-frame ticks, tall ticks and labels every 5 frames. */
    private static class TimelineRuler extends View {
        private final Paint bg = new Paint();
        private final Paint gutter = new Paint();
        private final Paint border = new Paint();
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final float density;
        private final float scaledDensity;
        private final int gutterPx;
        private final int cellPx;
        private final float thickness;

        private int totalFrames = 60;

        TimelineRuler(Context c) {
            super(c);
            this.density = getResources().getDisplayMetrics().density;
            this.scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            this.gutterPx = (int) (GUTTER_DP * density + 0.5f);
            this.cellPx = (int) (CELL_DP * density + 0.5f);
            this.thickness = Math.max(1f, density);

            bg.setColor(ROW_BG);
            bg.setStyle(Paint.Style.FILL);
            gutter.setColor(GUTTER_BG);
            gutter.setStyle(Paint.Style.FILL);
            border.setColor(BORDER);
            border.setStyle(Paint.Style.FILL);
            tick.setColor(TICK);
            tick.setStyle(Paint.Style.FILL);
            label.setColor(MUTED);
            label.setTypeface(Typeface.MONOSPACE);
            label.setTextSize(10f * scaledDensity);
            label.setTextAlign(Paint.Align.LEFT);
            setContentDescription("Timeline ruler");
        }

        void setTotalFrames(int n) {
            totalFrames = Math.max(1, n);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth();
            int h = getHeight();

            c.drawRect(0, 0, w, h, bg);
            c.drawRect(0, 0, gutterPx, h, gutter);

            float small = 4f * density;
            float tall = 8f * density;
            for (int f = 0; f < totalFrames; f++) {
                float x = gutterPx + f * cellPx;
                if (x > w) break;
                boolean major = (f % 5 == 0);
                float len = major ? tall : small;
                c.drawRect(x, h - len, x + thickness, h, tick);
                if (major) {
                    c.drawText(String.valueOf(f), x + 2f * density,
                        10f * scaledDensity, label);
                }
            }

            c.drawRect(gutterPx - thickness, 0, gutterPx, h, border);
            c.drawRect(0, h - thickness, w, h, border);
        }
    }

    /** One track: left label gutter plus a rounded keyframe/empty cell per frame. */
    private static class TrackRow extends View {
        private final Paint rowBg = new Paint();
        private final Paint gutterBg = new Paint();
        private final Paint border = new Paint();
        private final Paint fillKey = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokeKey = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokeEmpty = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private final float density;
        private final int gutterPx;
        private final int cellPx;
        private final float thickness;

        private final String label;
        private final int index;
        private int totalFrames = 60;
        private int[] keyframes;
        private FrameManager frameManager;
        private IntConsumer onCellTap;

        TrackRow(Context c, int index) {
            super(c);
            this.index = index;
            this.label = TRACK_LABELS[index];
            this.density = getResources().getDisplayMetrics().density;
            this.gutterPx = (int) (GUTTER_DP * density + 0.5f);
            this.cellPx = (int) (CELL_DP * density + 0.5f);
            this.thickness = Math.max(1f, density);

            rowBg.setColor(ROW_BG);
            rowBg.setStyle(Paint.Style.FILL);
            gutterBg.setColor(GUTTER_BG);
            gutterBg.setStyle(Paint.Style.FILL);
            border.setColor(BORDER);
            border.setStyle(Paint.Style.FILL);

            fillKey.setColor(CELL_KEY_BG);
            fillKey.setStyle(Paint.Style.FILL);
            strokeKey.setColor(ACCENT);
            strokeKey.setStyle(Paint.Style.STROKE);
            strokeKey.setStrokeWidth(1.5f * density);
            strokeKey.setAntiAlias(true);
            strokeEmpty.setColor(BORDER);
            strokeEmpty.setStyle(Paint.Style.STROKE);
            strokeEmpty.setStrokeWidth(thickness);
            strokeEmpty.setAntiAlias(true);

            labelPaint.setColor(MUTED);
            labelPaint.setTypeface(Typeface.MONOSPACE);
            labelPaint.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            setContentDescription("Track " + label);
        }

        void setTotalFrames(int n) {
            totalFrames = Math.max(1, n);
            invalidate();
        }

        void setKeyframes(int[] frames) {
            keyframes = frames;
            invalidate();
        }

        void setFrameManager(FrameManager manager) {
            frameManager = manager;
            invalidate();
        }

        void setOnCellTap(IntConsumer l) {
            onCellTap = l;
        }

        private boolean isKeyframe(int frame) {
            if (frameManager != null) {
                LayerManager frameLayers = frameManager.getFrame(frame);
                return frameLayers != null && frameLayers.size() > index;
            }
            if (keyframes != null) {
                for (int k : keyframes) {
                    if (k == frame) return true;
                }
            }
            return false;
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth();
            int h = getHeight();

            c.drawRect(0, 0, w, h, rowBg);
            c.drawRect(0, 0, gutterPx, h, gutterBg);

            float inset = 1f * density;
            float top = 6f * density;
            float bottom = h - 6f * density;
            float maxR = 8f * density;

            for (int f = 0; f < totalFrames; f++) {
                float left = gutterPx + f * cellPx + inset;
                if (left > w) break;
                float right = gutterPx + (f + 1) * cellPx - inset;
                float rx = Math.min(maxR, Math.min((right - left) / 2f,
                    (bottom - top) / 2f));
                rect.set(left, top, right, bottom);
                if (isKeyframe(f)) {
                    c.drawRoundRect(rect, rx, rx, fillKey);
                    c.drawRoundRect(rect, rx, rx, strokeKey);
                } else {
                    c.drawRoundRect(rect, rx, rx, strokeEmpty);
                }
            }

            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            float ty = h / 2f - (fm.ascent + fm.descent) / 2f;
            c.drawText(label, gutterPx / 2f, ty, labelPaint);

            c.drawRect(gutterPx - thickness, 0, gutterPx, h, border);
            c.drawRect(0, h - thickness, w, h, border);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    int frame = Math.round((e.getX() - gutterPx) / (float) cellPx);
                    if (onCellTap != null) onCellTap.accept(frame);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    performClick();
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
