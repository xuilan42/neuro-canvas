package com.example.neurocanvas.avatar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Writes one small example for every kind of cosmetic, so there is a working model of each to start from. */
public final class ExamplePack {
    private ExamplePack() { }

    static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    private static JsonObject cube(String uuid, double[] from, double[] to) {
        JsonObject c = new JsonObject();
        c.addProperty("uuid", uuid);
        c.addProperty("name", uuid);
        c.add("from", arr(from));
        c.add("to", arr(to));
        c.add("origin", arr(0, 0, 0));
        JsonObject faces = new JsonObject();
        for (String f : BbModel.FACES) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", 0);
            face.add("uv", arr(0, 0, 16, 16));
            faces.add(f, face);
        }
        c.add("faces", faces);
        return c;
    }

    static JsonObject bone(String uuid, double[] origin, String... children) {
        JsonObject b = new JsonObject();
        b.addProperty("uuid", uuid);
        b.addProperty("name", uuid);
        b.add("origin", arr(origin));
        b.add("rotation", arr(0, 0, 0));
        JsonArray c = new JsonArray();
        for (String s : children) {
            c.add(s);
        }
        b.add("children", c);
        return b;
    }

    private static JsonObject key(double time, double x, double y, double z) {
        JsonObject k = new JsonObject();
        k.addProperty("channel", "rotation");
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

    static JsonObject loop(String bone, double length, double[]... frames) {
        JsonObject animators = new JsonObject();
        JsonObject a = new JsonObject();
        JsonArray ks = new JsonArray();
        for (int i = 0; i < frames.length; i++) {
            ks.add(key(length * i / (frames.length - 1), frames[i][0], frames[i][1], frames[i][2]));
        }
        a.add("keyframes", ks);
        animators.add(bone, a);
        JsonObject anim = new JsonObject();
        anim.addProperty("name", "idle");
        anim.addProperty("loop", "loop");
        anim.addProperty("length", length);
        anim.add("animators", animators);
        return anim;
    }

    private static String texture(int rgb) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(rgb));
        g.fillRect(0, 0, 16, 16);
        g.setColor(new Color(rgb).brighter());
        g.fillRect(0, 0, 16, 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        JsonObject t = new JsonObject();
        t.addProperty("name", "texture.png");
        t.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
        t.addProperty("uv_width", 16);
        t.addProperty("uv_height", 16);
        return t.toString();
    }

    private static void model(Path folder, int color, JsonArray elements, JsonArray outliner, JsonArray animations) throws IOException {
        Files.createDirectories(folder);
        JsonObject root = new JsonObject();
        JsonObject res = new JsonObject();
        res.addProperty("width", 16);
        res.addProperty("height", 16);
        root.add("resolution", res);
        JsonArray textures = new JsonArray();
        textures.add(com.google.gson.JsonParser.parseString(texture(color)));
        root.add("textures", textures);
        root.add("elements", elements);
        root.add("outliner", outliner);
        root.add("animations", animations);
        Files.writeString(folder.resolve("model.bbmodel"), root.toString(), StandardCharsets.UTF_8);
    }

    static JsonArray list(JsonObject... items) {
        JsonArray a = new JsonArray();
        for (JsonObject o : items) {
            a.add(o);
        }
        return a;
    }

    public static void write(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("README.txt"), "One folder per item: wardrobe/<type>/<name>/ with a model.bbmodel (graffiti: a .png) and an optional cosmetic.json.\n"
                + "Types: capes, wings, hats, outfits, pets, emotes, graffiti.\n\n"
                + "Models use the same space as an avatar of your own body: 16 units are one block, feet at y 0, head at y 24-32, front is north (-z), back is +z.\n"
                + "A cape hangs from y 24 at z 2 (it swings by itself), wings sit on the back, a hat sits above y 32 and follows the head, an outfit sits on the body,\n"
                + "a pet is a small model with its feet at y 0 that walks beside you. Animations named idle (and walk, for pets) play on their own.\n"
                + "Emotes are full avatars with an animation (set \"animation\" in cosmetic.json, or the first one plays). Graffiti is a picture ({ \"size\": 1.5 } is its width in blocks).\n\n"
                + "Then press \"Reload\" in the Wardrobe module and pick the item in its slot.\n");

        // Cape: 8 wide, 16 long, 1 thick, hanging from the shoulders at the back.
        model(root.resolve("capes/Example Cape"), 0xB03030, list(cube("cape", new double[] {-4, 8, 2}, new double[] {4, 24, 3})),
                list(bone("b", new double[] {0, 24, 2}, "cape")), new JsonArray());

        // Wings: two panels that flap about the shoulders.
        JsonObject flap = loop("wr", 1.2, new double[] {0, 10, 0}, new double[] {0, -25, 0}, new double[] {0, 10, 0});
        JsonObject flapLeft = loop("wl", 1.2, new double[] {0, -10, 0}, new double[] {0, 25, 0}, new double[] {0, -10, 0});
        flap.getAsJsonObject("animators").add("wl", flapLeft.getAsJsonObject("animators").get("wl"));
        JsonArray wingAnims = new JsonArray();
        wingAnims.add(flap);
        model(root.resolve("wings/Example Wings"), 0x6FB6FF,
                list(cube("wing-r", new double[] {-16, 12, 2}, new double[] {-2, 28, 3}), cube("wing-l", new double[] {2, 12, 2}, new double[] {16, 28, 3})),
                list(bone("wr", new double[] {-2, 22, 2}, "wing-r"), bone("wl", new double[] {2, 22, 2}, "wing-l")), wingAnims);

        // Hat: a brim and a crown above the head.
        model(root.resolve("hats/Example Hat"), 0x5A3A8C, list(cube("brim", new double[] {-6, 32, -6}, new double[] {6, 33, 6}),
                cube("crown", new double[] {-4, 33, -4}, new double[] {4, 40, 4})), list(bone("b", new double[] {0, 32, 0}, "brim", "crown")), new JsonArray());

        // Outfit: a jacket a little wider than the body.
        model(root.resolve("outfits/Example Jacket"), 0x2E8B57, list(cube("jacket", new double[] {-4.6, 11.6, -2.6}, new double[] {4.6, 24.2, 2.6})),
                list(bone("b", new double[] {0, 12, 0}, "jacket")), new JsonArray());

        // Pet: a small body and head that bobs.
        JsonArray petAnims = new JsonArray();
        petAnims.add(loop("body", 1.0, new double[] {0, 0, 0}, new double[] {6, 0, 0}, new double[] {0, 0, 0}));
        model(root.resolve("pets/Example Pet"), 0xE8A33D, list(cube("body", new double[] {-3, 2, -4}, new double[] {3, 8, 4}),
                cube("head", new double[] {-2.5, 8, -6}, new double[] {2.5, 13, -1})), list(bone("body", new double[] {0, 4, 0}, "body", "head")), petAnims);

        // Emote: the example avatar, whose "wave" animation plays.
        if (!Files.isDirectory(root.resolve("emotes/Example Wave")) || ExampleAvatar.outdated(root.resolve("emotes/Example Wave"))) {
            ExampleAvatar.write(root.resolve("emotes/Example Wave"));
            Files.writeString(root.resolve("emotes/Example Wave/cosmetic.json"), "{ \"animation\": \"wave\" }\n");
        }
        if (!Files.isDirectory(root.resolve("emotes/Example Strike")) || ExampleAvatar.outdated(root.resolve("emotes/Example Strike"))) {
            ExampleAvatar.write(root.resolve("emotes/Example Strike"));
            Files.writeString(root.resolve("emotes/Example Strike/cosmetic.json"), "{ \"animation\": \"attack\" }\n");
        }

        // Graffiti: a heart.
        Path graffiti = root.resolve("graffiti/Example Heart");
        Files.createDirectories(graffiti);
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xFF4D7D));
        g.fillOval(6, 8, 28, 28);
        g.fillOval(30, 8, 28, 28);
        g.fillPolygon(new int[] {8, 56, 32}, new int[] {28, 28, 58}, 3);
        g.setColor(new Color(0xFFB3C8));
        g.fillOval(14, 14, 8, 8);
        g.dispose();
        ImageIO.write(img, "png", graffiti.resolve("image.png").toFile());
        Files.writeString(graffiti.resolve("cosmetic.json"), "{ \"size\": 1.5 }\n");
    }
}
