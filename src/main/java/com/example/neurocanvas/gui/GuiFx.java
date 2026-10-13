package com.example.neurocanvas.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Shader passes for the menu: a blurred copy of the screen for glass panels, and animated effects (CRT lines, neon
 * grid, aurora) clipped to a rounded rectangle. If a shader cannot be built the effect is skipped; nothing crashes.
 */
public final class GuiFx {
    private static ShaderProgram blur;
    private static ShaderProgram ui;
    private static ShaderProgram glass;
    private static boolean glassFailed;
    private static Framebuffer fbA;
    private static Framebuffer fbB;
    private static boolean blurFailed;
    private static boolean uiFailed;
    private static boolean blurReady;

    private GuiFx() { }

    public static boolean blurAvailable() {
        return blurReady;
    }

    private static void fullscreenQuad() {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buf.vertex(-1.0, -1.0, 0.0).next();
        buf.vertex(1.0, -1.0, 0.0).next();
        buf.vertex(1.0, 1.0, 0.0).next();
        buf.vertex(-1.0, 1.0, 0.0).next();
        tess.draw();
    }

    private static void pass(ShaderProgram program, Framebuffer from, int textureId, Framebuffer to, float dx, float dy) {
        to.setClearColor(0f, 0f, 0f, 1f);
        to.clear(false);
        to.beginWrite(true);
        GlUniform dir = program.getUniform("Direction");
        if (dir != null) {
            dir.set(dx / Math.max(1, from.textureWidth), dy / Math.max(1, from.textureHeight));
        }
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(() -> program);
        fullscreenQuad();
    }

    /**
     * Makes a blurred half-resolution copy of everything drawn so far this frame.
     * @param strength 0..2, how far the blur reaches
     */
    public static void captureBlur(MinecraftClient mc, DrawContext ctx, float strength) {
        blurReady = false;
        if (blurFailed || strength <= 0.01f) {
            return;
        }
        try {
            ctx.draw();
            {
                blur = com.example.neurocanvas.fx.Shaders.get("neuro_blur");
                if (blur == null) {
                    blurFailed = true;
                    return;
                }
            }
            Framebuffer main = mc.getFramebuffer();
            int w = Math.max(2, main.textureWidth / 2);
            int h = Math.max(2, main.textureHeight / 2);
            if (fbA == null) {
                fbA = new SimpleFramebuffer(w, h, false, MinecraftClient.IS_SYSTEM_MAC);
                fbB = new SimpleFramebuffer(w, h, false, MinecraftClient.IS_SYSTEM_MAC);
                fbA.setTexFilter(9729);
                fbB.setTexFilter(9729);
            } else if (fbA.textureWidth != w || fbA.textureHeight != h) {
                fbA.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
                fbB.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
            }
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            float r = 0.8f + strength * 1.6f;
            pass(blur, main, main.getColorAttachment(), fbA, r, 0f);
            pass(blur, fbA, fbA.getColorAttachment(), fbB, 0f, r);
            if (strength > 0.9f) {
                pass(blur, fbB, fbB.getColorAttachment(), fbA, r, 0f);
                pass(blur, fbA, fbA.getColorAttachment(), fbB, 0f, r);
            }
            main.beginWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            blurReady = true;
            com.example.neurocanvas.fx.Shaders.finish();
        } catch (Throwable e) {
            blurFailed = true;
            mc.getFramebuffer().beginWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    /** Draws the blurred copy inside a rounded rectangle of the menu (GUI coordinates). */
    public static void drawBlurred(DrawContext ctx, int x, int y, int w, int h, int r, float alpha) {
        if (!blurReady || fbB == null) {
            return;
        }
        Ui.texturedRound(ctx, x, y, w, h, r, fbB.getColorAttachment(), ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), alpha);
    }

    /**
     * Draws an animated effect clipped to a rounded rectangle.
     * @param mode 1 CRT lines, 2 neon grid, 3 aurora
     */
    public static void overlay(MinecraftClient mc, DrawContext ctx, int mode, int x, int y, int w, int h, int radius,
                               int accent, int accent2, float strength) {
        if (uiFailed || mode <= 0 || strength <= 0.01f) {
            return;
        }
        try {
            ctx.draw();
            {
                ui = com.example.neurocanvas.fx.Shaders.get("neuro_ui");
                if (ui == null) {
                    uiFailed = true;
                    return;
                }
            }
            double scale = mc.getWindow().getScaleFactor();
            int fbh = mc.getWindow().getFramebufferHeight();
            set1(ui, "Mode", mode);
            set1(ui, "Time", (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f);
            set1(ui, "Strength", strength);
            set1(ui, "Scale", (float) scale);
            set1(ui, "Radius", (float) (radius * scale));
            GlUniform rect = ui.getUniform("Rect");
            if (rect != null) {
                rect.set((float) (x * scale), (float) (fbh - (y + h) * scale), (float) (w * scale), (float) (h * scale));
            }
            rgb(ui, "Accent", accent);
            rgb(ui, "Accent2", accent2);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> ui);
            fullscreenQuad();
            RenderSystem.enableDepthTest();
            com.example.neurocanvas.fx.Shaders.finish();
        } catch (Throwable e) {
            uiFailed = true;
        }
    }


    /**
     * Draws the frosted glass panel of the Glass theme over the rectangle (GUI coordinates, current matrix applied) using the
     * blurred copy made by {@link #captureBlur}. Returns false when it cannot be drawn (no blur yet or the shader is missing),
     * so the caller can paint a plain panel instead.
     * @param opacity 0 clear glass .. 1 nearly solid
     */
    public static boolean drawGlass(MinecraftClient mc, DrawContext ctx, int x, int y, int w, int h, int radius,
                                    int tint, int accent, int accent2, float opacity) {
        if (glassFailed || !blurReady || fbB == null) {
            return false;
        }
        try {
            ctx.draw();
            glass = com.example.neurocanvas.fx.Shaders.get("neuro_glass");
            if (glass == null) {
                glassFailed = true;
                return false;
            }
            org.joml.Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
            org.joml.Vector3f a = m.transformPosition(x, y, 0f, new org.joml.Vector3f());
            org.joml.Vector3f b = m.transformPosition(x + w, y + h, 0f, new org.joml.Vector3f());
            double scale = mc.getWindow().getScaleFactor();
            int fbw = mc.getWindow().getFramebufferWidth();
            int fbh = mc.getWindow().getFramebufferHeight();
            float k = Math.abs(b.x - a.x) / Math.max(1f, w);
            set1(glass, "Time", (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f);
            set1(glass, "Opacity", opacity);
            set1(glass, "Scale", (float) scale);
            set1(glass, "Radius", (float) (radius * scale * k));
            GlUniform rect = glass.getUniform("Rect");
            if (rect != null) {
                rect.set((float) (a.x * scale), (float) (fbh - b.y * scale), (float) ((b.x - a.x) * scale), (float) ((b.y - a.y) * scale));
            }
            GlUniform fb = glass.getUniform("FbSize");
            if (fb != null) {
                fb.set((float) fbw, (float) fbh);
            }
            rgb(glass, "Tint", tint);
            rgb(glass, "Accent", accent);
            rgb(glass, "Accent2", accent2);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, fbB.getColorAttachment());
            RenderSystem.setShader(() -> glass);
            fullscreenQuad();
            RenderSystem.enableDepthTest();
            com.example.neurocanvas.fx.Shaders.finish();
            return true;
        } catch (Throwable e) {
            glassFailed = true;
            return false;
        }
    }

    private static void set1(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }

    private static void rgb(ShaderProgram p, String name, int argb) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f);
        }
    }
}
