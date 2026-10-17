package com.star.app.anim;

import java.util.HashMap;
import java.util.Map;

public class FrameManager {
    public static final int DEFAULT_FRAMES = 120;

    private final Map<Integer, LayerManager> frames = new HashMap<>();

    public FrameManager() {
        this(DEFAULT_FRAMES);
    }

    public FrameManager(int totalFrames) {
        int count = Math.max(0, totalFrames);
        for (int i = 0; i < count; i++) {
            frames.put(i, new LayerManager());
        }
    }

    public LayerManager createFrame(int frame) {
        LayerManager manager = new LayerManager();
        frames.put(frame, manager);
        return manager;
    }

    public void deleteFrame(int frame) {
        frames.remove(frame);
    }

    public LayerManager getFrame(int frame) {
        return frames.get(frame);
    }

    public boolean hasFrame(int frame) {
        return frames.containsKey(frame);
    }

    public void clear() {
        frames.clear();
    }

    public int size() {
        return frames.size();
    }

    public Map<Integer, LayerManager> getFrames() {
        return frames;
    }
}
