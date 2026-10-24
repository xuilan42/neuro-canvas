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
 * The sky and the mood of the world. A shader paints the whole sky (instead of the game's), so it can be a night of stars, a black hole bending
 * the light behind it, northern lights, a synthwave sunset, a dusk or a meteor shower. The stars turn with the time of day, shooting stars cross
 * the sky now and then, and the distant terrain fades into the colour of the sky's horizon instead of a blue haze.
 */
public final class Ambience extends Module {
    private static final String[] STYLES = {"Space", "Black hole", "Aurora", "Synthwave", "Dusk", "Meteor shower"};
    private static final Matrix4f INV = new Matrix4f();
    private static final Matrix3f ROT = new Matrix3f();

    private final Setting style = add(Setting.choice("Style", 1, STYLES));
    private final Setting color1 = add(Setting.color("Color 1", 0.74));
    private final Setting color2 = add(Setting.color("Color 2", 0.56));
    private final Setting brightness = add(Setting.number("Brightness", 0.3, 2, 1));
    private final Setting stars = add(Setting.number("Stars", 0, 2, 1));
    private final Setting meteors = add(Setting.number("Shooting stars", 0, 1, 0.25));
    private final Setting turn = add(Setting.bool("Sky turns with the time", true));
    private final Setting fogFollows = add(Setting.bool("Fog follows the sky", true));
    private final Setting holeSize = add(Setting.number("Black hole size", 0.05, 0.4, 0.16));
    private final Setting sunYaw = add(Setting.number("Hole / sun direction", 0, 360, 40));
    private final Setting sunHeight = add(Setting.number("Black hole height", -10, 80, 22));

    public Ambience() {
        super("Ambience", Category.WORLD, "A sky of your own: space, a black hole, northern lights, synthwave, dusk or a meteor shower", false);
        color1.setArgb(0xFF7A3DDC);
        color2.setArgb(0xFF3D8BFF);
    }

    private boolean active(Camera camera) {
        ClientWorld world = MinecraftClient.getInstance().world;
        return enabled && world != null && world.getDimension().hasSkyLight() && !CustomFog.specialFog(camera);
    }

    public boolean fogOn(Camera camera) {
        return active(camera) && fogFollows.on();
    }

    /** The colour the far terrain fades into: dark and tinted by the palette, warm for the sunset styles. */
    private float[] fogColor() {
        int c1 = color1.argb();
        int c2 = color2.argb();
        float b = brightness.f();
        return switch (style.index()) {
            case 3 -> new float[] {rgb(c2, 16) * 0.5f * b, rgb(c2, 8) * 0.5f * b, rgb(c2, 0) * 0.5f * b};
            case 4 -> new float[] {rgb(c2, 16) * 0.55f * b, rgb(c2, 8) * 0.45f * b, rgb(c2, 0) * 0.4f * b};
            case 2 -> new float[] {0.01f + rgb(c1, 16) * 0.05f, 0.02f + rgb(c2, 8) * 0.05f, 0.035f + rgb(c2, 0) * 0.05f};
            default -> new float[] {0.012f + rgb(c1, 16) * 0.03f, 0.015f + rgb(c1, 8) * 0.03f, 0.035f + rgb(c1, 0) * 0.04f};
        };
    }

    private static float rgb(int argb, int shift) {
        return ((argb >> shift) & 0xFF) / 255f;
    }

    public void applyFogColor() {
        float[] c = fogColor();
        RenderSystem.setShaderFogColor(c[0], c[1], c[2], 1f);
    }

    public void applyClearColor() {
        float[] c = fogColor();
        RenderSystem.clearColor(c[0], c[1], c[2], 0f);
    }

    /** Draws the sky over the game's own (called right after it); the world is drawn on top afterwards. */
    public void drawSky(Matrix4f projection, Matrix4f view, Camera camera) {
        if (!active(camera) || !Interface.shaderVisuals()) {
            return;
        }
        ShaderProgram p = Shaders.get("neuro_sky");
        ClientWorld world = MinecraftClient.getInstance().world;
        if (p == null || world == null) {
            return;
        }
        set(p, "Mode", style.index());
        set(p, "HoleSize", holeSize.f());
        set(p, "NcGameTime", (float) (System.currentTimeMillis() % 1_000_000L / 1000.0));
        set(p, "Brightness", brightness.f());
        set(p, "Stars", stars.f());
        set(p, "Meteors", meteors.f());
        set(p, "Turn", turn.on() ? (float) ((world.getTimeOfDay() % 24000L) / 24000.0 * Math.PI * 2) : 0f);
        double yaw = Math.toRadians(sunYaw.value);
        double pitch = Math.toRadians(sunHeight.value);
        GlUniform dir = p.getUniform("HoleDir");
        if (dir != null) {
            dir.set((float) (Math.sin(yaw) * Math.cos(pitch)), (float) Math.sin(pitch), (float) (-Math.cos(yaw) * Math.cos(pitch)));
        }
        color(p, "Color1", color1.argb());
        color(p, "Color2", color2.argb());
        GlUniform inv = p.getUniform("InvProjMat");
        if (inv != null) {
            inv.set(INV.set(projection).invert());
        }
        GlUniform rot = p.getUniform("CamRot");
        if (rot != null) {
            // The view matrix is what the game applies to the world; its transpose turns a direction on the screen into a direction in the world.
            rot.set(ROT.set(view).transpose());
        }
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
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
            u.set(rgb(c, 16), rgb(c, 8), rgb(c, 0));
        }
    }
}
