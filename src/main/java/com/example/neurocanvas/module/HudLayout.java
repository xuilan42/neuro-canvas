package com.example.neurocanvas.module;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Positions of movable HUD elements, stored as fractions of the screen so they survive resolution
 * and GUI-scale changes. Elements measure themselves each frame (w, h) so the editor can draw a box.
 */
public final class HudLayout {
    private static final Map<String, Element> ELEMENTS = new LinkedHashMap<>();

    public static final class Element {
        public final String id;
        public final String label;
        /** When true, fx is the right edge of the element instead of the left. */
        public final boolean right;
        public final double defFx;
        public final double defFy;
        public double fx;
        public double fy;
        public int w = 60;
        public int h = 14;

        private Element(String id, String label, boolean right, double fx, double fy) {
            this.id = id;
            this.label = label;
            this.right = right;
            this.defFx = fx;
            this.defFy = fy;
            this.fx = fx;
            this.fy = fy;
        }

        public int x(int screenW) {
            int px = (int) Math.round(fx * screenW) - (right ? w : 0);
            return Math.max(0, Math.min(Math.max(0, screenW - w), px));
        }

        public int y(int screenH) {
            return Math.max(0, Math.min(Math.max(0, screenH - h), (int) Math.round(fy * screenH)));
        }

        public void setFromTopLeft(int x, int y, int screenW, int screenH) {
            fx = Math.max(0, Math.min(1, (x + (right ? w : 0)) / (double) screenW));
            fy = Math.max(0, Math.min(1, y / (double) screenH));
        }
    }

    private HudLayout() { }

    public static Element register(String id, String label, boolean right, double fx, double fy) {
        return ELEMENTS.computeIfAbsent(id, k -> new Element(id, label, right, fx, fy));
    }

    public static Collection<Element> all() {
        return ELEMENTS.values();
    }

    public static void reset() {
        for (Element e : ELEMENTS.values()) {
            e.fx = e.defFx;
            e.fy = e.defFy;
        }
    }

    public static Map<String, Double> snapshot() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Element e : ELEMENTS.values()) {
            out.put(e.id + ".x", e.fx);
            out.put(e.id + ".y", e.fy);
        }
        return out;
    }

    public static void apply(Map<String, Double> values) {
        for (Element e : ELEMENTS.values()) {
            Double x = values.get(e.id + ".x");
            Double y = values.get(e.id + ".y");
            if (x != null) e.fx = Math.max(0, Math.min(1, x));
            if (y != null) e.fy = Math.max(0, Math.min(1, y));
        }
    }
}
