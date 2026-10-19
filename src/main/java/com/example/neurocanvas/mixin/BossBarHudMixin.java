package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Boss bars: a rounded track and fill in the colour of the bar, with thin ticks for the notched styles. The name above stays the game's. */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
    @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$bar(DrawContext context, int x, int y, BossBar bar, int width, int textureV, CallbackInfo ci) {
        if (GuiSkin.bossBar(context, x, y, bar, width, textureV)) {
            ci.cancel();
        }
    }
}
