package com.example.neurocanvas.avatar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Turns a picture painted in the editor into a cosmetic: the canvas of each kind is laid out for a simple model, and
 * {@link #build} writes that model (a .bbmodel with the picture inside) and its settings.
 */
public final class CosmeticBuilder {
    /** A part of the canvas that belongs to a face of the model, drawn as a guide. */
    public record Zone(String label, int x, int y, int w, int h, int color) { }

    public record Template(int width, int height, List<Zone> zones, String hint) { }

    private CosmeticBuilder() { }

    public static boolean hasEditor(CosmeticPack.Type type) {
        return type != CosmeticPack.Type.EMOTE;
    }

    /** Canvas sizes a graffiti can have; the other kinds have one size. */
    public static final int[] GRAFFITI_SIZES = {16, 32, 48, 64};

    private static List<Zone> boxZones(int w, int h, int d) {
        List<Zone> z = new ArrayList<>();
        z.add(new Zone("Top", d, 0, w, d, 0x5CE08A));
        z.add(new Zone("Bottom", d + w, 0, w, d, 0xFF8A3D));
        z.add(new Zone("Side", 0, d, d, h, 0x4DD2FF));
        z.add(new Zone("Front", d, d, w, h, 0xFFD23F));
        z.add(new Zone("Side", d + w, d, d, h, 0x4DD2FF));
        z.add(new Zone("Back", 2 * d + w, d, w, h, 0xB06BFF));
        return z;
    }

    public static Template template(CosmeticPack.Type type, int graffitiSize) {
        return switch (type) {
            case GRAFFITI -> new Template(graffitiSize, graffitiSize, List.of(), "A picture for a wall. Transparent pixels stay clear.");
            case CAPE -> new Template(16, 32, List.of(), "The back of the cape, seen from behind.");
            case WINGS -> new Template(32, 32, List.of(), "One wing seen from behind: the outer edge on the left, the body side on the right. The other wing is its mirror.");
            case HAT -> {
                List<Zone> z = boxZones(8, 8, 8);
                z.add(new Zone("Brim colour", 1, 1, 6, 6, 0xFF7AB6));
                yield new Template(32, 16, z, "The crown of the hat, unfolded. The pink corner sets the colour of the brim.");
            }
            case OUTFIT -> new Template(28, 18, boxZones(9, 13, 5), "A jacket around the body, unfolded: front, back, sides, top and bottom.");
            case PET -> new Template(32, 16, boxZones(8, 8, 8), "A cube-shaped friend, unfolded. Draw its face on the front.");
            default -> new Template(16, 16, List.of(), "");
        };
    }

    private static String pngBase64(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static JsonObject faces(double[][] uv) {
        JsonObject faces = new JsonObject();
        for (int i = 0; i < BbModel.FACES.length; i++) {
            JsonObject f = new JsonObject();
            f.addProperty("texture", 0);
            if (uv != null && uv[i] != null) {
                f.add("uv", ExamplePack.arr(uv[i]));
            }
            faces.add(BbModel.FACES[i], f);
        }
        return faces;
    }

    private static JsonObject cube(String id, double[] from, double[] to, boolean box, double[][] uv) {
        JsonObject c = new JsonObject();
        c.addProperty("uuid", id);
        c.addProperty("name", id);
        c.add("from", ExamplePack.arr(from));
        c.add("to", ExamplePack.arr(to));
        c.add("origin", ExamplePack.arr(0, 0, 0));
        if (box) {
            c.addProperty("box_uv", true);
            c.add("uv_offset", ExamplePack.arr(0, 0));
        }
        c.add("faces", faces(uv));
        return c;
    }

    private static double[][] sheet(double[] outer, double[] inner, double[] edge) {
        // north, east, south, west, up, down
        return new double[][] {inner, edge, outer, edge, edge, edge};
    }

    private static String model(BufferedImage img, JsonArray elements, JsonArray outliner, JsonArray animations) throws IOException {
        JsonObject root = new JsonObject();
        JsonObject res = new JsonObject();
        res.addProperty("width", img.getWidth());
        res.addProperty("height", img.getHeight());
        root.add("resolution", res);
        JsonObject tex = new JsonObject();
        tex.addProperty("name", "texture.png");
        tex.addProperty("source", "data:image/png;base64," + pngBase64(img));
        tex.addProperty("uv_width", img.getWidth());
        tex.addProperty("uv_height", img.getHeight());
        JsonArray textures = new JsonArray();
        textures.add(tex);
        root.add("textures", textures);
        root.add("elements", elements);
        root.add("outliner", outliner);
        root.add("animations", animations);
        return root.toString();
    }

    /** The files of the cosmetic, by file name. */
    public static Map<String, byte[]> build(CosmeticPack.Type type, BufferedImage img) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        double w = img.getWidth();
        double h = img.getHeight();
        double[] edge = {0, 0, 1, 1};
        switch (type) {
            case GRAFFITI -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(img, "png", out);
                files.put("image.png", out.toByteArray());
                files.put("cosmetic.json", "{ \"size\": 1.5 }\n".getBytes(StandardCharsets.UTF_8));
                return files;
            }
            case CAPE -> files.put("model.bbmodel", model(img,
                    ExamplePack.list(cube("cape", new double[] {-4, 8, 2}, new double[] {4, 24, 3}, false,
                            sheet(new double[] {0, 0, w, h}, new double[] {0, 0, w, h}, edge))),
                    ExamplePack.list(ExamplePack.bone("b", new double[] {0, 24, 2}, "cape")), new JsonArray()).getBytes(StandardCharsets.UTF_8));
            case WINGS -> {
                JsonObject flap = ExamplePack.loop("wr", 1.2, new double[] {0, 10, 0}, new double[] {0, -25, 0}, new double[] {0, 10, 0});
                JsonObject flapLeft = ExamplePack.loop("wl", 1.2, new double[] {0, -10, 0}, new double[] {0, 25, 0}, new double[] {0, -10, 0});
                flap.getAsJsonObject("animators").add("wl", flapLeft.getAsJsonObject("animators").get("wl"));
                JsonArray anims = new JsonArray();
                anims.add(flap);
                files.put("model.bbmodel", model(img, ExamplePack.list(
                        cube("wing-r", new double[] {-16, 12, 2}, new double[] {-2, 28, 3}, false,
                                sheet(new double[] {0, 0, w, h}, new double[] {0, 0, w, h}, edge)),
                        cube("wing-l", new double[] {2, 12, 2}, new double[] {16, 28, 3}, false,
                                sheet(new double[] {w, 0, 0, h}, new double[] {w, 0, 0, h}, edge))),
                        ExamplePack.list(ExamplePack.bone("wr", new double[] {-2, 22, 2}, "wing-r"), ExamplePack.bone("wl", new double[] {2, 22, 2}, "wing-l")),
                        anims).getBytes(StandardCharsets.UTF_8));
            }
            case HAT -> {
                double[] brim = {1, 1, 2, 2};
                files.put("model.bbmodel", model(img, ExamplePack.list(
                        cube("crown", new double[] {-4, 33, -4}, new double[] {4, 41, 4}, true, null),
                        cube("brim", new double[] {-6, 32, -6}, new double[] {6, 33, 6}, false, new double[][] {brim, brim, brim, brim, brim, brim})),
                        ExamplePack.list(ExamplePack.bone("b", new double[] {0, 32, 0}, "crown", "brim")), new JsonArray()).getBytes(StandardCharsets.UTF_8));
            }
            case OUTFIT -> files.put("model.bbmodel", model(img,
                    ExamplePack.list(cube("jacket", new double[] {-4.5, 11.5, -2.5}, new double[] {4.5, 24.5, 2.5}, true, null)),
                    ExamplePack.list(ExamplePack.bone("b", new double[] {0, 12, 0}, "jacket")), new JsonArray()).getBytes(StandardCharsets.UTF_8));
            case PET -> {
                JsonArray anims = new JsonArray();
                anims.add(ExamplePack.loop("body", 1.0, new double[] {0, 0, 0}, new double[] {6, 0, 0}, new double[] {0, 0, 0}));
                files.put("model.bbmodel", model(img,
                        ExamplePack.list(cube("pet", new double[] {-4, 0, -4}, new double[] {4, 8, 4}, true, null)),
                        ExamplePack.list(ExamplePack.bone("body", new double[] {0, 4, 0}, "pet")), anims).getBytes(StandardCharsets.UTF_8));
            }
            default -> throw new IOException("this kind has no editor");
        }
        return files;
    }
}
