package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Supplier;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The shader-drawn target styles: Neon Marker, Neon Ring, Crystal Prism and Lightning (Phantom is a render layer, see GhostLayer).
 *
 * <p>Nothing is allocated per frame: the matrices are two fields reused by every shape, and each shape starts again from the camera-relative view
 * matrix, so no transform leaks from one shape into the next (the same isolation a push/pop gives, without building a new matrix entry). The
 * position is interpolated by tick delta and made relative to the camera in double precision, so far from the world origin nothing jitters.
 * The caller sets the blend and depth state (see TargetStyles).
 */
public final class PremiumTargets {
    private static final float[][] QUAD = {{-1, -1, 0, 0}, {1, -1, 1, 0}, {1, 1, 1, 1}, {-1, 1, 0, 1}};
    private static final float[][] OCTA = {{0, 1.25f, 0}, {0, -1.25f, 0}, {0.75f, 0, 0}, {-0.75f, 0, 0}, {0, 0, 0.75f}, {0, 0, -0.75f}};
    private static final int[][] OCTA_FACES = {{0, 4, 2}, {0, 2, 5}, {0, 5, 3}, {0, 3, 4}, {1, 2, 4}, {1, 5, 2}, {1, 3, 5}, {1, 4, 3}};
    private static final float[][] TRI_UV = {{0, 0}, {1, 0}, {0, 1}, {0, 1}};
    private static final float DEG = (float) (Math.PI / 180.0);

    private static final Matrix4f WORK = new Matrix4f();
    private static final Matrix4f HALO = new Matrix4f();
    private static ShaderProgram active;
    private static final Supplier<ShaderProgram> ACTIVE = () -> active;

    private PremiumTargets() { }

    /** @return false when the style could not be drawn (its shader is missing), so the caller can fall back to a plain one */
    public static boolean draw(int style, RenderEnv env, EffectSpec s) {
        LivingEntity target = env.state().target;
        if (target == null) {
            return true;
        }
        float td = env.tickDelta();
        double x = MathHelper.lerp(td, target.lastRenderX, target.getX()) - env.cam().x;
        double y = MathHelper.lerp(td, target.lastRenderY, target.getY()) - env.cam().y;
        double z = MathHelper.lerp(td, target.lastRenderZ, target.getZ()) - env.cam().z;
        float time = target.age + td;
        return switch (style) {
            case 0 -> marker(env, s, target, x, y, z, time);
            case 1 -> ring(env, s, target, x, y, z, time);
            case 3 -> crystal(env, s, target, x, y, z, time);
            case 4 -> lightning(env, s, target, x, y, z, time);
            default -> true;
        };
    }

    private static BufferBuilder begin(ShaderProgram p) {
        active = p;
        RenderSystem.setShader(ACTIVE);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        return b;
    }

    private static void finish() {
        Tessellator.getInstance().draw();
        Shaders.finish();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        active = null;
    }

