package com.star.app.anim;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

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

    private static final String VERTEX_SHADER =
        "attribute vec2 aPos;\n"
        + "uniform vec4 uRect;\n"
        + "void main() {\n"
        + "    vec2 p = uRect.xy + aPos * (uRect.zw - uRect.xy);\n"
        + "    gl_Position = vec4(p, 0.0, 1.0);\n"
        + "}\n";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n"
        + "uniform vec4 uColor;\n"
        + "void main() {\n"
        + "    gl_FragColor = uColor;\n"
        + "}\n";

    private static final float[] UNIT_QUAD = {
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f,
    };

    public static final int TOUCH_DOWN = 0;
    public static final int TOUCH_MOVE = 1;
    public static final int TOUCH_UP = 2;

    private static final int MAX_TOUCH_POINTS = 4096;

    private final float density;

    private final List<float[]> touchPoints = new ArrayList<>();

    private FloatBuffer quadBuffer;
    private int program;
    private int aPosLoc;
    private int uRectLoc;
    private int uColorLoc;
    private boolean ready;

    private int viewWidth;
    private int viewHeight;

    public GLRenderer() {
        this(DEFAULT_DENSITY);
    }

    public GLRenderer(float density) {
        this.density = density > 0f ? density : DEFAULT_DENSITY;
    }

    public synchronized void onTouch(float x, float y, int action) {
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            touchPoints.clear();
            touchPoints.add(new float[] { x, y, TOUCH_DOWN });
        } else if (action == android.view.MotionEvent.ACTION_MOVE) {
            touchPoints.add(new float[] { x, y, TOUCH_MOVE });
        } else if (action == android.view.MotionEvent.ACTION_UP) {
            touchPoints.add(new float[] { x, y, TOUCH_UP });
        } else {
            return;
        }
        while (touchPoints.size() > MAX_TOUCH_POINTS) {
            touchPoints.remove(0);
        }
    }

    public synchronized List<float[]> getTouchPoints() {
        return new ArrayList<>(touchPoints);
    }

    public synchronized void clearTouchPoints() {
        touchPoints.clear();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(CLEAR_R, CLEAR_G, CLEAR_B, CLEAR_A);

        ByteBuffer bb = ByteBuffer.allocateDirect(UNIT_QUAD.length * 4);
        bb.order(ByteOrder.nativeOrder());
        quadBuffer = bb.asFloatBuffer();
        quadBuffer.put(UNIT_QUAD);
        quadBuffer.position(0);

        int vs = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = link(vs, fs);
        aPosLoc = GLES30.glGetAttribLocation(program, "aPos");
        uRectLoc = GLES30.glGetUniformLocation(program, "uRect");
        uColorLoc = GLES30.glGetUniformLocation(program, "uColor");
        ready = program != 0 && aPosLoc >= 0 && uRectLoc >= 0 && uColorLoc >= 0;
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

        float borderPx = density;
        float bHalfX = borderPx / viewWidth;
        float bHalfY = borderPx / viewHeight;

        float[] paper = { -halfW, -halfH, halfW, halfH };
        float[] border = { -halfW - bHalfX, -halfH - bHalfY, halfW + bHalfX, halfH + bHalfY };

        GLES30.glUseProgram(program);
        GLES30.glEnableVertexAttribArray(aPosLoc);
        GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer);

        drawRect(border, BORDER_R, BORDER_G, BORDER_B, 1.0f);
        drawRect(paper, PAPER_R, PAPER_G, PAPER_B, 1.0f);

        GLES30.glDisableVertexAttribArray(aPosLoc);
    }

    private void drawRect(float[] rect, float r, float g, float b, float a) {
        GLES30.glUniform4f(uRectLoc, rect[0], rect[1], rect[2], rect[3]);
        GLES30.glUniform4f(uColorLoc, r, g, b, a);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
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
