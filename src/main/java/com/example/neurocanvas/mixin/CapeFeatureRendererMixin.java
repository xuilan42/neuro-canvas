package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Wardrobe;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin {
    /** The game's own cape is hidden while a Wardrobe cape is worn. */
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I"
            + "Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$hideCape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                      AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                                      float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(Wardrobe.class).hidesCape(player)) {
            ci.cancel();
        }
    }
}
