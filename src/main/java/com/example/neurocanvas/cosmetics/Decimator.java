package com.example.neurocanvas.cosmetics;

import java.util.HashMap;
import java.util.Map;

/**
 * Reduces the triangle count by vertex clustering: nearby vertices are merged into one on a grid whose cell size is
 * searched until the part fits its budget. Texture coordinates stay with each original corner, so textures keep
 * their look. Every part is simplified on its own, so small parts like eyes and teeth do not disappear.
 */
public final class Decimator {
    private record Key(int x, int y, int z) { }

    private Decimator() { }

    public static Mesh simplify(Mesh mesh, int budget) {
        int total = mesh.triangles();
        if (total <= budget) {
            return mesh;
        }
        Mesh out = new Mesh();
        for (Mesh.Part p : mesh.parts) {
            int own = p.triangles();
            int target = (int) Math.max(Math.min(own, 24), Math.round((double) budget * own / total));
            out.parts.add(own <= target ? p : simplify(p, target));
        }
        return out;
    }

    private static Mesh.Part simplify(Mesh.Part part, int target) {
        int corners = part.pos.length / 3;
        int tris = corners / 3;

        // Weld corners that sit at the same position, so triangles that touch share vertices.
        Map<Key, Integer> ids = new HashMap<>();
        int[] weld = new int[corners];
        float[][] wpos = new float[corners][];
        int count = 0;
        for (int i = 0; i < corners; i++) {
            float x = part.pos[i * 3], y = part.pos[i * 3 + 1], z = part.pos[i * 3 + 2];
            Key k = new Key(Math.round(x * 20000f), Math.round(y * 20000f), Math.round(z * 20000f));
            Integer id = ids.get(k);
            if (id == null) {
                id = count++;
                wpos[id] = new float[] {x, y, z};
                ids.put(k, id);
            }
            weld[i] = id;
        }
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < count; i++) {
            for (int k = 0; k < 3; k++) {
                min[k] = Math.min(min[k], wpos[i][k]);
                max[k] = Math.max(max[k], wpos[i][k]);
            }
        }
        float span = Math.max(1e-6f, Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])));

        // Smallest cell size whose result still fits the budget.
        float lo = span / 4096f;
        float hi = span;
        for (int i = 0; i < 18; i++) {
            float mid = (lo + hi) / 2f;
            if (surviving(weld, wpos, count, tris, min, mid) > target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return rebuild(part, weld, wpos, count, tris, min, hi);
    }

    private static int[] cluster(float[][] wpos, int count, float[] min, float cell, int[] size) {
        Map<Long, Integer> map = new HashMap<>();
        int[] cl = new int[count];
        for (int i = 0; i < count; i++) {
            long cx = (long) ((wpos[i][0] - min[0]) / cell);
            long cy = (long) ((wpos[i][1] - min[1]) / cell);
            long cz = (long) ((wpos[i][2] - min[2]) / cell);
            long key = (cx & 0x1FFFFF) | ((cy & 0x1FFFFF) << 21) | ((cz & 0x1FFFFF) << 42);
            Integer id = map.get(key);
            if (id == null) {
                id = map.size();
                map.put(key, id);
            }
            cl[i] = id;
        }
        size[0] = map.size();
        return cl;
    }

    private static int surviving(int[] weld, float[][] wpos, int count, int tris, float[] min, float cell) {
        int[] size = new int[1];
        int[] cl = cluster(wpos, count, min, cell, size);
        int n = 0;
        for (int t = 0; t < tris; t++) {
            int a = cl[weld[t * 3]], b = cl[weld[t * 3 + 1]], c = cl[weld[t * 3 + 2]];
            if (a != b && b != c && a != c) {
                n++;
            }
        }
        return n;
    }

    private static Mesh.Part rebuild(Mesh.Part src, int[] weld, float[][] wpos, int count, int tris, float[] min, float cell) {
        int[] size = new int[1];
        int[] cl = cluster(wpos, count, min, cell, size);
        float[][] sum = new float[size[0]][4];
        for (int i = 0; i < count; i++) {
            float[] s = sum[cl[i]];
            s[0] += wpos[i][0];
            s[1] += wpos[i][1];
            s[2] += wpos[i][2];
            s[3]++;
        }
        int keep = 0;
        boolean[] ok = new boolean[tris];
        for (int t = 0; t < tris; t++) {
            int a = cl[weld[t * 3]], b = cl[weld[t * 3 + 1]], c = cl[weld[t * 3 + 2]];
            ok[t] = a != b && b != c && a != c;
            if (ok[t]) {
                keep++;
            }
        }
        Mesh.Part out = new Mesh.Part(src.name);
        out.color = src.color;
        out.texture = src.texture;
        out.pos = new float[keep * 9];
        out.uv = new float[keep * 6];
        int w = 0;
        for (int t = 0; t < tris; t++) {
            if (!ok[t]) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                int corner = t * 3 + k;
                float[] s = sum[cl[weld[corner]]];
                out.pos[w * 9 + k * 3] = s[0] / s[3];
                out.pos[w * 9 + k * 3 + 1] = s[1] / s[3];
                out.pos[w * 9 + k * 3 + 2] = s[2] / s[3];
                out.uv[w * 6 + k * 2] = src.uv.length > corner * 2 + 1 ? src.uv[corner * 2] : 0f;
                out.uv[w * 6 + k * 2 + 1] = src.uv.length > corner * 2 + 1 ? src.uv[corner * 2 + 1] : 0f;
            }
            w++;
        }
        return out;
    }
}
