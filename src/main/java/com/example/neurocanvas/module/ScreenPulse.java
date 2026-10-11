package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** When you take damage the screen flashes at the edges and shakes briefly. */
public final class ScreenPulse extends Module implements WorldState.CombatListener {
    private final Setting flash = add(Setting.bool("Flash", true));
    private final Setting flashColor = add(Setting.color("Flash color", 0));
    private final Setting flashStrength = add(Setting.number("Flash strength", 0.1, 1, 0.65));
    private final Setting shake = add(Setting.bool("Shake", true));
    private final Setting shakeStrength = add(Setting.number("Shake strength", 0.2, 3, 1));
    private final Setting duration = add(Setting.number("Duration (s)", 0.1, 1.5, 0.5));
    private final Setting onDealt = add(Setting.bool("Also when you hit", false));

    private long start;
    private float base;

    public ScreenPulse() {
        super("ScreenPulse", Category.VISUALS, "Edge flash and screen shake when you take damage", false);
        flashColor.setArgb(0xFFFF2020);
        WorldState.addCombatListener(this);
    }

    @Override
    public void onDamage(String name, float amount, boolean dealt) {
        if (!enabled || (dealt && !onDealt.on())) {
            return;
        }
        start = System.currentTimeMillis();
        base = Math.min(1f, 0.35f + amount / 8f) * (dealt ? 0.5f : 1f);
    }

    /** 1 right after a hit, fading to 0. */
    private float level() {
        float age = (System.currentTimeMillis() - start) / (duration.f() * 1000f);
        if (age >= 1f || base <= 0f) {
            return 0f;
        }
        float k = 1f - age;
        return base * k * k;
    }

    public void draw(DrawContext ctx, MinecraftClient mc) {
        float v = level();
        if (!flash.on() || v <= 0.005f) {
            return;
        }
        int c = flashColor.argb();
        float a = Math.min(1f, v * flashStrength.f() * ((c >>> 24) / 255f));
        Ui.vignette(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), 0.35f, Ui.alpha(c & 0xFFFFFF, a));
    }

    /** Tilts the camera a little; called at the start of world rendering. */
    public void applyShake(MatrixStack matrices) {
        float v = level();
        if (!shake.on() || v <= 0.005f) {
            return;
        }
        double t = System.currentTimeMillis() / 1000.0;
        float amp = v * shakeStrength.f();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 55) * 2.2f * amp));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(t * 41) * 1.1f * amp));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.cos(t * 47) * 1.1f * amp));
    }
}
