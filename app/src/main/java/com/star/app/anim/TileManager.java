package com.star.app.anim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileManager {

    public static final int TILE_SIZE = Tile.SIZE;

    private final HashMap<String, Tile> tiles = new HashMap<>();

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

    public synchronized void clear() {
        for (Tile tile : tiles.values()) {
            tile.recycle();
        }
        tiles.clear();
    }
}
