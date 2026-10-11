package com.example.neurocanvas.module;

/** Narrows the field of view while active. Bound to C in hold mode by default; change it in the module. */
public final class Zoom extends Module {
    private final Setting factor = add(Setting.number("Zoom", 1.5, 20, 4));
    private final Setting smooth = add(Setting.bool("Smooth", true));

    private double current = 1.0;
    private long lastNano = System.nanoTime();

    public Zoom() {
        super("Zoom", Category.WORLD, "Zoom in while the key is held (default C)", false);
        key = 67;
        hold = true;
    }

    /** Called once per frame with the vanilla field of view; returns the zoomed one. */
    public double apply(double fov) {
        long now = System.nanoTime();
        double dt = Math.min((now - lastNano) / 1_000_000_000.0, 0.1);
        lastNano = now;
        double target = enabled ? factor.value : 1.0;
        current = smooth.on() ? current + (target - current) * Math.min(1.0, dt * 12.0) : target;
        return current > 1.001 ? fov / current : fov;
    }
}
