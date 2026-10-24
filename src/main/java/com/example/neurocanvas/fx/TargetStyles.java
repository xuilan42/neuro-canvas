package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** The looks of TargetESP: every style is drawn around the target from the time and a few settings, no state is kept. */
public final class TargetStyles {
    public static final String[] NAMES = {
        "Ring", "Ghost", "Points", "Rhomb", "Crystals", "Helix", "Pulse", "Spiral", "Halo", "Pyramid", "Marker",
        "Neon Marker", "Neon Ring", "Phantom", "Crystal Prism", "Lightning"};

    private static final int GLOW = 0, DOT = 3, RING = 4, DIAMOND = 5;

    private TargetStyles() { }

    /**
     * @param around true: the effect is drawn with the depth test on, so the part that is behind the target's body is hidden by it
     *               (and walls hide it too): particles really fly around the target. false: drawn over everything the camera can see.
     */
    public static void draw(int style, boolean around, RenderEnv env, EffectSpec s, Vec3d p, float h) {
        // One state for every style: additive light that does not write depth, so overlapping glow never darkens what is behind it.
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        if (around) {
            RenderSystem.enableDepthTest();
        } else {
            Depth.state(Depth.over(env.mc(), p.add(0, h * 0.5, 0)));
        }
        try {
            drawStyle(style, env, s, p, h);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void drawStyle(int style, RenderEnv env, EffectSpec s, Vec3d p, float h) {
        switch (style) {
            case 1 -> ghost(env, s, p, h);
            case 2 -> points(env, s, p, h);
            case 3 -> rhomb(env, s, p, h);
            case 4 -> crystals(env, s, p, h);
            case 5 -> helix(env, s, p, h);
            case 6 -> pulse(env, s, p, h);
            case 7 -> spiral(env, s, p, h);
            case 8 -> halo(env, s, p, h);
            case 9 -> pyramid(env, s, p, h);
            case 10 -> marker(env, s, p, h);
            case 11, 12, 13, 14, 15 -> {
                if (!PremiumTargets.draw(style - 11, env, s)) {
                    EffectDraw.targetRing(env, s, p, h);
                }
            }
            default -> EffectDraw.targetRing(env, s, p, h);
        }
    }

    private static int argb(float[] c) {
        return (Math.round(Math.max(0f, Math.min(1f, c[3])) * 255) << 24) | (Math.round(c[0] * 255) << 16)
                | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
    }

    private static int color(EffectSpec s, float t, float alpha) {
        return argb(EffectDraw.blend(s, Math.max(0f, Math.min(1f, t)), alpha));
    }

    // ---- camera-facing sprites drawn with the particle shader ------------------------------------

    private static final class Sprites {
        final Matrix4f m;
        BufferBuilder b;
        final Tessellator tess = Tessellator.getInstance();
        final float rx, rz, ux, uy, uz;

        Sprites(RenderEnv env) {
            m = env.m();
            double yaw = Math.toRadians(env.camYaw());
            double pitch = Math.toRadians(env.camPitch());
            float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
            float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
            float fx = -sy * cp, fy = -sp, fz = cy * cp;
            rx = cy;
            rz = sy;
            ux = fy * sy;
            uy = fz * cy - fx * sy;
            uz = -fy * cy;
        }

        void start(ShaderProgram program, int shape) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> program);
            Shaders.applyBrightness(program);
            GlUniform u = program.getUniform("Shape");
            if (u != null) {
                u.set((float) shape);
            }
            b = tess.getBuffer();
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        }

        void quad(double x, double y, double z, float size, float rot, int argb) {
            float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, bl = (argb & 0xFF) / 255f;
            float a = (argb >>> 24) / 255f;
            float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
            for (int k = 0; k < 4; k++) {
                float cu = k == 0 || k == 3 ? -1f : 1f;
                float cv = k < 2 ? -1f : 1f;
                b.vertex(m, (float) x + (rx * cu + ux * cv) * size, (float) y + uy * cv * size, (float) z + (rz * cu + uz * cv) * size)
                        .texture(cu * cr - cv * sr, cu * sr + cv * cr).color(r, g, bl, a).next();
            }
        }

        void end() {
            tess.draw();
            Shaders.finish();
        }
    }

