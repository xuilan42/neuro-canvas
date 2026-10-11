package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** A short log of damage you deal and take. */
public final class DamageFeed extends Module implements WorldState.CombatListener {
    private static final int ROW_H = 16;
    private static final HudLayout.Element ELEMENT = HudLayout.register("damagefeed", "Damage feed", false, 0.02, 0.36);

    static {
        ELEMENT.w = 110;
        ELEMENT.h = ROW_H * 3;
    }

    private record Entry(String text, int color, long time) { }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting duration = add(Setting.number("Duration (s)", 2, 10, 4));
    private final Setting maxLines = add(Setting.number("Max lines", 3, 10, 6));
    private final Setting dealt = add(Setting.bool("Damage dealt", true));
    private final Setting received = add(Setting.bool("Damage taken", true));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.7));

    private final List<Entry> entries = new ArrayList<>();

    public DamageFeed() {
        super("DamageFeed", Category.HUD, "Log of damage dealt and taken", false);
        WorldState.addCombatListener(this);
    }

    @Override
    public void onDamage(String name, float amount, boolean wasDealt) {
        if (!enabled || (wasDealt ? !dealt.on() : !received.on())) {
            return;
        }
        String text = String.format("-%.1f  %s", amount, name);
        entries.add(new Entry(text, wasDealt ? 0xFF6EE7A0 : 0xFFFF6B6B, System.currentTimeMillis()));
        while (entries.size() > (int) maxLines.value) {
            entries.remove(0);
        }
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        long now = System.currentTimeMillis();
        long life = (long) (duration.f() * 1000);
        entries.removeIf(e -> now - e.time > life);
        float s = scale.f();
        ELEMENT.w = Math.round(110 * s);
        ELEMENT.h = Math.round(ROW_H * 3 * s);
        if (entries.isEmpty() || mc.options.hudHidden) {
            return;
        }
        Theme th = Interface.theme();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        int y = 0;
        for (Entry e : entries) {
            long age = now - e.time;
            float in = Math.min(1f, age / 150f);
            float out = Math.min(1f, (life - age) / 400f);
            float a = Math.max(0f, Math.min(in, out));
            int w = Ui.width(mc.textRenderer, e.text) + 12;
            int off = Math.round((1f - in) * -10f);
            int bg = ((int) (255 * background.f() * a) << 24) | (th.panel() & 0xFFFFFF);
            Ui.roundRect(ctx, off, y, w, ROW_H - 2, 5, bg);
            Ui.text(ctx, mc.textRenderer, e.text, off + 6, y + 3, ((int) (255 * a) << 24) | (e.color & 0xFFFFFF));
            y += ROW_H;
        }
        ctx.getMatrices().pop();
    }
}
