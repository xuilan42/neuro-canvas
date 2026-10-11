package com.example.neurocanvas.gui;

/** The shape and texture of a theme, as opposed to its palette: corners, borders, glow, blur, overlay effect, switches, font. */
public record ThemeStyle(float radius, float border, int borderColor, float glow, boolean shadow, float blur,
                    Overlay overlay, float overlayStrength, SwitchStyle switchStyle, String font) {
    public enum Overlay { NONE, SCANLINES, GRID, AURORA }

    public enum SwitchStyle { PILL, BRACKET }

    /** Border colour sentinels (anything else is a real ARGB colour). */
    public static final int BORDER_ACCENT = 1;
    public static final int BORDER_DIM = 2;

    public static final ThemeStyle CLASSIC = new ThemeStyle(1f, 0f, BORDER_DIM, 0f, true, 0f, Overlay.NONE, 0f, SwitchStyle.PILL, null);

    public ThemeStyle withFont(String f) {
        return new ThemeStyle(radius, border, borderColor, glow, shadow, blur, overlay, overlayStrength, switchStyle, f);
    }

    public int resolveBorder(Theme th) {
        return switch (borderColor) {
            case BORDER_ACCENT -> th.accent();
            case BORDER_DIM -> Ui.mix(th.dim(), th.panel() | 0xFF000000, 0.55f);
            default -> borderColor;
        };
    }

    /** Overlays that look right on top of the content (CRT lines) as opposed to behind it (grid, aurora). */
    public boolean overlayOnTop() {
        return overlay == Overlay.SCANLINES;
    }
}
