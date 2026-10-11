package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.AspectRatio;
import com.example.neurocanvas.module.Comfort;
import com.example.neurocanvas.module.ScreenPulse;
import com.example.neurocanvas.module.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov && NeuroCanvasClient.modules() != null) {
            cir.setReturnValue(NeuroCanvasClient.modules().get(Zoom.class).apply(cir.getReturnValue()));
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(Comfort.class).noBob()) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noHurtTilt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(Comfort.class).noHurtTilt()) {
            ci.cancel();
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noTotem(ItemStack stack, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && stack.isOf(Items.TOTEM_OF_UNDYING)
                && NeuroCanvasClient.modules().get(Comfort.class).noTotem()) {
            ci.cancel();
        }
    }

    /** Shakes the whole world view when ScreenPulse is on and you were just hurt. */
    @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
    private void neurocanvas$shake(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null) {
            ScreenPulse pulse = NeuroCanvasClient.modules().get(ScreenPulse.class);
            if (pulse.enabled) {
                pulse.applyShake(matrices);
            }
        }
    }

    @ModifyArg(method = "getBasicProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;"),
            index = 1, require = 0)
    private float neurocanvas$aspect(float aspect) {
        if (NeuroCanvasClient.modules() == null) {
            return aspect;
        }
        AspectRatio ratio = NeuroCanvasClient.modules().get(AspectRatio.class);
        return ratio.enabled ? ratio.ratio() : aspect;
    }
}
