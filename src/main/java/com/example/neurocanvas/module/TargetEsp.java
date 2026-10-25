package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;
import com.example.neurocanvas.fx.TargetStyles;
import net.minecraft.entity.LivingEntity;

public final class TargetEsp extends EffectModule {
    private final Setting style = add(Setting.choice("Style", 0, TargetStyles.NAMES));
    private final Setting depth = add(Setting.choice("Depth", 0, "Around the body", "Over everything"));
    private final Setting thickness = add(Setting.number("Thickness", 0.3, 4, 1));
    private final Setting brightness = add(Setting.number("Brightness", 0.3, 3, 1));
    private final Setting ringMotion = add(Setting.choice("Neon ring motion", 1, "At the feet", "Sweeps the body"));

    public TargetEsp() {
        super("TargetESP", Category.VISUALS, "Effect on the entity you hit: ring, ghost orbs, crystals, halo and more", true, 0.97, 0.08, 0.8, 1.0, 12);
        settings.remove(style);
        settings.remove(depth);
        settings.remove(thickness);
        settings.remove(brightness);
        settings.add(0, style);
        settings.add(1, depth);
        settings.add(2, thickness);
        settings.add(3, brightness);
    }

    /** The Phantom style is drawn by a render layer while the entity itself is drawn, not in the world pass. */
    public boolean phantomOn() {
        return style.index() == 12;
    }

    /** Reused every frame: the render loop allocates nothing. */
    private final EffectSpec current = new EffectSpec();

    public EffectSpec phantomSpec() {
        fill(current, EffectSpec.Type.TARGET_RING);
        current.thickness = thickness.f();
        current.variant = ringMotion.index();
        return current;
    }

    @Override
    public void renderWorld(RenderEnv env) {
        LivingEntity target = env.state().target;
        if (target != null) {
            EffectSpec spec = phantomSpec();
            com.example.neurocanvas.fx.Shaders.setScale(brightness.f());
            try {
                TargetStyles.draw(style.index(), depth.index() == 0, env, spec, EffectDraw.lerpPos(target, env.tickDelta()), target.getHeight());
            } finally {
                com.example.neurocanvas.fx.Shaders.setScale(1f);
            }
        }
    }
}
