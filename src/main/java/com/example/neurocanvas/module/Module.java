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
