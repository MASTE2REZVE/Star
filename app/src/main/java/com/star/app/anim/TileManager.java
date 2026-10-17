package com.star.app.anim;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TileManager {

    public static final int TILE_SIZE = Tile.SIZE;

    public static final float BRUSH_RADIUS = 12.0f;

    private final HashMap<String, Tile> tiles = new HashMap<>();

    private final Set<Tile> dirty = Collections.newSetFromMap(new IdentityHashMap<Tile, Boolean>());

    private final Canvas brushCanvas = new Canvas();

    private final Paint brushFill;
    private final Paint brushStroke;

    private float lastX;
    private float lastY;
    private boolean haveLast;

    public TileManager() {
        brushFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        brushFill.setColor(Color.BLACK);
        brushFill.setStyle(Paint.Style.FILL);

        brushStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        brushStroke.setColor(Color.BLACK);
        brushStroke.setStyle(Paint.Style.STROKE);
        brushStroke.setStrokeWidth(BRUSH_RADIUS * 2.0f);
        brushStroke.setStrokeCap(Paint.Cap.ROUND);
        brushStroke.setStrokeJoin(Paint.Join.ROUND);
    }

    private static String key(int tileX, int tileY) {
        return tileX + "," + tileY;
    }

    private static int toTileX(float x) {
        return (int) Math.floor(x / TILE_SIZE);
    }

    private static int toTileY(float y) {
        return (int) Math.floor(y / TILE_SIZE);
    }

    public synchronized Tile getOrCreate(int tileX, int tileY) {
        String k = key(tileX, tileY);
        Tile tile = tiles.get(k);
        if (tile == null) {
            tile = new Tile(tileX, tileY);
            tiles.put(k, tile);
        }
        return tile;
    }

    public synchronized Tile getTile(int tileX, int tileY) {
        return tiles.get(key(tileX, tileY));
    }

    public synchronized int size() {
        return tiles.size();
    }

    public synchronized Map<String, Tile> getTiles() {
        return new HashMap<>(tiles);
    }

    public synchronized void onStroke(float x, float y) {
        getOrCreate(toTileX(x), toTileY(y));
    }

    public List<Tile> onStroke(float x0, float y0, float x1, float y1) {
        List<Tile> touched = new ArrayList<>();
        int minX = toTileX(Math.min(x0, x1));
        int maxX = toTileX(Math.max(x0, x1));
        int minY = toTileY(Math.min(y0, y1));
        int maxY = toTileY(Math.max(y0, y1));
        for (int ty = minY; ty <= maxY; ty++) {
            for (int tx = minX; tx <= maxX; tx++) {
                touched.add(getOrCreate(tx, ty));
            }
        }
        return touched;
    }

    public synchronized List<Tile> beginStroke(float x, float y) {
        haveLast = false;
        return strokeTo(x, y);
    }

    public synchronized List<Tile> strokeTo(float x, float y) {
        List<Tile> touched;
        if (haveLast) {
            touched = onStroke(lastX, lastY, x, y);
            for (Tile tile : touched) {
                drawSegment(tile, lastX, lastY, x, y);
            }
        } else {
            Tile tile = getOrCreate(toTileX(x), toTileY(y));
            drawDot(tile, x, y);
            touched = new ArrayList<>();
            touched.add(tile);
        }
        lastX = x;
        lastY = y;
        haveLast = true;
        return touched;
    }

    public synchronized void endStroke() {
        haveLast = false;
    }

    private void drawDot(Tile tile, float x, float y) {
        Bitmap bitmap = tile.getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;
        float ox = tile.getTileX() * (float) TILE_SIZE;
        float oy = tile.getTileY() * (float) TILE_SIZE;
        brushCanvas.setBitmap(bitmap);
        brushCanvas.drawCircle(x - ox, y - oy, BRUSH_RADIUS, brushFill);
        brushCanvas.setBitmap(null);
        dirty.add(tile);
    }

    private void drawSegment(Tile tile, float x0, float y0, float x1, float y1) {
        Bitmap bitmap = tile.getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;
        float ox = tile.getTileX() * (float) TILE_SIZE;
        float oy = tile.getTileY() * (float) TILE_SIZE;
        brushCanvas.setBitmap(bitmap);
        brushCanvas.drawLine(x0 - ox, y0 - oy, x1 - ox, y1 - oy, brushStroke);
        brushCanvas.setBitmap(null);
        dirty.add(tile);
    }

    public synchronized List<Tile> consumeDirty() {
        if (dirty.isEmpty()) return new ArrayList<>();
        List<Tile> out = new ArrayList<>(dirty);
        dirty.clear();
        return out;
    }

    public synchronized void clear() {
        for (Tile tile : tiles.values()) {
            tile.recycle();
        }
        tiles.clear();
        dirty.clear();
        haveLast = false;
    }
}
