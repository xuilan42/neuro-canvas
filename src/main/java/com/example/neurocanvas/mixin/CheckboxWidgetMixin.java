package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Checkboxes: the square sprite becomes a rounded box with an accent mark; the label stays the game's. */
@Mixin(CheckboxWidget.class)
public abstract class CheckboxWidgetMixin {
    @WrapOperation(method = "renderButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIFFIIII)V"), require = 0)
    private void neurocanvas$box(DrawContext context, Identifier texture, int x, int y, float u, float v, int w, int h, int tw, int th, Operation<Void> original) {
        if (!GuiSkin.checkbox((CheckboxWidget) (Object) this, context, x, y, w, h)) {
            original.call(context, texture, x, y, u, v, w, h, tw, th);
        }
    }
}
