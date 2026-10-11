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

    public NorthernLights() {
        super("NorthernLights", Category.WORLD, "Aurora curtains in the sky", false);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        if (env.mc().world == null || (nightOnly.on() && !env.mc().world.isNight())) {
            return;
        }
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