    private static void unitQuad(BufferBuilder b, Matrix4f m, int rgb, float a, boolean mirror) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, bl = (rgb & 0xFF) / 255f;
        for (float[] v : QUAD) {
            b.vertex(m, v[0], v[1], 0f).texture(mirror ? 1f - v[2] : v[2], v[3]).color(r, g, bl, a).next();
        }
    }

    private static float sizeScale(EffectSpec s) {
        return Math.max(0.5f, s.size / 0.8f);
    }

    /** A matrix that faces the camera: the camera's own rotation, which is its yaw and pitch combined. */
    private static Matrix4f billboard(RenderEnv env, double x, double y, double z) {
        Quaternionf rot = env.mc().gameRenderer.getCamera().getRotation();
        return WORK.set(env.view()).translate((float) x, (float) y, (float) z).rotate(rot);
    }

    // ---- Neon Marker: four corner brackets, the frame pulses in scale --------------------------------------

    private static boolean marker(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = Shaders.targetProgram("neuro_marker", s.speed, s.color2, time);
        if (p == null) {
            return false;
        }
        float k = sizeScale(s);
        float hx = (t.getWidth() * 0.5f + 0.35f) * k;
        float hy = (t.getHeight() * 0.5f + 0.3f) * k;
        float big = Math.max(hx, hy);
        float size = big / 0.55f;
        GlUniform box = p.getUniform("Box");
        if (box != null) {
            box.set(hx / big * 0.55f, hy / big * 0.55f);
        }
        BufferBuilder b = begin(p);
        Matrix4f m = billboard(env, x, y + t.getHeight() * 0.5, z).scale(size, size, size);
        unitQuad(b, m, s.color1, s.alpha, false);
        finish();
        return true;
    }

    // ---- Neon Ring: two broken rings lying at the feet, turning against each other -------------------------

    private static boolean ring(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = Shaders.targetProgram("neuro_ring", s.speed, s.color2, time);
        if (p == null) {
            return false;
        }
        BufferBuilder b = begin(p);
        float w = Math.max(t.getWidth(), 0.4f) * sizeScale(s);
        for (int i = 0; i < 2; i++) {
            float k = i == 0 ? 1.5f : 1.15f;
            Matrix4f m = WORK.set(env.view()).translate((float) x, (float) (y + 0.1 + i * 0.03), (float) z).rotateX(90.0f * DEG).scale(w * k, w * k, 1.0f);
            unitQuad(b, m, s.color1, s.alpha * (i == 0 ? 1f : 0.7f), i == 1);
        }
        finish();
        return true;
    }

    // ---- Crystal Prism: a spinning octahedron over the head, glass inside and a neon halo outside ----------

    private static void octaFace(BufferBuilder b, Matrix4f m, int f, int rgb, float k, float a, boolean uv) {
        float r = ((rgb >> 16) & 0xFF) / 255f * k, g = ((rgb >> 8) & 0xFF) / 255f * k, bl = (rgb & 0xFF) / 255f * k;
        int[] face = OCTA_FACES[f];
        for (int c = 0; c < 4; c++) {
            float[] v = OCTA[c == 3 ? face[2] : face[c]];
            b.vertex(m, v[0], v[1], v[2]);
            if (uv) {
                b.texture(TRI_UV[c][0], TRI_UV[c][1]);
            }
            b.color(r, g, bl, a).next();
        }
    }

    private static boolean crystal(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram glow = Shaders.targetProgram("neuro_crystal", s.speed, s.color2, time);
        if (glow == null) {
            return false;
        }
        float k = 0.4f * sizeScale(s);
        Matrix4f m = WORK.set(env.view()).translate((float) x, (float) (y + t.getHeight() + 0.7 + Math.sin(time * 0.1) * 0.2), (float) z)
                .rotateY(time * 5.0f * DEG).rotateX(45.0f * DEG).scale(k, k, k);

        // The body: faces add up so it reads as glass; it writes depth so its own faces sort correctly.
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int f = 0; f < OCTA_FACES.length; f++) {
            octaFace(b, m, f, blend(s.color1, s.color2, f / 7f), f < 4 ? 1f : 0.65f, 0.55f * s.alpha, false);
        }
        Tessellator.getInstance().draw();

        // The halo: a little larger, on top, without writing depth.
        RenderSystem.depthMask(false);
        HALO.set(m).scale(1.1f, 1.1f, 1.1f);
        b = begin(glow);
        for (int f = 0; f < OCTA_FACES.length; f++) {
            octaFace(b, HALO, f, s.color1, 1f, s.alpha, true);
        }
        finish();
        return true;
    }

    private static int blend(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    // ---- Lightning: three jittering bolts in a tall billboard around the target -----------------------------

    private static boolean lightning(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = Shaders.targetProgram("neuro_lightning", s.speed, s.color2, time);
        if (p == null) {
            return false;
        }
        BufferBuilder b = begin(p);
        float k = sizeScale(s);
        float w = Math.max(t.getWidth(), 0.6f) * 2.0f * k;
        float h = t.getHeight() * 0.75f * k + 0.3f;
        Matrix4f m = billboard(env, x, y + t.getHeight() * 0.5, z).scale(w, h, w);
        unitQuad(b, m, s.color1, s.alpha, false);
        finish();
        return true;
    }
}
