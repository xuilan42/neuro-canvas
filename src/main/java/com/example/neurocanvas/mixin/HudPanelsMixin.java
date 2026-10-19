package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The scoreboard and the name of the held item sit on themed panels. */
@Mixin(InGameHud.class)
public abstract class HudPanelsMixin {
    @WrapOperation(method = "renderScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void neurocanvas$scoreboard(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.hudFill(context, x1, y1, x2, y2, color)) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "renderHeldItemTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void neurocanvas$heldName(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.hudFill(context, x1, y1, x2, y2, color)) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }
}
