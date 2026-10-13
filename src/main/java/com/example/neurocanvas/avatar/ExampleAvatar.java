package com.example.neurocanvas.avatar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a small example avatar (a blocky figure with idle, walk, attack and wave animations) so there is something to start from. */
public final class ExampleAvatar {
    private ExampleAvatar() { }

    private static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    private static JsonObject cube(String uuid, String name, double[] from, double[] to, double[] origin, int u, int v) {
        JsonObject c = new JsonObject();
        c.addProperty("uuid", uuid);
        c.addProperty("name", name);
        c.add("from", arr(from));
        c.add("to", arr(to));
        c.add("origin", arr(origin));
        c.addProperty("box_uv", true);
        c.add("uv_offset", arr(u, v));
        JsonObject faces = new JsonObject();
        for (String f : BbModel.FACES) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", 0);
            faces.add(f, face);
        }
        c.add("faces", faces);
        return c;
    }

    private static JsonObject bone(String uuid, String name, double[] origin, JsonArray children) {
        JsonObject b = new JsonObject();
        b.addProperty("uuid", uuid);
        b.addProperty("name", name);
        b.add("origin", arr(origin));
        b.add("rotation", arr(0, 0, 0));
        b.add("children", children);
        return b;
    }

    private static JsonObject key(String channel, double time, double x, double y, double z) {
        JsonObject k = new JsonObject();
        k.addProperty("channel", channel);
        k.addProperty("time", time);
        JsonArray dp = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("x", x);
        p.addProperty("y", y);
        p.addProperty("z", z);
        dp.add(p);
        k.add("data_points", dp);
        return k;
    }

    private static JsonObject animator(JsonObject... keys) {
        JsonObject a = new JsonObject();
        JsonArray ks = new JsonArray();
        for (JsonObject k : keys) {
            ks.add(k);
        }
        a.add("keyframes", ks);
        return a;
    }

    private static JsonObject anim(String name, String loop, double length, JsonObject animators) {
        JsonObject a = new JsonObject();
        a.addProperty("name", name);
        a.addProperty("loop", loop);
        a.addProperty("length", length);
        a.add("animators", animators);
        return a;
    }

    /** True for an example written by an older version, whose model carries its own painted texture. */
    public static boolean outdated(Path folder) {
        try {
            Path model = folder.resolve("model.bbmodel");
            if (!Files.exists(model)) {
                return false;
            }
            String text = Files.readString(model);
            return text.contains("\"source\":\"data:image") || text.contains("\"uuid\":\"c-rarm\",\"name\":\"right arm\",\"from\":[-8.0");
        } catch (IOException e) {
            return false;
        }
    }

    /** A Steve with bones; {@code animated} adds idle, walk, attack and wave. Its texture is the game's skin ({@code skin} is "minecraft:steve" or "minecraft:player"). */
    static JsonObject steve(String skin, boolean animated) {
        JsonObject root = new JsonObject();
        JsonObject res = new JsonObject();
        res.addProperty("width", 64);
        res.addProperty("height", 64);
        root.add("resolution", res);
        JsonArray textures = new JsonArray();
        JsonObject tex = new JsonObject();
        tex.addProperty("name", "texture.png");
        // The game's own Steve skin is used at run time; no picture is stored in the model.
        tex.addProperty("source", skin);
        tex.addProperty("uv_width", 64);
        tex.addProperty("uv_height", 64);
        textures.add(tex);
        root.add("textures", textures);

        JsonArray elements = new JsonArray();
        elements.add(cube("c-head", "head", new double[] {-4, 24, -4}, new double[] {4, 32, 4}, new double[] {0, 24, 0}, 0, 0));
        elements.add(cube("c-body", "body", new double[] {-4, 12, -2}, new double[] {4, 24, 2}, new double[] {0, 12, 0}, 16, 16));
        elements.add(cube("c-rarm", "right arm", new double[] {4, 12, -2}, new double[] {8, 24, 2}, new double[] {6, 22, 0}, 40, 16));
        elements.add(cube("c-larm", "left arm", new double[] {-8, 12, -2}, new double[] {-4, 24, 2}, new double[] {-6, 22, 0}, 32, 48));
        elements.add(cube("c-rleg", "right leg", new double[] {0, 0, -2}, new double[] {4, 12, 2}, new double[] {2, 12, 0}, 0, 16));
        elements.add(cube("c-lleg", "left leg", new double[] {-4, 0, -2}, new double[] {0, 12, 2}, new double[] {-2, 12, 0}, 16, 48));
        root.add("elements", elements);

        JsonArray outliner = new JsonArray();
        JsonArray bodyChildren = new JsonArray();
        bodyChildren.add("c-body");
        for (String[] b : new String[][] {{"b-head", "Head", "c-head"}, {"b-rarm", "RightArm", "c-rarm"}, {"b-larm", "LeftArm", "c-larm"}}) {
            JsonArray c = new JsonArray();
            c.add(b[2]);
            double[] origin = b[0].equals("b-head") ? new double[] {0, 24, 0} : b[0].equals("b-rarm") ? new double[] {6, 22, 0} : new double[] {-6, 22, 0};
            bodyChildren.add(bone(b[0], b[1], origin, c));
        }
        outliner.add(bone("b-body", "Body", new double[] {0, 12, 0}, bodyChildren));
        for (String[] b : new String[][] {{"b-rleg", "RightLeg", "c-rleg"}, {"b-lleg", "LeftLeg", "c-lleg"}}) {
            JsonArray c = new JsonArray();
            c.add(b[2]);
            outliner.add(bone(b[0], b[1], b[0].equals("b-rleg") ? new double[] {2, 12, 0} : new double[] {-2, 12, 0}, c));
        }
        root.add("outliner", outliner);

        if (!animated) {
            return root;
        }
        JsonArray anims = new JsonArray();
        JsonObject idle = new JsonObject();
        idle.add("b-larm", animator(key("rotation", 0, 0, 0, 0), key("rotation", 1, 0, 0, -4), key("rotation", 2, 0, 0, 0)));
        idle.add("b-rarm", animator(key("rotation", 0, 0, 0, 0), key("rotation", 1, 0, 0, 4), key("rotation", 2, 0, 0, 0)));
        idle.add("b-head", animator(key("rotation", 0, 0, 0, 0), key("rotation", 1, 3, 0, 0), key("rotation", 2, 0, 0, 0)));
        anims.add(anim("idle", "loop", 2, idle));
        JsonObject walk = new JsonObject();
        walk.add("b-rleg", animator(key("rotation", 0, 35, 0, 0), key("rotation", 0.5, -35, 0, 0), key("rotation", 1, 35, 0, 0)));
        walk.add("b-lleg", animator(key("rotation", 0, -35, 0, 0), key("rotation", 0.5, 35, 0, 0), key("rotation", 1, -35, 0, 0)));
        walk.add("b-rarm", animator(key("rotation", 0, -30, 0, 0), key("rotation", 0.5, 30, 0, 0), key("rotation", 1, -30, 0, 0)));
        walk.add("b-larm", animator(key("rotation", 0, 30, 0, 0), key("rotation", 0.5, -30, 0, 0), key("rotation", 1, 30, 0, 0)));
        anims.add(anim("walk", "loop", 1, walk));
        JsonObject attack = new JsonObject();
        attack.add("b-rarm", animator(key("rotation", 0, 0, 0, 0), key("rotation", 0.12, -110, 0, 0), key("rotation", 0.4, 0, 0, 0)));
        attack.add("b-body", animator(key("rotation", 0, 0, 0, 0), key("rotation", 0.12, 0, -15, 0), key("rotation", 0.4, 0, 0, 0)));
        anims.add(anim("attack", "once", 0.4, attack));
        JsonObject wave = new JsonObject();
        wave.add("b-larm", animator(key("rotation", 0, 0, 0, 0), key("rotation", 0.25, 0, 0, -150), key("rotation", 0.5, 0, 0, -125), key("rotation", 0.75, 0, 0, -150), key("rotation", 1, 0, 0, 0)));
        anims.add(anim("wave", "once", 1, wave));
        root.add("animations", anims);
        return root;
    }

    /** The Steve used to show cosmetics in menus: no animations, wearing the skin of the player who plays. */
    public static String mannequinJson() {
        return steve("minecraft:player", false).toString();
    }

    public static void write(Path folder) throws IOException {
        Files.createDirectories(folder);
        JsonObject root = steve("minecraft:steve", true);
        Files.writeString(folder.resolve("model.bbmodel"), root.toString(), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("avatar.json"), "{\n  \"model\": \"model.bbmodel\",\n  \"scale\": 1.0,\n  \"yaw\": 0,\n  \"keys\": { \"G\": \"wave\" }\n}\n",
                StandardCharsets.UTF_8);
    }
}
