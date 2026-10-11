package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.TimeChanger;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    /** The sky, sun, moon and light level all read the time from here. */
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$time(CallbackInfoReturnable<Long> cir) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        TimeChanger changer = NeuroCanvasClient.modules().get(TimeChanger.class);
        if (changer.enabled) {
            cir.setReturnValue(changer.currentTime(cir.getReturnValue()));
        }
    }
}
