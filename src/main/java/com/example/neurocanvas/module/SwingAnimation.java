package com.example.neurocanvas.module;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/** Replaces the vanilla hand swing with other motions and lets you change how fast it is. */
public final class SwingAnimation extends Module {
    private final Setting style = add(Setting.choice("Style", 1, "Vanilla", "Smooth", "Slash", "Chop", "Poke", "Whip", "Slap", "Spear"));
    private final Setting easing = add(Setting.choice("Easing", 1, "Sine", "Cubic", "Back"));
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

    /**
     * The shape of one swing, 0..1..0: a quick rise with the chosen easing to the peak at 40% of the swing, then a slow soft return.
     * "Back" overshoots a little at the peak, which makes a hit feel snappy.
     */
    private float profile(float p) {
        float peak = 0.4f;
        if (p < peak) {
            float x = p / peak;
            return switch (easing.index()) {
                case 1 -> 1f - (float) Math.pow(1f - x, 3);
                case 2 -> {
                    float c1 = 1.70158f;
                    float c3 = c1 + 1f;
                    float d = x - 1f;
                    yield 1f + c3 * d * d * d + c1 * d * d;
                }
                default -> (float) Math.sin(x * Math.PI * 0.5);
            };
        }
        float y = (p - peak) / (1f - peak);
        return (float) (1.0 + Math.cos(Math.PI * y)) * 0.5f;
    }

    /** @param p swing progress 0..1 of this hand (the game's own progress, already tick-delta smoothed), @param side +1 right arm, -1 left arm */
    public void apply(MatrixStack m, Hand h, float p, float side) {
        if (!replacesVanilla(h) || p <= 0f) {
            return;
        }
        float k = strength.f();
        float s = profile(Math.min(p, 1f));
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
            case 6 -> {
                // Slap: a backhand sweep across the screen.
                m.translate(-0.4 * s * k * side, -0.06 * s * k, -0.05 * s * k);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70f * s * k * side));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-35f * s * k * side));
            }
            case 7 -> {
                // Spear: the hand draws back a little first, then thrusts forward.
                float pull = p < 0.25f ? (float) Math.sin(p / 0.25f * Math.PI) : 0f;
                m.translate(0.0, 0.03 * s * k, (0.14 * pull - 0.85 * s) * k);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8f * s * k));
            }
            default -> { }
        }
    }
}
