package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.FxParticles;
import com.example.neurocanvas.fx.Lines;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;
import net.minecraft.entity.LivingEntity;

/** A flourish where the entity you killed dies: a burst, rising souls or a lightning bolt. */
public final class KillEffects extends Module implements WorldState.CombatListener {
    private static final long BOLT_MS = 500;

    private record Bolt(double[] x, double[] y, double[] z, long start) { }

    private final Setting style = add(Setting.choice("Style", 0, "Burst", "Souls", "Lightning", "All"));
    private final Setting count = add(Setting.number("Count", 4, 80, 30));
    private final Setting size = add(Setting.number("Size", 0.04, 0.5, 0.14));
    private final Setting lifetime = add(Setting.number("Lifetime (s)", 0.3, 3, 1.2));
    private final Setting color1 = add(Setting.color("Color 1", 0.12));
    private final Setting color2 = add(Setting.color("Color 2", 0.0));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.95));

    private final FxParticles fx = new FxParticles(900);
    private final List<Bolt> bolts = new ArrayList<>();
    private final Random random = new Random();
    private double lastT;

    public KillEffects() {
        super("KillEffects", Category.VISUALS, "Effect where the entity you killed dies", false);
        WorldState.addCombatListener(this);
    }

    @Override
    public void onKill(LivingEntity e) {
        if (!enabled) {
            return;
        }
        int s = style.index();
        if (s == 0 || s == 3) {
            burst(e);
        }
        if (s == 1 || s == 3) {
            souls(e);
        }
        if (s == 2 || s == 3) {
            bolt(e);
        }
    }

    private FxParticles.P base(double x, double y, double z) {
        FxParticles.P p = new FxParticles.P();
        p.x = x;
        p.y = y;
        p.z = z;
        p.max = lifetime.f() * (0.7f + random.nextFloat() * 0.6f);
        p.size = size.f() * (0.7f + random.nextFloat() * 0.6f);
        p.alpha = alpha.f();
        p.c1 = color1.argb();
        p.c2 = color2.argb();
        return p;
    }

    private void burst(LivingEntity e) {
        for (int i = 0; i < (int) count.value; i++) {
            FxParticles.P p = base(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
            double theta = random.nextDouble() * Math.PI * 2;
            double up = random.nextDouble() * 1.6 - 0.4;
            double flat = Math.sqrt(Math.max(0, 1 - up * up));
            double v = 2.5 + random.nextDouble() * 3.0;
            p.vx = Math.cos(theta) * flat * v;
            p.vy = up * v + 1.0;
            p.vz = Math.sin(theta) * flat * v;
            p.gravity = 1.4f;
            p.drag = 1.4f;
            p.rot = random.nextFloat() * 6.28f;
            p.spin = (random.nextFloat() - 0.5f) * 10f;
            p.shape = random.nextBoolean() ? FxParticles.Shape.STAR : FxParticles.Shape.SQUARE;
            fx.spawn(p);
        }
    }

    private void souls(LivingEntity e) {
        for (int i = 0; i < (int) count.value; i++) {
            FxParticles.P p = base(e.getX() + (random.nextDouble() - 0.5) * 0.8, e.getY() + random.nextDouble() * e.getHeight(),
                    e.getZ() + (random.nextDouble() - 0.5) * 0.8);
            p.vx = (random.nextDouble() - 0.5) * 0.4;
            p.vy = 0.8 + random.nextDouble() * 1.4;
            p.vz = (random.nextDouble() - 0.5) * 0.4;
            p.gravity = -0.3f;
            p.drag = 0.6f;
            p.shape = random.nextBoolean() ? FxParticles.Shape.CIRCLE : FxParticles.Shape.PIXEL;
            fx.spawn(p);
        }
        FxParticles.P ring = base(e.getX(), e.getY() + 0.05, e.getZ());
        ring.size = 0.4f;
        ring.grow = 4f;
        ring.max = lifetime.f();
        ring.shape = FxParticles.Shape.RING;
        fx.spawn(ring);
    }

    private void bolt(LivingEntity e) {
        int n = 14;
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double jitter = i == n - 1 ? 0 : 0.7 * (1 - t) + 0.15;
            xs[i] = e.getX() + (random.nextDouble() - 0.5) * jitter * 2;
            ys[i] = e.getY() + 14 * (1 - t);
            zs[i] = e.getZ() + (random.nextDouble() - 0.5) * jitter * 2;
        }
        bolts.add(new Bolt(xs, ys, zs, System.currentTimeMillis()));
    }

    @Override
    public void renderWorld(RenderEnv env) {
        float dt = (float) Math.max(0, Math.min(0.1, env.t() - lastT));
        lastT = env.t();
        fx.update(dt);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        fx.render(env);
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();

        long now = System.currentTimeMillis();
        bolts.removeIf(b -> now - b.start > BOLT_MS);
        if (bolts.isEmpty()) {
            return;
        }
        for (float[] pass : new float[][] {{5f, 0.55f}, {1.8f, 1f}}) {
            Lines lines = Lines.begin(env, pass[0]);
            for (Bolt b : bolts) {
                float fade = 1f - (now - b.start) / (float) BOLT_MS;
                float flicker = 0.7f + 0.3f * (float) Math.sin((now - b.start) * 0.08);
                int a = Math.round(255 * fade * flicker * pass[1] * alpha.f());
                int col = pass[0] > 3f ? (color1.argb() & 0xFFFFFF) : 0xFFFFFF;
                for (int i = 0; i + 1 < b.x.length; i++) {
                    lines.line(b.x[i], b.y[i], b.z[i], b.x[i + 1], b.y[i + 1], b.z[i + 1], (a << 24) | col);
                }
            }
            lines.end();
        }
    }
}
