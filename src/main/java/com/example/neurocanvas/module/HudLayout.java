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
        /** Horizontal anchor: 0 = fx is the left edge, 1 = the centre, 2 = the right edge. */
        public int ax;
        /** Vertical anchor: 0 = fy is the top edge, 1 = the middle, 2 = the bottom edge. */
        public int ay;
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
            this.ax = right ? 2 : 0;
            this.ay = 0;
        }

        public int x(int screenW) {
            int px = (int) Math.round(fx * screenW) - (ax == 1 ? w / 2 : ax == 2 ? w : 0);
            return Math.max(0, Math.min(Math.max(0, screenW - w), px));
        }

        public int y(int screenH) {
            int py = (int) Math.round(fy * screenH) - (ay == 1 ? h / 2 : ay == 2 ? h : 0);
            return Math.max(0, Math.min(Math.max(0, screenH - h), py));
        }

        /**
         * Places the element from its dragged top-left corner. The side of the screen it ends up in (left third, middle,
         * right third) becomes its anchor, so it keeps hugging that side or the centre when the window changes size.
         * Close to an edge or to the centre it snaps there.
         */
        public void setFromTopLeft(int x, int y, int screenW, int screenH) {
            final int snap = 6;
            final int margin = 4;
            double cx = x + w / 2.0;
            ax = cx < screenW / 3.0 ? 0 : cx > screenW * 2 / 3.0 ? 2 : 1;
            if (ax == 0) {
                x = x < snap + margin ? margin : x;
                fx = x / (double) screenW;
            } else if (ax == 2) {
                int right = x + w;
                right = right > screenW - snap - margin ? screenW - margin : right;
                fx = right / (double) screenW;
            } else {
                fx = Math.abs(cx - screenW / 2.0) < snap ? 0.5 : cx / screenW;
            }
            double cy = y + h / 2.0;
            ay = cy < screenH / 3.0 ? 0 : cy > screenH * 2 / 3.0 ? 2 : 1;
            if (ay == 0) {
                y = y < snap + margin ? margin : y;
                fy = y / (double) screenH;
            } else if (ay == 2) {
                int bottom = y + h;
                bottom = bottom > screenH - snap - margin ? screenH - margin : bottom;
                fy = bottom / (double) screenH;
            } else {
                fy = Math.abs(cy - screenH / 2.0) < snap ? 0.5 : cy / screenH;
            }
            fx = Math.max(0, Math.min(1, fx));
            fy = Math.max(0, Math.min(1, fy));
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
            e.ax = e.right ? 2 : 0;
            e.ay = 0;
        }
    }

    public static Map<String, Double> snapshot() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Element e : ELEMENTS.values()) {
            out.put(e.id + ".x", e.fx);
            out.put(e.id + ".y", e.fy);
            out.put(e.id + ".ax", (double) e.ax);
            out.put(e.id + ".ay", (double) e.ay);
        }
        return out;
    }

    public static void apply(Map<String, Double> values) {
        for (Element e : ELEMENTS.values()) {
            Double x = values.get(e.id + ".x");
            Double y = values.get(e.id + ".y");
            if (x != null) e.fx = Math.max(0, Math.min(1, x));
            if (y != null) e.fy = Math.max(0, Math.min(1, y));
            Double ax = values.get(e.id + ".ax");
            Double ay = values.get(e.id + ".ay");
            if (ax != null) e.ax = (int) Math.max(0, Math.min(2, Math.round(ax)));
            if (ay != null) e.ay = (int) Math.max(0, Math.min(2, Math.round(ay)));
        }
    }
}
