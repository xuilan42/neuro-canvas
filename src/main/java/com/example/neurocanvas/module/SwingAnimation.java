package com.example.neurocanvas.module;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/** Replaces the vanilla hand swing with other motions and lets you change how fast it is. */
public final class SwingAnimation extends Module {
    private final Setting style = add(Setting.choice("Style", 1, "Vanilla", "Smooth", "Slash", "Chop", "Poke", "Whip"));
    private final Setting strength = add(Setting.number("Strength", 0.2, 2, 1));
    private final Setting speed = add(Setting.number("Speed", 0.3, 3, 1));
    private final Setting hand = add(Setting.choice("Hand", 0, "Both", "Main"));

    public SwingAnimation() {
        super("SwingAnimation", Category.VISUALS, "Custom hand swing styles and speed", false);
    }

    /** True when the vanilla swing motion must be switched off because we draw our own. */
    public boolean replacesVanilla(Hand h) {
        return enabled && style.index() != 0 && appliesTo(h);
    }

    private boolean appliesTo(Hand h) {
        return hand.index() == 0 || h == Hand.MAIN_HAND;
    }

    public int adjustDuration(int base) {
        return Math.max(1, Math.round(base / speed.f()));
    }

    /** @param p swing progress 0..1 of this hand, @param side +1 right arm, -1 left arm */
    public void apply(MatrixStack m, Hand h, float p, float side) {
        if (!replacesVanilla(h) || p <= 0f) {
            return;
        }
        float k = strength.f();
        float s = (float) Math.sin(p * Math.PI);
        switch (style.index()) {
            case 1 -> {
                m.translate(0.0, -0.1 * s * k, -0.15 * s * k);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-20f * s * k));
            }
            case 2 -> {
                m.translate(-0.1 * s * k * side, 0.0, 0.0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-65f * s * k * side));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25f * s * k * side));
            }
            case 3 -> m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-75f * s * k));
            case 4 -> m.translate(0.0, 0.0, -0.7 * s * k);
            case 5 -> {
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-35f * s * k * side));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(55f * s * k * side));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-20f * s * k));
            }
            default -> { }
        }
    }
}
