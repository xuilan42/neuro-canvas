package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The tabs of the world creation and similar screens. The label and the line under the current tab stay the game's. */
@Mixin(TabButtonWidget.class)
public abstract class TabButtonWidgetMixin {
    @WrapOperation(method = "renderButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawNineSlicedTexture(Lnet/minecraft/util/Identifier;IIIIIIIIIIII)V"), require = 0)
    private void neurocanvas$tab(DrawContext context, Identifier texture, int x, int y, int w, int h, int a, int b, int c, int d, int e, int f, int g, int i, Operation<Void> original) {
        if (!GuiSkin.tab((TabButtonWidget) (Object) this, context, x, y, w, h)) {
            original.call(context, texture, x, y, w, h, a, b, c, d, e, f, g, i);
        }
    }
}
