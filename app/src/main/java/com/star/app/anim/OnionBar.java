package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.star.app.PressFeedback;

import java.util.function.IntConsumer;

public class OnionBar extends LinearLayout {

    private static final int BG = 0xFF1E1E1E;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF8E8E8E;
    private static final int ACCENT = 0xFF7CB3FF;

    private static final int[] FPS_OPTIONS = {12, 24, 30, 60};
    private static final String GLYPH_PREV = "\u25C0\u25C0";
    private static final String GLYPH_PLAY = "\u25B6";
    private static final String GLYPH_PAUSE = "\u23F8";
    private static final String GLYPH_NEXT = "\u25B6\u25B6";

    private final float density;

    private TextView playButton;
    private TextView frameCounter;
    private TextView fpsDisplay;
    private TextView loopButton;
    private TextView tagButton;

    private int currentFrame = 0;
    private int totalFrames = 120;
    private int fps = 24;
    private boolean playing = false;
    private boolean loopActive = false;
    private boolean tagged = false;

    private Runnable onPlay;
    private Runnable onPause;
    private Runnable onPrev;
    private Runnable onNext;
    private Runnable onTag;
    private IntConsumer onFps;
    private IntConsumer onFrameAdvance;

    private final Handler playbackHandler = new Handler(Looper.getMainLooper());

    /** Advances the playhead one frame per tick, wrapping back to the first frame. */
    private final Runnable playbackTick = new Runnable() {
        @Override public void run() {
            if (!playing) return;
            if (totalFrames > 0) {
                int next = currentFrame + 1;
                if (next >= totalFrames) next = 0;
                currentFrame = next;
                updateFrameCounter();
                if (onFrameAdvance != null) onFrameAdvance.accept(currentFrame);
            }
            playbackHandler.postDelayed(this, frameDelay());
        }
    };

