package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The black backgrounds behind chat lines take the theme's dark colour, with the opacity the player chose. */
@Mixin(ChatHud.class)
public abstract class ChatBackgroundMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void neurocanvas$fill(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        original.call(context, x1, y1, x2, y2, GuiSkin.chatFill(color));
    }
}
