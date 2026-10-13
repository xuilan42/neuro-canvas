package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Slowly waving curtains of light high in the sky, drawn around you. */
public final class NorthernLights extends Module {
    private final Setting curtains = add(Setting.number("Curtains", 1, 6, 3));
    private final Setting height = add(Setting.number("Height", 30, 150, 80));
    private final Setting radius = add(Setting.number("Distance", 50, 150, 95));
    private final Setting arc = add(Setting.number("Width (degrees)", 40, 360, 200));
    private final Setting speed = add(Setting.number("Speed", 0.1, 4, 1));
    private final Setting intensity = add(Setting.number("Intensity", 0.05, 1, 0.55));
    private final Setting color1 = add(Setting.color("Color 1", 0.38));
    private final Setting color2 = add(Setting.color("Color 2", 0.78));
    private final Setting nightOnly = add(Setting.bool("Only at night", false));
    private final Setting brightness = add(Setting.number("Brightness", 0.3, 3, 1));
    private final Setting sharpness = add(Setting.number("Ray sharpness", 0.6, 3, 1.4));
    private final Setting curtainHeight = add(Setting.number("Curtain height", 20, 120, 55));

    public NorthernLights() {
        super("NorthernLights", Category.WORLD, "Aurora curtains in the sky", false);
    }

    /** 0 in daylight, 1 in the dark; changes smoothly through dusk and dawn. */
    private static float darkness(RenderEnv env) {
        float sky = env.mc().world.getSkyBrightness(env.tickDelta());
        float x = Math.max(0f, Math.min(1f, (0.8f - sky) / 0.5f));
        return x * x * (3f - 2f * x);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        if (env.mc().world == null) {
            return;
        }
        float night = nightOnly.on() ? darkness(env) : 1f;
        if (night < 0.01f) {
            return;
        }
        net.minecraft.client.gl.ShaderProgram program = com.example.neurocanvas.fx.Shaders.get("neuro_aurora");
        if (program != null && Interface.shaderVisuals()) {
            renderShader(env, program, night);
        } else {
            renderLegacy(env);
        }
    }

