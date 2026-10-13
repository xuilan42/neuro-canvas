package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.WeatherChanger;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    /** Only the client's own world is changed: the integrated server's world is a World too and must stay as it is. */
    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$rain(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld && NeuroCanvasClient.modules() != null) {
            cir.setReturnValue(NeuroCanvasClient.modules().get(WeatherChanger.class).rain(cir.getReturnValue()));
        }
    }

    @Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld && NeuroCanvasClient.modules() != null) {
            WeatherChanger w = NeuroCanvasClient.modules().get(WeatherChanger.class);
            cir.setReturnValue(w.enabled ? w.thunder(cir.getReturnValue()) * w.rain(1f) : cir.getReturnValue());
        }
    }
}
