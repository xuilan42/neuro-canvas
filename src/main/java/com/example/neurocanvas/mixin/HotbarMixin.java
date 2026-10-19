package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The hotbar and the experience bar: the sprites become themed rounded shapes at the same places. The hearts, armour and hunger are not
 * touched at all, so mods that anchor to them (AppleSkin) keep working. The items, counts and the attack indicator stay the game's.
 */
@Mixin(InGameHud.class)
public abstract class HotbarMixin {
    @WrapOperation(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"), require = 0)
    private void neurocanvas$hotbar(DrawContext context, Identifier texture, int x, int y, int u, int v, int w, int h, Operation<Void> original) {
        if (!GuiSkin.hotbar(context, texture, x, y, u, v, w, h)) {
            original.call(context, texture, x, y, u, v, w, h);
        }
    }

    @WrapOperation(method = "renderExperienceBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"), require = 0)
    private void neurocanvas$experience(DrawContext context, Identifier texture, int x, int y, int u, int v, int w, int h, Operation<Void> original) {
        if (!GuiSkin.experienceBar(context, texture, x, y, u, v, w, h)) {
            original.call(context, texture, x, y, u, v, w, h);
        }
    }
}
