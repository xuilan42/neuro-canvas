package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.gui.Theme;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Settings of the menu itself: theme, accent colour, font, animations, sounds. Always on; it has no switch. */
public final class Interface extends Module {
    private static final String[] FONTS = {"Inter", "Nunito", "Montserrat", "Minecraft"};
    private static Interface instance;

    private final Setting theme = add(Setting.choice("Theme", 0, Theme.names()));
    private final Setting customAccent = add(Setting.bool("Use accent color", false));
    private final Setting accent = add(Setting.color("Accent color", 0.42));
    private final Setting applyAll = add(Setting.bool("Apply accent to all modules", false));
    private final Setting font = add(Setting.choice("Font", 0, FONTS));
    private final Setting animations = add(Setting.bool("Animations", true));
    private final Setting openAnimation = add(Setting.choice("Open animation", 0, "Slide", "Zoom", "Pop"));
    private final Setting background = add(Setting.choice("Background", 0, "Dim", "Gradient", "Glow"));
    private final Setting sounds = add(Setting.bool("Sounds", true));
    private final Setting updates = add(Setting.bool("Check for updates", true));

    public Interface() {
        super("Interface", Category.MISC, "Theme, accent colour, font, animations and sounds of this menu", true);
        aiControllable = false;
        instance = this;
    }

    public static Theme theme() {
        if (instance.customAccent.on()) {
            return Theme.fromAccent(instance.accent.argb());
        }
        return Theme.ALL.get(Math.min(instance.theme.index(), Theme.ALL.size() - 1));
    }

    /** @return the bundled font to use, or null for the vanilla font */
    public static Identifier font() {
        int i = instance.font.index();
        return i >= FONTS.length - 1 ? null : new Identifier("neurocanvas", FONTS[i].toLowerCase());
    }

    public static boolean animations() { return instance.animations.on(); }
    public static int openAnimation() { return instance.openAnimation.index(); }
    public static int backgroundStyle() { return instance.background.index(); }
    public static boolean checkUpdates() { return instance != null && instance.updates.on(); }

    /** Plays the menu click sound; higher pitch for "on". */
    public static void click(float pitch) {
        if (instance.sounds.on()) {
            MinecraftClient.getInstance().getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (!applyAll.on()) {
            return;
        }
        applyAll.set(0);
        try {
            NeuroCanvasClient.modules().savePreset("Before accent");
        } catch (IOException ignored) {
            // The sync still runs; only the safety copy is missing.
        }
        Theme t = Theme.fromAccent(accent.argb());
        int[] palette = {t.accent(), t.accent2(), Theme.mixRgb(t.accent(), 0xFFFFFFFF, 0.35f), Theme.mixRgb(t.accent2(), 0xFFFFFFFF, 0.35f)};
        int changed = 0;
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (m == this) {
                continue;
            }
            int index = 0;
            for (Setting s : m.settings) {
                if (s.kind == Setting.Kind.COLOR) {
                    int alpha = s.argb() & 0xFF000000;
                    s.setArgb(alpha | (palette[Math.min(index, palette.length - 1)] & 0xFFFFFF));
                    index++;
                    changed++;
                }
            }
        }
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Interface] accent applied to " + changed
                    + " colours. The old look is saved as preset \"Before accent\"."), false);
        }
    }
}
