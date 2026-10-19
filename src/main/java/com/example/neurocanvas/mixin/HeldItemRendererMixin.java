package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.SwingAnimation;
import com.example.neurocanvas.module.ViewModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    /**
     * Wraps the whole first-person render of one hand in its own matrix level, so our transform
     * never leaks into the other hand.
     */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void neurocanvas$begin(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                   float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.push();
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1f : -1f;
        float swing = hand == player.preferredHand ? player.getHandSwingProgress(tickDelta) : 0f;
        NeuroCanvasClient.modules().get(SwingAnimation.class).apply(matrices, hand, swing, side);
        NeuroCanvasClient.modules().get(ViewModel.class).apply(matrices, hand, side);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"), require = 0)
    private void neurocanvas$end(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                 float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.pop();
    }

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float neurocanvas$swingProgress(float swingProgress) {
        if (NeuroCanvasClient.modules() == null) {
            return swingProgress;
        }
        SwingAnimation swing = NeuroCanvasClient.modules().get(SwingAnimation.class);
        return swing.enabled && swing.replacesVanilla(Hand.MAIN_HAND) ? 0f : swingProgress;
    }

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float neurocanvas$equipProgress(float equipProgress) {
        if (NeuroCanvasClient.modules() == null) {
            return equipProgress;
        }
        return NeuroCanvasClient.modules().get(ViewModel.class).skipEquipAnimation() ? 0f : equipProgress;
    }
}
