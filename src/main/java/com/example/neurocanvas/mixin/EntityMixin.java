package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.FreeCam;
import com.example.neurocanvas.module.FreeLook;
import com.example.neurocanvas.module.Zoom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** While zooming, the same mouse movement turns the view less, in step with the zoom factor. */
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private double neurocanvas$zoomX(double dx) {
        return neurocanvas$scale(dx);
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private double neurocanvas$zoomY(double dy) {
        return neurocanvas$scale(dy);
    }

    private double neurocanvas$scale(double delta) {
        if ((Object) this instanceof ClientPlayerEntity && NeuroCanvasClient.modules() != null) {
            return delta * NeuroCanvasClient.modules().get(Zoom.class).sensitivityScale();
        }
        return delta;
    }

    /** Mouse movement goes to the free camera or free look instead of turning the character. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$look(double dx, double dy, CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity) || NeuroCanvasClient.modules() == null) {
            return;
        }
        FreeCam cam = NeuroCanvasClient.modules().get(FreeCam.class);
        if (cam.active()) {
            cam.look(dx, dy);
            ci.cancel();
            return;
        }
        FreeLook look = NeuroCanvasClient.modules().get(FreeLook.class);
        if (look.active()) {
            look.look(dx, dy);
            ci.cancel();
        }
    }
}
