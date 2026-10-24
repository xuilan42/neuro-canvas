package com.example.neurocanvas.module;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    public boolean enabled;
    /** GLFW key code, or -1 when unbound. */
    public int key = -1;
    /** true: active only while the key is held; false: key toggles the module. */
    public boolean hold;
    /** False for modules the AI must never change (gameplay helpers, the menu itself). */
    public boolean aiControllable = true;
    /** True when the module uses its key itself (for example to open a wheel) instead of switching the module on and off. */
    public boolean customKey;
    /** True for modules that are just settings (no on/off switch, no key): they are always on. */
    public boolean alwaysOn;
    /** True for modules that can count as an advantage on strict servers (see-through boxes, free camera, zoom...): Safe mode switches them off. */
    public boolean risky;
    public boolean keyDown;
    public final List<Setting> settings = new ArrayList<>();

    protected Module(String name, Category category, String description, boolean enabledByDefault) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.enabled = enabledByDefault;
    }

    public Setting setting(String name) {
        String wanted = name.replace(" ", "");
        for (Setting s : settings) {
            if (s.name.replace(" ", "").equalsIgnoreCase(wanted)) {
                return s;
            }
        }
        return null;
    }

    /** Set by modules that draw HUD elements (see {@link #hudSize()}). */
    private Setting hudSize;

    /** Gives the module a "HUD size" setting; call it from the constructor of a module that draws a HUD element. */
    protected final void hudSize() {
        if (hudSize == null) {
            hudSize = add(Setting.number("HUD size", 0.5, 2, 1));
        }
    }

    public final boolean hasHud() {
        return hudSize != null;
    }

    /** How big this module's HUD is drawn: its own size times the global HUD scale. */
    public final float hudScale() {
        return hudSize == null ? 1f : hudSize.f() * Interface.globalHudScale();
    }

    /** Changes the module's own HUD size (the HUD editor's wheel); stays between 0.5 and 2. */
    public final void adjustHudSize(double delta) {
        if (hudSize != null) {
            hudSize.value = Math.max(hudSize.min, Math.min(hudSize.max, Math.round((hudSize.value + delta) * 20) / 20.0));
        }
    }

    protected Setting add(Setting s) {
        settings.add(s);
        return s;
    }

    public void tick(MinecraftClient mc) { }

    /** Runs every tick while in a world, even when the module is off (for modules that must clean up). */
    public void tickAlways(MinecraftClient mc) { }

    public void renderWorld(RenderEnv env) { }

    public void renderHud(DrawContext ctx, MinecraftClient mc) { }
}
