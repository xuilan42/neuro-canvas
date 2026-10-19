package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The panel behind item and button tooltips: a rounded themed panel instead of the purple frame. */
@Mixin(TooltipBackgroundRenderer.class)
public abstract class TooltipBackgroundRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private static void neurocanvas$panel(DrawContext context, int x, int y, int width, int height, int z, CallbackInfo ci) {
        if (GuiSkin.tooltip(context, x, y, width, height, z)) {
            ci.cancel();
        }
    }
}
