package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Text fields: the game draws two plain rectangles (a border and a black fill); they become one themed, rounded field. The text and the cursor stay as they were. */
@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {
    @WrapOperation(method = "renderButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 0), require = 0)
    private void neurocanvas$border(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.fieldBackground((TextFieldWidget) (Object) this, context, x1, y1, x2, y2)) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "renderButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 1), require = 0)
    private void neurocanvas$fill(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (!GuiSkin.fieldsOn()) {
            original.call(context, x1, y1, x2, y2, color);
        }
    }
}
