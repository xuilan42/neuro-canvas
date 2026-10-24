package com.example.neurocanvas.client;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Turns the bytes of a project icon (PNG, JPEG, GIF or WebP) into a small square of ARGB pixels. It needs nothing from the game. */
public final class IconDecoder {
    private static final int MAX_SIDE = 2048;

    private IconDecoder() { }

    private static BufferedImage read(byte[] data) throws IOException {
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
            try (MemoryCacheImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
                reader.setInput(in);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    /** @return size x size ARGB pixels: the middle square of the picture (a wide video thumbnail loses its sides), smoothly scaled */
    public static int[] decodeCover(byte[] data, int size) throws IOException {
        BufferedImage src = read(data);
        if (src == null || src.getWidth() < 2 || src.getHeight() < 2 || src.getWidth() > MAX_SIDE || src.getHeight() > MAX_SIDE) {
            throw new IOException("not a usable picture");
        }
        int side = Math.min(src.getWidth(), src.getHeight());
        int sx = (src.getWidth() - side) / 2;
        int sy = (src.getHeight() - side) / 2;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, size, size, sx, sy, sx + side, sy + side, null);
        g.dispose();
        return out.getRGB(0, 0, size, size, null, 0, size);
    }

    /** @return size x size ARGB pixels, the picture scaled down (or up) to fit, centred, with transparent margins */
    public static int[] decode(byte[] data, int size) throws IOException {
        BufferedImage src = read(data);
        if (src == null || src.getWidth() < 1 || src.getHeight() < 1 || src.getWidth() > MAX_SIDE || src.getHeight() > MAX_SIDE) {
            throw new IOException("not a usable picture");
        }
        int sw = src.getWidth();
        int sh = src.getHeight();
        double scale = Math.min(size / (double) sw, size / (double) sh);
        int dw = Math.max(1, (int) Math.round(sw * scale));
        int dh = Math.max(1, (int) Math.round(sh * scale));
        int ox = (size - dw) / 2;
        int oy = (size - dh) / 2;
        int[] px = src.getRGB(0, 0, sw, sh, null, 0, sw);
        int[] out = new int[size * size];
        // Area average, with colours weighted by alpha so transparent edges do not turn dark.
        for (int y = 0; y < dh; y++) {
            int y0 = (int) Math.floor(y * sh / (double) dh);
            int y1 = Math.max(y0 + 1, (int) Math.ceil((y + 1) * sh / (double) dh));
            for (int x = 0; x < dw; x++) {
                int x0 = (int) Math.floor(x * sw / (double) dw);
                int x1 = Math.max(x0 + 1, (int) Math.ceil((x + 1) * sw / (double) dw));
                double a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y0; yy < Math.min(y1, sh); yy++) {
                    for (int xx = x0; xx < Math.min(x1, sw); xx++) {
                        int p = px[yy * sw + xx];
                        double al = (p >>> 24) / 255.0;
                        a += al;
                        r += ((p >> 16) & 0xFF) * al;
                        g += ((p >> 8) & 0xFF) * al;
                        b += (p & 0xFF) * al;
                        n++;
                    }
                }
                if (n == 0 || a <= 0) {
                    continue;
                }
                int ai = (int) Math.round(a / n * 255);
                out[(y + oy) * size + x + ox] = (ai << 24) | ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8) | (int) Math.round(b / a);
            }
        }
        return out;
    }
}
