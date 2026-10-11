package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

/** Pop-up cards for kills, used totems and broken armor. */
public final class CombatNotifier extends Module implements WorldState.CombatListener {
    private static final int CARD_W = 150;
    private static final int CARD_H = 30;
    private static final HudLayout.Element ELEMENT = HudLayout.register("notifications", "Notifications", false, 0.42, 0.08);

    static {
        ELEMENT.w = CARD_W;
        ELEMENT.h = CARD_H;
    }

    private record Card(String title, String text, int color, long time) { }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting duration = add(Setting.number("Duration (s)", 1, 10, 3));
    private final Setting kills = add(Setting.bool("Kills", true));
    private final Setting totems = add(Setting.bool("Totem used", true));
    private final Setting armor = add(Setting.bool("Armor broken", true));

    private final List<Card> cards = new ArrayList<>();

    public CombatNotifier() {
        super("CombatNotifier", Category.HUD, "Cards for kills, used totems and broken armor", false);
        WorldState.addCombatListener(this);
    }

    private void push(String title, String text, int color) {
        cards.add(new Card(title, text, color, System.currentTimeMillis()));
        while (cards.size() > 4) {
            cards.remove(0);
        }
    }

    @Override
    public void onKill(LivingEntity entity) {
        if (enabled && kills.on()) {
            push("Kill", entity.getName().getString(), 0xFF6EE7A0);
        }
    }

    @Override
    public void onTotemUsed() {
        if (enabled && totems.on()) {
            push("Totem used", "Totem of Undying popped", 0xFFFFC040);
        }
    }

    @Override
    public void onArmorBreak(String piece) {
        if (enabled && armor.on()) {
            push("Armor broken", piece, 0xFFFF6B6B);
        }
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        long now = System.currentTimeMillis();
        long life = (long) (duration.f() * 1000);
        cards.removeIf(c -> now - c.time > life);
        float s = scale.f();
        ELEMENT.w = Math.round(CARD_W * s);
        ELEMENT.h = Math.round(CARD_H * s);
        if (cards.isEmpty() || mc.options.hudHidden) {
            return;
        }
        Theme th = Interface.theme();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        int y = 0;
        for (int i = cards.size() - 1; i >= 0; i--) {
            Card c = cards.get(i);
            long age = now - c.time;
            float in = Math.min(1f, age / 180f);
            float out = Math.min(1f, (life - age) / 350f);
            float a = Math.max(0f, Math.min(in, out));
            int dy = Math.round((1f - in) * -8f);
            int bg = ((int) (255 * 0.85f * a) << 24) | (th.panel() & 0xFFFFFF);
            Ui.roundRect(ctx, 0, y + dy, CARD_W, CARD_H, 7, bg);
            Ui.roundRect(ctx, 5, y + dy + 6, 3, CARD_H - 12, 1, ((int) (255 * a) << 24) | (c.color & 0xFFFFFF));
            Ui.text(ctx, mc.textRenderer, c.title, 14, y + dy + 5, ((int) (255 * a) << 24) | (th.text() & 0xFFFFFF));
            Ui.text(ctx, mc.textRenderer, mc.textRenderer.trimToWidth(c.text, CARD_W - 22), 14, y + dy + 16,
                    ((int) (255 * a) << 24) | (th.dim() & 0xFFFFFF));
            y += CARD_H + 4;
        }
        ctx.getMatrices().pop();
    }
}
