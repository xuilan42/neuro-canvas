package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Geometry for every effect type. Callers must set up GL state (blend, shader) first. */
public final class EffectDraw {
    private EffectDraw() { }

    public static Vec3d lerpPos(Entity e, float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, e.prevX, e.getX()),
                MathHelper.lerp(tickDelta, e.prevY, e.getY()),
                MathHelper.lerp(tickDelta, e.prevZ, e.getZ()));
    }

    /** A glowing ring that rises and falls along the entity's body while pulsing. */
    public static void targetRing(RenderEnv env, EffectSpec s, Vec3d p, float height) {
        double t0 = env.t();
        double phase0 = (Math.sin(t0 * s.speed) + 1) / 2;
        double y0 = p.y + 0.05 + phase0 * (height - 0.1);
        float radius0 = (float) (s.size * 0.6 * (1 + s.pulse * Math.sin(t0 * s.speed * 3)));
        float width0 = 0.07f * s.size + 0.03f;
        float outer = radius0 + width0 * 5f;
        FxBatch fx = FxBatch.begin(env, FxBatch.RING, width0 / outer, radius0 / outer, s.speed * 2f, s.color2);
        if (fx != null) {
            flatQuad(fx, p.x, y0, p.z, outer, ((int) (s.alpha * 255) << 24) | (s.color1 & 0xFFFFFF));
            fx.end();
            return;
        }
        targetRingLegacy(env, s, p, height);
    }

    /** A flat square lying on the ground plane that the ring shader draws a ring into. */
    private static void flatQuad(FxBatch fx, double cx, double y, double cz, double half, int argb) {
        fx.vertex(cx - half, y, cz - half, -1f, -1f, argb);
        fx.vertex(cx + half, y, cz - half, 1f, -1f, argb);
        fx.vertex(cx + half, y, cz + half, 1f, 1f, argb);
        fx.vertex(cx - half, y, cz + half, -1f, 1f, argb);
    }

    private static void targetRingLegacy(RenderEnv env, EffectSpec s, Vec3d p, float height) {
        Matrix4f m = env.m();
        double t = env.t();
        double phase = (Math.sin(t * s.speed) + 1) / 2;
        double y = p.y + 0.05 + phase * (height - 0.1);
        float radius = (float) (s.size * 0.6 * (1 + s.pulse * Math.sin(t * s.speed * 3)));
        float width = 0.07f * s.size + 0.03f;
        int segments = 48;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2 / segments;
            float[] c = blend(s, (float) (0.5 + 0.5 * Math.sin(a * 2 + t * s.speed * 2)), s.alpha);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            b.vertex(m, (float) p.x + cos * (radius - width), (float) y, (float) p.z + sin * (radius - width))
                    .color(c[0], c[1], c[2], c[3]).next();
            b.vertex(m, (float) p.x + cos * (radius + width), (float) y, (float) p.z + sin * (radius + width))
                    .color(c[0], c[1], c[2], 0f).next();
        }
        Tessellator.getInstance().draw();
    }

    /** Small glowing sprites circling a point at different heights. */
    public static void orbit(RenderEnv env, EffectSpec s, Vec3d p, float height) {
        Matrix4f m = env.m();
        double t = env.t();
        double yaw = Math.toRadians(env.camYaw());
        float rx = (float) -Math.cos(yaw), rz = (float) -Math.sin(yaw);
        float half = 0.04f + 0.02f * s.size;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < s.count; i++) {
            double f = i / (double) s.count;
            double a = f * Math.PI * 2 + t * s.speed * (i % 2 == 0 ? 1 : -1);
            double radius = s.size * 0.7 + 0.15 * Math.sin(t * 2 + i);
            float x = (float) (p.x + Math.cos(a) * radius);
            float z = (float) (p.z + Math.sin(a) * radius);
            float y = (float) (p.y + height * (0.5 + 0.5 * Math.sin(t * s.speed * 0.7 + i * 1.7)));
            float[] c = blend(s, (float) f, s.alpha);
            b.vertex(m, x - rx * half, y - half, z - rz * half).color(c[0], c[1], c[2], c[3]).next();
            b.vertex(m, x + rx * half, y - half, z + rz * half).color(c[0], c[1], c[2], c[3]).next();
            b.vertex(m, x + rx * half, y + half, z + rz * half).color(c[0], c[1], c[2], c[3]).next();
            b.vertex(m, x - rx * half, y + half, z - rz * half).color(c[0], c[1], c[2], c[3]).next();
        }
        Tessellator.getInstance().draw();
    }

    /** An expanding, fading ring on the ground. progress runs 0..1. */
    public static void ripple(RenderEnv env, EffectSpec s, Vec3d p, float progress) {
        float radius0 = (0.2f + progress * 2.2f) * s.size;
        float width0 = 0.12f * s.size;
        float outer = radius0 + width0 * 5f;
        float[] c0 = blend(s, progress, s.alpha * (1f - progress));
        int rgb = (Math.round(c0[0] * 255) << 16) | (Math.round(c0[1] * 255) << 8) | Math.round(c0[2] * 255);
        FxBatch fx = FxBatch.begin(env, FxBatch.RING, width0 / outer, radius0 / outer, 0f, rgb);
        if (fx != null) {
            flatQuad(fx, p.x, p.y + 0.02, p.z, outer, (Math.round(c0[3] * 255) << 24) | rgb);
            fx.end();
            return;
        }
        rippleLegacy(env, s, p, progress);
    }

    private static void rippleLegacy(RenderEnv env, EffectSpec s, Vec3d p, float progress) {
        Matrix4f m = env.m();
        float radius = (0.2f + progress * 2.2f) * s.size;
        float width = 0.12f * s.size;
        float alpha = s.alpha * (1f - progress);
        int segments = 48;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2 / segments;
            float[] c = blend(s, progress, alpha);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            b.vertex(m, (float) p.x + cos * (radius - width), (float) p.y + 0.02f, (float) p.z + sin * (radius - width))
                    .color(c[0], c[1], c[2], 0f).next();
            b.vertex(m, (float) p.x + cos * radius, (float) p.y + 0.02f, (float) p.z + sin * radius)
                    .color(c[0], c[1], c[2], c[3]).next();
        }
        Tessellator.getInstance().draw();
    }

    /** A ribbon following the recent path of points; oldest end is transparent. */
    public static void trail(RenderEnv env, EffectSpec s, List<Vec3d> points) {
        int count = Math.min(points.size(), s.count);
        if (count >= 2) {
            FxBatch fx = FxBatch.begin(env, FxBatch.RIBBON, 0f, 0f, 0f, 0);
            if (fx != null) {
                double half = 0.25 * s.size + 0.05;
                Vec3d cam = env.mc().gameRenderer.getCamera().getPos();
                int base = points.size() - count;
                Vec3d[] left = new Vec3d[count];
                Vec3d[] right = new Vec3d[count];
                for (int i = 0; i < count; i++) {
                    Vec3d p = points.get(base + i).add(0, 0.12, 0);
                    Vec3d prev = points.get(base + Math.max(i - 1, 0));
                    Vec3d next = points.get(base + Math.min(i + 1, count - 1));
                    Vec3d tangent = next.subtract(prev);
                    Vec3d side = tangent.crossProduct(cam.subtract(p));
                    side = side.lengthSquared() < 1e-9 ? new Vec3d(0, 1, 0) : side.normalize();
                    left[i] = p.add(side.multiply(half));
                    right[i] = p.subtract(side.multiply(half));
                }
                for (int i = 0; i + 1 < count; i++) {
                    float fa = i / (float) (count - 1);
                    float fb = (i + 1) / (float) (count - 1);
                    float[] ca = blend(s, 1f - fa, s.alpha * fa);
                    float[] cb = blend(s, 1f - fb, s.alpha * fb);
                    int colA = (Math.round(ca[3] * 255) << 24) | (Math.round(ca[0] * 255) << 16) | (Math.round(ca[1] * 255) << 8) | Math.round(ca[2] * 255);
                    int colB = (Math.round(cb[3] * 255) << 24) | (Math.round(cb[0] * 255) << 16) | (Math.round(cb[1] * 255) << 8) | Math.round(cb[2] * 255);
                    fx.vertex(left[i].x, left[i].y, left[i].z, 0f, -1f, colA);
                    fx.vertex(right[i].x, right[i].y, right[i].z, 0f, 1f, colA);
                    fx.vertex(right[i + 1].x, right[i + 1].y, right[i + 1].z, 1f, 1f, colB);
                    fx.vertex(left[i + 1].x, left[i + 1].y, left[i + 1].z, 1f, -1f, colB);
                }
                fx.end();
                return;
            }
        }
        trailLegacy(env, s, points);
    }

    private static void trailLegacy(RenderEnv env, EffectSpec s, List<Vec3d> points) {
        int n = Math.min(points.size(), s.count);
        if (n < 2) {
            return;
        }
        Matrix4f m = env.m();
        float h = 0.3f * s.size + 0.1f;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            Vec3d p = points.get(points.size() - n + i);
            float f = i / (float) (n - 1);
            float[] c = blend(s, 1f - f, s.alpha * f);
            b.vertex(m, (float) p.x, (float) p.y + 0.05f, (float) p.z).color(c[0], c[1], c[2], c[3]).next();
            b.vertex(m, (float) p.x, (float) p.y + 0.05f + h, (float) p.z).color(c[0], c[1], c[2], 0f).next();
        }
        Tessellator.getInstance().draw();
    }

    public static float[] blend(EffectSpec s, float t, float alpha) {
        float r1 = ((s.color1 >> 16) & 0xFF) / 255f, g1 = ((s.color1 >> 8) & 0xFF) / 255f, b1 = (s.color1 & 0xFF) / 255f;
        float r2 = ((s.color2 >> 16) & 0xFF) / 255f, g2 = ((s.color2 >> 8) & 0xFF) / 255f, b2 = (s.color2 & 0xFF) / 255f;
        return new float[] {
            MathHelper.lerp(t, r1, r2), MathHelper.lerp(t, g1, g2), MathHelper.lerp(t, b1, b2), alpha
        };
    }
}
