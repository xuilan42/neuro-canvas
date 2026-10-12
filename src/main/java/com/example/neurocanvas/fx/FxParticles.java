package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/** Camera-facing particles of several shapes, all drawn in one triangle batch. */
public final class FxParticles {
    public enum Shape { HEART, SQUARE, CIRCLE, STAR, PIXEL, RING }

    public static final class P {
        public double x, y, z;
        public double vx, vy, vz;
        public float life;
        public float max = 1f;
        public float size = 0.1f;
        /** Extra size gained over the lifetime (1 = doubles). */
        public float grow;
        public float rot;
        public float spin;
        public float gravity;
        public float drag;
        public float alpha = 1f;
        public int c1 = 0xFFFFFFFF;
        public int c2 = 0xFFFFFFFF;
        public Shape shape = Shape.CIRCLE;
    }

    private static final Map<Shape, float[]> POLY = new EnumMap<>(Shape.class);

    static {
        POLY.put(Shape.CIRCLE, circle(16));
        POLY.put(Shape.HEART, heart(28));
        POLY.put(Shape.STAR, star());
        float[] square = {-0.8f, -0.8f, 0.8f, -0.8f, 0.8f, 0.8f, -0.8f, 0.8f};
        POLY.put(Shape.SQUARE, square);
        POLY.put(Shape.PIXEL, square);
    }

    private final List<P> particles = new ArrayList<>();
    private final int cap;

    public FxParticles(int cap) {
        this.cap = cap;
    }

    public void spawn(P p) {
        if (particles.size() < cap) {
            particles.add(p);
        }
    }

    public void clear() {
        particles.clear();
    }

    public void update(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            P p = particles.get(i);
            p.life += dt;
            if (p.life >= p.max) {
                particles.remove(i);
                continue;
            }
            p.vy -= p.gravity * dt;
            float keep = Math.max(0f, 1f - p.drag * dt);
            p.vx *= keep;
            p.vy *= keep;
            p.vz *= keep;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.rot += p.spin * dt;
        }
    }

    public void render(RenderEnv env) {
        if (particles.isEmpty()) {
            return;
        }
        double sumX = 0, sumY = 0, sumZ = 0;
        for (P p : particles) {
            sumX += p.x;
            sumY += p.y;
            sumZ += p.z;
        }
        int count = particles.size();
        Depth.state(Depth.over(env.mc(), new net.minecraft.util.math.Vec3d(sumX / count, sumY / count, sumZ / count)));
        Matrix4f m = env.m();
        double yaw = Math.toRadians(env.camYaw());
        double pitch = Math.toRadians(env.camPitch());
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float fx = -sy * cp, fy = -sp, fz = cy * cp;
        float rx = cy, rz = sy;
        float ux = fy * sy, uy = fz * cy - fx * sy, uz = -fy * cy;

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (P p : particles) {
            float f = p.life / p.max;
            int argb = mix(p.c1, p.c2, f);
            float a = p.alpha * (1f - f * f) * ((argb >>> 24) / 255f);
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float bl = (argb & 0xFF) / 255f;
            float size = p.size * (1f + p.grow * f);
            float rot = p.shape == Shape.PIXEL ? 0f : p.rot;
            float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);

            if (p.shape == Shape.RING) {
                int segs = 20;
                for (int i = 0; i < segs; i++) {
                    double a0 = Math.PI * 2 * i / segs;
                    double a1 = Math.PI * 2 * (i + 1) / segs;
                    float[] q = {
                        (float) Math.cos(a0) * 0.8f, (float) Math.sin(a0) * 0.8f,
                        (float) Math.cos(a0), (float) Math.sin(a0),
                        (float) Math.cos(a1), (float) Math.sin(a1),
                        (float) Math.cos(a1) * 0.8f, (float) Math.sin(a1) * 0.8f};
                    int[] order = {0, 1, 2, 0, 2, 3};
                    for (int k : order) {
                        vertex(b, m, p, q[k * 2], q[k * 2 + 1], size, cr, sr, rx, rz, ux, uy, uz, r, g, bl, a);
                    }
                }
                continue;
            }
            float[] poly = POLY.get(p.shape);
            int n = poly.length / 2;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                vertex(b, m, p, 0f, 0f, size, cr, sr, rx, rz, ux, uy, uz, r, g, bl, a);
                vertex(b, m, p, poly[i * 2], poly[i * 2 + 1], size, cr, sr, rx, rz, ux, uy, uz, r, g, bl, a);
                vertex(b, m, p, poly[j * 2], poly[j * 2 + 1], size, cr, sr, rx, rz, ux, uy, uz, r, g, bl, a);
            }
        }
        Tessellator.getInstance().draw();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
    }

    private static void vertex(BufferBuilder b, Matrix4f m, P p, float lx, float ly, float size, float cr, float sr,
                               float rx, float rz, float ux, float uy, float uz, float r, float g, float bl, float a) {
        float x = (lx * cr - ly * sr) * size;
        float y = (lx * sr + ly * cr) * size;
        b.vertex(m, (float) p.x + rx * x + ux * y, (float) p.y + uy * y, (float) p.z + rz * x + uz * y)
                .color(r, g, bl, a).next();
    }

    private static int mix(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int aa = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    private static float[] circle(int n) {
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            out[i * 2] = (float) Math.cos(a);
            out[i * 2 + 1] = (float) Math.sin(a);
        }
        return out;
    }

    private static float[] heart(int n) {
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double t = Math.PI * 2 * i / n;
            double x = Math.pow(Math.sin(t), 3);
            double y = (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 16.0;
            out[i * 2] = (float) x;
            out[i * 2 + 1] = (float) (y + 0.1);
        }
        return out;
    }

    private static float[] star() {
        float[] out = new float[20];
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + Math.PI * 2 * i / 10;
            float r = i % 2 == 0 ? 1f : 0.45f;
            out[i * 2] = (float) Math.cos(a) * r;
            out[i * 2 + 1] = (float) Math.sin(a) * r;
        }
        return out;
    }
}
