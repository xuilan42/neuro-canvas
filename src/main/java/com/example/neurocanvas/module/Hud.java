package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Watermark and a list of enabled modules. Both can be moved in the HUD editor. */
public final class Hud extends Module {
    private static final HudLayout.Element MARK = HudLayout.register("watermark", "Watermark", false, 0.006, 0.012);
    private static final HudLayout.Element LIST = HudLayout.register("modulelist", "Module list", true, 0.994, 0.012);

    public Hud() {
        super("HUD", Category.HUD, "Watermark and module list (move them in Configs > Edit HUD layout)", true);
        hudSize();
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mc.options.hudHidden) {
            return;
        }
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        Theme t = Interface.theme();

        String mark = "NeuroCanvas";
        MARK.w = Ui.width(mc.textRenderer, mark) + 12;
        MARK.h = 16;
        int mx = MARK.x(sw);
        int my = MARK.y(sh);
        Ui.roundRect(ctx, mx, my, MARK.w, MARK.h, 4, 0xB0000000);
        Ui.text(ctx, mc.textRenderer, mark, mx + 6, my + 4, t.accent());

        List<Module> shown = new ArrayList<>();
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (m.enabled && m != this && m.category != Category.MISC && m.category != Category.AI) {
                shown.add(m);
            }
        }
        Comparator<Module> byWidth = Comparator.comparingInt(m -> Ui.width(mc.textRenderer, m.name));
        switch (Interface.listSort()) {
            case 1 -> shown.sort(byWidth);
            case 2 -> shown.sort(Comparator.comparing((Module m) -> m.name.toLowerCase()));
            case 3 -> shown.sort(Comparator.comparing((Module m) -> m.name.toLowerCase()).reversed());
            case 4 -> shown.sort(Comparator.comparingInt((Module m) -> m.category.ordinal())
                    .thenComparing(m -> m.name.toLowerCase()));
            case 5 -> shown.sort(Comparator.comparingInt((Module m) -> m.name.length()).thenComparing(m -> m.name));
            case 6 -> java.util.Collections.shuffle(shown, new java.util.Random(7));
            default -> shown.sort(byWidth.reversed());
        }
        if (shown.isEmpty()) {
            LIST.w = 70;
            LIST.h = 14;
            return;
        }
        int maxW = 0;
        for (Module m : shown) {
            maxW = Math.max(maxW, Ui.width(mc.textRenderer, m.name));
        }
        LIST.w = maxW + 10;
        LIST.h = shown.size() * 16 - 2;
        int left = LIST.x(sw);
        int y = LIST.y(sh);
        for (Module m : shown) {
            int w = Ui.width(mc.textRenderer, m.name) + 10;
            int x = LIST.ax == 2 ? left + LIST.w - w : LIST.ax == 1 ? left + (LIST.w - w) / 2 : left;
            Ui.roundRect(ctx, x, y, w, 14, 4, 0xB0000000);
            Ui.text(ctx, mc.textRenderer, m.name, x + 5, y + 3, t.text());
            y += 16;
        }
    }
}
