package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;

public final class Particles extends EffectModule {
    public Particles() {
        super("Particles", Category.VISUALS, "Sparks flying around you", false, 0.12, 0.6, 0.9, 1.0, 14);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        if (env.mc().player == null) {
            return;
        }
        EffectDraw.orbit(env, spec(EffectSpec.Type.ORBIT_PARTICLES),
                EffectDraw.lerpPos(env.mc().player, env.tickDelta()), env.mc().player.getHeight());
    }
}
