package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The icon buttons of the title screen (language, accessibility) are drawn in the theme. */
@Mixin(TexturedButtonWidget.class)
public abstract class TexturedButtonWidgetMixin {
    @Shadow
    @Final
    private Identifier texture;
    @Shadow
    @Final
    private int u;
    @Shadow
    @Final
    private int v;

    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$icon(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TexturedButtonWidget self = (TexturedButtonWidget) (Object) this;
        if (GuiSkin.titleIcon(self, context, this.texture, this.u, this.v, 1f)) {
            ci.cancel();
        }
    }
}
