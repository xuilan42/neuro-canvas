package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;

/** Your potion effects with an icon, level and time left. */
public final class EffectsHud extends Module {
    private static final int ROW_H = 20;
    private static final HudLayout.Element ELEMENT = HudLayout.register("effects", "Effects", true, 0.994, 0.45);

    static {
        ELEMENT.w = 110;
        ELEMENT.h = ROW_H * 2;
    }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.7));
    private final Setting hideVanilla = add(Setting.bool("Hide vanilla icons", true));
    private final Setting warnSeconds = add(Setting.number("Warn below (s)", 0, 60, 10));

    public EffectsHud() {
        super("EffectsHUD", Category.HUD, "Potion effects with icons and timers", false);
    }

    public boolean hidesVanilla() {
        return enabled && hideVanilla.on();
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static String time(StatusEffectInstance e) {
        if (e.isInfinite()) {
            return "inf";
        }
        int s = e.getDuration() / 20;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null) {
            return;
        }
        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        effects.sort(Comparator.comparing((StatusEffectInstance e) -> e.getEffectType().isBeneficial() ? 0 : 1)
                .thenComparing(e -> e.getEffectType().getTranslationKey()));
        float s = scale.f();
        int w = 118;
        ELEMENT.w = Math.round(w * s);
        ELEMENT.h = Math.round(Math.max(1, effects.size()) * ROW_H * s);
        if (effects.isEmpty() || mc.options.hudHidden) {
            return;
        }
        Theme th = Interface.theme();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        int y = 0;
        int bg = ((int) (255 * background.f()) << 24) | (th.panel() & 0xFFFFFF);
        for (StatusEffectInstance e : effects) {
            Ui.roundRect(ctx, 0, y, w, ROW_H - 2, 6, bg);
            Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(e.getEffectType());
            ctx.drawSprite(3, y + 1, 0, 16, 16, sprite);
            String name = e.getEffectType().getName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
            Ui.text(ctx, mc.textRenderer, mc.textRenderer.trimToWidth(name, w - 62), 23, y + 5, th.text());
            boolean low = !e.isInfinite() && e.getDuration() / 20 < warnSeconds.value;
            String t = time(e);
            int color = low ? (System.currentTimeMillis() / 400 % 2 == 0 ? 0xFFFF6B6B : 0xFFFFC040)
                    : (e.getEffectType().isBeneficial() ? 0xFF6EE7A0 : 0xFFFF8A8A);
            Ui.text(ctx, mc.textRenderer, t, w - 6 - Ui.width(mc.textRenderer, t), y + 5, color);
            y += ROW_H;
        }
        ctx.getMatrices().pop();
    }
}
