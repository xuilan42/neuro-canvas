package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Colours the whole picture: a tint, brighter or darker overall, and a vignette around the edges. */
public final class ColorGrading extends Module {
    private final Setting tint = add(Setting.color("Tint color", 0.08));
    private final Setting tintStrength = add(Setting.number("Tint strength", 0, 0.6, 0.15));
    private final Setting brightness = add(Setting.number("Brightness", -0.5, 0.5, 0));
    private final Setting vignette = add(Setting.bool("Vignette", true));
    private final Setting vignetteSize = add(Setting.number("Vignette size", 0.1, 0.6, 0.3));
    private final Setting vignetteStrength = add(Setting.number("Vignette strength", 0, 1, 0.6));
    private final Setting vignetteColor = add(Setting.color("Vignette color", 0));

    public ColorGrading() {
        super("ColorGrading", Category.VISUALS, "Tint, brightness and vignette over the whole screen", false);
        vignetteColor.setArgb(0xFF000000);
    }

    /** Drawn before the vanilla HUD, so the HUD itself is not tinted. */
    public void draw(DrawContext ctx, MinecraftClient mc) {
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        if (tintStrength.value > 0.001) {
            ctx.fill(0, 0, w, h, Ui.alpha(tint.argb() & 0xFFFFFF, tintStrength.f()));
        }
        if (Math.abs(brightness.value) > 0.001) {
            int base = brightness.value > 0 ? 0xFFFFFF : 0x000000;
            ctx.fill(0, 0, w, h, Ui.alpha(base, (float) Math.abs(brightness.value)));
        }
        if (vignette.on() && vignetteStrength.value > 0.001) {
            int c = vignetteColor.argb();
            Ui.vignette(ctx, w, h, vignetteSize.f(), Ui.alpha(c & 0xFFFFFF, vignetteStrength.f() * ((c >>> 24) / 255f)));
        }
    }
}
