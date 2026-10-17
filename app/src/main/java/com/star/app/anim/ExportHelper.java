package com.star.app.anim;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.opengl.GLES30;
import android.util.Log;

import com.star.app.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ExportHelper {

    private static final String TAG = "StarExport";
    public static final String EXPORT_DIR = "/sdcard/Star/exports";

    public interface Listener {
        void onResult(String path, String error);
    }

    private ExportHelper() {}

    /** Read the current OpenGL framebuffer into an upright bitmap. Must run on the GL thread. */
    public static Bitmap readFramebuffer(int width, int height) {
        if (width <= 0 || height <= 0) return null;
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        pixels.order(ByteOrder.nativeOrder());
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE, pixels);
        pixels.rewind();

        Bitmap raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        raw.copyPixelsFromBuffer(pixels);

        Matrix flip = new Matrix();
        flip.postScale(1f, -1f, width / 2f, height / 2f);
        Bitmap upright = Bitmap.createBitmap(raw, 0, 0, width, height, flip, true);
        if (upright != raw) raw.recycle();
        return upright;
    }

    /** Encode the bitmap as PNG at EXPORT_DIR/frame_<num>.png, falling back to app storage. */
    public static String savePng(Context ctx, Bitmap bitmap, int frameNumber) throws Exception {
        String name = "frame_" + frameNumber + ".png";
        File target = new File(EXPORT_DIR, name);
        File dir = target.getParentFile();
        try {
            if (dir != null && !dir.exists()) dir.mkdirs();
            writePng(bitmap, target);
            return target.getAbsolutePath();
        } catch (Exception direct) {
            Log.w(TAG, "direct export failed: " + direct.getMessage());
        }

        File base = ctx.getExternalFilesDir(null);
        if (base == null) base = ctx.getFilesDir();
        File fallbackDir = new File(base, "exports");
        if (!fallbackDir.exists()) fallbackDir.mkdirs();
        File fallback = new File(fallbackDir, name);
        writePng(bitmap, fallback);

        Shell.run("mkdir -p " + EXPORT_DIR + " && cp -f '" + fallback.getAbsolutePath()
            + "' '" + target.getAbsolutePath() + "'");
        return target.exists() ? target.getAbsolutePath() : fallback.getAbsolutePath();
    }

    private static void writePng(Bitmap bitmap, File file) throws Exception {
        OutputStream out = new FileOutputStream(file);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new Exception("PNG encode failed");
            }
        } finally {
            out.close();
        }
    }
}
