package com.star.app.anim;

import android.graphics.Bitmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HistoryManager {

    public static final int MAX_STEPS = 20;

    public static class Snapshot {
        private final int tileX;
        private final int tileY;
        private final Bitmap bitmap;

        Snapshot(int tileX, int tileY, Bitmap bitmap) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.bitmap = bitmap;
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

        void recycle() {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private final Deque<List<Snapshot>> undoStack = new ArrayDeque<>();
    private final Deque<List<Snapshot>> redoStack = new ArrayDeque<>();

    private Map<String, Snapshot> pending;

    private static String key(int tileX, int tileY) {
        return tileX + "," + tileY;
    }

    private static Bitmap copyOf(Bitmap src) {
        if (src == null || src.isRecycled()) return null;
        Bitmap.Config config = src.getConfig();
        if (config == null) config = Bitmap.Config.ARGB_8888;
        return src.copy(config, false);
    }

    public void beginAction() {
        pending = new LinkedHashMap<>();
    }

    public void snapshot(Tile tile) {
        if (pending == null || tile == null) return;
        String k = key(tile.getTileX(), tile.getTileY());
        if (pending.containsKey(k)) return;
        Bitmap copy = copyOf(tile.getBitmap());
        if (copy != null) {
            pending.put(k, new Snapshot(tile.getTileX(), tile.getTileY(), copy));
        }
    }

    public boolean commitAction() {
        if (pending == null) return false;
        boolean recorded = !pending.isEmpty();
        if (recorded) {
            undoStack.push(new ArrayList<>(pending.values()));
            trimUndo();
            clearRedo();
        }
        pending = null;
        return recorded;
    }

    public void cancelAction() {
        if (pending == null) return;
        for (Snapshot s : pending.values()) {
            s.recycle();
        }
        pending = null;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undo(TileManager tiles) {
        if (undoStack.isEmpty()) return false;
        List<Snapshot> before = undoStack.pop();
        List<Snapshot> after = capture(tiles, before);
        tiles.applySnapshots(before);
        redoStack.push(after);
        recycle(before);
        return true;
    }

    public boolean redo(TileManager tiles) {
        if (redoStack.isEmpty()) return false;
        List<Snapshot> after = redoStack.pop();
        List<Snapshot> before = capture(tiles, after);
        tiles.applySnapshots(after);
        undoStack.push(before);
        trimUndo();
        recycle(after);
        return true;
    }

    public void clear() {
        recycle(undoStack);
        recycle(redoStack);
        undoStack.clear();
        redoStack.clear();
        if (pending != null) {
            for (Snapshot s : pending.values()) {
                s.recycle();
            }
            pending = null;
        }
    }

    private List<Snapshot> capture(TileManager tiles, List<Snapshot> source) {
        List<Snapshot> out = new ArrayList<>();
        for (Snapshot s : source) {
            Tile tile = tiles.getTile(s.getTileX(), s.getTileY());
            if (tile == null) continue;
            Bitmap copy = copyOf(tile.getBitmap());
            if (copy != null) {
                out.add(new Snapshot(s.getTileX(), s.getTileY(), copy));
            }
        }
        return out;
    }

    private void trimUndo() {
        while (undoStack.size() > MAX_STEPS) {
            recycle(undoStack.removeLast());
        }
    }

    private void clearRedo() {
        recycle(redoStack);
        redoStack.clear();
    }

    private static void recycle(Deque<List<Snapshot>> stack) {
        for (List<Snapshot> action : stack) {
            recycle(action);
        }
    }

    private static void recycle(List<Snapshot> action) {
        if (action == null) return;
        for (Snapshot s : action) {
            s.recycle();
        }
    }
}
