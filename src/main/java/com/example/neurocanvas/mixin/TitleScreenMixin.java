package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The title screen keeps its layout and logic; the panorama gets a theme tint, the Mojang logo becomes ours and the splash text takes the accent. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"), require = 0)
    private void neurocanvas$logo(LogoDrawer drawer, DrawContext context, int width, float alpha, Operation<Void> original) {
        GuiSkin.titlePanorama(context);
        if (!GuiSkin.titleLogo(context, alpha)) {
            original.call(drawer, context, width, alpha);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;I)V"), require = 0)
    private void neurocanvas$splash(SplashTextRenderer splash, DrawContext context, int width, TextRenderer font, int color, Operation<Void> original) {
        if (!GuiSkin.titleHidesSplash()) {
            original.call(splash, context, width, font, color);
        }
    }
}
