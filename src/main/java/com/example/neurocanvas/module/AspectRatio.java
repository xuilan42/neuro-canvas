package com.example.neurocanvas.module;

/** Stretches the 3D view to another aspect ratio without changing the window. */
public final class AspectRatio extends Module {
    private static final double[] PRESETS = {0, 4.0 / 3.0, 5.0 / 4.0, 16.0 / 10.0, 21.0 / 9.0, 1.0};

    private final Setting preset = add(Setting.choice("Preset", 1, "Custom", "4:3", "5:4", "16:10", "21:9", "1:1"));
    private final Setting custom = add(Setting.number("Custom ratio", 0.5, 3, 1.33));

    public AspectRatio() {
        super("AspectRatio", Category.WORLD, "Change the shape of the game view (4:3 stretches players wider)", false);
    }

    public float ratio() {
        return (float) (preset.index() == 0 ? custom.value : PRESETS[preset.index()]);
    }
}
