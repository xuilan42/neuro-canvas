package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sliders (volume, FOV, sensitivity and so on) get a themed track and handle. */
@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin extends ClickableWidget {
    @Shadow
    protected double value;

    public SliderWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$slider(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (GuiSkin.slider((SliderWidget) (Object) this, context, this.value, this.alpha)) {
            int color = (this.active ? 0xFFFFFF : 0xA0A0A0) | (MathHelper.ceil(this.alpha * 255.0f) << 24);
            this.drawScrollableText(context, MinecraftClient.getInstance().textRenderer, 2, color);
            ci.cancel();
        }
    }
}
