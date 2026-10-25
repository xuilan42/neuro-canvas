package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;

/**
 * Lifts the limits on how many things the effect modules make: the Count of particles, hit particles, bubbles and kill effects, the number of
 * target effects, lines and curtains. The sliders reach ten times as far and the hidden caps inside the effects rise the same way. Heavy settings
 * can drop the frame rate; turning the module off puts every limit back (values above the old limit are brought down to it).
 */
public final class NoLimited extends Module {
    private boolean applied;

    public NoLimited() {
        super("NoLimited", Category.MISC, "Lifts the limits on how many particles and other objects the effect modules make (can lower the FPS)", false);
        aiControllable = false;
    }

    /** The cap an effect uses for its objects: ten times higher while the limits are lifted. */
    public static int cap(int normal) {
        return Setting.unlocked ? normal * 10 : normal;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        boolean on = enabled;
        Setting.unlocked = on;
        if (on != applied) {
            applied = on;
            for (Module m : com.example.neurocanvas.client.NeuroCanvasClient.modules().all()) {
                for (Setting s : m.settings) {
                    s.unlock(on);
                }
            }
        }
    }
}
