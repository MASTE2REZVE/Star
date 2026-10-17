package com.star.app.anim;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
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
        surface.setRenderer(new GLRenderer());
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surface.setBackgroundColor(BG);
        surface.setFocusable(true);
        surface.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

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

        surface.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
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
        });

        addView(surface);
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
}
