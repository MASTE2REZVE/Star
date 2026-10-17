package com.star.app.anim;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer {
    private final LayerManager layerManager = new LayerManager();
    private final float[] projectionMatrix = new float[16];
    private final List<Float> currentStroke = new ArrayList<>();
    private final List<List<Float>> allStrokes = new ArrayList<>();
    
    private FloatBuffer vertexBuffer;
    private int program;
    private int uColorLoc, uMatrixLoc;

    private final float[] currentColor = {0, 0, 0, 1};

    private int brushRadius = 8;
    private int brushOpacity = 255;
    private boolean onionSkinEnabled = false;
    private int currentFrame = 0;
    private FrameManager frameManager;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.058f, 0.058f, 0.058f, 1.0f);
        setupShaders();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        Matrix.orthoM(projectionMatrix, 0, 0, width, height, 0, -1, 1);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(uMatrixLoc, 1, false, projectionMatrix, 0);

        // Draw White Paper
        drawRect(100, 100, 1800, 1000, 1.0f, 1.0f, 1.0f); 

        // Draw Strokes
        GLES30.glLineWidth(brushRadius);
        GLES30.glUniform4f(uColorLoc, currentColor[0], currentColor[1], currentColor[2], brushOpacity / 255.0f);
        for (List<Float> stroke : allStrokes) {
            drawLineStrip(stroke);
        }
        if (!currentStroke.isEmpty()) {
            drawLineStrip(currentStroke);
        }
    }

    public void onTouchStart(float x, float y) { currentStroke.clear(); currentStroke.add(x); currentStroke.add(y); }
    public void onTouchMove(float x, float y) { currentStroke.add(x); currentStroke.add(y); }
    public void onTouchEnd() { if (currentStroke.size() > 2) allStrokes.add(new ArrayList<>(currentStroke)); currentStroke.clear(); }
    
    public void undo() { if (!allStrokes.isEmpty()) allStrokes.remove(allStrokes.size() - 1); }
    public void redo() { /* Placeholder for now */ }
    
    public void setCanvasSize(int w, int h) {}
    public void setFrameManager(FrameManager fm) { this.frameManager = fm; }
    public void setOnionSkinEnabled(boolean enabled) { this.onionSkinEnabled = enabled; }
    public void setBrushRadius(int radius) { this.brushRadius = radius; }
    public void setBrushColor(float r, float g, float b) { currentColor[0] = r; currentColor[1] = g; currentColor[2] = b; }
    public void setBrushOpacity(int opacity) { this.brushOpacity = opacity; }
    public void setCurrentFrame(int frame) { this.currentFrame = frame; }
    public void exportFramePng(int frame, ExportHelper.Listener listener) { /* Placeholder */ }

    public LayerManager getLayerManager() { return layerManager; }

    private void setupShaders() {
        String vShader = "uniform mat4 uMatrix; attribute vec4 vPosition; void main() { gl_Position = uMatrix * vPosition; }";
        String fShader = "precision mediump float; uniform vec4 uColor; void main() { gl_FragColor = uColor; }";

        int v = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER);
        GLES30.glShaderSource(v, vShader); GLES30.glCompileShader(v);
        int f = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER);
        GLES30.glShaderSource(f, fShader); GLES30.glCompileShader(f);

        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, v); GLES30.glAttachShader(program, f);
        GLES30.glLinkProgram(program);
        uColorLoc = GLES30.glGetUniformLocation(program, "uColor");
        uMatrixLoc = GLES30.glGetUniformLocation(program, "uMatrix");
        GLES30.glEnableVertexAttribArray(0);
    }

    private void drawRect(float l, float t, float r, float b, float cr, float cg, float cb) {
        float[] verts = { l,t,0, r,t,0, l,b,0, r,t,0, r,b,0, l,b,0 };
        GLES30.glUniform4f(uColorLoc, cr, cg, cb, 1.0f);
        vertexBuffer = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(verts).position(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
    }

    private void drawLineStrip(List<Float> stroke) {
        float[] verts = new float[stroke.size() / 2 * 3];
        int j = 0;
        for (int i = 0; i < stroke.size(); i += 2) {
            verts[j++] = stroke.get(i);
            verts[j++] = stroke.get(i+1);
            verts[j++] = 0;
        }
        vertexBuffer = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(verts).position(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, verts.length / 3);
    }
}
