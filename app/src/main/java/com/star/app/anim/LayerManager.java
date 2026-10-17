package com.star.app.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LayerManager {
    public static class Layer {
        private final String id;
        private String name;
        private boolean visible = true;
        private boolean locked = false;
        private volatile int opacity = 100;
        private final TileManager tiles;

        public Layer(String name) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.tiles = new TileManager(); // Assuming TileManager has a no-arg constructor
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isVisible() { return visible; }
        public boolean isLocked() { return locked; }
        public int getOpacity() { return opacity; }
        public void setOpacity(int opacity) { this.opacity = opacity; }
        public TileManager getTiles() { return tiles; }
    }

    private final List<Layer> layers = new ArrayList<>();
    private int activeIndex = 0;

    public LayerManager() {}

    public void clear() {
        layers.clear();
        activeIndex = 0;
    }

    public Layer addLayer(String name) {
        Layer layer = new Layer(name);
        layers.add(layer);
        activeIndex = layers.size() - 1;
        return layer;
    }

    public void addLayer() {
        addLayer("Layer " + (layers.size() + 1));
    }

    public void removeLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            layers.remove(index);
            if (activeIndex >= layers.size()) {
                activeIndex = Math.max(0, layers.size() - 1);
            }
        }
    }

    public void duplicateLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            Layer original = layers.get(index);
            Layer copy = new Layer(original.getName() + " copy");
            copy.setOpacity(original.getOpacity());
            layers.add(index + 1, copy);
            activeIndex = index + 1;
        }
    }

    public Layer getActiveLayer() {
        if (layers.isEmpty()) return null;
        return layers.get(activeIndex);
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public int size() {
        return layers.size();
    }

    public void setActiveLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            activeIndex = index;
        }
    }
}
