package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Shimmer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    private static final String HELD = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V";

    private static float[] neurocanvas$tint;

    /** Only items held by an entity are tinted (this overload carries the holder); menus and dropped items stay as they are. */
    @Inject(method = HELD, at = @At("HEAD"), require = 0)
    private void neurocanvas$begin(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                   MatrixStack matrices, VertexConsumerProvider vertexConsumers, World world, int light,
                                   int overlay, int seed, CallbackInfo ci) {
        neurocanvas$tint = NeuroCanvasClient.modules() == null ? null
                : NeuroCanvasClient.modules().get(Shimmer.class).heldTint(entity);
    }

    @Inject(method = HELD, at = @At("RETURN"), require = 0)
    private void neurocanvas$end(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers, World world, int light,
                                 int overlay, int seed, CallbackInfo ci) {
        neurocanvas$tint = null;
    }

    @ModifyArgs(method = "renderBakedItemQuads", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;"
                    + "Lnet/minecraft/client/render/model/BakedQuad;FFFII)V"), require = 0)
    private void neurocanvas$tintQuad(Args args) {
        if (neurocanvas$tint != null) {
            args.set(2, (float) args.get(2) * neurocanvas$tint[0]);
            args.set(3, (float) args.get(3) * neurocanvas$tint[1]);
            args.set(4, (float) args.get(4) * neurocanvas$tint[2]);
        }
    }
}
