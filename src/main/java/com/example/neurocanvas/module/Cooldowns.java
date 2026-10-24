package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Item cooldowns (ender pearl, golden apples, chorus fruit, shield, ...) with an icon, a bar and the time left. */
public final class Cooldowns extends Module {
    private static final int ROW_H = 20;
    private static final HudLayout.Element ELEMENT = HudLayout.register("cooldowns", "Cooldowns", false, 0.36, 0.84);

    static {
        ELEMENT.w = 110;
        ELEMENT.h = ROW_H;
    }

    private record Tracked(Setting setting, Item item) { }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.7));
    private final Setting showReady = add(Setting.bool("Show when ready", false));
    private final List<Tracked> tracked = new ArrayList<>();

    public Cooldowns() {
        super("Cooldowns", Category.HUD, "Cooldown timers of ender pearls, apples, chorus fruit and more", false);
        hudSize();
        track("Ender pearl", Items.ENDER_PEARL, true);
        track("Golden apple", Items.GOLDEN_APPLE, true);
        track("Enchanted golden apple", Items.ENCHANTED_GOLDEN_APPLE, true);
        track("Chorus fruit", Items.CHORUS_FRUIT, true);
        track("Shield", Items.SHIELD, true);
        track("Totem of Undying", Items.TOTEM_OF_UNDYING, false);
        track("Ender eye", Items.ENDER_EYE, false);
    }

    private void track(String name, Item item, boolean on) {
        tracked.add(new Tracked(add(Setting.bool(name, on)), item));
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null) {
            return;
        }
        List<Object[]> rows = new ArrayList<>();
        for (Tracked t : tracked) {
            if (!t.setting().on()) {
                continue;
            }
            float progress = mc.player.getItemCooldownManager().getCooldownProgress(t.item(), 0f);
            if (progress > 0f) {
                float seconds = CooldownReader.secondsLeft(mc.player.getItemCooldownManager(), t.item());
                rows.add(new Object[] {t.item(), progress, seconds});
            } else if (showReady.on()) {
                rows.add(new Object[] {t.item(), 0f, 0f});
            }
        }
        float s = scale.f();
        int w = 112;
        ELEMENT.w = Math.round(w * s);
        ELEMENT.h = Math.round(Math.max(1, rows.size()) * ROW_H * s);
        if (rows.isEmpty() || mc.options.hudHidden) {
            return;
        }
        Theme th = Interface.theme();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        int y = 0;
        int bg = ((int) (255 * background.f()) << 24) | (th.panel() & 0xFFFFFF);
        for (Object[] r : rows) {
            Item item = (Item) r[0];
            float progress = (Float) r[1];
            float seconds = (Float) r[2];
            Ui.roundRect(ctx, 0, y, w, ROW_H - 2, 6, bg);
            ctx.drawItem(new ItemStack(item), 3, y + 1);
            int bx = 24;
            int bw = w - bx - 38;
            Ui.roundRect(ctx, bx, y + 7, bw, 4, 2, th.hover());
            if (progress > 0f) {
                Ui.roundRect(ctx, bx, y + 7, Math.max(2, Math.round(bw * progress)), 4, 2, th.accent());
            }
            String text = progress <= 0f ? "ready" : seconds >= 0 ? String.format("%.1fs", seconds) : Math.round(progress * 100) + "%";
            Ui.text(ctx, mc.textRenderer, text, w - 5 - Ui.width(mc.textRenderer, text), y + 5, progress <= 0f ? 0xFF6EE7A0 : th.text());
            y += ROW_H;
        }
        ctx.getMatrices().pop();
    }
}
