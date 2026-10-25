package com.example.neurocanvas.mixin;

import com.example.neurocanvas.fx.GhostLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The target of TargetESP's Phantom style is drawn with our hologram layer instead of its normal one. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getRenderLayer", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$phantom(LivingEntity entity, boolean showBody, boolean translucent, boolean showOutline,
                                     CallbackInfoReturnable<RenderLayer> cir) {
        if (cir.getReturnValue() == null) {
            return;
        }
        try {
            net.minecraft.util.Identifier texture = ((LivingEntityRenderer<LivingEntity, ?>) (Object) this).getTexture(entity);
            RenderLayer layer = com.example.neurocanvas.fx.ModelFx.override(entity, texture);
            if (layer == null) {
                layer = GhostLayer.forEntity(entity, texture);
            }
            if (layer != null) {
                cir.setReturnValue(layer);
            }
        } catch (RuntimeException ignored) {
            // Keep the game's own layer.
        }
    }

    /** A creature whose death is replaced by an effect is not drawn by the game while the effect plays. */
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$hideBody(LivingEntity entity, float yaw, float tickDelta, net.minecraft.client.util.math.MatrixStack matrices,
                                      net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.example.neurocanvas.fx.ModelFx.hidden(entity)) {
            ci.cancel();
        }
    }
}
