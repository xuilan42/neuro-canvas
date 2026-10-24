package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.NoRender;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noWeather(LightmapTextureManager manager, float tickDelta, double x, double y, double z,
                                       CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(NoRender.class).hidesWeather()) {
            ci.cancel();
        }
    }

    /** The game has drawn its own sky; Ambience's sky replaces it (the world is drawn afterwards). */
    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At("RETURN"), require = 0)
    private void neurocanvas$sky(net.minecraft.client.util.math.MatrixStack matrices, org.joml.Matrix4f projectionMatrix, float tickDelta,
                                 net.minecraft.client.render.Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null) {
            NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Ambience.class).drawSky(projectionMatrix, matrices.peek().getPositionMatrix(), camera);
        }
    }
}
