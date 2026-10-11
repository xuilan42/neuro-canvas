package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Fullbright;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleOption.class)
public abstract class SimpleOptionMixin {
    /** Makes the game read an enormous brightness value instead of the saved slider position. */
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$gamma(CallbackInfoReturnable<Object> cir) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null || (Object) this != mc.options.getGamma()) {
            return;
        }
        Fullbright fullbright = NeuroCanvasClient.modules().get(Fullbright.class);
        if (fullbright.enabled) {
            cir.setReturnValue(fullbright.gamma());
        }
    }
}
