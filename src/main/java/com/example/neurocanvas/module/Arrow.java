package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/** An arrow around the crosshair pointing toward your target or the nearest player. */
public final class Arrow extends Module {
    private final Setting mode = add(Setting.choice("Points to", 0, "Last hit", "Nearest player"));
    private final Setting radius = add(Setting.number("Radius", 20, 150, 60));
    private final Setting size = add(Setting.number("Size", 4, 20, 9));
    private final Setting range = add(Setting.number("Max range", 16, 200, 64));
    private final Setting distance = add(Setting.bool("Show distance", true));
    private final Setting fade = add(Setting.bool("Fade when aimed", true));
    private final Setting color = add(Setting.color("Color", 0.0));

    public Arrow() {
        super("Arrow", Category.COMBAT, "Arrow pointing at your target or the nearest player", false);
        risky = true;
    }

    private Entity pick(MinecraftClient mc) {
        if (mode.index() == 0) {
            return NeuroCanvasClient.state().target;
        }
        Entity best = null;
        double bestDist = range.value;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || Teams.ignores(p)) {
                continue;
            }
            double d = mc.player.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.options.hudHidden) {
            return;
        }
        Entity target = pick(mc);
        if (target == null) {
            return;
        }
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = MathHelper.wrapDegrees(targetYaw - mc.gameRenderer.getCamera().getYaw());

        float alpha = 1f;
        if (fade.on()) {
            alpha = MathHelper.clamp((Math.abs(rel) - 6f) / 24f, 0.15f, 1f);
        }
        int argb = color.argb();
        int c = (Math.round(255 * alpha * ((argb >>> 24) / 255f)) << 24) | (argb & 0xFFFFFF);

        double rad = Math.toRadians(rel);
        float dirX = (float) Math.sin(rad);
        float dirY = (float) -Math.cos(rad);
        float cx = ctx.getScaledWindowWidth() / 2f;
        float cy = ctx.getScaledWindowHeight() / 2f;
        float r = radius.f();
        float sz = size.f();
        float tipX = cx + dirX * r;
        float tipY = cy + dirY * r;
        float baseX = cx + dirX * (r - sz * 1.5f);
        float baseY = cy + dirY * (r - sz * 1.5f);
        float perpX = -dirY * sz * 0.8f;
        float perpY = dirX * sz * 0.8f;
        Ui.triangle(ctx, tipX, tipY, baseX + perpX, baseY + perpY, baseX - perpX, baseY - perpY, c);

        if (distance.on()) {
            String text = String.format("%.0fm", mc.player.distanceTo(target));
            Ui.textCentered(ctx, mc.textRenderer, text, Math.round(baseX - dirX * 4), Math.round(baseY - dirY * 4 - 4),
                    (Math.round(255 * alpha) << 24) | 0xFFFFFF);
        }
    }
}
