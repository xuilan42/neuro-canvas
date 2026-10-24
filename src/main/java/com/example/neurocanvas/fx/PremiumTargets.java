package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * The shader-drawn target styles: Marker, Ring, Crystal and Lightning (the Phantom style is a render layer, see GhostLayer).
 * Every shape is a quad (or a small solid) placed with its own matrix level, so nothing leaks between them; the look is computed in the fragment
 * shader. Time is the entity's age plus the tick delta, so the animation follows the game and not the frame rate.
 */
public final class PremiumTargets {
    private static final float[][] QUAD = {{-1, -1, 0, 0}, {1, -1, 1, 0}, {1, 1, 1, 1}, {-1, 1, 0, 1}};
    private static final float[][] OCTA = {{0, 1.25f, 0}, {0, -1.25f, 0}, {0.75f, 0, 0}, {-0.75f, 0, 0}, {0, 0, 0.75f}, {0, 0, -0.75f}};
    private static final int[][] OCTA_FACES = {{0, 4, 2}, {0, 2, 5}, {0, 5, 3}, {0, 3, 4}, {1, 2, 4}, {1, 5, 2}, {1, 3, 5}, {1, 4, 3}};

    private PremiumTargets() { }

    /** @return false when the style could not be drawn (shader missing), so the caller can fall back to a plain one */
    public static boolean draw(int style, RenderEnv env, EffectSpec s) {
        LivingEntity target = env.state().target;
        if (target == null) {
            return true;
        }
        float td = env.tickDelta();
        double x = MathHelper.lerp(td, target.lastRenderX, target.getX());
        double y = MathHelper.lerp(td, target.lastRenderY, target.getY());
        double z = MathHelper.lerp(td, target.lastRenderZ, target.getZ());
        float time = target.age + td;
        try {
            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.disableCull();
            return switch (style) {
                case 0 -> marker(env, s, target, x, y, z, time);
                case 1 -> ring(env, s, target, x, y, z, time);
                case 2 -> true;
                case 3 -> crystal(env, s, target, x, y, z, time);
                case 4 -> lightning(env, s, target, x, y, z, time);
                default -> true;
            };
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }
    }

    private static MatrixStack stack(RenderEnv env) {
        MatrixStack ms = new MatrixStack();
        ms.peek().getPositionMatrix().set(env.m());
        return ms;
    }

    private static ShaderProgram program(String name, EffectSpec s, float time) {
        ShaderProgram p = Shaders.get(name);
        if (p == null || !com.example.neurocanvas.module.Interface.shaderVisuals()) {
            return null;
        }
        GlUniform t = p.getUniform("Time");
        if (t != null) {
            t.set(time);
        }
        GlUniform sp = p.getUniform("Speed");
        if (sp != null) {
            sp.set(s.speed);
        }
        GlUniform c2 = p.getUniform("Color2");
        if (c2 != null) {
            c2.set(((s.color2 >> 16) & 0xFF) / 255f, ((s.color2 >> 8) & 0xFF) / 255f, (s.color2 & 0xFF) / 255f);
        }
        Shaders.applyBrightness(p);
        return p;
    }

    private static BufferBuilder begin(ShaderProgram p) {
        RenderSystem.setShader(() -> p);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        return b;
    }

    private static void finish() {
        Tessellator.getInstance().draw();
        Shaders.finish();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
    }


