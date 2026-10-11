package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.SwingAnimation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /** Changes how long the local player's swing animation lasts. Only the client's own animation. */
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$swingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ClientPlayerEntity && NeuroCanvasClient.modules() != null) {
            SwingAnimation swing = NeuroCanvasClient.modules().get(SwingAnimation.class);
            if (swing.enabled) {
                cir.setReturnValue(swing.adjustDuration(cir.getReturnValue()));
            }
        }
    }
}
