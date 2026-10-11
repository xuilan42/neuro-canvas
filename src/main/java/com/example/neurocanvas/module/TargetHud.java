package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/** Panel with the face, name, animated health bar and distance of the entity you last hit. */
public final class TargetHud extends Module {
    private static final int BASE_W = 140;
    private static final int BASE_H = 46;
    private static final HudLayout.Element ELEMENT = HudLayout.register("targethud", "Target HUD", false, 0.54, 0.52);

    static {
        ELEMENT.w = BASE_W;
        ELEMENT.h = BASE_H;
    }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.75));
    private final Setting duration = add(Setting.number("Duration (s)", 1, 20, 6));
    private final Setting showArmor = add(Setting.bool("Show armor", true));
    private final Setting showDistance = add(Setting.bool("Show distance", true));
    private final Setting dynamic = add(Setting.bool("Dynamic color", true));
    private final Setting barColor1 = add(Setting.color("Bar color 1", 0.38));
    private final Setting barColor2 = add(Setting.color("Bar color 2", 0.5));

    private LivingEntity shown;
    private float shownHealth;
    private float anim;
    private long lastNano = System.nanoTime();

    public TargetHud() {
        super("TargetHUD", Category.HUD, "Face, name and health of the entity you hit", true);
    }

    @Override
    public void tick(MinecraftClient mc) {
        WorldState.targetLifetimeMs = (long) (duration.f() * 1000);
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = now;

        LivingEntity current = NeuroCanvasClient.state().target;
        if (current != null) {
            if (current != shown) {
                shown = current;
                shownHealth = current.getHealth();
            }
        }
        float goal = current != null ? 1f : 0f;
        anim += Math.signum(goal - anim) * Math.min(Math.abs(goal - anim), dt * 6f);

        float s = scale.f();
        ELEMENT.w = Math.round(BASE_W * s);
        ELEMENT.h = Math.round(BASE_H * s);
        if (shown == null || anim <= 0.01f || mc.options.hudHidden) {
            return;
        }
        shownHealth += (shown.getHealth() - shownHealth) * Math.min(1f, dt * 8f);

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        Theme th = Interface.theme();
        int x = ELEMENT.x(sw);
        int y = ELEMENT.y(sh) + Math.round((1f - anim) * 8f * s);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(s, s, 1f);

        Ui.roundRect(ctx, 0, 0, BASE_W, BASE_H, 8, fade(withAlpha(th.panel(), background.f())));
        drawFace(ctx, th, mc);

        String name = mc.textRenderer.trimToWidth(shown.getName().getString(), 70);
        Ui.text(ctx, mc.textRenderer, name, 46, 8, fade(th.text()));
        if (showDistance.on() && mc.player != null) {
            String d = String.format("%.1f m", mc.player.distanceTo(shown));
            Ui.text(ctx, mc.textRenderer, d, BASE_W - 8 - Ui.width(mc.textRenderer, d), 8, fade(th.dim()));
        }

        float max = Math.max(1f, shown.getMaxHealth());
        float ratio = MathHelper.clamp(shownHealth / max, 0f, 1f);
        int c1;
        int c2;
        if (dynamic.on()) {
            int base = 0xFF000000 | (java.awt.Color.HSBtoRGB(ratio * 0.33f, 0.85f, 1f) & 0xFFFFFF);
            c1 = Ui.mix(base, 0xFF000000, 0.35f);
            c2 = base;
        } else {
            c1 = barColor1.argb();
            c2 = barColor2.argb();
        }
        int barX = 46;
        int barW = BASE_W - barX - 8;
        Ui.roundRect(ctx, barX, 22, barW, 7, 3, fade(th.hover()));
        int fill = Math.round((barW - 2) * ratio);
        if (fill > 0) {
            Ui.gradientH(ctx, barX + 1, 23, fill, 5, fade(c1), fade(c2));
        }

        StringBuilder info = new StringBuilder(String.format("%.1f HP", shownHealth));
        if (shown.getAbsorptionAmount() > 0) {
            info.append(String.format(" +%.0f", shown.getAbsorptionAmount()));
        }
        if (showArmor.on() && shown.getArmor() > 0) {
            info.append("   Armor ").append(shown.getArmor());
        }
        Ui.text(ctx, mc.textRenderer, info.toString(), barX, 33, fade(th.dim()));

        ctx.getMatrices().pop();
    }

    private void drawFace(DrawContext ctx, Theme th, MinecraftClient mc) {
        Ui.roundRect(ctx, 5, 5, 36, 36, 7, fade(th.panel2()));
        if (shown instanceof AbstractClientPlayerEntity player) {
            ctx.setShaderColor(1f, 1f, 1f, anim);
            ctx.drawTexture(player.getSkinTexture(), 8, 8, 30, 30, 8f, 8f, 8, 8, 64, 64);
            ctx.drawTexture(player.getSkinTexture(), 8, 8, 30, 30, 40f, 8f, 8, 8, 64, 64);
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            String letter = shown.getName().getString().isEmpty() ? "?" : shown.getName().getString().substring(0, 1).toUpperCase();
            Ui.textCentered(ctx, mc.textRenderer, letter, 23, 18, fade(th.text()));
        }
    }

    private int fade(int argb) {
        return ((int) ((argb >>> 24) * anim) << 24) | (argb & 0xFFFFFF);
    }

    private static int withAlpha(int argb, float a) {
        return ((int) ((argb >>> 24) * a) << 24) | (argb & 0xFFFFFF);
    }
}