    private static void quadUv(BufferBuilder b, Matrix4f m, int rgb, float a, boolean mirror) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, bl = (rgb & 0xFF) / 255f;
        for (float[] v : QUAD) {
            b.vertex(m, v[0], v[1], 0f).texture(mirror ? 1f - v[2] : v[2], v[3]).color(r, g, bl, a).next();
        }
    }

    // ---- 1. Marker: four corner brackets that breathe toward the centre --------------------------------

    private static boolean marker(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = program("neuro_marker", s, time);
        if (p == null) {
            return false;
        }
        Camera cam = env.mc().gameRenderer.getCamera();
        float k = Math.max(0.5f, s.size / 0.8f);
        float hx = (t.getWidth() * 0.5f + 0.35f) * k;
        float hy = (t.getHeight() * 0.5f + 0.3f) * k;
        float big = Math.max(hx, hy);
        float size = big / 0.55f;
        GlUniform box = p.getUniform("Box");
        if (box != null) {
            box.set(hx / big * 0.55f, hy / big * 0.55f);
        }
        MatrixStack ms = stack(env);
        BufferBuilder b = begin(p);
        ms.push();
        ms.translate(x, y + t.getHeight() / 2.0, z);
        ms.multiply(cam.getRotation());
        ms.scale(size, size, size);
        quadUv(b, ms.peek().getPositionMatrix(), s.color1, s.alpha, false);
        ms.pop();
        finish();
        return true;
    }

    // ---- 2. Ring: two counter-rotating broken rings on the ground ------------------------------------------

    private static boolean ring(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = program("neuro_ring", s, time);
        if (p == null) {
            return false;
        }
        MatrixStack ms = stack(env);
        BufferBuilder b = begin(p);
        float w = t.getWidth() * s.size / 0.8f;
        for (int i = 0; i < 2; i++) {
            ms.push();
            ms.translate(x, y + 0.1 + i * 0.03, z);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
            float k = i == 0 ? 1.5f : 1.15f;
            ms.scale(w * k, w * k, 1.0f);
            quadUv(b, ms.peek().getPositionMatrix(), s.color1, s.alpha * (i == 0 ? 1f : 0.7f), i == 1);
            ms.pop();
        }
        finish();
        return true;
    }

    // ---- 4. Crystal: a spinning octahedron above the head, glass inside and a neon halo outside ------------

    private static boolean crystal(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram glow = program("neuro_crystal", s, time);
        if (glow == null) {
            return false;
        }
        MatrixStack ms = stack(env);
        ms.push();
        ms.translate(x, y + t.getHeight() + 0.7 + Math.sin(time * 0.1) * 0.2, z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 5.0F));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0F));
        ms.scale(0.4f * Math.max(0.5f, s.size / 0.8f), 0.4f * Math.max(0.5f, s.size / 0.8f), 0.4f * Math.max(0.5f, s.size / 0.8f));

        // The body: faces add up, so the crystal reads as glass; it writes depth so its own faces sort correctly.
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f m = ms.peek().getPositionMatrix();
        for (int f = 0; f < OCTA_FACES.length; f++) {
            float k = f < 4 ? 1f : 0.65f;
            int rgb = blend(s.color1, s.color2, f / 7f);
            float r = ((rgb >> 16) & 0xFF) / 255f * k, g = ((rgb >> 8) & 0xFF) / 255f * k, bl = (rgb & 0xFF) / 255f * k;
            int[] face = OCTA_FACES[f];
            for (int c : new int[] {face[0], face[1], face[2], face[2]}) {
                b.vertex(m, OCTA[c][0], OCTA[c][1], OCTA[c][2]).color(r, g, bl, 0.55f * s.alpha).next();
            }
        }
        Tessellator.getInstance().draw();

        // The halo: slightly larger, drawn on top without writing depth.
        RenderSystem.depthMask(false);
        ms.push();
        ms.scale(1.1f, 1.1f, 1.1f);
        b = begin(glow);
        m = ms.peek().getPositionMatrix();
        float[][] tri = {{0, 0}, {1, 0}, {0, 1}, {0, 1}};
        for (int f = 0; f < OCTA_FACES.length; f++) {
            int rgb = s.color1;
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, bl = (rgb & 0xFF) / 255f;
            int[] face = OCTA_FACES[f];
            int[] idx = {face[0], face[1], face[2], face[2]};
            for (int c = 0; c < 4; c++) {
                b.vertex(m, OCTA[idx[c]][0], OCTA[idx[c]][1], OCTA[idx[c]][2]).texture(tri[c][0], tri[c][1]).color(r, g, bl, s.alpha).next();
            }
        }
        ms.pop();
        ms.pop();
        finish();
        return true;
    }

    private static int blend(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    // ---- 5. Lightning: three jittering bolts drawn procedurally in front of the target ----------------------

    private static boolean lightning(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = program("neuro_lightning", s, time);
        if (p == null) {
            return false;
        }
        Camera cam = env.mc().gameRenderer.getCamera();
        MatrixStack ms = stack(env);
        BufferBuilder b = begin(p);
        ms.push();
        ms.translate(x, y + t.getHeight() / 2.0, z);
        ms.multiply(cam.getRotation());
        float k = Math.max(0.5f, s.size / 0.8f);
        ms.scale(Math.max(t.getWidth(), 0.6f) * 2.0f * k, t.getHeight() * 1.5f * 0.5f * k + 0.3f, Math.max(t.getWidth(), 0.6f) * 2.0f * k);
        quadUv(b, ms.peek().getPositionMatrix(), s.color1, s.alpha, false);
        ms.pop();
        finish();
        return true;
    }
}
