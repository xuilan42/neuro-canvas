package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;

/** Makes armour and the item in the hand shimmer through colours: a rainbow, a gradient between two colours, or a glow. */
public final class Shimmer extends Module {
    private final Setting mode = add(Setting.choice("Mode", 0, "Rainbow", "Gradient", "Pulse"));
    private final Setting color1 = add(Setting.color("Color 1", 0.55));
    private final Setting color2 = add(Setting.color("Color 2", 0.9));
    private final Setting speed = add(Setting.number("Speed", 0.1, 5, 1));
    private final Setting strength = add(Setting.number("Strength", 0.1, 1, 0.85));
    private final Setting saturation = add(Setting.number("Rainbow saturation", 0.2, 1, 0.8));
    private final Setting flow = add(Setting.number("Flow between pieces", 0, 1, 0.5));
    private final Setting who = add(Setting.choice("Who", 0, "Me", "Everyone"));
    private final Setting helmet = add(Setting.bool("Helmet", true));
    private final Setting chest = add(Setting.bool("Chestplate", true));
    private final Setting legs = add(Setting.bool("Leggings", true));
    private final Setting boots = add(Setting.bool("Boots", true));
    private final Setting held = add(Setting.bool("Held items", true));

    public Shimmer() {
        super("Shimmer", Category.COSMETICS, "Armour and held items that shift through rainbow or gradient colours", false);
        color1.setArgb(0xFF3DDC97);
        color2.setArgb(0xFFB06BFF);
    }

    private boolean applies(LivingEntity entity) {
        if (!enabled || entity == null) {
            return false;
        }
        return who.index() == 1 || entity == MinecraftClient.getInstance().player;
    }

    private float[] colourAt(double phase) {
        double t = System.currentTimeMillis() / 1000.0;
        float r;
        float g;
        float b;
        switch (mode.index()) {
            case 1 -> {
                float k = (float) (0.5 + 0.5 * Math.sin(Math.PI * 2 * (t * 0.15 * speed.value + phase * flow.value)));
                int a = color1.argb();
                int c = color2.argb();
                r = mix(((a >> 16) & 0xFF) / 255f, ((c >> 16) & 0xFF) / 255f, k);
                g = mix(((a >> 8) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, k);
                b = mix((a & 0xFF) / 255f, (c & 0xFF) / 255f, k);
            }
            case 2 -> {
                float k = (float) (0.5 + 0.5 * Math.sin(Math.PI * 2 * (t * 0.3 * speed.value + phase * flow.value)));
                int a = color1.argb();
                float scale = 0.4f + 0.6f * k;
                r = ((a >> 16) & 0xFF) / 255f * scale;
                g = ((a >> 8) & 0xFF) / 255f * scale;
                b = (a & 0xFF) / 255f * scale;
            }
            default -> {
                float hue = (float) (((t * 0.12 * speed.value + phase * flow.value) % 1.0 + 1.0) % 1.0);
                int c = java.awt.Color.HSBtoRGB(hue, saturation.f(), 1f);
                r = ((c >> 16) & 0xFF) / 255f;
                g = ((c >> 8) & 0xFF) / 255f;
                b = (c & 0xFF) / 255f;
            }
        }
        float s = strength.f();
        return new float[] {mix(1f, r, s), mix(1f, g, s), mix(1f, b, s)};
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Multiplier for the colour of one armour piece, or null when it stays as it is. */
    public float[] armorTint(LivingEntity entity, EquipmentSlot slot) {
        if (!applies(entity)) {
            return null;
        }
        return switch (slot) {
            case HEAD -> helmet.on() ? colourAt(0.0) : null;
            case CHEST -> chest.on() ? colourAt(0.2) : null;
            case LEGS -> legs.on() ? colourAt(0.4) : null;
            case FEET -> boots.on() ? colourAt(0.6) : null;
            default -> null;
        };
    }

    public float[] heldTint(LivingEntity entity) {
        return applies(entity) && held.on() ? colourAt(0.8) : null;
    }
}
