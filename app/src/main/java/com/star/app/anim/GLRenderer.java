package com.star.app.anim;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer {

    private static final float CLEAR_R = 0.058f;
    private static final float CLEAR_G = 0.058f;
    private static final float CLEAR_B = 0.058f;
    private static final float CLEAR_A = 1.0f;

    private static final String VERTEX_SHADER =
        "attribute vec2 aPos;\n"
        + "varying vec2 vUV;\n"
        + "void main() {\n"
        + "    vUV = aPos * 0.5 + 0.5;\n"
        + "    gl_Position = vec4(aPos, 0.0, 1.0);\n"
        + "}\n";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n"
        + "varying vec2 vUV;\n"
        + "void main() {\n"
        + "    vec2 d = vUV - 0.5;\n"
        + "    float r = length(d) * 2.0;\n"
        + "    float alpha = 1.0 - smoothstep(0.0, 1.0, r);\n"
        + "    gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);\n"
        + "}\n";

    private static final float[] QUAD = {
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f,
    };

    private FloatBuffer quadBuffer;
    private int program;
    private int aPosLoc;
    private boolean ready;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(CLEAR_R, CLEAR_G, CLEAR_B, CLEAR_A);

        ByteBuffer bb = ByteBuffer.allocateDirect(QUAD.length * 4);
        bb.order(ByteOrder.nativeOrder());
        quadBuffer = bb.asFloatBuffer();
        quadBuffer.put(QUAD);
        quadBuffer.position(0);

        int vs = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = link(vs, fs);
        aPosLoc = GLES30.glGetAttribLocation(program, "aPos");
        ready = program != 0 && aPosLoc >= 0;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        if (!ready) return;

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        GLES30.glUseProgram(program);
        GLES30.glEnableVertexAttribArray(aPosLoc);
        GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(aPosLoc);

        GLES30.glDisable(GLES30.GL_BLEND);
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
