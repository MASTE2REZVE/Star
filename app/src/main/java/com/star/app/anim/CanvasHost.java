package com.star.app.anim;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.View;

public class CanvasHost extends GLSurfaceView {
    private final GLRenderer renderer;

    public CanvasHost(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        renderer = new GLRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getX();
                float y = event.getY();
                int action = event.getActionMasked();
                queueEvent(() -> {
                    if (action == MotionEvent.ACTION_DOWN) renderer.onTouchStart(x, y);
                    else if (action == MotionEvent.ACTION_MOVE) renderer.onTouchMove(x, y);
                    else if (action == MotionEvent.ACTION_UP) renderer.onTouchEnd();
                });
                if (action == MotionEvent.ACTION_DOWN
                        || action == MotionEvent.ACTION_MOVE
                        || action == MotionEvent.ACTION_UP) {
                    CanvasHost.this.requestRender();
                }
                return true;
            }
        });
    }

    public LayerManager getLayerManager() { return renderer.getLayerManager(); }
    public TileManager getTileManager() {
        LayerManager.Layer active = renderer.getLayerManager().getActiveLayer();
        return active != null ? active.getTiles() : null;
    }
    
    public void undo() { queueEvent(() -> renderer.undo()); }
    public void redo() { queueEvent(() -> renderer.redo()); }
    public void setCanvasSize(int w, int h) { queueEvent(() -> renderer.setCanvasSize(w, h)); }
    public void setFrameManager(FrameManager fm) { queueEvent(() -> renderer.setFrameManager(fm)); }
    public void setOnionSkinEnabled(boolean enabled) { queueEvent(() -> renderer.setOnionSkinEnabled(enabled)); }
    public void setBrushRadius(int radius) { queueEvent(() -> renderer.setBrushRadius(radius)); }
    public void setBrushOpacity(int opacity) { queueEvent(() -> renderer.setBrushOpacity(opacity)); }
    public void setCurrentFrame(int frame) { queueEvent(() -> renderer.setCurrentFrame(frame)); }
    public void exportFramePng(int frame, ExportHelper.Listener listener) {
        queueEvent(() -> renderer.exportFramePng(frame, listener));
    }
}
