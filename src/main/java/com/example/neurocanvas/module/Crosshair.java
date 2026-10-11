package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

/**
 * Replaces the vanilla crosshair (see InGameHudMixin). Supports several shapes, an outline,
 * a gap that widens while moving or hitting, and colour changes over enemies and on hit.
 */
public final class Crosshair extends Module {
    private static final long FLASH_MS = 220;

    private final Setting style = add(Setting.choice("Style", 0, "Cross", "Dot", "Circle", "Square"));
    private final Setting size = add(Setting.number("Size", 1, 30, 6));
    private final Setting gap = add(Setting.number("Gap", 0, 20, 3));
    private final Setting thickness = add(Setting.number("Thickness", 1, 6, 2));
    private final Setting centerDot = add(Setting.bool("Center dot", false));
    private final Setting tStyle = add(Setting.bool("T style", false));
    private final Setting rounded = add(Setting.bool("Rounded", true));
    private final Setting outline = add(Setting.bool("Outline", true));
    private final Setting outlineWidth = add(Setting.number("Outline width", 1, 3, 1));
    private final Setting color = add(Setting.color("Color", 0));
    private final Setting outlineColor = add(Setting.color("Outline color", 0));
    private final Setting dynamic = add(Setting.bool("Dynamic gap", true));
    private final Setting dynamicAmount = add(Setting.number("Dynamic amount", 0, 10, 4));
    private final Setting hitFlash = add(Setting.bool("Hit flash", true));
    private final Setting flashColor = add(Setting.color("Flash color", 0.0));
    private final Setting enemyColor = add(Setting.bool("Color over entity", true));
    private final Setting entityColor = add(Setting.color("Entity color", 0.02));

    private float spread;
    private float pulse;
    private long flashUntil;
    private long lastNano = System.nanoTime();

    public Crosshair() {
        super("Crosshair", Category.HUD, "Custom crosshair that replaces the vanilla one", false);
        color.setArgb(0xFFFFFFFF);
        outlineColor.setArgb(0xCC000000);
        WorldState.addHitListener(e -> {
            if (enabled) {
                flashUntil = System.currentTimeMillis() + FLASH_MS;
                pulse = 1f;
            }
        });
    }

    public void draw(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = now;

        float target = 0f;
        if (dynamic.on()) {
            double speed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
            float move = (float) Math.min(1.0, speed / 0.2);
            target = dynamicAmount.f() * Math.min(1.3f, move * 0.7f + (mc.player.isOnGround() ? 0f : 0.6f));
            target += dynamicAmount.f() * 0.8f * pulse;
        }
        spread += (target - spread) * Math.min(1f, dt * 14f);
        pulse = Math.max(0f, pulse - dt * 5f);

        int main = color.argb();
        if (hitFlash.on() && System.currentTimeMillis() < flashUntil) {
            main = flashColor.argb();
        } else if (enemyColor.on() && mc.targetedEntity instanceof LivingEntity) {
            main = entityColor.argb();
        }

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int g = Math.round(gap.f() + spread);
        if (outline.on()) {
            shape(ctx, cx, cy, g, Math.round(outlineWidth.f()), outlineColor.argb());
        }
        shape(ctx, cx, cy, g, 0, main);
    }

    /** Draws the chosen shape grown by {@code grow} pixels on every side (used for the outline). */
    private void shape(DrawContext ctx, int cx, int cy, int g, int grow, int argb) {
        int len = Math.round(size.f());
        int t = Math.round(thickness.f());
        switch (style.index()) {
            case 1 -> {
                int d = Math.max(2, len);
                rect(ctx, cx - d / 2, cy - d / 2, d, d, grow, d / 2, argb);
            }
            case 2 -> {
                float radius = len + g;
                Ui.ring(ctx, cx, cy, radius, t + 2 * grow, argb);
                if (centerDot.on()) {
                    rect(ctx, cx - t / 2, cy - t / 2, t, t, grow, t / 2, argb);
                }
            }
            case 3 -> {
                int half = len + g;
                rect(ctx, cx - half, cy - half, 2 * half + 1, t, grow, corner(t), argb);
                rect(ctx, cx - half, cy + half - t + 1, 2 * half + 1, t, grow, corner(t), argb);
                rect(ctx, cx - half, cy - half + t, t, 2 * half + 1 - 2 * t, grow, corner(t), argb);
                rect(ctx, cx + half - t + 1, cy - half + t, t, 2 * half + 1 - 2 * t, grow, corner(t), argb);
                if (centerDot.on()) {
                    rect(ctx, cx - t / 2, cy - t / 2, t, t, grow, corner(t), argb);
                }
            }
            default -> {
                rect(ctx, cx - g - len, cy - t / 2, len, t, grow, corner(t), argb);
                rect(ctx, cx + g, cy - t / 2, len, t, grow, corner(t), argb);
                rect(ctx, cx - t / 2, cy + g, t, len, grow, corner(t), argb);
                if (!tStyle.on()) {
                    rect(ctx, cx - t / 2, cy - g - len, t, len, grow, corner(t), argb);
                }
                if (centerDot.on()) {
                    rect(ctx, cx - t / 2, cy - t / 2, t, t, grow, corner(t), argb);
                }
            }
        }
    }

    private int corner(int t) {
        return rounded.on() ? t / 2 : 0;
    }

    private static void rect(DrawContext ctx, int x, int y, int w, int h, int grow, int radius, int argb) {
        Ui.roundRect(ctx, x - grow, y - grow, w + 2 * grow, h + 2 * grow, radius + grow, argb);
    }
}
