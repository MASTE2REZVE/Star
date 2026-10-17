package com.star.app.anim;

import android.graphics.Bitmap;
import android.graphics.Color;

public class Tile {

    public static final int SIZE = 256;

    private final int tileX;
    private final int tileY;
    private final Bitmap bitmap;

    public Tile(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        this.bitmap.eraseColor(Color.WHITE);
    }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public boolean contains(float x, float y) {
        float left = tileX * (float) SIZE;
        float top = tileY * (float) SIZE;
        return x >= left && x < left + SIZE && y >= top && y < top + SIZE;
    }

    public void recycle() {
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
