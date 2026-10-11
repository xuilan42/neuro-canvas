package com.example.neurocanvas.gui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Builds a theme palette from a picture: the most vivid colour that covers a good part of the image becomes the accent,
 * a clearly different second colour becomes the second accent, and the surfaces are dark tints of the average hue.
 */
public final class ImagePalette {
    private static final int BUCKETS = 24;

    private ImagePalette() { }

    public static Theme fromFile(Path file) throws IOException {
        BufferedImage img = ImageIO.read(Files.newInputStream(file));
        if (img == null) {
            throw new IOException("This is not a picture the game can read (use PNG or JPG)");
        }
        return fromImage(img);
    }

    public static Theme fromImage(BufferedImage img) {
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 96);
        double[] weight = new double[BUCKETS];
        double[] sat = new double[BUCKETS];
        double[] val = new double[BUCKETS];
        double dark = 0;
        double darkSat = 0;
        int darkCount = 0;
        float[] hsb = new float[3];
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    continue;
                }
                Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);
                if (hsb[2] < 0.25f) {
                    dark += hsb[0];
                    darkSat += hsb[1];
                    darkCount++;
                }
                if (hsb[1] < 0.18f || hsb[2] < 0.25f) {
                    continue;
                }
                int b = Math.min(BUCKETS - 1, (int) (hsb[0] * BUCKETS));
                double w = hsb[1] * hsb[1] * (0.4 + hsb[2]);
                weight[b] += w;
                sat[b] += hsb[1] * w;
                val[b] += hsb[2] * w;
            }
        }
        int first = -1;
        for (int i = 0; i < BUCKETS; i++) {
            if (first < 0 || weight[i] > weight[first]) {
                first = i;
            }
        }
        if (first < 0 || weight[first] <= 0) {
            return Theme.fromAccent(0xFF6C8CFF);
        }
        int second = -1;
        for (int i = 0; i < BUCKETS; i++) {
            int dist = Math.min(Math.abs(i - first), BUCKETS - Math.abs(i - first));
            if (dist >= 3 && weight[i] > 0 && (second < 0 || weight[i] > weight[second])) {
                second = i;
            }
        }
        float h1 = (first + 0.5f) / BUCKETS;
        float s1 = (float) Math.min(1.0, Math.max(0.45, sat[first] / weight[first]));
        float v1 = (float) Math.min(1.0, Math.max(0.75, val[first] / weight[first]));
        float h2 = second >= 0 ? (second + 0.5f) / BUCKETS : (h1 + 0.1f) % 1f;
        float s2 = second >= 0 ? (float) Math.min(1.0, Math.max(0.4, sat[second] / weight[second])) : s1;
        float v2 = second >= 0 ? (float) Math.min(1.0, Math.max(0.75, val[second] / weight[second])) : v1;
        float hd = darkCount > 20 ? (float) (dark / darkCount) : h1;
        float sd = darkCount > 20 ? (float) Math.min(0.5, darkSat / darkCount) : 0.3f;

        int accent = 0xFF000000 | (Color.HSBtoRGB(h1, s1, v1) & 0xFFFFFF);
        int accent2 = 0xFF000000 | (Color.HSBtoRGB(h2, s2, v2) & 0xFFFFFF);
        return new Theme("Image",
                0xF2000000 | (Color.HSBtoRGB(hd, sd, 0.07f) & 0xFFFFFF),
                0xFF000000 | (Color.HSBtoRGB(hd, sd, 0.10f) & 0xFFFFFF),
                0xFF000000 | (Color.HSBtoRGB(hd, sd, 0.14f) & 0xFFFFFF),
                0xFF000000 | (Color.HSBtoRGB(hd, sd, 0.19f) & 0xFFFFFF),
                accent, accent2,
                0xFF000000 | (Color.HSBtoRGB(hd, 0.05f, 0.95f) & 0xFFFFFF),
                0xFF000000 | (Color.HSBtoRGB(hd, Math.min(0.2f, sd), 0.62f) & 0xFFFFFF));
    }
}
