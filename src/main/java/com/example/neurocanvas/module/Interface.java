package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import net.minecraft.util.Identifier;

/** Settings of the menu itself: theme, font, animations. Always on; it has no switch. */
public final class Interface extends Module {
    private static final String[] FONTS = {"Inter", "Nunito", "Montserrat", "Minecraft"};
    private static Interface instance;

    private final Setting theme = add(Setting.choice("Theme", 0, Theme.names()));
    private final Setting font = add(Setting.choice("Font", 0, FONTS));
    private final Setting animations = add(Setting.bool("Animations", true));

    public Interface() {
        super("Interface", Category.MISC, "Theme, font and animations of this menu", true);
        aiControllable = false;
        instance = this;
    }

    public static Theme theme() {
        return Theme.ALL.get(Math.min(instance.theme.index(), Theme.ALL.size() - 1));
    }

    /** @return the bundled font to use, or null for the vanilla font */
    public static Identifier font() {
        int i = instance.font.index();
        return i >= FONTS.length - 1 ? null : new Identifier("neurocanvas", FONTS[i].toLowerCase());
    }

    public static boolean animations() {
        return instance.animations.on();
    }
}
