package com.star.app.anim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class CanvasHost extends FrameLayout {

    private static final int BACKDROP = 0xFF2A2A2A;
    private static final int CANVAS = 0xFFFFFFFF;
    private static final int GRID = 0xFFD0D0D0;
    private static final int BORDER = 0xFF7CB3FF;
    private static final int CHECKER_LIGHT = 0xFFFFFFFF;
    private static final int CHECKER_DARK = 0xFFC8C8C8;

    private static final float MIN_ZOOM = 0.05f;
    private static final float MAX_ZOOM = 20f;
    private static final int GRID_STEP = 100;

    public interface FloatConsumer {
        void accept(float value);
    }

    private final CanvasSurface surface;
    private final float density;

    private volatile int canvasWidth = 1240;
    private volatile int canvasHeight = 1754;
    private volatile float zoom = 1f;
    private volatile float offsetX = 0f;
    private volatile float offsetY = 0f;

    private FloatConsumer zoomCallback;

    public CanvasHost(Context c) {
        this(c, null);
    }

    public CanvasHost(Context c, AttributeSet attrs) {
        super(c, attrs);
        this.density = getResources().getDisplayMetrics().density;

        surface = new CanvasSurface(c);
        surface.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        addView(surface);
    }

    public void setCanvasSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        this.canvasWidth = w;
        this.canvasHeight = h;
    }

    public float getZoom() {
        return zoom;
    }

    public void resetView() {
        int vw = surface.getWidth();
        int vh = surface.getHeight();
        if (vw <= 0 || vh <= 0) return;
        float pad = dp(32);
        float fit = Math.min((vw - pad) / canvasWidth, (vh - pad) / canvasHeight);
        zoom = clamp(fit, MIN_ZOOM, MAX_ZOOM);
        offsetX = (vw - canvasWidth * zoom) / 2f;
        offsetY = (vh - canvasHeight * zoom) / 2f;
        notifyZoom();
    }

    public void setZoomCallback(FloatConsumer l) {
        this.zoomCallback = l;
        if (l != null) l.accept(zoom);
    }

    private void notifyZoom() {
        FloatConsumer l = zoomCallback;
        if (l != null) l.accept(zoom);
    }

    private float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    private class CanvasSurface extends SurfaceView
            implements SurfaceHolder.Callback, Runnable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF canvasRect = new RectF();

        private final ScaleGestureDetector scaleDetector;

        private Thread renderThread;
        private volatile boolean running = false;

        private boolean haveFocus = false;
        private float lastFocusX = 0f;
        private float lastFocusY = 0f;

        CanvasSurface(Context c) {
            super(c);
            getHolder().addCallback(this);
            setFocusable(true);
            scaleDetector = new ScaleGestureDetector(c,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        float before = zoom;
                        zoom = clamp(zoom * d.getScaleFactor(), MIN_ZOOM, MAX_ZOOM);
                        if (zoom != before) {
                            float focusX = d.getFocusX();
                            float focusY = d.getFocusY();
                            offsetX = focusX - (focusX - offsetX) * (zoom / before);
                            offsetY = focusY - (focusY - offsetY) * (zoom / before);
                            notifyZoom();
                        }
                        return true;
                    }
                });
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            scaleDetector.onTouchEvent(e);

            int count = e.getPointerCount();
            if (count >= 2) {
                float cx = (e.getX(0) + e.getX(1)) / 2f;
                float cy = (e.getY(0) + e.getY(1)) / 2f;
                if (haveFocus) {
                    offsetX += cx - lastFocusX;
                    offsetY += cy - lastFocusY;
                }
                lastFocusX = cx;
                lastFocusY = cy;
                haveFocus = true;
                return true;
            }

            if (e.getActionMasked() == MotionEvent.ACTION_UP
                || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                haveFocus = false;
            }
            return true;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            running = true;
            renderThread = new Thread(this, "CanvasHostRender");
            renderThread.start();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            if (zoom <= 0f) {
                resetView();
            } else if (offsetX == 0f && offsetY == 0f) {
                resetView();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            running = false;
            Thread t = renderThread;
            if (t != null) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {
                }
            }
            renderThread = null;
        }

        @Override
        public void run() {
            while (running) {
                Canvas c = null;
                SurfaceHolder holder = getHolder();
                try {
                    c = holder.lockCanvas();
                    if (c != null) render(c);
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {
                }
            }
        }

        private void render(Canvas c) {
            int vw = getWidth();
            int vh = getHeight();
            if (vw <= 0 || vh <= 0) return;

            float z = zoom;
            float ox = offsetX;
            float oy = offsetY;

            c.drawColor(BACKDROP);

            canvasRect.set(ox, oy, ox + canvasWidth * z, oy + canvasHeight * z);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CANVAS);
            c.drawRect(canvasRect, paint);

            c.save();
            c.clipRect(canvasRect);
            drawChecker(c);
            drawGrid(c, ox, oy, z);
            c.restore();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(BORDER);
            c.drawRect(canvasRect, paint);
        }

        private void drawChecker(Canvas c) {
            float ox = offsetX;
            float oy = offsetY;
            float z = zoom;
            float size = 10f * z;
            paint.setStyle(Paint.Style.FILL);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    paint.setColor(((row + col) % 2 == 0)
                        ? CHECKER_LIGHT : CHECKER_DARK);
                    float left = ox + col * size;
                    float top = oy + row * size;
                    c.drawRect(left, top, left + size, top + size, paint);
                }
            }
        }

        private void drawGrid(Canvas c, float ox, float oy, float z) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density * 0.5f));
            paint.setColor(GRID);

            for (int x = GRID_STEP; x < canvasWidth; x += GRID_STEP) {
                float sx = ox + x * z;
                c.drawLine(sx, canvasRect.top, sx, canvasRect.bottom, paint);
            }
            for (int y = GRID_STEP; y < canvasHeight; y += GRID_STEP) {
                float sy = oy + y * z;
                c.drawLine(canvasRect.left, sy, canvasRect.right, sy, paint);
            }
        }
    }
}
