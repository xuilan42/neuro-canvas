package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Shimmer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    private static float[] neurocanvas$tint;

    @Inject(method = "renderArmor", at = @At("HEAD"), require = 0)
    private void neurocanvas$begin(MatrixStack matrices, VertexConsumerProvider vertexConsumers, LivingEntity entity,
                                   EquipmentSlot slot, int light, net.minecraft.client.render.entity.model.BipedEntityModel<?> model,
                                   CallbackInfo ci) {
        neurocanvas$tint = NeuroCanvasClient.modules() == null ? null
                : NeuroCanvasClient.modules().get(Shimmer.class).armorTint(entity, slot);
    }

    @Inject(method = "renderArmor", at = @At("RETURN"), require = 0)
    private void neurocanvas$end(MatrixStack matrices, VertexConsumerProvider vertexConsumers, LivingEntity entity,
                                 EquipmentSlot slot, int light, net.minecraft.client.render.entity.model.BipedEntityModel<?> model,
                                 CallbackInfo ci) {
        neurocanvas$tint = null;
    }

    @ModifyVariable(method = "renderArmorParts", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float neurocanvas$red(float red) {
        return neurocanvas$tint == null ? red : red * neurocanvas$tint[0];
    }

    @ModifyVariable(method = "renderArmorParts", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float neurocanvas$green(float green) {
        return neurocanvas$tint == null ? green : green * neurocanvas$tint[1];
    }

    @ModifyVariable(method = "renderArmorParts", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float neurocanvas$blue(float blue) {
        return neurocanvas$tint == null ? blue : blue * neurocanvas$tint[2];
    }
}
