package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.CustomSky;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        CustomSky sky = NeuroCanvasClient.modules().get(CustomSky.class);
        if (sky.enabled) {
            cir.setReturnValue(sky.skyColor(cir.getReturnValue()));
        }
    }

    /** The moment an entity leaves the world, before it is gone: the fact kill detection reacts to, as Meteor's EntityRemovedEvent does. */
    @Inject(method = "removeEntity", at = @At("HEAD"), require = 0)
    private void neurocanvas$removed(int id, net.minecraft.entity.Entity.RemovalReason reason, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        net.minecraft.entity.Entity e = ((ClientWorld) (Object) this).getEntityById(id);
        if (e instanceof net.minecraft.entity.LivingEntity living) {
            NeuroCanvasClient.state().onRemoved(living);
        }
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$clouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        CustomSky sky = NeuroCanvasClient.modules().get(CustomSky.class);
        if (sky.enabled) {
            cir.setReturnValue(sky.cloudColor(cir.getReturnValue()));
        }
    }
}
