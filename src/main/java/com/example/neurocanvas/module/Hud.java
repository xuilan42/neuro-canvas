package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Watermark and a list of enabled modules. Both can be moved in the HUD editor. */
public final class Hud extends Module {
    private static final HudLayout.Element MARK = HudLayout.register("watermark", "Watermark", false, 0.006, 0.012);
    private static final HudLayout.Element LIST = HudLayout.register("modulelist", "Module list", true, 0.994, 0.012);

    public Hud() {
        super("HUD", Category.HUD, "Watermark and module list (move them in Configs > Edit HUD layout)", true);
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

        List<String> names = new ArrayList<>();
        int maxW = 0;
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (m.enabled && m != this && m.category != Category.MISC && m.category != Category.AI) {
                names.add(m.name);
                maxW = Math.max(maxW, Ui.width(mc.textRenderer, m.name));
            }
        }
        if (names.isEmpty()) {
            LIST.w = 70;
            LIST.h = 14;
            return;
        }
        LIST.w = maxW + 10;
        LIST.h = names.size() * 16 - 2;
        int right = LIST.x(sw) + LIST.w;
        int y = LIST.y(sh);
        for (String name : names) {
            int w = Ui.width(mc.textRenderer, name) + 10;
            Ui.roundRect(ctx, right - w, y, w, 14, 4, 0xB0000000);
            Ui.text(ctx, mc.textRenderer, name, right - w + 5, y + 3, t.text());
            y += 16;
        }
    }
}
