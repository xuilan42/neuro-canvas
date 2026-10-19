package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The scrollbar of lists (worlds, servers, options, resource packs): a rounded track and an accent handle instead of three grey rectangles. */
@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 0), require = 0)
    private void neurocanvas$track(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.scrollbar(context, 0, x1, y1, x2, y2)) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 1), require = 0)
    private void neurocanvas$handle(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.scrollbar(context, 1, x1, y1, x2, y2)) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 2), require = 0)
    private void neurocanvas$highlight(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.scrollbar(context, 2, x1, y1, x2, y2)) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }
}
