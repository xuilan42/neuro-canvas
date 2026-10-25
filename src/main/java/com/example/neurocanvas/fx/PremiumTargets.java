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

    // ---- Neon Ring: a broken ring that sweeps up and down the body leaving a fading wake, a second one at the feet, light on the ground ----

    private static final int WAKE = 5;

    private static boolean ring(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = Shaders.targetProgram("neuro_ring", s.speed, s.color2, time);
        if (p == null) {
            return false;
        }
        float w = Math.max(t.getWidth(), 0.4f) * sizeScale(s);
        glowPool(env, s, x, y, z, w * 2.4f, 0.4f, time);
        p = Shaders.targetProgram("neuro_ring", s.speed, s.color2, time);
        BufferBuilder b = begin(p);
        // The one at the feet, turning the other way.
        Matrix4f m = WORK.set(env.view()).translate((float) x, (float) (y + 0.06), (float) z).rotateX(90.0f * DEG).scale(w * 1.15f, w * 1.15f, 1.0f);
        unitQuad(b, m, s.color1, s.alpha * 0.7f, true);
        int n = s.variant == 1 ? WAKE : 1;
        double reach = Math.max(0.2, t.getHeight() * 0.92);
        for (int k = 0; k < n; k++) {
            double phase = s.variant == 1 ? 0.5 + 0.5 * Math.sin((time - k * 1.7f) * 0.09f * s.speed) : 0.0;
            float fade = (float) Math.pow(1.0 - k / (double) n, 1.7);
            m = WORK.set(env.view()).translate((float) x, (float) (y + 0.1 + phase * reach), (float) z).rotateX(90.0f * DEG).scale(w * 1.5f, w * 1.5f, 1.0f);
            unitQuad(b, m, s.color1, s.alpha * fade, false);
        }
        finish();
        return true;
    }

    /** A soft round light lying on the ground (or, with {@code upright}, facing the camera): the light the effect throws around itself. */
    private static void glowPool(RenderEnv env, EffectSpec s, double x, double y, double z, float size, float alpha, float time) {
        ShaderProgram g = Shaders.targetProgram("neuro_glow", s.speed, s.color2, time);
        if (g == null) {
            return;
        }
        BufferBuilder b = begin(g);
        Matrix4f m = WORK.set(env.view()).translate((float) x, (float) (y + 0.04), (float) z).rotateX(90.0f * DEG).scale(size, size, 1.0f);
        unitQuad(b, m, s.color1, s.alpha * alpha, false);
        finish();
    }

    private static void glowBillboard(RenderEnv env, EffectSpec s, double x, double y, double z, float size, float alpha, float time) {
        ShaderProgram g = Shaders.targetProgram("neuro_glow", s.speed, s.color2, time);
        if (g == null) {
            return;
        }
        BufferBuilder b = begin(g);
        Matrix4f m = billboard(env, x, y, z).scale(size, size, size);
        unitQuad(b, m, s.color1, s.alpha * alpha, false);
        finish();
    }

    // ---- Orbiting Crystals: small spinning crystals circling the target at a fixed height ------------------------------------

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

    /** Rows of crystals at fixed heights (a fraction of the body height); neighbouring rows turn the opposite way. */
    private static final float[] ROW_HEIGHT = {0.1f, 0.42f, 0.75f, 1.08f};

    private static int perRow(EffectSpec s) {
        return Math.max(3, Math.min(s.count / 4, com.example.neurocanvas.module.NoLimited.cap(6)));
    }

    /** The matrix that places and turns crystal i (the result is in WORK). Heights stay fixed; only the angle moves. */
    private static Matrix4f crystalMatrix(RenderEnv env, LivingEntity t, int i, int perRow, double x, double y, double z, float time, EffectSpec s, float k) {
        int row = i / perRow;
        int j = i % perRow;
        double dir = (row & 1) == 0 ? 1.0 : -1.0;
        double radius = (Math.max(t.getWidth(), 0.4f) * 0.5 + 0.5 - 0.12 * Math.abs(row - 1.5)) * sizeScale(s);
        double a = j * Math.PI * 2 / perRow + row * 0.8 + dir * time * 0.045 * s.speed;
        double cy = y + t.getHeight() * ROW_HEIGHT[row];
        return WORK.set(env.view()).translate((float) (x + Math.cos(a) * radius), (float) cy, (float) (z + Math.sin(a) * radius))
                .rotateY((time * 7.0f + i * 57.0f) * DEG).rotateX(20.0f * DEG).scale(k, k, k);
    }

    private static boolean crystal(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram halo = Shaders.targetProgram("neuro_crystal", s.speed, s.color2, time);
        if (halo == null) {
            return false;
        }
        int per = perRow(s);
        int total = per * ROW_HEIGHT.length;
        float k = 0.09f * sizeScale(s);

        ShaderProgram g = Shaders.targetProgram("neuro_glow", s.speed, s.color2, time);
        if (g != null) {
            BufferBuilder b = begin(g);
            for (int i = 0; i < total; i++) {
                crystalMatrix(env, t, i, per, x, y, z, time, s, 1f);
                float gx = WORK.m30(), gy = WORK.m31(), gz = WORK.m32();
                Matrix4f m = billboard(env, 0, 0, 0).setTranslation(gx, gy, gz).scale(k * 3.4f, k * 3.4f, k * 3.4f);
                unitQuad(b, m, blend(s.color1, s.color2, (i % per) / (float) per), s.alpha * 0.45f, false);
            }
            finish();
        }

        // Bodies: faces add up so each reads as glass; they write depth so their own faces sort correctly.
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < total; i++) {
            Matrix4f m = crystalMatrix(env, t, i, per, x, y, z, time, s, k);
            int col = blend(s.color1, s.color2, (i % per) / (float) per);
            for (int f = 0; f < OCTA_FACES.length; f++) {
                octaFace(b, m, f, blend(col, s.color2, f / 14f), f < 4 ? 1f : 0.65f, 0.6f * s.alpha, false);
            }
        }
        Tessellator.getInstance().draw();

        // Halos: a little larger, on top, without writing depth.
        RenderSystem.depthMask(false);
        b = begin(halo);
        for (int i = 0; i < total; i++) {
            HALO.set(crystalMatrix(env, t, i, per, x, y, z, time, s, k * 1.12f));
            int col = blend(s.color1, s.color2, (i % per) / (float) per);
            for (int f = 0; f < OCTA_FACES.length; f++) {
                octaFace(b, HALO, f, col, 1f, s.alpha, true);
            }
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

    // ---- Lightning: upright bolts standing around the target, each with its own seed so they flash at different moments ----------

    private static boolean lightning(RenderEnv env, EffectSpec s, LivingEntity t, double x, double y, double z, float time) {
        ShaderProgram p = Shaders.targetProgram("neuro_lightning", s.speed, s.color2, time);
        if (p == null) {
            return false;
        }
        GlUniform seed = p.getUniform("Seed");
        int n = Math.max(8, Math.min(s.count, com.example.neurocanvas.module.NoLimited.cap(16)));
        float k = sizeScale(s);
        double r = (Math.max(t.getWidth(), 0.4f) * 0.5 + 0.55) * k;
        float halfH = Math.max(t.getHeight(), 1.0f) * 0.62f * k;
        float halfW = (float) (Math.PI * r / n) * 1.25f;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + time * 0.015 * s.speed;
            double bx = x + Math.cos(a) * r;
            double bz = z + Math.sin(a) * r;
            if (seed != null) {
                seed.set(i * 1.618f);
            }
            BufferBuilder b = begin(p);
            // Upright: only turned around the vertical axis, toward the camera.
            Matrix4f m = WORK.set(env.view()).translate((float) bx, (float) (y + t.getHeight() * 0.5), (float) bz)
                    .rotateY((float) Math.atan2(-bx, -bz)).scale(halfW, halfH, halfW);
            unitQuad(b, m, s.color1, s.alpha, false);
            finish();
        }
        return true;
    }
}
