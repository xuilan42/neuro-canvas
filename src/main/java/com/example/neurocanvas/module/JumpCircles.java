package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;

public final class JumpCircles extends EffectModule {
    public JumpCircles() {
        super("JumpCircles", Category.VISUALS, "Ripple on the ground when you jump", false, 0.55, 0.75, 1.0, 1.0, 12);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        EffectSpec spec = spec(EffectSpec.Type.JUMP_RIPPLE);
        long now = System.currentTimeMillis();
        for (WorldState.Ripple r : env.state().ripples) {
            EffectDraw.ripple(env, spec, r.pos(), (now - r.start()) / (float) WorldState.RIPPLE_LIFETIME_MS);
        }
    }
}
