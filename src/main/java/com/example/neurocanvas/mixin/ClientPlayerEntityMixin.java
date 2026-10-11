package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.FreeCam;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    /**
     * While FreeCam flies, the character's movement step is skipped entirely, so nothing can move it:
     * not the keys, not another mod's input, not leftover velocity.
     */
    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$freeze(CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(FreeCam.class).active()) {
            ci.cancel();
        }
    }
}
