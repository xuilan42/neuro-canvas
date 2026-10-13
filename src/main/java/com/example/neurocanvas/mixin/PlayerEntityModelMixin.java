package com.example.neurocanvas.mixin;

import com.example.neurocanvas.avatar.EmotePose;
import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Emotes;
import com.example.neurocanvas.module.Showcase;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> {
    /** While an emote plays, its pose replaces the normal pose of the player's limbs, head and body. */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("RETURN"), require = 0)
    private void neurocanvas$emote(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
                                   float headPitch, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        float[][] pose = NeuroCanvasClient.modules().get(Emotes.class).poseFor(entity);
        if (pose == null) {
            pose = NeuroCanvasClient.modules().get(Showcase.class).poseFor(entity);
        }
        if (pose != null) {
            EmotePose.apply((PlayerEntityModel<?>) (Object) this, pose);
        }
    }
}
