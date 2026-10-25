package com.example.neurocanvas.fx;

/** Declarative description of one effect. This is what the neural network produces. */
public final class EffectSpec {
    public enum Type { TARGET_RING, ORBIT_PARTICLES, JUMP_RIPPLE }

    public Type type = Type.TARGET_RING;
    public int color1 = 0xFF3355;
    public int color2 = 0xFFAA33;
    public float size = 0.8f;
    public float speed = 1.0f;
    public float pulse = 0.2f;
    public int count = 12;
    public float alpha = 0.8f;
    /** Line and ring thickness multiplier (1 = default). */
    public float thickness = 1f;
    /** Style variant chosen in the module (for the ring: 0 stays at the feet, 1 sweeps up and down the body). */
    public int variant;

    public void clamp() {
        if (type == null) type = Type.TARGET_RING;
        size = Math.max(0.1f, Math.min(size, 4f));
        speed = Math.max(0.1f, Math.min(speed, 8f));
        pulse = Math.max(0f, Math.min(pulse, 1f));
        count = Math.max(1, Math.min(count, com.example.neurocanvas.module.NoLimited.cap(64)));
        alpha = Math.max(0.05f, Math.min(alpha, 1f));
        thickness = Math.max(0.2f, Math.min(thickness, 5f));
    }
}
