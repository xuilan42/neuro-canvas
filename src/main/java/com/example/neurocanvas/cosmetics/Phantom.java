package com.example.neurocanvas.cosmetics;

import java.util.ArrayList;
import java.util.List;

/** The built-in original stand, put together from boxes. It has no texture, only part colours. */
public final class Phantom {
    private Phantom() { }

    private static final class Builder {
        final List<Float> pos = new ArrayList<>();

        void quad(float[] a, float[] b, float[] c, float[] d) {
            for (float[] v : new float[][] {a, b, c, a, c, d}) {
                pos.add(v[0]);
                pos.add(v[1]);
                pos.add(v[2]);
            }
        }

        void box(float cx, float cy, float cz, float sx, float sy, float sz) {
            float x0 = cx - sx / 2, x1 = cx + sx / 2, y0 = cy - sy / 2, y1 = cy + sy / 2, z0 = cz - sz / 2, z1 = cz + sz / 2;
            quad(new float[] {x0, y0, z1}, new float[] {x1, y0, z1}, new float[] {x1, y1, z1}, new float[] {x0, y1, z1});
            quad(new float[] {x1, y0, z0}, new float[] {x0, y0, z0}, new float[] {x0, y1, z0}, new float[] {x1, y1, z0});
            quad(new float[] {x0, y0, z0}, new float[] {x0, y0, z1}, new float[] {x0, y1, z1}, new float[] {x0, y1, z0});
            quad(new float[] {x1, y0, z1}, new float[] {x1, y0, z0}, new float[] {x1, y1, z0}, new float[] {x1, y1, z1});
            quad(new float[] {x0, y1, z1}, new float[] {x1, y1, z1}, new float[] {x1, y1, z0}, new float[] {x0, y1, z0});
            quad(new float[] {x0, y0, z0}, new float[] {x1, y0, z0}, new float[] {x1, y0, z1}, new float[] {x0, y0, z1});
        }

        Mesh.Part part(String name, int color) {
            Mesh.Part p = new Mesh.Part(name);
            p.color = color;
            p.pos = new float[pos.size()];
            for (int i = 0; i < p.pos.length; i++) {
                p.pos[i] = pos.get(i);
            }
            p.uv = new float[p.pos.length / 3 * 2];
            return p;
        }
    }

    /** Front of the figure is +Z. */
    public static Mesh create() {
        Builder body = new Builder();
        body.box(0, 0.62f, 0, 0.36f, 0.36f, 0.2f);
        body.box(0, 0.42f, 0, 0.3f, 0.12f, 0.17f);
        body.box(-0.09f, 0.2f, 0, 0.13f, 0.4f, 0.15f);
        body.box(0.09f, 0.2f, 0, 0.13f, 0.4f, 0.15f);
        body.box(-0.25f, 0.68f, 0, 0.1f, 0.3f, 0.11f);
        body.box(0.25f, 0.68f, 0, 0.1f, 0.3f, 0.11f);
        body.box(-0.25f, 0.55f, 0.12f, 0.1f, 0.1f, 0.28f);
        body.box(0.25f, 0.55f, 0.12f, 0.1f, 0.1f, 0.28f);
        body.box(0, 0.86f, 0, 0.21f, 0.21f, 0.21f);
        Builder accent = new Builder();
        accent.box(-0.28f, 0.8f, 0, 0.15f, 0.05f, 0.15f);
        accent.box(0.28f, 0.8f, 0, 0.15f, 0.05f, 0.15f);
        accent.box(0, 0.65f, 0.105f, 0.12f, 0.12f, 0.012f);
        accent.box(0, 0.46f, 0.09f, 0.32f, 0.03f, 0.012f);
        accent.box(-0.25f, 0.55f, 0.27f, 0.12f, 0.12f, 0.04f);
        accent.box(0.25f, 0.55f, 0.27f, 0.12f, 0.12f, 0.04f);
        accent.box(0, 0.97f, 0, 0.05f, 0.1f, 0.18f);
        Builder eyes = new Builder();
        eyes.box(-0.05f, 0.88f, 0.108f, 0.06f, 0.03f, 0.012f);
        eyes.box(0.05f, 0.88f, 0.108f, 0.06f, 0.03f, 0.012f);

        Mesh mesh = new Mesh();
        mesh.parts.add(body.part("body", 0xFF6B3FD6));
        mesh.parts.add(accent.part("accent", 0xFF35E0E0));
        mesh.parts.add(eyes.part("eyes", 0xFFFFFFFF));
        mesh.normalize(1f);
        mesh.computeShading();
        return mesh;
    }
}
