package com.example.neurocanvas.module;

public enum Category {
    VISUALS("Visuals"), COMBAT("Combat"), WORLD("World"), HUD("HUD"), MISC("Misc"), AI("NeuroAura"), CONFIG("Configs");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
