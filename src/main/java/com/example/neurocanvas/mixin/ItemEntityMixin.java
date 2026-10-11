package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.ItemPhysics;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    /** The spin angle only feeds the renderer; a constant per-item angle makes each item rest at its own heading. */
    @Inject(method = "getRotation", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$noSpin(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(ItemPhysics.class).stopsSpin()) {
            cir.setReturnValue(((ItemEntity) (Object) this).uniqueOffset);
        }
    }
}
