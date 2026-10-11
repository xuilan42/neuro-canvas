package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.FxParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Random;
import net.minecraft.entity.LivingEntity;

/** Soft rings that float up from the entity you hit, growing as they fade. */
public final class HitBubbles extends Module {
    private final Setting count = add(Setting.number("Count", 1, 20, 6));
    private final Setting size = add(Setting.number("Size", 0.05, 1, 0.22));
    private final Setting grow = add(Setting.number("Growth", 0, 3, 1.4));
    private final Setting rise = add(Setting.number("Rise speed", 0, 3, 0.8));
    private final Setting lifetime = add(Setting.number("Lifetime (s)", 0.2, 3, 1.1));
    private final Setting color1 = add(Setting.color("Color 1", 0.52));
    private final Setting color2 = add(Setting.color("Color 2", 0.75));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.85));

    private final FxParticles fx = new FxParticles(300);
    private final Random random = new Random();
    private double lastT;

    public HitBubbles() {
        super("HitBubbles", Category.VISUALS, "Bubbles float up from the entity you hit", false);
        WorldState.addHitListener(this::onHit);
    }

    private void onHit(LivingEntity e) {
        if (!enabled) {
            return;
        }
        for (int i = 0; i < (int) count.value; i++) {
            FxParticles.P p = new FxParticles.P();
            p.x = e.getX() + (random.nextDouble() - 0.5) * e.getWidth() * 1.4;
            p.y = e.getY() + random.nextDouble() * e.getHeight();
            p.z = e.getZ() + (random.nextDouble() - 0.5) * e.getWidth() * 1.4;
            p.vx = (random.nextDouble() - 0.5) * 0.3;
            p.vy = rise.value * (0.6 + random.nextDouble() * 0.6);
            p.vz = (random.nextDouble() - 0.5) * 0.3;
            p.max = lifetime.f() * (0.7f + random.nextFloat() * 0.6f);
            p.size = size.f() * (0.6f + random.nextFloat() * 0.8f);
            p.grow = grow.f();
            p.drag = 0.8f;
            p.alpha = alpha.f();
            p.c1 = color1.argb();
            p.c2 = color2.argb();
            p.shape = FxParticles.Shape.RING;
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
