package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;
import com.example.neurocanvas.fx.TargetStyles;
import net.minecraft.entity.LivingEntity;

public final class TargetEsp extends EffectModule {
    private final Setting style = add(Setting.choice("Style", 0, TargetStyles.NAMES));

    public TargetEsp() {
        super("TargetESP", Category.COMBAT, "Effect on the entity you hit: ring, ghost orbs, crystals, halo and more", true, 0.97, 0.08, 0.8, 1.0, 12);
        settings.remove(style);
        settings.add(0, style);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        LivingEntity target = env.state().target;
        if (target != null) {
            TargetStyles.draw(style.index(), env, spec(EffectSpec.Type.TARGET_RING),
                    EffectDraw.lerpPos(target, env.tickDelta()), target.getHeight());
        }
    }
}
