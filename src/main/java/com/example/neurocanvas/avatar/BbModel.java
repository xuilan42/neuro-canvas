package com.example.neurocanvas.avatar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Blockbench model (.bbmodel): cubes grouped into bones, textures and keyframe animations.
 * This class only reads the file; it has no dependency on the game, so it can be tested on its own.
 */
public final class BbModel {
    public static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    public static final class Face {
        /** Normalised texture coordinates u1, v1, u2, v2 (0..1). */
        public float[] uv;
        public int texture;
    }

    public static final class Cube {
        public String uuid;
        public String name = "";
        public double[] from = new double[3];
        public double[] to = new double[3];
        public double[] origin = new double[3];
        public double[] rotation = new double[3];
        public double inflate;
        public final Face[] faces = new Face[6];
    }

    public static final class Bone {
        public String uuid;
        public String name = "";
        public double[] origin = new double[3];
        public double[] rotation = new double[3];
        public final List<Bone> children = new ArrayList<>();
        public final List<Cube> cubes = new ArrayList<>();
        public Bone parent;
    }

    public static final class Key {
        public double time;
        public double[] value = new double[3];
        public boolean step;
    }

    public static final class Channels {
        public final List<Key> position = new ArrayList<>();
        public final List<Key> rotation = new ArrayList<>();
        public final List<Key> scale = new ArrayList<>();
    }

    public static final class Anim {
        public String name;
        public double length = 1;
        /** 0 loop, 1 once, 2 hold the last frame. */
        public int loop;
        /** Keyed by the uuid of the bone (or cube) that is animated. */
        public final Map<String, Channels> animators = new LinkedHashMap<>();
    }

    public int resolutionW = 16;
    public int resolutionH = 16;
    public final List<byte[]> textures = new ArrayList<>();
    /** Per texture: "minecraft:steve" or "minecraft:player" when the texture is the game's own skin instead of an embedded picture, else null. */
    public final List<String> textureRefs = new ArrayList<>();
    public final List<int[]> textureUvSize = new ArrayList<>();
    public final List<Bone> roots = new ArrayList<>();
    public final Map<String, Bone> bonesByUuid = new LinkedHashMap<>();
    public final Map<String, Anim> animations = new LinkedHashMap<>();
    public int cubeCount;

