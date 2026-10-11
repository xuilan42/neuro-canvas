package com.example.neurocanvas.gui;

import com.example.neurocanvas.module.Interface;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/** Drawing helpers shared by the menu and HUD: styled text, rounded rectangles, gradients. */
public final class Ui {
    private Ui() { }

    public static Text text(String s) {
        Identifier font = Interface.font();
        return font == null ? Text.literal(s) : Text.literal(s).setStyle(Style.EMPTY.withFont(font));
    }

    public static int width(TextRenderer tr, String s) {
        return tr.getWidth(text(s));
    }

    public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
        c.drawText(tr, text(s), x, y, color, false);
    }

    public static void textCentered(DrawContext c, TextRenderer tr, String s, int cx, int y, int color) {
        text(c, tr, s, cx - width(tr, s) / 2, y, color);
    }

    /** Draws text vertically centred in a box of the given height. */
    public static void textMid(DrawContext c, TextRenderer tr, String s, int x, int y, int h, int color) {
        text(c, tr, s, x, y + (h - 8) / 2, color);
    }

    /**
     * Smooth rounded rectangle: a triangle fan over the outline (arcs at the corners) plus a
     * one-pixel fringe that fades to transparent, which anti-aliases the edge.
     */
    /** Splits text into lines no wider than {@code maxW}, breaking at spaces. */
    public static java.util.List<String> wrap(TextRenderer tr, String text, int maxW) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && width(tr, candidate) > maxW) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        int segs = Math.max(4, Math.min(16, (int) (r * 1.5)));
        int n = 4 * (segs + 1);
        float[] px = new float[n];
        float[] py = new float[n];
        float[] nx = new float[n];
        float[] ny = new float[n];
        float[][] corners = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        int k = 0;
        for (float[] corner : corners) {
            for (int i = 0; i <= segs; i++) {
                double a = Math.toRadians(corner[2] + 90.0 * i / segs);
                nx[k] = (float) Math.cos(a);
                ny[k] = (float) Math.sin(a);
                px[k] = corner[0] + nx[k] * r;
                py[k] = corner[1] + ny[k] * r;
                k++;
            }
        }

        float ca = ((color >>> 24) & 0xFF) / 255f;
        float cr = ((color >> 16) & 0xFF) / 255f;
        float cg = ((color >> 8) & 0xFF) / 255f;
        float cb = (color & 0xFF) / 255f;

        c.draw();
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();

        b.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        b.vertex(m, x + w / 2f, y + h / 2f, 0).color(cr, cg, cb, ca).next();
        for (int i = 0; i <= n; i++) {
            int j = i % n;
            b.vertex(m, px[j], py[j], 0).color(cr, cg, cb, ca).next();
        }
        tess.draw();

        float fringe = 0.8f;
        b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= n; i++) {
            int j = i % n;
            b.vertex(m, px[j], py[j], 0).color(cr, cg, cb, ca).next();
            b.vertex(m, px[j] + nx[j] * fringe, py[j] + ny[j] * fringe, 0).color(cr, cg, cb, 0f).next();
        }
        tess.draw();
        RenderSystem.enableCull();
    }

    /** Smooth ring centred on (cx, cy); the radius is that of the middle of the stroke. */
    public static void ring(DrawContext c, float cx, float cy, float radius, float thickness, int color) {
        arc(c, cx, cy, radius, thickness, 0f, 360f, color);
    }

    /** Part of a ring starting at {@code startDeg} (0 = right, 90 = down) and sweeping clockwise. */
    public static void arc(DrawContext c, float cx, float cy, float radius, float thickness,
                           float startDeg, float sweepDeg, int color) {
        if (sweepDeg <= 0f) {
            return;
        }
        c.draw();
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        float ca = ((color >>> 24) & 0xFF) / 255f;
        float cr = ((color >> 16) & 0xFF) / 255f;
        float cg = ((color >> 8) & 0xFF) / 255f;
        float cb = (color & 0xFF) / 255f;
        float inner = Math.max(0f, radius - thickness / 2f);
        float outer = radius + thickness / 2f;
        int segs = Math.max(8, (int) (radius * 3 * Math.min(360f, sweepDeg) / 360f));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        float[][] bands = {{inner, outer, 1f, 1f}, {outer, outer + 0.8f, 1f, 0f}, {inner - 0.8f, inner, 0f, 1f}};
        for (float[] band : bands) {
            b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= segs; i++) {
                double a = Math.toRadians(startDeg + Math.min(360f, sweepDeg) * i / segs);
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                b.vertex(m, cx + cos * Math.max(0f, band[0]), cy + sin * Math.max(0f, band[0]), 0).color(cr, cg, cb, ca * band[2]).next();
                b.vertex(m, cx + cos * band[1], cy + sin * band[1], 0).color(cr, cg, cb, ca * band[3]).next();
            }
            tess.draw();
        }
        RenderSystem.enableCull();
    }

    public static void triangle(DrawContext c, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        c.draw();
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        float ca = ((color >>> 24) & 0xFF) / 255f;
        float cr = ((color >> 16) & 0xFF) / 255f;
        float cg = ((color >> 8) & 0xFF) / 255f;
        float cb = (color & 0xFF) / 255f;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        b.vertex(m, x1, y1, 0).color(cr, cg, cb, ca).next();
        b.vertex(m, x2, y2, 0).color(cr, cg, cb, ca).next();
        b.vertex(m, x3, y3, 0).color(cr, cg, cb, ca).next();
        tess.draw();
        RenderSystem.enableCull();
    }

    /** A rectangle with a different colour at each corner (top-left, top-right, bottom-right, bottom-left). */
    public static void gradientQuad(DrawContext c, float x1, float y1, float x2, float y2, int tl, int tr, int br, int bl) {
        c.draw();
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        corner(b, m, x1, y1, tl);
        corner(b, m, x2, y1, tr);
        corner(b, m, x2, y2, br);
        corner(b, m, x1, y2, bl);
        tess.draw();
        RenderSystem.enableCull();
    }

    private static void corner(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0).color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f).next();
    }

    /** Darkens (or colours) the edges of the screen, fading to nothing toward the middle. */
    public static void vignette(DrawContext c, int w, int h, float size, int argb) {
        int band = Math.round(Math.min(w, h) * size);
        int clear = argb & 0xFFFFFF;
        gradientQuad(c, 0, 0, w, band, argb, argb, clear, clear);
        gradientQuad(c, 0, h - band, w, h, clear, clear, argb, argb);
        gradientQuad(c, 0, band, band, h - band, argb, clear, clear, argb);
        gradientQuad(c, w - band, band, w, h - band, clear, argb, argb, clear);
    }

    public static void shadow(DrawContext c, int x, int y, int w, int h, int r) {
        for (int i = 1; i <= 6; i++) {
            roundRect(c, x - i, y - i + 3, w + 2 * i, h + 2 * i, r + i, alpha(0x000000, 0.05f));
        }
    }

    public static void gradientH(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i++) {
            c.fill(x + i, y, x + i + 1, y + h, mix(c1, c2, w <= 1 ? 0 : i / (float) (w - 1)));
        }
    }

    public static void rainbowH(DrawContext c, int x, int y, int w, int h) {
        for (int i = 0; i < w; i++) {
            c.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | java.awt.Color.HSBtoRGB(i / (float) w, 0.85f, 1f));
        }
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    public static int alpha(int rgb, float a) {
        return ((int) (a * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
