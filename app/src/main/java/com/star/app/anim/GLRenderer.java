package com.star.app.anim;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer {

    private static final float CLEAR_R = 0.058f;
    private static final float CLEAR_G = 0.058f;
    private static final float CLEAR_B = 0.058f;
    private static final float CLEAR_A = 1.0f;

    private static final float PAPER_ASPECT = 1920f / 1080f;
    private static final float PAPER_FILL = 0.86f;

    private static final float PAPER_R = 1.0f;
    private static final float PAPER_G = 1.0f;
    private static final float PAPER_B = 1.0f;

    private static final float BORDER_R = 0x7C / 255f;
    private static final float BORDER_G = 0xB3 / 255f;
    private static final float BORDER_B = 0xFF / 255f;

    private static final float DEFAULT_DENSITY = 3.0f;

    public static final int CANVAS_W = 1920;
    public static final int CANVAS_H = 1080;

    private static final String VERTEX_SHADER =
        "attribute vec2 aPos;\n"
        + "attribute vec2 aTex;\n"
        + "uniform mat4 uProj;\n"
        + "uniform vec4 uRect;\n"
        + "varying vec2 vTex;\n"
        + "void main() {\n"
        + "    vec2 p = uRect.xy + aPos * (uRect.zw - uRect.xy);\n"
        + "    gl_Position = uProj * vec4(p, 0.0, 1.0);\n"
        + "    vTex = aTex;\n"
        + "}\n";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n"
        + "uniform sampler2D uTex;\n"
        + "uniform vec4 uColor;\n"
        + "uniform float uUseTex;\n"
        + "varying vec2 vTex;\n"
        + "void main() {\n"
        + "    vec4 t = texture2D(uTex, vTex) * uColor;\n"
        + "    gl_FragColor = mix(uColor, t, uUseTex);\n"
        + "}\n";

    private static final float[] QUAD_TEX = {
        0f, 0f, 0f, 1f,
        1f, 0f, 1f, 1f,
        0f, 1f, 0f, 0f,
        1f, 1f, 1f, 0f,
    };

    public static final int TOUCH_DOWN = 0;
    public static final int TOUCH_MOVE = 1;
    public static final int TOUCH_UP = 2;

    private final float density;

    private final TileManager tileManager = new TileManager();
    private final Map<Tile, Integer> textures = new IdentityHashMap<>();

    private FloatBuffer quadTexBuffer;
    private FloatBuffer tileBuffer;
    private ByteBuffer pixelBuffer;

    private int program;
    private int aPosLoc;
    private int aTexLoc;
    private int uProjLoc;
    private int uRectLoc;
    private int uColorLoc;
    private int uTexLoc;
    private int uUseTexLoc;
    private boolean ready;

    private final float[] proj = new float[16];

    private volatile float viewZoom = 1f;
    private volatile float viewPanX = 0f;
    private volatile float viewPanY = 0f;
    private boolean haveView = false;

    private int viewWidth;
    private int viewHeight;

    private float paperHalfW;
    private float paperHalfH;

    public GLRenderer() {
        this(DEFAULT_DENSITY);
    }

    public GLRenderer(float density) {
        this.density = density > 0f ? density : DEFAULT_DENSITY;
    }

    public TileManager getTileManager() {
        return tileManager;
    }

    public void undo() {
        tileManager.undo();
    }

    public void redo() {
        tileManager.redo();
    }

    public void setBrushRadius(float radius) {
        tileManager.setBrushRadius(radius);
    }

    public void setBrushAlpha(int alpha) {
        tileManager.setBrushAlpha(alpha);
    }

    public void setViewTransform(float zoom, float panX, float panY) {
        this.viewZoom = zoom > 0f ? zoom : 1f;
        this.viewPanX = panX;
        this.viewPanY = panY;
        this.haveView = true;
    }

    public synchronized void onTouch(float x, float y, int action) {
        if (paperHalfW <= 0f || paperHalfH <= 0f || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        float[] p = screenToCanvas(x, y);
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            tileManager.beginStroke(p[0], p[1]);
        } else if (action == android.view.MotionEvent.ACTION_MOVE) {
            tileManager.strokeTo(p[0], p[1]);
        } else if (action == android.view.MotionEvent.ACTION_UP
            || action == android.view.MotionEvent.ACTION_CANCEL) {
            tileManager.strokeTo(p[0], p[1]);
            tileManager.endStroke();
        }
    }

    private float[] screenToCanvas(float x, float y) {
        float ndcX = x / viewWidth * 2.0f - 1.0f;
        float ndcY = 1.0f - y / viewHeight * 2.0f;
        float tx = viewPanX / viewWidth * 2.0f;
        float ty = -viewPanY / viewHeight * 2.0f;
        float bx = (ndcX - tx) / viewZoom;
        float by = (ndcY - ty) / viewZoom;
        float u = (bx / paperHalfW + 1.0f) * 0.5f;
        float v = (paperHalfH - by) / (2.0f * paperHalfH);
        return new float[] { u * CANVAS_W, v * CANVAS_H };
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(CLEAR_R, CLEAR_G, CLEAR_B, CLEAR_A);

        quadTexBuffer = directFloatBuffer(QUAD_TEX);
        tileBuffer = directFloatBuffer(new float[16]);
        pixelBuffer = ByteBuffer.allocateDirect(Tile.SIZE * Tile.SIZE * 4);
        pixelBuffer.order(ByteOrder.nativeOrder());

        int vs = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = link(vs, fs);
        aPosLoc = GLES30.glGetAttribLocation(program, "aPos");
        aTexLoc = GLES30.glGetAttribLocation(program, "aTex");
        uProjLoc = GLES30.glGetUniformLocation(program, "uProj");
        uRectLoc = GLES30.glGetUniformLocation(program, "uRect");
        uColorLoc = GLES30.glGetUniformLocation(program, "uColor");
        uTexLoc = GLES30.glGetUniformLocation(program, "uTex");
        uUseTexLoc = GLES30.glGetUniformLocation(program, "uUseTex");
        ready = program != 0 && aPosLoc >= 0 && aTexLoc >= 0
            && uProjLoc >= 0 && uRectLoc >= 0 && uColorLoc >= 0
            && uTexLoc >= 0 && uUseTexLoc >= 0;
        Matrix.setIdentityM(proj, 0);
    }

    private static FloatBuffer directFloatBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);
        return fb;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        if (!ready || viewWidth <= 0 || viewHeight <= 0) return;

        float availW = viewWidth * PAPER_FILL;
        float availH = viewHeight * PAPER_FILL;
        float paperW = availW;
        float paperH = paperW / PAPER_ASPECT;
        if (paperH > availH) {
            paperH = availH;
            paperW = paperH * PAPER_ASPECT;
        }

        float halfW = paperW / viewWidth;
        float halfH = paperH / viewHeight;
        paperHalfW = halfW;
        paperHalfH = halfH;

        float borderPx = density;
        float bHalfX = borderPx / viewWidth;
        float bHalfY = borderPx / viewHeight;

        float[] paper = { -halfW, -halfH, halfW, halfH };
        float[] border = { -halfW - bHalfX, -halfH - bHalfY, halfW + bHalfX, halfH + bHalfY };

        GLES30.glUseProgram(program);
        buildProjection();
        GLES30.glUniformMatrix4fv(uProjLoc, 1, false, proj, 0);
        GLES30.glEnableVertexAttribArray(aPosLoc);
        GLES30.glEnableVertexAttribArray(aTexLoc);

        drawRect(border, BORDER_R, BORDER_G, BORDER_B, 1.0f);
        drawRect(paper, PAPER_R, PAPER_G, PAPER_B, 1.0f);

        drawTiles(halfW, halfH);

        GLES30.glDisableVertexAttribArray(aPosLoc);
        GLES30.glDisableVertexAttribArray(aTexLoc);
    }

    private void buildProjection() {
        float zoom = haveView ? viewZoom : 1f;
        float panX = haveView ? viewPanX : 0f;
        float panY = haveView ? viewPanY : 0f;
        float tx = panX / viewWidth * 2.0f;
        float ty = -panY / viewHeight * 2.0f;
        Matrix.setIdentityM(proj, 0);
        Matrix.translateM(proj, 0, tx, ty, 0f);
        Matrix.scaleM(proj, 0, zoom, zoom, 1f);
    }

    private void drawRect(float[] rect, float r, float g, float b, float a) {
        bindQuad(quadTexBuffer);
        GLES30.glUniform1f(uUseTexLoc, 0.0f);
        GLES30.glUniform4f(uRectLoc, rect[0], rect[1], rect[2], rect[3]);
        GLES30.glUniform4f(uColorLoc, r, g, b, a);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void bindQuad(FloatBuffer buffer) {
        int stride = 4 * 4;
        GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, stride, buffer);
        buffer.position(2);
        GLES30.glVertexAttribPointer(aTexLoc, 2, GLES30.GL_FLOAT, false, stride, buffer);
        buffer.position(0);
    }

    private void drawTiles(float halfW, float halfH) {
        Map<String, Tile> current = tileManager.getTiles();
        releaseStaleTextures(current);

        List<Tile> dirty = tileManager.consumeDirty();
        for (Tile tile : dirty) {
            uploadTile(tile);
        }
        if (current.isEmpty()) return;

        GLES30.glUniform1f(uUseTexLoc, 1.0f);
        GLES30.glUniform4f(uColorLoc, 1.0f, 1.0f, 1.0f, 1.0f);
        GLES30.glUniform1i(uTexLoc, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        for (Tile tile : current.values()) {
            Integer id = textures.get(tile);
            if (id == null || id == 0) continue;

            float x0 = -halfW + (tile.getTileX() * (float) Tile.SIZE / CANVAS_W) * (2.0f * halfW);
            float x1 = -halfW + ((tile.getTileX() + 1) * (float) Tile.SIZE / CANVAS_W) * (2.0f * halfW);
            float yTop = halfH - (tile.getTileY() * (float) Tile.SIZE / CANVAS_H) * (2.0f * halfH);
            float yBot = halfH - ((tile.getTileY() + 1) * (float) Tile.SIZE / CANVAS_H) * (2.0f * halfH);

            fillTileBuffer(x0, yBot, x1, yTop);
            bindQuad(tileBuffer);

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id);
            GLES30.glUniform4f(uRectLoc, x0, yBot, x1, yTop);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        }

        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    private void releaseStaleTextures(Map<String, Tile> current) {
        if (textures.isEmpty()) return;
        Set<Tile> live = Collections.newSetFromMap(new IdentityHashMap<Tile, Boolean>());
        live.addAll(current.values());
        Iterator<Map.Entry<Tile, Integer>> it = textures.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Tile, Integer> e = it.next();
            if (!live.contains(e.getKey())) {
                GLES30.glDeleteTextures(1, new int[] { e.getValue() }, 0);
                it.remove();
            }
        }
    }

    private void fillTileBuffer(float x0, float y0, float x1, float y1) {
        tileBuffer.position(0);
        tileBuffer.put(new float[] {
            x0, y0, 0f, 1f,
            x1, y0, 1f, 1f,
            x0, y1, 0f, 0f,
            x1, y1, 1f, 0f,
        });
        tileBuffer.position(0);
    }

    private void uploadTile(Tile tile) {
        Bitmap bitmap = tile.getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;

        Integer existing = textures.get(tile);
        int texId;
        if (existing == null) {
            int[] ids = new int[1];
            GLES30.glGenTextures(1, ids, 0);
            texId = ids[0];
            textures.put(tile, texId);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        } else {
            texId = existing;
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
        }

        pixelBuffer.clear();
        bitmap.copyPixelsToBuffer(pixelBuffer);
        pixelBuffer.position(0);

        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            bitmap.getWidth(), bitmap.getHeight(), 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer);
    }

    private int compile(int type, String src) {
        int shader = GLES30.glCreateShader(type);
        if (shader == 0) return 0;
        GLES30.glShaderSource(shader, src);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            GLES30.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private int link(int vs, int fs) {
        if (vs == 0 || fs == 0) return 0;
        int prog = GLES30.glCreateProgram();
        if (prog == 0) return 0;
        GLES30.glAttachShader(prog, vs);
        GLES30.glAttachShader(prog, fs);
        GLES30.glLinkProgram(prog);
        int[] status = new int[1];
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            GLES30.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }
}
