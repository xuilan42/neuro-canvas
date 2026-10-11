package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.FreeCam;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    private static boolean neurocanvas$flying() {
        return NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(FreeCam.class).active();
    }

    /** ESP "Outline" mode: ask the game to give the chosen entities its glow outline. */
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$espOutline(net.minecraft.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (NeuroCanvasClient.modules() != null
                && NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Esp.class).glowColor(entity) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (neurocanvas$flying()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noUse(CallbackInfo ci) {
        if (neurocanvas$flying()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noBreak(boolean breaking, CallbackInfo ci) {
        if (neurocanvas$flying()) {
            ci.cancel();
        }
    }
}
