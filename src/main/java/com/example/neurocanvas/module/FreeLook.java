package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/** While active, the mouse turns only the camera, so you can look around without changing where your character faces. */
public final class FreeLook extends Module {
    private final Setting thirdPerson = add(Setting.bool("Switch to third person", true));

    private float yaw;
    private float pitch;
    private boolean init;
    private Perspective previous;

    public FreeLook() {
        super("FreeLook", Category.WORLD, "Look around with the camera while your character keeps facing forward (Left Alt, toggle)", false);
        aiControllable = false;
        key = 342;
        hold = false;
    }

    public boolean active() {
        return enabled && init;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (!enabled) {
            if (init) {
                init = false;
                if (previous != null && thirdPerson.on()) {
                    mc.options.setPerspective(previous);
                }
                previous = null;
            }
        } else if (!init && mc.player != null) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            previous = mc.options.getPerspective();
            if (thirdPerson.on() && previous.isFirstPerson()) {
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            init = true;
        }
    }
}
