package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loading screen (startup and resource reloads) keeps all of the game's timing, fading and error handling; only what it draws changes:
 * the red becomes the theme's dark colour, the Mojang logo becomes ours and the white progress bar becomes a thin accent one.
 * No text is drawn here, because the fonts may not be loaded yet.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow
    private float progress;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"), require = 0)
    private int neurocanvas$background(IntSupplier brand, Operation<Integer> original) {
        return GuiSkin.splashBackground(original.call(brand));
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V", ordinal = 0), require = 0)
    private void neurocanvas$logo(DrawContext context, Identifier texture, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th,
                                  Operation<Void> original) {
        if (!GuiSkin.splashLogo(context)) {
            original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V", ordinal = 1), require = 0)
    private void neurocanvas$logoSecondHalf(DrawContext context, Identifier texture, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th,
                                            Operation<Void> original) {
        if (!GuiSkin.splashDrewLogo()) {
            original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th);
        }
    }

    @Inject(method = "renderProgressBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$bar(DrawContext context, int x1, int y1, int x2, int y2, float opacity, CallbackInfo ci) {
        if (GuiSkin.splashBar(context, x1, y1, x2, y2, opacity, this.progress)) {
            ci.cancel();
        }
    }
}
