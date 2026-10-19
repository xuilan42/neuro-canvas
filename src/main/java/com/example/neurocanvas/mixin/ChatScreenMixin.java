package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The strip behind the chat input takes the theme's dark colour too. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void neurocanvas$fill(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        original.call(context, x1, y1, x2, y2, GuiSkin.chatFill(color));
    }
}
