package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Shaders;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Northern lights painted on the sky the way Ambience paints its sky: ribbons of light high above, part of the sky itself and not objects in the
 * world. They are added over whatever sky there is (the game's own or Ambience's), so they work with every sky.
 */
public final class NorthernLights extends Module {
    private final Setting speed = add(Setting.number("Speed", 0.1, 4, 1));
    private final Setting intensity = add(Setting.number("Intensity", 0.05, 2, 0.8));
    private final Setting color1 = add(Setting.color("Color 1", 0.74));
    private final Setting color2 = add(Setting.color("Color 2", 0.38));
    private final Setting nightOnly = add(Setting.bool("Only at night", true));
    private final Setting brightness = add(Setting.number("Brightness", 0.3, 3, 1));

    private static final Matrix4f INV = new Matrix4f();
    private static final Matrix3f ROT = new Matrix3f();

    public NorthernLights() {
        super("NorthernLights", Category.WORLD, "Aurora ribbons painted on the sky (over the game's sky or Ambience's)", false);
        color1.setArgb(0xFF7A3DDC);
        color2.setArgb(0xFF3DDC8B);
    }

    private float night(ClientWorld world, float tickDelta) {
        if (!nightOnly.on()) {
            return 1f;
        }
        float x = Math.max(0f, Math.min(1f, (0.8f - world.getSkyBrightness(tickDelta)) / 0.5f));
        return x * x * (3f - 2f * x);
    }

    /** Draws the lights over the sky that has just been drawn (called right after Ambience's). */
    public void drawSky(Matrix4f projection, Matrix4f view, Camera camera, float tickDelta) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (!enabled || world == null || !world.getDimension().hasSkyLight() || !Interface.shaderVisuals()) {
            return;
        }
        float n = night(world, tickDelta);
        ShaderProgram p = n < 0.01f ? null : Shaders.get("neuro_skyadd");
        if (p == null) {
            return;
        }
        set(p, "Mode", 10f);
        set(p, "NcGameTime", (float) (System.currentTimeMillis() % 1_000_000L / 1000.0) * speed.f());
        set(p, "Brightness", brightness.f() * intensity.f() * n);
        color(p, "Color1", color1.argb());
        color(p, "Color2", color2.argb());
        GlUniform inv = p.getUniform("InvProjMat");
        if (inv != null) {
            inv.set(INV.set(projection).invert());
        }
        GlUniform rot = p.getUniform("CamRot");
        if (rot != null) {
            rot.set(ROT.set(view).transpose());
        }
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShader(() -> p);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        b.vertex(-1, -1, 0).next();
        b.vertex(1, -1, 0).next();
        b.vertex(1, 1, 0).next();
        b.vertex(-1, 1, 0).next();
        Tessellator.getInstance().draw();
        RenderSystem.enableDepthTest();
        Shaders.finish();
    }

    private static void set(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }

    private static void color(ShaderProgram p, String name, int c) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
