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
        /** Constant acceleration (blocks per second squared); gravity is separate. */
        public float ax, ay, az;
        /** Turns the particle around the vertical axis through (cx, cz), radians per second. */
        public float swirl;
        public double cx, cz;
        /** Pulls toward (tx, ty, tz) with this strength (falls off with the square of the distance). */
        public float attract;
        public double tx, ty, tz;
        /** The particle stops at this height (and bounces by {@code bounce}, 0 = lies still). */
        public double floorY = -1e9;
        public float bounce;
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
        if (particles.size() < com.example.neurocanvas.module.NoLimited.cap(cap)) {
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
            if (p.life < 0) {
                continue;
            }
            if (p.life >= p.max) {
                particles.remove(i);
                continue;
            }
            p.vx += p.ax * dt;
            p.vy += p.ay * dt;
            p.vz += p.az * dt;
            if (p.attract != 0f) {
                double dx = p.tx - p.x, dy = p.ty - p.y, dz = p.tz - p.z;
                double d2 = Math.max(0.04, dx * dx + dy * dy + dz * dz);
                double k = p.attract / d2 / Math.sqrt(d2) * dt;
                p.vx += dx * k;
                p.vy += dy * k;
                p.vz += dz * k;
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
            if (p.swirl != 0f) {
                double a = p.swirl * dt;
                double dx = p.x - p.cx, dz = p.z - p.cz;
                double c = Math.cos(a), sn = Math.sin(a);
                p.x = p.cx + dx * c - dz * sn;
                p.z = p.cz + dx * sn + dz * c;
            }
            if (p.y < p.floorY) {
                p.y = p.floorY;
                p.vy = -p.vy * p.bounce;
                p.vx *= 0.6;
                p.vz *= 0.6;
                if (p.bounce <= 0f) {
                    p.vy = 0;
                    p.spin = 0;
                }
            }
        }
    }

    /** Index of the matching shape in neuro_particle.fsh. */
    private static int shaderShape(Shape s) {
        return switch (s) {
            case HEART -> 6;
            case SQUARE -> 5;
            case CIRCLE -> 0;
            case STAR -> 1;
            case PIXEL -> 3;
            case RING -> 4;
        };
    }

    public void render(RenderEnv env) {
        if (particles.isEmpty()) {
            return;
        }
        net.minecraft.client.gl.ShaderProgram program = Shaders.get("neuro_particle");
        if (program != null && com.example.neurocanvas.module.Interface.shaderVisuals()) {
            renderShader(env, program);
        } else {
            renderPolygons(env);
        }
    }

    private void renderShader(RenderEnv env, net.minecraft.client.gl.ShaderProgram program) {
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
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> program);
        Shaders.applyBrightness(program);
        net.minecraft.client.gl.GlUniform shapeUniform = program.getUniform("Shape");
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        for (int shape = 0; shape < 8; shape++) {
            boolean any = false;
            for (P p : particles) {
                if (shaderShape(p.shape) == shape) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                continue;
            }
            if (shapeUniform != null) {
                shapeUniform.set((float) shape);
            }
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (P p : particles) {
                if (shaderShape(p.shape) != shape || p.life < 0) {
                    continue;
                }
                float f = p.life / p.max;
                int argb = mix(p.c1, p.c2, f);
                float a = p.alpha * (1f - f * f) * ((argb >>> 24) / 255f);
                float r = ((argb >> 16) & 0xFF) / 255f;
                float g = ((argb >> 8) & 0xFF) / 255f;
                float bl = (argb & 0xFF) / 255f;
                float size = p.size * (1f + p.grow * f) * (p.shape == Shape.RING ? 1f : 1.25f);
                float rot = p.shape == Shape.PIXEL ? 0f : p.rot;
                float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
                for (int k = 0; k < 4; k++) {
                    float cu = k == 0 || k == 3 ? -1f : 1f;
                    float cv = k < 2 ? -1f : 1f;
                    b.vertex(m, (float) p.x + (rx * cu + ux * cv) * size, (float) p.y + (uy * cv) * size,
                            (float) p.z + (rz * cu + uz * cv) * size)
                            .texture(cu * cr - cv * sr, cu * sr + cv * cr).color(r, g, bl, a).next();
                }
            }
            tess.draw();
        }
        Shaders.finish();
    }

    private void renderPolygons(RenderEnv env) {
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
            if (p.life < 0) {
                continue;
            }
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
