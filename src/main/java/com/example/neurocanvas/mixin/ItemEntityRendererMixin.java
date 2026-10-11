package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.ItemPhysics;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    /** Feeding 0 to the bobbing sine makes the item hover at a constant height. */
    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(F)F"),
            index = 0, require = 0)
    private float neurocanvas$noBob(float angle) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(ItemPhysics.class).stopsBobbing()) {
            return 0f;
        }
        return angle;
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V",
            shift = At.Shift.AFTER, ordinal = 0), require = 0)
    private void neurocanvas$layFlat(ItemEntity item, float yaw, float tickDelta, MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null) {
            NeuroCanvasClient.modules().get(ItemPhysics.class).layFlat(item, matrices);
        }
    }
}
