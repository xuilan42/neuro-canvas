package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.ColorGrading;
import com.example.neurocanvas.module.Comfort;
import com.example.neurocanvas.module.Crosshair;
import com.example.neurocanvas.module.ScreenPulse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(Comfort.class).noVignette()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noPortal(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(Comfort.class).noPortal()) {
            ci.cancel();
        }
    }

    /** Pumpkin on the head: skip only its blur texture, leave the powder-snow overlay alone. */
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$noPumpkin(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() != null && texture.getPath().contains("pumpkinblur")
                && NeuroCanvasClient.modules().get(Comfort.class).noPumpkin()) {
            ci.cancel();
        }
    }

    /** Full-screen overlays go under the vanilla HUD, so interface elements are not tinted. */
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void neurocanvas$overlays(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        ColorGrading grading = NeuroCanvasClient.modules().get(ColorGrading.class);
        if (grading.enabled) {
            grading.draw(context, mc);
        }
        ScreenPulse pulse = NeuroCanvasClient.modules().get(ScreenPulse.class);
        if (pulse.enabled) {
            pulse.draw(context, mc);
        }
    }

    /** Replaces the vanilla crosshair with ours while the Crosshair module is on. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$renderCrosshair(DrawContext context, CallbackInfo ci) {
        Crosshair crosshair = NeuroCanvasClient.modules().get(Crosshair.class);
        if (crosshair.enabled) {
            crosshair.draw(context, MinecraftClient.getInstance());
            ci.cancel();
        }
    }
}
