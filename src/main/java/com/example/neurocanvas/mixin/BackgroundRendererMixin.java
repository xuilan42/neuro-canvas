package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.CustomFog;
import com.example.neurocanvas.module.VolumetricFog;
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
        com.example.neurocanvas.module.CustomSky sky = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.CustomSky.class);
        if (sky.enabled && !(fog.enabled && fog.overridesColor())) {
            sky.applyFogColor(camera, tickDelta);
        }
        com.example.neurocanvas.module.Ambience ambience = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Ambience.class);
        if (ambience.fogOn(camera) && !(fog.enabled && fog.overridesColor())) {
            ambience.applyFogColor();
        }
        if (fogType == BackgroundRenderer.FogType.FOG_TERRAIN
                && camera.getSubmersionType() == net.minecraft.client.render.CameraSubmersionType.NONE
                && NeuroCanvasClient.modules().get(VolumetricFog.class).replacesVanillaFog()) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
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
        com.example.neurocanvas.module.CustomSky sky = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.CustomSky.class);
        if (sky.enabled && !(fog.enabled && fog.overridesColor())) {
            sky.applyClearColor(camera, tickDelta);
        }
        com.example.neurocanvas.module.Ambience ambience = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Ambience.class);
        if (ambience.fogOn(camera) && !(fog.enabled && fog.overridesColor())) {
            ambience.applyClearColor();
        }
    }
}
