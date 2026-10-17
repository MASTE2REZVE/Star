package com.star.app.anim;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class CanvasHost extends FrameLayout {

    private static final int BG = 0xFF0F0F0F;

    private static final float MIN_ZOOM = 0.05f;
    private static final float MAX_ZOOM = 20f;

    public interface FloatConsumer {
        void accept(float value);
    }

    private final GLSurfaceView surface;
    private final GLRenderer renderer;
    private final float density;

    private volatile int canvasWidth = 1240;
    private volatile int canvasHeight = 1754;
    private volatile float zoom = 1f;
    private volatile float offsetX = 0f;
    private volatile float offsetY = 0f;

    private FloatConsumer zoomCallback;

    private boolean haveFocus = false;
    private float lastFocusX = 0f;
    private float lastFocusY = 0f;

    private boolean strokeActive = false;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;

    private final ScaleGestureDetector scaleDetector;

    public CanvasHost(Context c) {
        this(c, null);
    }

    public CanvasHost(Context c, AttributeSet attrs) {
        super(c, attrs);
        this.density = getResources().getDisplayMetrics().density;

        surface = new GLSurfaceView(c);
        surface.setEGLContextClientVersion(3);
        surface.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        renderer = new GLRenderer(density);
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surface.setBackgroundColor(BG);
        surface.setFocusable(true);
        surface.setClickable(false);
        surface.setLongClickable(false);
        surface.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        scaleDetector = new ScaleGestureDetector(c,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    float before = zoom;
                    zoom = clamp(zoom * d.getScaleFactor(), MIN_ZOOM, MAX_ZOOM);
                    if (zoom != before) {
                        float vw = surface.getWidth();
                        float vh = surface.getHeight();
                        float cx = vw * 0.5f;
                        float cy = vh * 0.5f;
                        float r = zoom / before;
                        offsetX = (d.getFocusX() - cx) * (1f - r) + r * offsetX;
                        offsetY = (d.getFocusY() - cy) * (1f - r) + r * offsetY;
                        notifyZoom();
                        pushView();
                    }
                    return true;
                }
            });

        addView(surface);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);

        final int action = e.getActionMasked();
        int count = e.getPointerCount();
        if (count >= 2) {
            if (strokeActive) {
                strokeActive = false;
                final float sx = lastTouchX;
                final float sy = lastTouchY;
                surface.queueEvent(() -> renderer.onTouch(sx, sy, MotionEvent.ACTION_CANCEL));
            }
            float cx = (e.getX(0) + e.getX(1)) / 2f;
            float cy = (e.getY(0) + e.getY(1)) / 2f;
            if (haveFocus) {
                offsetX += cx - lastFocusX;
                offsetY += cy - lastFocusY;
                pushView();
            }
            lastFocusX = cx;
            lastFocusY = cy;
            haveFocus = true;
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN
            || action == MotionEvent.ACTION_MOVE
            || action == MotionEvent.ACTION_UP) {
            final float x = e.getX();
            final float y = e.getY();
            lastTouchX = x;
            lastTouchY = y;
            strokeActive = action != MotionEvent.ACTION_UP;
            surface.queueEvent(() -> renderer.onTouch(x, y, action));
        }

        if (action == MotionEvent.ACTION_UP
            || action == MotionEvent.ACTION_CANCEL
            || action == MotionEvent.ACTION_POINTER_UP) {
            haveFocus = false;
            strokeActive = false;
        }
        return true;
    }

    public void onResume() {
        surface.onResume();
    }

    public void onPause() {
        surface.onPause();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        surface.onResume();
    }

    @Override
    protected void onDetachedFromWindow() {
        surface.onPause();
        super.onDetachedFromWindow();
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
        zoom = 1f;
        offsetX = 0f;
        offsetY = 0f;
        notifyZoom();
        pushView();
    }

    private void pushView() {
        final float z = zoom;
        final float ox = offsetX;
        final float oy = offsetY;
        surface.queueEvent(() -> renderer.setViewTransform(z, ox, oy));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetView();
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
}
