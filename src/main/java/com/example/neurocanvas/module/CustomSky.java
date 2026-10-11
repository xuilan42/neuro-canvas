package com.example.neurocanvas.module;

import net.minecraft.util.math.Vec3d;

/** Replaces the colour of the sky and the clouds. The horizon fog follows the sky colour. */
public final class CustomSky extends Module {
    private final Setting sky = add(Setting.color("Sky color", 0.6));
    private final Setting skyStrength = add(Setting.number("Sky strength", 0, 1, 0.8));
    private final Setting clouds = add(Setting.color("Cloud color", 0.9));
    private final Setting cloudStrength = add(Setting.number("Cloud strength", 0, 1, 0.7));
    private final Setting rainbow = add(Setting.bool("Rainbow sky", false));
    private final Setting rainbowSpeed = add(Setting.number("Rainbow speed", 0.1, 5, 1));

    public CustomSky() {
        super("CustomSky", Category.WORLD, "Your own sky and cloud colours", false);
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Vec3d rgb(int argb) {
        return new Vec3d(((argb >> 16) & 0xFF) / 255.0, ((argb >> 8) & 0xFF) / 255.0, (argb & 0xFF) / 255.0);
    }

    public Vec3d skyColor(Vec3d vanilla) {
        Vec3d target;
        if (rainbow.on()) {
            float hue = (float) ((System.currentTimeMillis() / 1000.0 * 0.05 * rainbowSpeed.value) % 1.0);
            target = rgb(java.awt.Color.HSBtoRGB(hue, 0.7f, 1f));
        } else {
            target = rgb(sky.argb());
        }
        return lerp(vanilla, target, skyStrength.value);
    }

    public Vec3d cloudColor(Vec3d vanilla) {
        return lerp(vanilla, rgb(clouds.argb()), cloudStrength.value);
    }
}
