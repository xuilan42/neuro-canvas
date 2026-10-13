package com.example.neurocanvas.module;

/** Overrides the weather you see. Only on your screen; the server's weather is untouched. */
public final class WeatherChanger extends Module {
    private final Setting mode = add(Setting.choice("Weather", 0, "Clear", "Rain", "Thunderstorm", "Custom"));
    private final Setting rain = add(Setting.number("Rain strength", 0, 1, 0.8));
    private final Setting thunder = add(Setting.number("Thunder strength", 0, 1, 0.6));

    public WeatherChanger() {
        super("WeatherChanger", Category.WORLD, "Choose clear sky, rain or a thunderstorm on your screen", false);
    }

    /** The rain level to show, or the real one when the module is off. */
    public float rain(float real) {
        if (!enabled) {
            return real;
        }
        return switch (mode.index()) {
            case 1, 2 -> 1f;
            case 3 -> rain.f();
            default -> 0f;
        };
    }

    public float thunder(float real) {
        if (!enabled) {
            return real;
        }
        return switch (mode.index()) {
            case 2 -> 1f;
            case 3 -> thunder.f();
            default -> 0f;
        };
    }
}
