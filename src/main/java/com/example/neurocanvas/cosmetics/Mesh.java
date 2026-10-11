package com.example.neurocanvas.cosmetics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Identifier;

/** A triangle soup split into named parts. Every triangle has its own three corners (no shared vertices). */
public final class Mesh {
    public static final class Part {
        public final String name;
        /** x, y, z per corner. */
        public float[] pos = new float[0];
        /** u, v per corner. */
        public float[] uv = new float[0];
        /** Brightness per corner, from the face direction. */
        public float[] shade = new float[0];
        /** Used when the part has no texture. */
        public int color = 0xFFFFFFFF;
        /** Name of the texture file that covers this part, or null. */
        public String texture;
        public Identifier texId;

        public Part(String name) {
            this.name = name;
        }

        public int triangles() {
            return pos.length / 9;
        }
    }

    public final List<Part> parts = new ArrayList<>();

    public int triangles() {
        int n = 0;
        for (Part p : parts) {
            n += p.triangles();
        }
        return n;
    }

    /** Moves the mesh so it stands on y = 0, is centred in x and z, and is exactly {@code height} tall. */
    public void normalize(float height) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Part p : parts) {
            for (int i = 0; i < p.pos.length; i += 3) {
                for (int k = 0; k < 3; k++) {
                    min[k] = Math.min(min[k], p.pos[i + k]);
                    max[k] = Math.max(max[k], p.pos[i + k]);
                }
            }
        }
        float h = max[1] - min[1];
        if (h < 1e-6f) {
            h = Math.max(max[0] - min[0], max[2] - min[2]);
        }
        float scale = h < 1e-6f ? 1f : height / h;
        float cx = (min[0] + max[0]) / 2f;
        float cz = (min[2] + max[2]) / 2f;
        for (Part p : parts) {
            for (int i = 0; i < p.pos.length; i += 3) {
                p.pos[i] = (p.pos[i] - cx) * scale;
                p.pos[i + 1] = (p.pos[i + 1] - min[1]) * scale;
                p.pos[i + 2] = (p.pos[i + 2] - cz) * scale;
            }
        }
    }

    /** Computes the per-corner brightness from the direction each triangle faces. */
    public void computeShading() {
        float lx = 0.35f, ly = 0.8f, lz = 0.5f;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll;
        ly /= ll;
        lz /= ll;
        for (Part p : parts) {
            p.shade = new float[p.pos.length / 3];
            for (int t = 0; t < p.triangles(); t++) {
                int o = t * 9;
                float ax = p.pos[o + 3] - p.pos[o], ay = p.pos[o + 4] - p.pos[o + 1], az = p.pos[o + 5] - p.pos[o + 2];
                float bx = p.pos[o + 6] - p.pos[o], by = p.pos[o + 7] - p.pos[o + 1], bz = p.pos[o + 8] - p.pos[o + 2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                float s = 0.75f;
                if (len > 1e-9f) {
                    float d = (nx * lx + ny * ly + nz * lz) / len;
                    s = Math.max(0.35f, Math.min(1f, 0.62f + 0.38f * d));
                }
                p.shade[t * 3] = s;
                p.shade[t * 3 + 1] = s;
                p.shade[t * 3 + 2] = s;
            }
        }
    }
}
