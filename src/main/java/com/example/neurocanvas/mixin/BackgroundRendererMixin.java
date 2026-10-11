package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.CustomFog;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    /** Runs after vanilla has chosen the fog, so our values simply overwrite it. */
    @Inject(method = "applyFog", at = @At("RETURN"), require = 0)
    private static void neurocanvas$fog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                        boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        CustomFog fog = NeuroCanvasClient.modules().get(CustomFog.class);
        if (fog.enabled) {
            fog.apply(camera, fogType, viewDistance);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private static void neurocanvas$clearColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
                                               float skyDarkness, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        CustomFog fog = NeuroCanvasClient.modules().get(CustomFog.class);
        if (fog.enabled) {
            fog.applyClearColor(camera);
        }
    }
}
