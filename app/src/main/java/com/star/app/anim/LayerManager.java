package com.star.app.anim;

import java.util.ArrayList;
import java.util.List;

public class LayerManager {

    public static class Layer {

        private final int id;
        private final TileManager tiles = new TileManager();

        private String name;
        private boolean visible = true;
        private boolean locked = false;
        private int opacity = 100;

        Layer(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public TileManager getTiles() {
            return tiles;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public boolean isLocked() {
            return locked;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        public int getOpacity() {
            return opacity;
        }

        public void setOpacity(int opacity) {
            if (opacity < 0) opacity = 0;
            if (opacity > 100) opacity = 100;
            this.opacity = opacity;
        }
    }

    private final List<Layer> layers = new ArrayList<>();
    private int activeIndex = -1;
    private int nextId = 1;

    public synchronized Layer addLayer() {
        return addLayer(null);
    }

    public synchronized Layer addLayer(String name) {
        int id = nextId++;
        if (name == null || name.trim().isEmpty()) {
            name = "Layer " + id;
        }
        Layer layer = new Layer(id, name);
        layers.add(layer);
        if (activeIndex < 0) {
            activeIndex = 0;
        }
        return layer;
    }

    public synchronized Layer removeLayer(int index) {
        if (index < 0 || index >= layers.size()) return null;
        Layer removed = layers.remove(index);
        removed.getTiles().clear();
        if (layers.isEmpty()) {
            activeIndex = -1;
        } else if (activeIndex > index) {
            activeIndex--;
        } else if (activeIndex == index) {
            activeIndex = Math.min(index, layers.size() - 1);
        }
        return removed;
    }

    public synchronized Layer setActiveLayer(int index) {
        if (index < 0 || index >= layers.size()) return null;
        activeIndex = index;
        return layers.get(index);
    }

    public synchronized Layer getActiveLayer() {
        if (activeIndex < 0 || activeIndex >= layers.size()) return null;
        return layers.get(activeIndex);
    }

    public synchronized int getActiveIndex() {
        return activeIndex;
    }

    public synchronized Layer getLayer(int index) {
        if (index < 0 || index >= layers.size()) return null;
        return layers.get(index);
    }

    public synchronized int size() {
        return layers.size();
    }

    public synchronized List<Layer> getLayers() {
        return new ArrayList<>(layers);
    }

    public synchronized void clear() {
        for (Layer layer : layers) {
            layer.getTiles().clear();
        }
        layers.clear();
        activeIndex = -1;
    }
}
