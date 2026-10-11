package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Zoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    /** While zoom is held in the game (no menu open), the wheel adjusts the zoom instead of changing the hotbar slot. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$zoomWheel(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && MinecraftClient.getInstance().currentScreen == null
                && NeuroCanvasClient.modules().get(Zoom.class).scroll(vertical)) {
            ci.cancel();
        }
    }
}
