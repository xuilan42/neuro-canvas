package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.FxParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Random;
import net.minecraft.entity.LivingEntity;

/** A burst of shapes flying out of the entity you hit. */
public final class HitParticles extends Module {
    private static final FxParticles.Shape[] SHAPES = {
        FxParticles.Shape.HEART, FxParticles.Shape.SQUARE, FxParticles.Shape.CIRCLE,
        FxParticles.Shape.STAR, FxParticles.Shape.PIXEL};

    private final Setting shape = add(Setting.choice("Shape", 0, "Heart", "Square", "Circle", "Star", "Pixel", "Mixed"));
    private final Setting count = add(Setting.number("Count", 1, 60, 14));
    private final Setting size = add(Setting.number("Size", 0.02, 0.5, 0.12));
    private final Setting speed = add(Setting.number("Speed", 0.2, 6, 2));
    private final Setting lifetime = add(Setting.number("Lifetime (s)", 0.2, 3, 0.9));
    private final Setting gravity = add(Setting.number("Gravity", -1, 2, 0.4));
    private final Setting spin = add(Setting.bool("Spin", true));
    private final Setting color1 = add(Setting.color("Color 1", 0.97));
    private final Setting color2 = add(Setting.color("Color 2", 0.08));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.9));

    private final FxParticles fx = new FxParticles(800);
    private final Random random = new Random();
    private double lastT;

    public HitParticles() {
        super("HitParticles", Category.VISUALS, "Hearts, squares, circles, stars or pixels burst out when you hit", false);
        WorldState.addHitListener(this::onHit);
    }

    private void onHit(LivingEntity e) {
        if (!enabled) {
            return;
        }
        double cx = e.getX();
        double cy = e.getY() + e.getHeight() * 0.6;
        double cz = e.getZ();
        for (int i = 0; i < (int) count.value; i++) {
            FxParticles.P p = new FxParticles.P();
            p.x = cx + (random.nextDouble() - 0.5) * 0.4;
            p.y = cy + (random.nextDouble() - 0.5) * 0.4;
            p.z = cz + (random.nextDouble() - 0.5) * 0.4;
            double theta = random.nextDouble() * Math.PI * 2;
            double up = random.nextDouble() * 1.2 - 0.3;
            double flat = Math.sqrt(Math.max(0, 1 - up * up));
            double v = speed.value * (0.5 + random.nextDouble() * 0.7);
            p.vx = Math.cos(theta) * flat * v;
            p.vy = up * v + 0.3;
            p.vz = Math.sin(theta) * flat * v;
            p.max = lifetime.f() * (0.7f + random.nextFloat() * 0.6f);
            p.size = size.f() * (0.7f + random.nextFloat() * 0.6f);
            p.gravity = gravity.f();
            p.drag = 1.2f;
            p.rot = random.nextFloat() * 6.28f;
            p.spin = spin.on() ? (random.nextFloat() - 0.5f) * 8f : 0f;
            p.alpha = alpha.f();
            p.c1 = color1.argb();
            p.c2 = color2.argb();
            p.shape = shape.index() >= SHAPES.length ? SHAPES[random.nextInt(SHAPES.length)] : SHAPES[shape.index()];
            if (p.shape == FxParticles.Shape.PIXEL) {
                p.size *= 0.5f;
            }
            fx.spawn(p);
        }
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
    }
}
