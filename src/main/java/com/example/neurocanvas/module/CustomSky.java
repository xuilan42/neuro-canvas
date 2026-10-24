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
    private final Setting skyEffect = add(Setting.choice("Sky effect", 0, "None", "Space", "Black hole"));
    private final Setting holeSize = add(Setting.number("Black hole size", 0.05, 0.4, 0.16));
    private final Setting holeYaw = add(Setting.number("Black hole direction", 0, 360, 40));
    private final Setting holeHeight = add(Setting.number("Black hole height", -10, 80, 22));
    private final Setting nebula1 = add(Setting.color("Nebula color 1", 0.74));
    private final Setting nebula2 = add(Setting.color("Nebula color 2", 0.58));
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
        if (skyEffect.index() != 0) {
            // Space is black at the horizon too, so the far terrain fades into it instead of into a blue haze.
            return new float[] {0.012f, 0.015f, 0.035f};
        }
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

    private static final org.joml.Matrix4f INV = new org.joml.Matrix4f();
    private static final org.joml.Matrix3f ROT = new org.joml.Matrix3f();

    /** Draws the procedural night sky over the vanilla one (called when the game has just drawn its own sky; the world is drawn after it). */
    public void drawSky(org.joml.Matrix4f projection, net.minecraft.client.render.Camera camera) {
        if (skyEffect.index() == 0 || !active(camera) || !Interface.shaderVisuals()) {
            return;
        }
        net.minecraft.client.gl.ShaderProgram p = com.example.neurocanvas.fx.Shaders.get("neuro_sky");
        net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;
        if (p == null || world == null) {
            return;
        }
        set(p, "Mode", skyEffect.index() == 2 ? 1f : 2f);
        set(p, "HoleSize", holeSize.f());
        set(p, "NcGameTime", (float) (System.currentTimeMillis() % 1_000_000L / 1000.0));
        double yaw = Math.toRadians(holeYaw.value);
        double pitch = Math.toRadians(holeHeight.value);
        net.minecraft.client.gl.GlUniform dir = p.getUniform("HoleDir");
        if (dir != null) {
            dir.set((float) (Math.sin(yaw) * Math.cos(pitch)), (float) Math.sin(pitch), (float) (-Math.cos(yaw) * Math.cos(pitch)));
        }
        color(p, "Color1", nebula1.argb());
        color(p, "Color2", nebula2.argb());
        net.minecraft.client.gl.GlUniform inv = p.getUniform("InvProjMat");
        if (inv != null) {
            inv.set(INV.set(projection).invert());
        }
        net.minecraft.client.gl.GlUniform rot = p.getUniform("CamRot");
        if (rot != null) {
            rot.set(ROT.set(camera.getRotation()));
        }
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> p);
        net.minecraft.client.render.BufferBuilder b = net.minecraft.client.render.Tessellator.getInstance().getBuffer();
        b.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION);
        b.vertex(-1, -1, 0).next();
        b.vertex(1, -1, 0).next();
        b.vertex(1, 1, 0).next();
        b.vertex(-1, 1, 0).next();
        net.minecraft.client.render.Tessellator.getInstance().draw();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.example.neurocanvas.fx.Shaders.finish();
    }

    private static void set(net.minecraft.client.gl.ShaderProgram p, String name, float v) {
        net.minecraft.client.gl.GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }

    private static void color(net.minecraft.client.gl.ShaderProgram p, String name, int c) {
        net.minecraft.client.gl.GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
