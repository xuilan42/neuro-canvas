package com.example.neurocanvas.avatar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;

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

    private static void paint(Graphics2D g, int u, int v, int w, int h, int d, Color base) {
        int[][] r = {{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + 2 * d + w, v + d, w, h}};
        for (int[] q : r) {
            g.setColor(base);
            g.fillRect(q[0], q[1], q[2], q[3]);
            g.setColor(base.darker());
            g.drawRect(q[0], q[1], q[2] - 1, q[3] - 1);
        }
    }

    public static void write(Path folder) throws IOException {
        Files.createDirectories(folder);
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color skin = new Color(0xE8B88C);
        Color suit = new Color(0x3D7BDC);
        Color pants = new Color(0x2B3A6B);
        paint(g, 0, 0, 8, 8, 8, skin);
        g.setColor(new Color(0x303030));
        g.fillRect(8 + 1, 8 + 3, 2, 2);
        g.fillRect(8 + 5, 8 + 3, 2, 2);
        paint(g, 16, 16, 8, 12, 4, suit);
        paint(g, 40, 16, 4, 12, 4, suit);
        paint(g, 32, 48, 4, 12, 4, suit);
        paint(g, 0, 16, 4, 12, 4, pants);
        paint(g, 16, 48, 4, 12, 4, pants);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);

        JsonObject root = new JsonObject();
        JsonObject res = new JsonObject();
        res.addProperty("width", 64);
        res.addProperty("height", 64);
        root.add("resolution", res);
        JsonArray textures = new JsonArray();
        JsonObject tex = new JsonObject();
        tex.addProperty("name", "texture.png");
        tex.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
        tex.addProperty("uv_width", 64);
        tex.addProperty("uv_height", 64);
        textures.add(tex);
        root.add("textures", textures);

        JsonArray elements = new JsonArray();
        elements.add(cube("c-head", "head", new double[] {-4, 24, -4}, new double[] {4, 32, 4}, new double[] {0, 24, 0}, 0, 0));
        elements.add(cube("c-body", "body", new double[] {-4, 12, -2}, new double[] {4, 24, 2}, new double[] {0, 12, 0}, 16, 16));
        elements.add(cube("c-rarm", "right arm", new double[] {-8, 12, -2}, new double[] {-4, 24, 2}, new double[] {-6, 22, 0}, 40, 16));
        elements.add(cube("c-larm", "left arm", new double[] {4, 12, -2}, new double[] {8, 24, 2}, new double[] {6, 22, 0}, 32, 48));
        elements.add(cube("c-rleg", "right leg", new double[] {-4, 0, -2}, new double[] {0, 12, 2}, new double[] {-2, 12, 0}, 0, 16));
        elements.add(cube("c-lleg", "left leg", new double[] {0, 0, -2}, new double[] {4, 12, 2}, new double[] {2, 12, 0}, 16, 48));
        root.add("elements", elements);

        JsonArray outliner = new JsonArray();
        JsonArray bodyChildren = new JsonArray();
        bodyChildren.add("c-body");
        for (String[] b : new String[][] {{"b-head", "Head", "c-head"}, {"b-rarm", "RightArm", "c-rarm"}, {"b-larm", "LeftArm", "c-larm"}}) {
            JsonArray c = new JsonArray();
            c.add(b[2]);
            double[] origin = b[0].equals("b-head") ? new double[] {0, 24, 0} : b[0].equals("b-rarm") ? new double[] {-6, 22, 0} : new double[] {6, 22, 0};
            bodyChildren.add(bone(b[0], b[1], origin, c));
        }
        outliner.add(bone("b-body", "Body", new double[] {0, 12, 0}, bodyChildren));
        for (String[] b : new String[][] {{"b-rleg", "RightLeg", "c-rleg"}, {"b-lleg", "LeftLeg", "c-lleg"}}) {
            JsonArray c = new JsonArray();
            c.add(b[2]);
            outliner.add(bone(b[0], b[1], b[0].equals("b-rleg") ? new double[] {-2, 12, 0} : new double[] {2, 12, 0}, c));
        }
        root.add("outliner", outliner);

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
        wave.add("b-larm", animator(key("rotation", 0, 0, 0, 0), key("rotation", 0.25, 0, 0, 150), key("rotation", 0.5, 0, 0, 125), key("rotation", 0.75, 0, 0, 150), key("rotation", 1, 0, 0, 0)));
        anims.add(anim("wave", "once", 1, wave));
        root.add("animations", anims);

        Files.writeString(folder.resolve("model.bbmodel"), root.toString(), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("avatar.json"), "{\n  \"model\": \"model.bbmodel\",\n  \"scale\": 1.0,\n  \"yaw\": 0,\n  \"keys\": { \"G\": \"wave\" }\n}\n",
                StandardCharsets.UTF_8);
    }
}