    public static BbModel parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        BbModel m = new BbModel();
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject r = root.getAsJsonObject("resolution");
            m.resolutionW = r.has("width") ? r.get("width").getAsInt() : 16;
            m.resolutionH = r.has("height") ? r.get("height").getAsInt() : 16;
        }
        if (root.has("textures")) {
            for (JsonElement e : root.getAsJsonArray("textures")) {
                JsonObject t = e.getAsJsonObject();
                String src = t.has("source") ? t.get("source").getAsString() : "";
                int comma = src.indexOf(',');
                byte[] data = new byte[0];
                if (src.startsWith("data:") && comma > 0) {
                    data = Base64.getDecoder().decode(src.substring(comma + 1).trim());
                }
                m.textures.add(data);
                m.textureRefs.add(src.equals("minecraft:steve") || src.equals("minecraft:player") ? src : null);
                int uw = t.has("uv_width") ? t.get("uv_width").getAsInt() : m.resolutionW;
                int uh = t.has("uv_height") ? t.get("uv_height").getAsInt() : m.resolutionH;
                m.textureUvSize.add(new int[] {Math.max(1, uw), Math.max(1, uh)});
            }
        }
        Map<String, Cube> cubes = new LinkedHashMap<>();
        if (root.has("elements")) {
            for (JsonElement e : root.getAsJsonArray("elements")) {
                JsonObject o = e.getAsJsonObject();
                String type = o.has("type") ? o.get("type").getAsString() : "cube";
                if (!type.equals("cube")) {
                    continue;
                }
                Cube c = m.readCube(o);
                cubes.put(c.uuid, c);
                m.cubeCount++;
            }
        }
        if (root.has("outliner")) {
            for (JsonElement e : root.getAsJsonArray("outliner")) {
                if (e.isJsonObject()) {
                    m.roots.add(m.readBone(e.getAsJsonObject(), null, cubes));
                } else if (e.isJsonPrimitive() && cubes.containsKey(e.getAsString())) {
                    // A cube outside every group goes into a bone of its own, so it still draws.
                    Bone b = new Bone();
                    b.uuid = "root-" + m.roots.size();
                    b.name = "root";
                    b.cubes.add(cubes.get(e.getAsString()));
                    m.bonesByUuid.put(b.uuid, b);
                    m.roots.add(b);
                }
            }
        }
        if (root.has("animations")) {
            for (JsonElement e : root.getAsJsonArray("animations")) {
                Anim a = readAnim(e.getAsJsonObject());
                m.animations.put(a.name, a);
            }
        }
        return m;
    }

    private static double[] vec(JsonObject o, String key, double def) {
        double[] v = {def, def, def};
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) {
                v[i] = number(a.get(i));
            }
        }
        return v;
    }

    /** Numbers or strings that hold a plain number; expressions (Molang) count as 0. */
    private static double number(JsonElement e) {
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return 0;
        }
        try {
            return e.getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException ex) {
            return 0;
        }
    }

    private Cube readCube(JsonObject o) {
        Cube c = new Cube();
        c.uuid = o.has("uuid") ? o.get("uuid").getAsString() : "cube-" + cubeCount;
        c.name = o.has("name") ? o.get("name").getAsString() : "";
        c.from = vec(o, "from", 0);
        c.to = vec(o, "to", 0);
        c.origin = vec(o, "origin", 0);
        c.rotation = vec(o, "rotation", 0);
        c.inflate = o.has("inflate") ? number(o.get("inflate")) : 0;
        boolean boxUv = o.has("box_uv") && o.get("box_uv").getAsBoolean();
        double[] uvOffset = {0, 0, 0};
        if (o.has("uv_offset") && o.get("uv_offset").isJsonArray()) {
            JsonArray a = o.getAsJsonArray("uv_offset");
            uvOffset[0] = number(a.get(0));
            uvOffset[1] = number(a.get(1));
        }
        boolean mirrorUv = boxUv && o.has("mirror_uv") && o.get("mirror_uv").getAsBoolean();
        JsonObject faces = o.has("faces") && o.get("faces").isJsonObject() ? o.getAsJsonObject("faces") : new JsonObject();
        double w = Math.abs(c.to[0] - c.from[0]);
        double h = Math.abs(c.to[1] - c.from[1]);
        double d = Math.abs(c.to[2] - c.from[2]);
        for (int i = 0; i < 6; i++) {
            JsonObject f = faces.has(FACES[i]) && faces.get(FACES[i]).isJsonObject() ? faces.getAsJsonObject(FACES[i]) : null;
            if (f == null || !f.has("texture") || f.get("texture").isJsonNull()) {
                continue;
            }
            Face face = new Face();
            face.texture = f.get("texture").getAsInt();
            double[] uv;
            if (boxUv) {
                uv = boxUvFor(i, uvOffset[0], uvOffset[1], w, h, d);
            } else if (f.has("uv") && f.get("uv").isJsonArray() && f.getAsJsonArray("uv").size() >= 4) {
                JsonArray a = f.getAsJsonArray("uv");
                uv = new double[] {number(a.get(0)), number(a.get(1)), number(a.get(2)), number(a.get(3))};
            } else {
                continue;
            }
            int[] size = face.texture >= 0 && face.texture < textureUvSize.size() ? textureUvSize.get(face.texture) : new int[] {resolutionW, resolutionH};
            face.uv = new float[] {(float) (uv[0] / size[0]), (float) (uv[1] / size[1]), (float) (uv[2] / size[0]), (float) (uv[3] / size[1])};
            c.faces[i] = face;
        }
        if (mirrorUv) {
            // Mirrored box UV: every face is flipped left to right and the east and west faces swap places.
            for (Face f : c.faces) {
                if (f != null && f.uv != null) {
                    float t = f.uv[0];
                    f.uv[0] = f.uv[2];
                    f.uv[2] = t;
                }
            }
            Face east = c.faces[1];
            c.faces[1] = c.faces[3];
            c.faces[3] = east;
        }
        return c;
    }

    /** The standard Minecraft box layout, in texture pixels: {u1, v1, u2, v2} for face i (north, east, south, west, up, down). */
    private static double[] boxUvFor(int face, double u, double v, double w, double h, double d) {
        return switch (face) {
            case 0 -> new double[] {u + d, v + d, u + d + w, v + d + h};
            case 1 -> new double[] {u + d + w, v + d, u + d + w + d, v + d + h};
            case 2 -> new double[] {u + d + w + d, v + d, u + d + w + d + w, v + d + h};
            case 3 -> new double[] {u, v + d, u + d, v + d + h};
            case 4 -> new double[] {u + d, v, u + d + w, v + d};
            default -> new double[] {u + d + w, v + d, u + d + w + w, v};
        };
    }

    private Bone readBone(JsonObject o, Bone parent, Map<String, Cube> cubes) {
        Bone b = new Bone();
        b.uuid = o.has("uuid") ? o.get("uuid").getAsString() : "bone-" + bonesByUuid.size();
        b.name = o.has("name") ? o.get("name").getAsString() : "";
        b.origin = vec(o, "origin", 0);
        b.rotation = vec(o, "rotation", 0);
        b.parent = parent;
        bonesByUuid.put(b.uuid, b);
        if (o.has("children")) {
            for (JsonElement e : o.getAsJsonArray("children")) {
                if (e.isJsonObject()) {
                    b.children.add(readBone(e.getAsJsonObject(), b, cubes));
                } else if (cubes.containsKey(e.getAsString())) {
                    b.cubes.add(cubes.get(e.getAsString()));
                }
            }
        }
        return b;
    }

    private static Anim readAnim(JsonObject o) {
        Anim a = new Anim();
        a.name = o.has("name") ? o.get("name").getAsString() : "anim";
        a.length = o.has("length") ? Math.max(0.05, number(o.get("length"))) : 1;
        String loop = o.has("loop") ? o.get("loop").getAsString() : "once";
        a.loop = loop.equals("loop") ? 0 : loop.equals("hold") ? 2 : 1;
        if (o.has("animators") && o.get("animators").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("animators").entrySet()) {
                JsonObject animator = en.getValue().getAsJsonObject();
                Channels ch = new Channels();
                if (animator.has("keyframes")) {
                    for (JsonElement ke : animator.getAsJsonArray("keyframes")) {
                        JsonObject k = ke.getAsJsonObject();
                        String channel = k.has("channel") ? k.get("channel").getAsString() : "";
                        if (!k.has("data_points") || k.getAsJsonArray("data_points").isEmpty()) {
                            continue;
                        }
                        JsonObject p = k.getAsJsonArray("data_points").get(0).getAsJsonObject();
                        Key key = new Key();
                        key.time = number(k.get("time"));
                        key.value = new double[] {number(p.get("x")), number(p.get("y")), number(p.get("z"))};
                        key.step = k.has("interpolation") && k.get("interpolation").getAsString().equals("step");
                        switch (channel) {
                            case "position" -> ch.position.add(key);
                            case "rotation" -> ch.rotation.add(key);
                            case "scale" -> ch.scale.add(key);
                            default -> { }
                        }
                    }
                }
                Comparator<Key> byTime = Comparator.comparingDouble(k -> k.time);
                ch.position.sort(byTime);
                ch.rotation.sort(byTime);
                ch.scale.sort(byTime);
                a.animators.put(en.getKey(), ch);
            }
        }
        return a;
    }

    /** The value of a channel at {@code time}: null when the channel has no keyframes. */
    public static double[] sample(List<Key> keys, double time) {
        if (keys.isEmpty()) {
            return null;
        }
        Key first = keys.get(0);
        if (time <= first.time) {
            return first.value;
        }
        for (int i = 1; i < keys.size(); i++) {
            Key b = keys.get(i);
            if (time <= b.time) {
                Key a = keys.get(i - 1);
                if (a.step || b.time - a.time < 1e-9) {
                    return a.value;
                }
                double f = (time - a.time) / (b.time - a.time);
                return new double[] {a.value[0] + (b.value[0] - a.value[0]) * f, a.value[1] + (b.value[1] - a.value[1]) * f,
                    a.value[2] + (b.value[2] - a.value[2]) * f};
            }
        }
        return keys.get(keys.size() - 1).value;
    }
}
