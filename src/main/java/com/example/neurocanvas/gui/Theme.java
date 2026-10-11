package com.example.neurocanvas.gui;

import java.util.List;

/** Colour palette of the interface. All colours are ARGB. */
public record Theme(String name, int bg, int panel, int panel2, int hover, int accent, int accent2, int text, int dim) {
    public static final List<Theme> ALL = List.of(
            new Theme("Mint", 0xF2101412, 0xFF171C1A, 0xFF1F2623, 0xFF28332E, 0xFF3DDC97, 0xFF2BB7A3, 0xFFEAF2EE, 0xFF8A9A93),
            new Theme("Violet", 0xF212101A, 0xFF1A1724, 0xFF241F33, 0xFF2F2943, 0xFFA46BFF, 0xFF6C7CFF, 0xFFF1EEFA, 0xFF9490A8),
            new Theme("Ocean", 0xF20E1319, 0xFF141B24, 0xFF1B2532, 0xFF243247, 0xFF3DA5FF, 0xFF35E0E0, 0xFFEAF2FA, 0xFF8798AB),
            new Theme("Rose", 0xF2181013, 0xFF21161B, 0xFF2C1D24, 0xFF3A2630, 0xFFFF5C8A, 0xFFFF9A5C, 0xFFFAEEF2, 0xFFAB8E98),
            new Theme("Light", 0xF2F2F4F7, 0xFFE6E9EE, 0xFFD9DEE6, 0xFFCBD3DE, 0xFF3B82F6, 0xFF22C1C3, 0xFF1B2330, 0xFF5B677A),
            new Theme("Mono", 0xF20E0E0E, 0xFF171717, 0xFF202020, 0xFF2A2A2A, 0xFFFFFFFF, 0xFFB0B0B0, 0xFFF0F0F0, 0xFF8A8A8A));

    public static int mixRgb(int a, int b, float t) {
        return Ui.mix(a, b, t) | 0xFF000000;
    }

    private static int cachedKey;
    private static Theme cached;

    /** A whole palette derived from one accent colour: dark surfaces tinted with its hue. */
    public static Theme fromAccent(int argb) {
        if (cached != null && cachedKey == argb) {
            return cached;
        }
        float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        float h = hsb[0];
        float s = Math.max(0.35f, hsb[1]);
        float v = Math.max(0.65f, hsb[2]);
        int accent = 0xFF000000 | (java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF);
        int accent2 = 0xFF000000 | (java.awt.Color.HSBtoRGB((h + 0.07f) % 1f, s, v) & 0xFFFFFF);
        float tint = Math.min(0.4f, s * 0.45f);
        cached = new Theme("Accent",
                0xF2000000 | (java.awt.Color.HSBtoRGB(h, tint, 0.07f) & 0xFFFFFF),
                0xFF000000 | (java.awt.Color.HSBtoRGB(h, tint, 0.10f) & 0xFFFFFF),
                0xFF000000 | (java.awt.Color.HSBtoRGB(h, tint, 0.14f) & 0xFFFFFF),
                0xFF000000 | (java.awt.Color.HSBtoRGB(h, tint, 0.19f) & 0xFFFFFF),
                accent, accent2,
                0xFF000000 | (java.awt.Color.HSBtoRGB(h, 0.04f, 0.95f) & 0xFFFFFF),
                0xFF000000 | (java.awt.Color.HSBtoRGB(h, 0.12f, 0.62f) & 0xFFFFFF));
        cachedKey = argb;
        return cached;
    }

    public Theme withAccents(int a, int a2) {
        return new Theme(name, bg, panel, panel2, hover, a, a2, text, dim);
    }

    public static String[] names() {
        return ALL.stream().map(Theme::name).toArray(String[]::new);
    }
}
