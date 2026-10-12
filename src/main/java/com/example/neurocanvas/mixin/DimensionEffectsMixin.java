package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.CustomSky;
import net.minecraft.client.render.DimensionEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionEffects.class)
public abstract class DimensionEffectsMixin {
    /** Removes the orange sunrise and sunset fan unless CustomSky's "Sunset glow" is on. */
    @Inject(method = "getFogColorOverride", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$noSunset(float skyAngle, float tickDelta, CallbackInfoReturnable<float[]> cir) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        CustomSky sky = NeuroCanvasClient.modules().get(CustomSky.class);
        if (sky.enabled && !sky.sunsetGlow()) {
            cir.setReturnValue(null);
        }
    }
}
