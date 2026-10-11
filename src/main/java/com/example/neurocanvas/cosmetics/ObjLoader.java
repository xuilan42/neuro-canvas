package com.example.neurocanvas.cosmetics;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads Wavefront .obj files: positions, texture coordinates, polygon faces and named objects/groups. */
public final class ObjLoader {
    private static final int MAX_FILE_BYTES = 80 * 1024 * 1024;

    private static final class Floats {
        float[] data = new float[1024];
        int size;

        void add(float v) {
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = v;
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private static final class Builder {
        final Floats pos = new Floats();
        final Floats uv = new Floats();
    }

    private ObjLoader() { }

    public static Mesh load(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("The model file is larger than 80 MB");
        }
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        Map<String, Builder> parts = new LinkedHashMap<>();
        Builder current = parts.computeIfAbsent("model", k -> new Builder());

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "v" -> positions.add(new float[] {Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                    case "vt" -> uvs.add(new float[] {Float.parseFloat(t[1]), t.length > 2 ? Float.parseFloat(t[2]) : 0f});
                    case "o", "g" -> current = parts.computeIfAbsent(t.length > 1 ? line.substring(2).strip() : "part", k -> new Builder());
                    case "f" -> face(t, positions, uvs, current);
                    default -> { }
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IOException("The model file is not a valid OBJ: " + e.getMessage());
        }

        Mesh mesh = new Mesh();
        for (Map.Entry<String, Builder> e : parts.entrySet()) {
            if (e.getValue().pos.size == 0) {
                continue;
            }
            Mesh.Part p = new Mesh.Part(e.getKey());
            p.pos = e.getValue().pos.toArray();
            p.uv = e.getValue().uv.toArray();
            mesh.parts.add(p);
        }
        if (mesh.parts.isEmpty()) {
            throw new IOException("The OBJ has no faces");
        }
        mesh.normalize(1f);
        return mesh;
    }

    /** Splits a polygon into triangles (a fan from its first corner). */
    private static void face(String[] t, List<float[]> positions, List<float[]> uvs, Builder b) {
        int n = t.length - 1;
        int[] pi = new int[n];
        int[] ti = new int[n];
        for (int i = 0; i < n; i++) {
            String[] c = t[i + 1].split("/");
            pi[i] = resolve(Integer.parseInt(c[0]), positions.size());
            ti[i] = c.length > 1 && !c[1].isEmpty() ? resolve(Integer.parseInt(c[1]), uvs.size()) : -1;
        }
        for (int i = 1; i + 1 < n; i++) {
            corner(b, positions, uvs, pi[0], ti[0]);
            corner(b, positions, uvs, pi[i], ti[i]);
            corner(b, positions, uvs, pi[i + 1], ti[i + 1]);
        }
    }

    private static int resolve(int index, int count) {
        return index > 0 ? index - 1 : count + index;
    }

    private static void corner(Builder b, List<float[]> positions, List<float[]> uvs, int p, int t) {
        float[] v = positions.get(p);
        b.pos.add(v[0]);
        b.pos.add(v[1]);
        b.pos.add(v[2]);
        if (t >= 0 && t < uvs.size()) {
            b.uv.add(uvs.get(t)[0]);
            b.uv.add(1f - uvs.get(t)[1]);
        } else {
            b.uv.add(0f);
            b.uv.add(0f);
        }
    }
}
