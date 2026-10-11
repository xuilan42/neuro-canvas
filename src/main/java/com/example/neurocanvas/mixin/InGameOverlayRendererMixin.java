package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Comfort;
import com.example.neurocanvas.module.NoRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), require = 0, order = 2000)
    private static void neurocanvas$lowerFire(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        matrices.push();
        if (NeuroCanvasClient.modules() != null) {
            matrices.translate(0.0, -NeuroCanvasClient.modules().get(Comfort.class).fireDrop(), 0.0);
        }
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"), require = 0)
    private static void neurocanvas$restoreFire(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        matrices.pop();
    }

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void neurocanvas$noFire(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(NoRender.class).hidesFire()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void neurocanvas$noWater(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(NoRender.class).hidesWater()) {
            ci.cancel();
        }
    }
}
