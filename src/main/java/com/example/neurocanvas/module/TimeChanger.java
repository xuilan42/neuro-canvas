package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;

/** Overrides the time of day you see. Only on your screen; the server's clock is untouched. */
public final class TimeChanger extends Module {
    private final Setting mode = add(Setting.choice("Mode", 0, "Fixed", "Moving"));
    private final Setting time = add(Setting.number("Time", 0, 24000, 6000));
    private final Setting speed = add(Setting.number("Speed", 0.1, 20, 1));

    private double elapsed;

    public TimeChanger() {
        super("TimeChanger", Category.WORLD, "Set the time of day (0 sunrise, 6000 noon, 12000 sunset, 18000 midnight)", false);
    }

    @Override
    public void tick(MinecraftClient mc) {
        elapsed += speed.value;
    }

    public long currentTime(long real) {
        if (mode.index() == 0) {
            return (long) time.value;
        }
        return Math.floorMod((long) (time.value + elapsed), 24000L);
    }
}
