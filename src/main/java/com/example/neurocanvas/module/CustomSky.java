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
    private final Setting horizon = add(Setting.color("Horizon color", 0.85));
    private final Setting horizonBlend = add(Setting.number("Horizon gradient", 0, 1, 0.5));
    private final Setting sunset = add(Setting.bool("Sunset glow", false));

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

    public boolean sunsetGlow() {
        return sunset.on();
    }

    /**
     * The colour the sky fades into at the horizon, and that the distant terrain fades into. It is the sky colour
     * blended towards the horizon colour, so sky, horizon and fog form one smooth gradient with no seam.
     */
    private float[] horizonColor(net.minecraft.client.world.ClientWorld world, net.minecraft.client.render.Camera camera, float tickDelta) {
        Vec3d skyNow = world.getSkyColor(camera.getPos(), tickDelta);
        float bright = Math.max(0.2f, Math.min(1f, world.getSkyBrightness(tickDelta) * 0.8f + 0.2f));
        Vec3d target = lerp(skyNow, rgb(horizon.argb()).multiply(bright), horizonBlend.value);
        return new float[] {(float) target.x, (float) target.y, (float) target.z};
    }

    private boolean active(net.minecraft.client.render.Camera camera) {
        net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;
        return enabled && world != null && world.getDimension().hasSkyLight() && !CustomFog.specialFog(camera);
    }

    public void applyFogColor(net.minecraft.client.render.Camera camera, float tickDelta) {
        if (active(camera)) {
            float[] c = horizonColor(net.minecraft.client.MinecraftClient.getInstance().world, camera, tickDelta);
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogColor(c[0], c[1], c[2], 1f);
        }
    }

    public void applyClearColor(net.minecraft.client.render.Camera camera, float tickDelta) {
        if (active(camera)) {
            float[] c = horizonColor(net.minecraft.client.MinecraftClient.getInstance().world, camera, tickDelta);
            com.mojang.blaze3d.systems.RenderSystem.clearColor(c[0], c[1], c[2], 0f);
        }
    }

    public Vec3d cloudColor(Vec3d vanilla) {
        return lerp(vanilla, rgb(clouds.argb()), cloudStrength.value);
    }
}
