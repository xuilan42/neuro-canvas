package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.FreeCam;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    /** While FreeCam flies, the character receives no movement input. */
    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void neurocanvas$freeze(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(FreeCam.class).active()) {
            Input in = (Input) (Object) this;
            in.movementForward = 0f;
            in.movementSideways = 0f;
            in.pressingForward = false;
            in.pressingBack = false;
            in.pressingLeft = false;
            in.pressingRight = false;
            in.jumping = false;
            in.sneaking = false;
        }
    }
}
