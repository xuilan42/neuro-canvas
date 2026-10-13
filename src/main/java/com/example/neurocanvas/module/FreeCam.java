package com.example.neurocanvas.module;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Detaches the camera from your character and lets you fly it around. Your character stands still and cannot act. */
public final class FreeCam extends Module {
    private final Setting speed = add(Setting.number("Speed", 0.2, 5, 1));

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean init;
    private long last;

    public FreeCam() {
        super("FreeCam", Category.WORLD, "Fly the camera around while your character stays in place (WASD, Space up, Shift down)", false);
        risky = true;
        aiControllable = false;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            enabled = false;
            init = false;
        });
    }

    public boolean active() {
        return enabled && init;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (!enabled) {
            init = false;
        } else if (!init) {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d p = cam.getPos();
            x = p.x;
            y = p.y;
            z = p.z;
            yaw = cam.getYaw();
            pitch = cam.getPitch();
            last = System.nanoTime();
            init = true;
        }
    }

    /** Reads the physical key behind a binding, because the binding itself reports "released" during FreeCam. */
    private static boolean held(MinecraftClient mc, KeyBinding binding) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        return key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode());
    }

    /** Called once per frame by the camera: moves the free camera according to the movement keys. */
    public void step(MinecraftClient mc) {
        long now = System.nanoTime();
        double dt = Math.min((now - last) / 1_000_000_000.0, 0.1);
        last = now;
        if (mc.currentScreen != null) {
            return;
        }
        double fwd = (held(mc, mc.options.forwardKey) ? 1 : 0) - (held(mc, mc.options.backKey) ? 1 : 0);
        double side = (held(mc, mc.options.rightKey) ? 1 : 0) - (held(mc, mc.options.leftKey) ? 1 : 0);
        double up = (held(mc, mc.options.jumpKey) ? 1 : 0) - (held(mc, mc.options.sneakKey) ? 1 : 0);
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        double fx = -Math.sin(ry) * Math.cos(rp);
        double fy = -Math.sin(rp);
        double fz = Math.cos(ry) * Math.cos(rp);
        double sx = -Math.cos(ry);
        double sz = -Math.sin(ry);
        double v = speed.value * 10.0 * dt;
        x += (fx * fwd + sx * side) * v;
        y += (fy * fwd + up) * v;
        z += (fz * fwd + sz * side) * v;
    }
}
