package com.example.neurocanvas.gui;

import com.google.gson.JsonObject;

/** A complete theme: palette, style, and optionally a pack of module settings that go with the look. */
public record ThemeDef(String name, Theme palette, ThemeStyle style, JsonObject pack, boolean builtin) {
    public boolean hasPack() {
        return pack != null && !pack.entrySet().isEmpty();
    }

    public ThemeDef withPalette(Theme t) {
        return new ThemeDef(name, t, style, pack, builtin);
    }

    public ThemeDef withStyle(ThemeStyle s) {
        return new ThemeDef(name, palette, s, pack, builtin);
    }
}
