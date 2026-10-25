package com.example.neurocanvas.module;

/** Raises the brightness (gamma) far above the normal slider so dark places are fully visible. */
public final class Fullbright extends Module {
    private final Setting brightness = add(Setting.number("Brightness", 1, 20, 15));

    public Fullbright() {
        super("Fullbright", Category.VISUALS, "See clearly in the dark", false);
        risky = true;
    }

    public double gamma() {
        return brightness.value;
    }
}
