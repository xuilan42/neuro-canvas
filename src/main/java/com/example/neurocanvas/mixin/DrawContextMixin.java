package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every draw of the dirt tile (the backgrounds of lists, headers and footers) and of a toast background ends in this method; they are replaced by themed panels. */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Inject(method = "drawTexturedQuad(Lnet/minecraft/util/Identifier;IIIIIFFFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$dirt(Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2, CallbackInfo ci) {
        if (Screen.OPTIONS_BACKGROUND_TEXTURE.equals(texture) && GuiSkin.dirt((DrawContext) (Object) this, x1, x2, y1, y2)) {
            ci.cancel();
        } else if (net.minecraft.client.toast.Toast.TEXTURE.equals(texture) && GuiSkin.suppressToastTexture(x1, x2, y1, y2)) {
            ci.cancel();
        }
    }
}
