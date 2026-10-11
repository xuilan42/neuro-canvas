package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;
import net.minecraft.entity.LivingEntity;

public final class TargetEsp extends EffectModule {
    public TargetEsp() {
        super("TargetESP", Category.COMBAT, "Ring on the entity you hit", true, 0.97, 0.08, 0.8, 1.0, 12);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        LivingEntity target = env.state().target;
        if (target != null) {
            EffectDraw.targetRing(env, spec(EffectSpec.Type.TARGET_RING),
                    EffectDraw.lerpPos(target, env.tickDelta()), target.getHeight());
        }
    }
}