    public OnionBar(Context c) {
        super(c);
        this.density = getResources().getDisplayMetrics().density;

        setOrientation(HORIZONTAL);
        setBackground(new BarBackground(BG, BORDER, density));
        setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(c);
        label.setText("ONION");
        label.setTextSize(11f);
        label.setTextColor(MUTED);
        label.setTypeface(Typeface.MONOSPACE);
        label.setAllCaps(true);
        label.setLetterSpacing(0.15f);
        label.setGravity(Gravity.CENTER);
        label.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        addView(label);

        TextView prev = glyphButton(GLYPH_PREV, 12f);
        prev.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (onPrev != null) onPrev.run();
            }
        });
        addView(prev);

        playButton = glyphButton(GLYPH_PLAY, 13f);
        playButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                togglePlay();
            }
        });
        addView(playButton);

        TextView next = glyphButton(GLYPH_NEXT, 12f);
        next.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (onNext != null) onNext.run();
            }
        });
        addView(next);

        frameCounter = new TextView(c);
        frameCounter.setTextSize(12f);
        frameCounter.setTextColor(TEXT);
        frameCounter.setTypeface(Typeface.MONOSPACE);
        frameCounter.setGravity(Gravity.CENTER);
        frameCounter.setPadding(dp(4), 0, dp(4), 0);
        frameCounter.setLayoutParams(margins(WRAP, MATCH, 8));
        updateFrameCounter();
        addView(frameCounter);

        fpsDisplay = new TextView(c);
        fpsDisplay.setTextSize(11f);
        fpsDisplay.setTextColor(MUTED);
        fpsDisplay.setTypeface(Typeface.MONOSPACE);
        fpsDisplay.setGravity(Gravity.CENTER);
        fpsDisplay.setPadding(dp(6), 0, dp(6), 0);
        fpsDisplay.setClickable(true);
        fpsDisplay.setLayoutParams(margins(WRAP, MATCH, 8));
        fpsDisplay.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                showFpsMenu(v);
            }
        });
        PressFeedback.attach(fpsDisplay);
        updateFpsDisplay();
        addView(fpsDisplay);

        View spacer = new View(c);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addView(spacer);

        loopButton = glyphButton("Loop", 11f);
        loopButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                loopActive = !loopActive;
                updateLoop();
            }
        });
        addView(loopButton);

        tagButton = glyphButton("Tag", 11f);
        tagButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                tagged = !tagged;
                updateTag();
                if (onTag != null) onTag.run();
            }
        });
        addView(tagButton);
    }

    public void setOnPlay(Runnable l) {
        this.onPlay = l;
    }

    public void setOnPause(Runnable l) {
        this.onPause = l;
    }

    public void setOnPrev(Runnable l) {
        this.onPrev = l;
    }

    public void setOnNext(Runnable l) {
        this.onNext = l;
    }

    public void setOnFps(IntConsumer l) {
        this.onFps = l;
    }

    public void setOnFrameAdvance(IntConsumer l) {
        this.onFrameAdvance = l;
    }

    public void setOnTag(Runnable l) {
        this.onTag = l;
    }

    public void setFrame(int current, int total) {
        this.currentFrame = Math.max(0, current);
        if (total > 0) this.totalFrames = total;
        if (this.currentFrame > this.totalFrames) this.currentFrame = this.totalFrames;
        updateFrameCounter();
    }

    public void setFps(int value) {
        if (value > 0) this.fps = value;
        updateFpsDisplay();
        if (playing) startPlayback();
    }

    public void setPlaying(boolean value) {
        if (this.playing == value) return;
        this.playing = value;
        updatePlayGlyph();
        if (playing) {
            startPlayback();
        } else {
            stopPlayback();
        }
    }

    public int getFrame() {
        return currentFrame;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public int getFps() {
        return fps;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isLooping() {
        return loopActive;
    }

    private void togglePlay() {
        boolean wasPlaying = playing;
        playing = !wasPlaying;
        updatePlayGlyph();
        if (wasPlaying) {
            stopPlayback();
            if (onPause != null) onPause.run();
        } else {
            startPlayback();
            if (onPlay != null) onPlay.run();
        }
    }

    private void startPlayback() {
        playbackHandler.removeCallbacks(playbackTick);
        playbackHandler.postDelayed(playbackTick, frameDelay());
    }

    private void stopPlayback() {
        playbackHandler.removeCallbacks(playbackTick);
    }

    private int frameDelay() {
        return Math.max(16, 1000 / Math.max(1, fps));
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPlayback();
        playing = false;
        super.onDetachedFromWindow();
    }

    private void showFpsMenu(View anchor) {
        PopupMenu pm = new PopupMenu(getContext(), anchor);
        for (int i = 0; i < FPS_OPTIONS.length; i++) {
            pm.getMenu().add(0, i, i, FPS_OPTIONS[i] + " fps");
        }
        pm.setOnMenuItemClickListener(item -> {
            int value = FPS_OPTIONS[item.getItemId()];
            setFps(value);
            if (onFps != null) onFps.accept(value);
            return true;
        });
        pm.show();
    }

    private TextView glyphButton(String glyph, float sizeSp) {
        TextView b = new TextView(getContext());
        b.setText(glyph);
        b.setTextSize(sizeSp);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.MONOSPACE);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setBackground(glyphBackground(false));
        if (glyph.length() > 2) {
            b.setLayoutParams(margins(WRAP, dp(32), 8));
            b.setPadding(dp(8), 0, dp(8), 0);
        } else {
            b.setLayoutParams(margins(dp(32), dp(32), 8));
        }
        PressFeedback.attach(b);
        return b;
    }

    private void updateFrameCounter() {
        frameCounter.setText(currentFrame + " / " + totalFrames);
    }

    private void updateFpsDisplay() {
        fpsDisplay.setText(fps + " fps");
    }

    private void updatePlayGlyph() {
        playButton.setText(playing ? GLYPH_PAUSE : GLYPH_PLAY);
    }

    private void updateLoop() {
        loopButton.setTextColor(loopActive ? ACCENT : TEXT);
    }

    private void updateTag() {
        tagButton.setTextColor(tagged ? ACCENT : TEXT);
    }

    private Drawable glyphBackground(final boolean active) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas c) {
                Rect b = getBounds();
                p.setColor(active ? 0xFF2A2A2A : 0x00000000);
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(b.left, b.top, b.right, b.bottom,
                    dp(6), dp(6), p);
            }
            @Override public void setAlpha(int a) { p.setAlpha(a); }
            @Override public void setColorFilter(ColorFilter cf) {
                p.setColorFilter(cf);
            }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    private LinearLayout.LayoutParams margins(int w, int h, int gapDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.leftMargin = dp(gapDp);
        return lp;
    }

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private int dp(int v) {
        return (int) (v * density + 0.5f);
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
            c.drawRect(b.left, b.top, b.right, b.top + thickness, border);
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
