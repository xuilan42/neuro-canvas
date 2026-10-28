package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.NameTags;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players who use the mod get our logo in front of their name in the player list (Tab). The logo is a character of a small font of the mod, so it
 * is part of the name's text: the game measures it and the columns widen by themselves.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListNameMixin {
    private static final Identifier FONT = new Identifier("neurocanvas", "logo");

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void neurocanvas$logo(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        NameTags tags = NeuroCanvasClient.modules().get(NameTags.class);
        if (tags.showsLogoFor(entry.getProfile().getName())) {
            Text logo = Text.literal("\uE000").setStyle(Style.EMPTY.withFont(FONT).withColor(0xFFFFFF));
            cir.setReturnValue(Text.empty().append(logo).append(cir.getReturnValue()));
        }
    }
}
