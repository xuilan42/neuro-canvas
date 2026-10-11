package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;

public final class Trails extends EffectModule {
    public Trails() {
        super("Trails", Category.VISUALS, "Ribbon behind you (Count = length)", false, 0.8, 0.5, 1.0, 1.0, 30);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        EffectDraw.trail(env, spec(EffectSpec.Type.TARGET_RING), env.state().trail);
    }
}
