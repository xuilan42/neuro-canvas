package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;
import com.example.neurocanvas.fx.TargetStyles;
import net.minecraft.entity.LivingEntity;

public final class TargetEsp extends EffectModule {
    private final Setting style = add(Setting.choice("Style", 0, TargetStyles.NAMES));
    private final Setting thickness = add(Setting.number("Thickness", 0.3, 4, 1));
    private final Setting brightness = add(Setting.number("Brightness", 0.3, 3, 1));

    public TargetEsp() {
        super("TargetESP", Category.COMBAT, "Effect on the entity you hit: ring, ghost orbs, crystals, halo and more", true, 0.97, 0.08, 0.8, 1.0, 12);
        settings.remove(style);
        settings.remove(thickness);
        settings.remove(brightness);
        settings.add(0, style);
        settings.add(1, thickness);
        settings.add(2, brightness);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        LivingEntity target = env.state().target;
        if (target != null) {
            EffectSpec spec = spec(EffectSpec.Type.TARGET_RING);
            spec.thickness = thickness.f();
            com.example.neurocanvas.fx.Shaders.setScale(brightness.f());
            try {
                TargetStyles.draw(style.index(), env, spec, EffectDraw.lerpPos(target, env.tickDelta()), target.getHeight());
            } finally {
                com.example.neurocanvas.fx.Shaders.setScale(1f);
            }
        }
    }
}
