package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Shows how many Totems of Undying you carry, with the item icon. */
public final class TotemCounter extends Module {
    private static final int BASE_W = 44;
    private static final int BASE_H = 24;
    private static final HudLayout.Element ELEMENT = HudLayout.register("totems", "Totem counter", false, 0.36, 0.93);

    static {
        ELEMENT.w = BASE_W;
        ELEMENT.h = BASE_H;
    }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.75));
    private final Setting hideZero = add(Setting.bool("Hide when zero", false));
    private final Setting warn = add(Setting.bool("Warn when low", true));

    private final ItemStack icon = new ItemStack(Items.TOTEM_OF_UNDYING);

    public TotemCounter() {
        super("TotemCounter", Category.HUD, "Number of totems in your inventory", false);
    }

    private static int count(MinecraftClient mc) {
        int total = 0;
        for (ItemStack s : mc.player.getInventory().main) {
            if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                total += s.getCount();
            }
        }
        for (ItemStack s : mc.player.getInventory().offHand) {
            if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                total += s.getCount();
            }
        }
        return total;
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        float s = scale.f();
        ELEMENT.w = Math.round(BASE_W * s);
        ELEMENT.h = Math.round(BASE_H * s);
        if (mc.player == null || mc.options.hudHidden) {
            return;
        }
        int n = count(mc);
        if (n == 0 && hideZero.on()) {
            return;
        }
        Theme th = Interface.theme();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        int bg = ((int) (255 * background.f()) << 24) | (th.panel() & 0xFFFFFF);
        Ui.roundRect(ctx, 0, 0, BASE_W, BASE_H, 7, bg);
        ctx.drawItem(icon, 4, 4);
        int color = n == 0 && warn.on() ? 0xFFFF5555 : (n == 1 && warn.on() ? 0xFFFFC040 : th.text());
        Ui.text(ctx, mc.textRenderer, "x" + n, 23, 8, color);
        ctx.getMatrices().pop();
    }
}
