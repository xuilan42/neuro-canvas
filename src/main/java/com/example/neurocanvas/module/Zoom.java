package com.example.neurocanvas.module;

/** Narrows the field of view while active. Bound to C in hold mode by default; change it in the module. */
public final class Zoom extends Module {
    private final Setting factor = add(Setting.number("Zoom", 1.5, 20, 4));
    private final Setting smooth = add(Setting.bool("Smooth", true));
    private final Setting wheel = add(Setting.bool("Mouse wheel changes zoom", true));
    private final Setting slowAim = add(Setting.number("Lower sensitivity", 0, 1, 1));

    private double current = 1.0;
    private long lastNano = System.nanoTime();

    public Zoom() {
        super("Zoom", Category.WORLD, "Zoom in while the key is held (default C)", false);
        key = 67;
        hold = true;
    }

    /**
     * While zoom is held the mouse wheel changes the zoom level instead of scrolling the hotbar.
     * @return true when the scroll was used
     */
    public boolean scroll(double vertical) {
        if (!enabled || !wheel.on() || vertical == 0) {
            return false;
        }
        double next = factor.value * (vertical > 0 ? 1.18 : 1 / 1.18);
        factor.set(Math.max(factor.min, Math.min(factor.max, next)));
        return true;
    }

    /**
     * How much the mouse movement is multiplied while zoomed. At 1 it is exactly as slow as the picture is enlarged
     * (4x zoom, a quarter of the speed), so aiming stays steady; 0 leaves the sensitivity alone.
     */
    public double sensitivityScale() {
        return current > 1.001 ? 1.0 + (1.0 / current - 1.0) * slowAim.value : 1.0;
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
