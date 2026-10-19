package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.SubtitlesHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Subtitles sit on themed panels. */
@Mixin(SubtitlesHud.class)
public abstract class SubtitlesHudMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void neurocanvas$fill(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.hudFill(context, x1, y1, x2, y2, color)) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }
}
