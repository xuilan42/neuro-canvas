package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.FreeCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    /** While FreeCam flies, the game and other mods see the movement keys as released; FreeCam reads them itself. */
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$hideMovementKeys(CallbackInfoReturnable<Boolean> cir) {
        if (NeuroCanvasClient.modules() == null || !NeuroCanvasClient.modules().get(FreeCam.class).active()) {
            return;
        }
        GameOptions o = MinecraftClient.getInstance().options;
        if (o == null) {
            return;
        }
        Object self = this;
        if (self == o.forwardKey || self == o.backKey || self == o.leftKey || self == o.rightKey
                || self == o.jumpKey || self == o.sneakKey || self == o.sprintKey) {
            cir.setReturnValue(false);
        }
    }
}