    /** Null when the shader is not available: the caller then draws the plain ring instead. */
    private static Sprites sprites(RenderEnv env, int shape, boolean additive) {
        if (!Interface_shaderVisuals()) {
            return null;
        }
        ShaderProgram program = Shaders.get(additive ? "neuro_particle_add" : "neuro_particle");
        if (program == null) {
            return null;
        }
        Sprites sp = new Sprites(env);
        sp.start(program, shape);
        return sp;
    }

    private static boolean Interface_shaderVisuals() {
        return com.example.neurocanvas.module.Interface.shaderVisuals();
    }

    // ---- styles -----------------------------------------------------------------------------------

    private static Vec3d orb(EffectSpec s, Vec3d p, float h, int i, int n, double t) {
        double a = i * Math.PI * 2 / n + t * s.speed * 1.6 * (i % 2 == 0 ? 1 : -1);
        double r = s.size * 0.6 + 0.12 * Math.sin(t * 2 + i);
        double y = p.y + h * (0.5 + 0.42 * Math.sin(t * s.speed * 1.2 + i * 2.1));
        return new Vec3d(p.x + Math.cos(a) * r, y, p.z + Math.sin(a) * r);
    }

    private static void ghost(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        Sprites sp = sprites(env, GLOW, true);
        if (sp == null) {
            EffectDraw.targetRing(env, s, p, h);
            return;
        }
        int n = Math.max(2, Math.min(s.count, 12));
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 7; k++) {
                Vec3d q = orb(s, p, h, i, n, env.t() - k * 0.035);
                float f = 1f - k / 7f;
                sp.quad(q.x, q.y, q.z, (0.05f + 0.07f * s.size) * s.thickness * (0.4f + 0.6f * f), 0f,
                        color(s, i / (float) n, s.alpha * f * f));
            }
        }
        sp.end();
    }

    private static void points(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        Sprites sp = sprites(env, DOT, false);
        if (sp == null) {
            EffectDraw.targetRing(env, s, p, h);
            return;
        }
        int n = Math.max(3, Math.min(s.count * 2, 40));
        double t = env.t();
        for (int i = 0; i < n; i++) {
            double a = i * 2.399963 + t * s.speed * 0.9;
            double u = (i + 0.5) / n;
            double yy = p.y + h * (0.1 + 0.85 * u);
            double r = s.size * 0.55 * Math.sqrt(1 - Math.pow(2 * u - 1, 2)) + 0.1;
            sp.quad(p.x + Math.cos(a) * r, yy, p.z + Math.sin(a) * r, (0.025f + 0.025f * s.size) * s.thickness, 0f,
                    color(s, (float) u, s.alpha));
        }
        sp.end();
    }

    private static void rhomb(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        Sprites glow = sprites(env, GLOW, true);
        if (glow == null) {
            EffectDraw.targetRing(env, s, p, h);
            return;
        }
        double t = env.t();
        double y = p.y + h * 0.55 + 0.06 * Math.sin(t * s.speed * 1.5);
        float rot = (float) (Math.sin(t * s.speed * 0.8) * Math.PI);
        float size = (0.28f * s.size + 0.08f) * (0.5f + 0.5f * s.thickness);
        glow.quad(p.x, y, p.z, size * 2.2f, 0f, color(s, 0.5f, s.alpha * 0.45f));
        glow.end();
        Sprites d = sprites(env, DIAMOND, false);
        d.quad(p.x, y, p.z, size, rot, color(s, (float) (0.5 + 0.5 * Math.sin(t * s.speed)), s.alpha));
        d.end();
    }

    private static void crystals(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        int n = Math.max(2, Math.min(s.count, 10));
        double t = env.t();
        Lines lines = Lines.begin(env, 2.5f * s.thickness);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + t * s.speed * 0.9;
            double r = s.size * 0.7;
            double cx = p.x + Math.cos(a) * r;
            double cz = p.z + Math.sin(a) * r;
            double cy = p.y + h * (0.5 + 0.3 * Math.sin(t * s.speed + i * 1.3));
            double spin = t * s.speed * 2 + i;
            double ca = Math.cos(spin), sa = Math.sin(spin);
            double sz = 0.07 + 0.05 * s.size;
            double[][] v = {{sz, 0, 0}, {-sz, 0, 0}, {0, sz * 1.5, 0}, {0, -sz * 1.5, 0}, {0, 0, sz}, {0, 0, -sz}};
            double[][] w = new double[6][];
            for (int k = 0; k < 6; k++) {
                w[k] = new double[] {cx + v[k][0] * ca - v[k][2] * sa, cy + v[k][1], cz + v[k][0] * sa + v[k][2] * ca};
            }
            int col = color(s, i / (float) n, s.alpha);
            int[][] edges = {{0, 2}, {2, 1}, {1, 3}, {3, 0}, {4, 2}, {2, 5}, {5, 3}, {3, 4}, {0, 4}, {4, 1}, {1, 5}, {5, 0}};
            for (int[] e : edges) {
                lines.line(w[e[0]][0], w[e[0]][1], w[e[0]][2], w[e[1]][0], w[e[1]][1], w[e[1]][2], col);
            }
        }
        lines.end();
    }

    private static void helix(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        double t = env.t();
        int steps = 40;
        Lines lines = Lines.begin(env, 2.2f * s.thickness);
        for (int strand = 0; strand < 2; strand++) {
            double px = 0, py = 0, pz = 0;
            for (int k = 0; k <= steps; k++) {
                double u = k / (double) steps;
                double a = u * Math.PI * 6 + t * s.speed * 1.5 + strand * Math.PI;
                double r = s.size * 0.55;
                double x = p.x + Math.cos(a) * r, y = p.y + u * h, z = p.z + Math.sin(a) * r;
                if (k > 0) {
                    float fade = (float) Math.sin(u * Math.PI);
                    lines.line(px, py, pz, x, y, z, color(s, (float) u, s.alpha * (0.15f + 0.85f * fade)));
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        lines.end();
    }

    private static void pulse(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        Sprites sp = sprites(env, RING, true);
        if (sp == null) {
            EffectDraw.targetRing(env, s, p, h);
            return;
        }
        double t = env.t();
        for (int i = 0; i < 3; i++) {
            double f = (t * s.speed * 0.5 + i / 3.0) % 1.0;
            float size = (float) (s.size * (0.25 + 0.7 * f));
            sp.quad(p.x, p.y + h * 0.5, p.z, size, (float) (t * 0.5), color(s, (float) f, s.alpha * (float) (1 - f)));
        }
        sp.end();
    }

    private static void spiral(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        Sprites sp = sprites(env, GLOW, true);
        if (sp == null) {
            EffectDraw.targetRing(env, s, p, h);
            return;
        }
        double t = env.t();
        int n = Math.max(8, Math.min(s.count * 3, 60));
        for (int k = 0; k < n; k++) {
            double u = k / (double) n;
            double a = u * Math.PI * 4 + t * s.speed * 1.4;
            double r = s.size * (0.25 + 0.5 * u);
            double y = p.y + h * 0.5 + (u - 0.5) * h * 0.7 + 0.08 * Math.sin(t * 3 + k);
            sp.quad(p.x + Math.cos(a) * r, y, p.z + Math.sin(a) * r, (0.04f + 0.04f * s.size) * s.thickness,
                    0f, color(s, (float) u, s.alpha * (float) (1 - u * 0.7)));
        }
        sp.end();
    }

    private static void halo(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        double t = env.t();
        double y = p.y + h + 0.22 + 0.04 * Math.sin(t * s.speed * 2);
        float radius = (float) (s.size * 0.32 * (1 + s.pulse * 0.4 * Math.sin(t * s.speed * 3)));
        float width = (0.03f + 0.02f * s.size) * s.thickness;
        float outer = radius + width * 5f;
        FxBatch fx = FxBatch.begin(env, FxBatch.RING, width / outer, radius / outer, s.speed * 2f, s.color2);
        if (fx == null) {
            EffectDraw.targetRing(env, s, p, h);
            return;
        }
        int argb = ((int) (s.alpha * 255) << 24) | (s.color1 & 0xFFFFFF);
        fx.vertex(p.x - outer, y, p.z - outer, -1f, -1f, argb);
        fx.vertex(p.x + outer, y, p.z - outer, 1f, -1f, argb);
        fx.vertex(p.x + outer, y, p.z + outer, 1f, 1f, argb);
        fx.vertex(p.x - outer, y, p.z + outer, -1f, 1f, argb);
        fx.end();
    }

    private static void pyramid(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        double t = env.t();
        double bob = 0.06 * Math.sin(t * s.speed * 2);
        double top = p.y + h + 0.55 + bob;
        double tip = p.y + h + 0.18 + bob;
        double r = 0.12 + 0.1 * s.size;
        double spin = t * s.speed * 1.6;
        Lines lines = Lines.begin(env, 2.6f * s.thickness);
        double[][] base = new double[4][];
        for (int k = 0; k < 4; k++) {
            double a = spin + k * Math.PI / 2;
            base[k] = new double[] {p.x + Math.cos(a) * r, top, p.z + Math.sin(a) * r};
        }
        for (int k = 0; k < 4; k++) {
            double[] a = base[k], b = base[(k + 1) % 4];
            int col = color(s, k / 4f, s.alpha);
            lines.line(a[0], a[1], a[2], b[0], b[1], b[2], col);
            lines.line(a[0], a[1], a[2], p.x, tip, p.z, col);
        }
        lines.end();
    }

    private static void marker(RenderEnv env, EffectSpec s, Vec3d p, float h) {
        // The right/up vectors of the camera lay the brackets in the screen plane.
        Sprites cam = new Sprites(env);
        double rx = cam.rx, rz = cam.rz, ux = cam.ux, uy = cam.uy, uz = cam.uz;
        Lines lines = Lines.begin(env, 2.6f * s.thickness);
        double t = env.t();
        double rot = t * s.speed * 0.9;
        double c = Math.cos(rot), sn = Math.sin(rot);
        double d = s.size * 0.5 + 0.2 + 0.05 * Math.sin(t * s.speed * 3) * s.pulse * 5;
        double cx = p.x, cy = p.y + h * 0.5, cz = p.z;
        for (int q = 0; q < 4; q++) {
            double sx = (q & 1) == 0 ? -1 : 1, sy = (q & 2) == 0 ? -1 : 1;
            double[][] pts = {{sx * d, sy * d * 0.5}, {sx * d, sy * d}, {sx * d * 0.5, sy * d}};
            double[][] w = new double[3][];
            for (int k = 0; k < 3; k++) {
                double a = pts[k][0] * c - pts[k][1] * sn, b = pts[k][0] * sn + pts[k][1] * c;
                w[k] = new double[] {cx + rx * a + ux * b, cy + uy * b, cz + rz * a + uz * b};
            }
            int col = color(s, q / 4f, s.alpha);
            lines.line(w[0][0], w[0][1], w[0][2], w[1][0], w[1][1], w[1][2], col);
            lines.line(w[1][0], w[1][1], w[1][2], w[2][0], w[2][1], w[2][2], col);
        }
        lines.end();
    }
}
