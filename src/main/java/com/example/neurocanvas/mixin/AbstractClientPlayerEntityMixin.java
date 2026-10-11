package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Comfort;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    /** The multiplier that widens the view while sprinting, with speed effects or when drawing a bow. */
    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$steadyFov(CallbackInfoReturnable<Float> cir) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(Comfort.class).noFov()) {
            cir.setReturnValue(1.0f);
        }
    }
}
