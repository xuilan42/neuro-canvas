package com.example.neurocanvas.module;

/** Removes or tones down distracting camera and screen effects. Every switch is independent. */
public final class Comfort extends Module {
    private final Setting lowFire = add(Setting.bool("Low fire", true));
    private final Setting fireDrop = add(Setting.number("Fire lowering", 0.05, 0.6, 0.3));
    private final Setting noBob = add(Setting.bool("No view bobbing", true));
    private final Setting noHurtTilt = add(Setting.bool("No hurt camera shake", true));
    private final Setting noFov = add(Setting.bool("No FOV change", true));
    private final Setting noVignette = add(Setting.bool("No vignette", false));
    private final Setting noPortal = add(Setting.bool("No portal overlay", false));
    private final Setting noPumpkin = add(Setting.bool("No pumpkin overlay", true));
    private final Setting noTotem = add(Setting.bool("No totem animation", false));

    public Comfort() {
        super("Comfort", Category.WORLD, "Lower fire, no camera bobbing or shake, steady field of view and fewer screen overlays", false);
    }

    /** How far to move the fire overlay down (0 when off). */
    public float fireDrop() { return enabled && lowFire.on() ? fireDrop.f() : 0f; }
    public boolean noBob() { return enabled && noBob.on(); }
    public boolean noHurtTilt() { return enabled && noHurtTilt.on(); }
    public boolean noFov() { return enabled && noFov.on(); }
    public boolean noVignette() { return enabled && noVignette.on(); }
    public boolean noPortal() { return enabled && noPortal.on(); }
    public boolean noPumpkin() { return enabled && noPumpkin.on(); }
    public boolean noTotem() { return enabled && noTotem.on(); }
}