    private void renderShader(RenderEnv env, net.minecraft.client.gl.ShaderProgram program, float night) {
        Vec3d cam = env.mc().gameRenderer.getCamera().getPos();
        Matrix4f m = env.m();
        double t = env.t() * speed.value;
        int c1 = color1.argb();
        int c2 = color2.argb();
        float alpha = intensity.f() * ((c1 >>> 24) / 255f) * night;
        float r1 = ((c1 >> 16) & 0xFF) / 255f, g1 = ((c1 >> 8) & 0xFF) / 255f, b1 = (c1 & 0xFF) / 255f;

        net.minecraft.client.gl.GlUniform u = program.getUniform("Time");
        if (u != null) {
            u.set((float) t);
        }
        u = program.getUniform("Color2");
        if (u != null) {
            u.set(((c2 >> 16) & 0xFF) / 255f, ((c2 >> 8) & 0xFF) / 255f, (c2 & 0xFF) / 255f);
        }
        u = program.getUniform("Params");
        if (u != null) {
            u.set(sharpness.f(), 2.2f, 0.3f, 0f);
        }
        com.example.neurocanvas.fx.Shaders.setScale(brightness.f());
        com.example.neurocanvas.fx.Shaders.applyBrightness(program);
        com.example.neurocanvas.fx.Shaders.setScale(1f);
        net.minecraft.client.gl.GlUniform seed = program.getUniform("Seed");

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(() -> program);
        int n = (int) curtains.value;
        int segs = 96;
        double arcRad = Math.toRadians(arc.value);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        for (int c = 0; c < n; c++) {
            if (seed != null) {
                seed.set(c * 1.37f);
            }
            double r = radius.value * (0.85 + 0.15 * c / Math.max(1, n - 1));
            double base = cam.y + height.value * (0.65 + 0.12 * c);
            double start = c * 1.7 + t * 0.02;
            float layer = alpha * (c == 0 ? 1f : 0.75f);
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            double px0 = 0, pz0 = 0, py0 = 0, px1 = 0, pz1 = 0, py1 = 0, pf = 0;
            for (int i = 0; i <= segs; i++) {
                double f = i / (double) segs;
                double ang = start + (f - 0.5) * arcRad;
                double rr = r + Math.sin(ang * 3.0 + t * 0.18 + c) * 5.0 + Math.sin(ang * 7.0 - t * 0.25) * 2.0;
                double bottom = base + Math.sin(ang * 2.5 + t * 0.12 + c * 2.0) * 5.0;
                double h = curtainHeight.value * (0.85 + 0.15 * Math.sin(ang * 4.0 + t * 0.2 + c));
                double x0 = cam.x + Math.cos(ang) * rr, z0 = cam.z + Math.sin(ang) * rr;
                double x1 = cam.x + Math.cos(ang) * (rr + 8.0), z1 = cam.z + Math.sin(ang) * (rr + 8.0);
                if (i > 0) {
                    float e0 = edge(pf), e1 = edge(f);
                    float u0 = (float) ((start + (pf - 0.5) * arcRad) * r / 30.0);
                    float uu1 = (float) (ang * r / 30.0);
                    b.vertex(m, (float) px0, (float) py0, (float) pz0).texture(u0, 0f).color(r1, g1, b1, layer * e0).next();
                    b.vertex(m, (float) px1, (float) py1, (float) pz1).texture(u0, 1f).color(r1, g1, b1, layer * e0).next();
                    b.vertex(m, (float) x1, (float) (bottom + h), (float) z1).texture(uu1, 1f).color(r1, g1, b1, layer * e1).next();
                    b.vertex(m, (float) x0, (float) bottom, (float) z0).texture(uu1, 0f).color(r1, g1, b1, layer * e1).next();
                }
                px0 = x0;
                py0 = bottom;
                pz0 = z0;
                px1 = x1;
                py1 = bottom + h;
                pz1 = z1;
                pf = f;
            }
            tess.draw();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        com.example.neurocanvas.fx.Shaders.finish();
    }

    /** Fades the two ends of a curtain smoothly to nothing so it never shows a cut edge. */
    private static float edge(double f) {
        float x = (float) Math.min(1.0, Math.min(f, 1.0 - f) / 0.2);
        return x * x * (3f - 2f * x);
    }

    private void renderLegacy(RenderEnv env) {
        Vec3d cam = env.mc().gameRenderer.getCamera().getPos();
        Matrix4f m = env.m();
        double t = env.t() * 0.25 * speed.value;
        EffectSpec spec = new EffectSpec();
        spec.color1 = color1.argb() & 0xFFFFFF;
        spec.color2 = color2.argb() & 0xFFFFFF;
        float alpha = intensity.f() * ((color1.argb() >>> 24) / 255f);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        int n = (int) curtains.value;
        int segs = 48;
        double arcRad = Math.toRadians(arc.value);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        for (int c = 0; c < n; c++) {
            double r = radius.value * (0.85 + 0.15 * c / Math.max(1, n - 1));
            double base = cam.y + height.value * (0.7 + 0.18 * c);
            double start = c * 1.7 + t * 0.15;
            b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= segs; i++) {
                double f = i / (double) segs;
                double ang = start + (f - 0.5) * arcRad;
                double wave = Math.sin(ang * 5.0 + t * 2.0 + c) * 6.0 + Math.sin(ang * 11.0 - t * 3.0) * 2.5;
                double rr = r + wave;
                double x = cam.x + Math.cos(ang) * rr;
                double z = cam.z + Math.sin(ang) * rr;
                double h = 22.0 + 14.0 * Math.sin(ang * 3.0 + t * 1.3 + c * 2.0);
                float edge = (float) Math.sin(Math.PI * f);
                float a = alpha * edge;
                float[] low = EffectDraw.blend(spec, (float) (0.5 + 0.5 * Math.sin(ang * 4.0 + t)), a);
                float[] high = EffectDraw.blend(spec, 1f, a * 0.35f);
                b.vertex(m, (float) x, (float) (base - h * 0.15), (float) z).color(low[0], low[1], low[2], 0f).next();
                b.vertex(m, (float) x, (float) (base + h * 0.2), (float) z).color(low[0], low[1], low[2], low[3]).next();
                b.vertex(m, (float) x, (float) (base + h * 0.7), (float) z).color(high[0], high[1], high[2], high[3]).next();
                b.vertex(m, (float) x, (float) (base + h), (float) z).color(high[0], high[1], high[2], 0f).next();
            }
            Tessellator.getInstance().draw();
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
    }
}
