package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Shows how charged your next attack is, as a ring around the crosshair or a bar under it. */
public final class AttackIndicator extends Module {
    private final Setting style = add(Setting.choice("Style", 0, "Ring", "Bar"));
    private final Setting size = add(Setting.number("Size", 6, 40, 14));
    private final Setting thickness = add(Setting.number("Thickness", 1, 5, 2));
    private final Setting hideFull = add(Setting.bool("Hide when ready", true));
    private final Setting charging = add(Setting.color("Charging color", 0.1));
    private final Setting ready = add(Setting.color("Ready color", 0.38));

    public AttackIndicator() {
        super("AttackIndicator", Category.HUD, "Attack charge around the crosshair", false);
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden
                || !mc.options.getPerspective().isFirstPerson()) {
            return;
        }
        float p = mc.player.getAttackCooldownProgress(0.5f);
        boolean full = p >= 0.999f;
        if (full && hideFull.on()) {
            return;
        }
        int color = full ? ready.argb() : charging.argb();
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int t = Math.round(thickness.f());
        if (style.index() == 0) {
            float r = size.f();
            Ui.ring(ctx, cx, cy, r, t, Ui.alpha(0x000000, 0.35f));
            Ui.arc(ctx, cx, cy, r, t, -90f, 360f * p, color);
        } else {
            int half = Math.round(size.f());
            int y = cy + 14;
            Ui.roundRect(ctx, cx - half, y, half * 2, t + 1, (t + 1) / 2, Ui.alpha(0x000000, 0.45f));
            Ui.roundRect(ctx, cx - half, y, Math.max(1, Math.round(half * 2 * p)), t + 1, (t + 1) / 2, color);
        }
    }
}
