package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Buttons that use the game's own drawing (ButtonWidget, CyclingButtonWidget and the buttons of other mods that do not draw their own) get the themed look. */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin extends ClickableWidget {
    public PressableWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Shadow
    public abstract void drawMessage(DrawContext context, TextRenderer textRenderer, int color);

    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$button(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (GuiSkin.button((PressableWidget) (Object) this, context, this.alpha)) {
            int color = (this.active ? 0xFFFFFF : 0xA0A0A0) | (MathHelper.ceil(this.alpha * 255.0f) << 24);
            this.drawMessage(context, MinecraftClient.getInstance().textRenderer, color);
            ci.cancel();
        }
    }
}
