package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectSpec;

/** A module whose look is the standard set of knobs (two colours, size, speed, pulse, count, alpha). */
public abstract class EffectModule extends Module {
    protected final Setting hue1;
    protected final Setting hue2;
    protected final Setting size;
    protected final Setting speed;
    protected final Setting pulse;
    protected final Setting count;
    protected final Setting alpha;

    protected EffectModule(String name, Category category, String description, boolean on,
                           double h1, double h2, double size, double speed, int count) {
        super(name, category, description, on);
        this.hue1 = add(Setting.color("Color 1", h1));
        this.hue2 = add(Setting.color("Color 2", h2));
        this.size = add(Setting.number("Size", 0.1, 4, size));
        this.speed = add(Setting.number("Speed", 0.1, 8, speed));
        this.pulse = add(Setting.number("Pulse", 0, 1, 0.2));
        this.count = add(Setting.number("Count", 1, 64, count));
        this.alpha = add(Setting.number("Alpha", 0.05, 1, 0.8));
    }

    protected EffectSpec spec(EffectSpec.Type type) {
        return fill(new EffectSpec(), type);
    }

    /** Writes the module's current settings into an existing spec (no allocation, for per-frame use). */
    protected EffectSpec fill(EffectSpec s, EffectSpec.Type type) {
        s.type = type;
        s.color1 = hue1.argb() & 0xFFFFFF;
        s.color2 = hue2.argb() & 0xFFFFFF;
        s.size = size.f();
        s.speed = speed.f();
        s.pulse = pulse.f();
        s.count = (int) count.value;
        s.alpha = alpha.f() * ((hue1.argb() >>> 24) / 255f);
        return s;
    }

    public static int rgb(float hue) {
        return java.awt.Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF;
    }
}
