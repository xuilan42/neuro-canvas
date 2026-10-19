package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The backgrounds of Minecraft's screens come from the theme (see GuiSkin). Both hooks leave the vanilla drawing alone when the skin is off. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$background(DrawContext context, CallbackInfo ci) {
        if (GuiSkin.worldBackground((Screen) (Object) this, context)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBackgroundTexture(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$menuBackground(DrawContext context, CallbackInfo ci) {
        if (GuiSkin.menuBackground((Screen) (Object) this, context)) {
            ci.cancel();
        }
    }
}
