package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.NoRender;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends Entity> void neurocanvas$noEntities(E entity, Frustum frustum, double x, double y, double z,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(NoRender.class).hides(entity)) {
            cir.setReturnValue(false);
        }
    }
}
