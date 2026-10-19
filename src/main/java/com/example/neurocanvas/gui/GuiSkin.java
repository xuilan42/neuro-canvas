package com.example.neurocanvas.gui;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Interface;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * The look of Minecraft's own screens, taken from the current theme. This first part draws the backgrounds: in a world the screen
 * behind the menu is blurred and tinted, on the main menus the dirt tile is replaced by a themed gradient with a slow aurora, and
 * the dirt of lists, headers and footers becomes a themed panel. Vanilla text is white, so the backgrounds are always dark whatever
 * the theme. If our drawing ever throws, that part switches itself off for the session and the vanilla look comes back.
 */
public final class GuiSkin {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("neurocanvas");
    private static final long FADE_MS = 220;

    private static final Set<String> BROKEN = new HashSet<>();
    private static final List<String> EXCLUDED = new ArrayList<>();
    private static Screen lastScreen;
    private static long openedAt;

    private GuiSkin() { }

    /** Reads neurocanvas/gui-exclude.txt: one class name (or the start of one) per line for screens that keep their vanilla look. */
    public static void init(Path dir) {
        EXCLUDED.clear();
        EXCLUDED.add("com.example.neurocanvas.gui.");
        Path file = dir.resolve("gui-exclude.txt");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dir);
                Files.writeString(file, "# Screens that keep their vanilla background: a class name or the start of one, one per line.\n"
                        + "# Example: me.jellysquid.mod.sodium.client.gui\n", StandardCharsets.UTF_8);
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    EXCLUDED.add(t);
                }
            }
        } catch (IOException ignored) {
            // The built-in exclusions stay.
        }
    }

    private static boolean enabled(String area) {
        return NeuroCanvasClient.modules() != null && Interface.guiSkin() && Interface.guiBackgrounds() && !BROKEN.contains(area);
    }

    private static boolean excluded(Screen s) {
        String name = s.getClass().getName();
        for (String prefix : EXCLUDED) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void broke(String area, Throwable e) {
        BROKEN.add(area);
        LOG.error("GUI skin part '{}' failed and is switched off until the game restarts", area, e);
    }

    /** 0..1: how far the opening of this screen has got (eased), so the background fades in. */
    private static float open(Screen s) {
        if (s != lastScreen) {
            lastScreen = s;
            openedAt = System.currentTimeMillis();
        }
        if (!Interface.animations()) {
            return 1f;
        }
        float p = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) FADE_MS);
        return 1f - (1f - p) * (1f - p) * (1f - p);
    }

    private static float luminance(int rgb) {
        return (0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF)) / 255f;
    }

    /** The dark colour all backgrounds are made from: the theme's background, darkened when the theme is light (vanilla text is white). */
    private static int base(Theme th) {
        int rgb = th.bg() & 0xFFFFFF;
        return luminance(rgb) > 0.4f ? Ui.mix(0xFF000000 | rgb, 0xFF000000, 0.85f) & 0xFFFFFF : rgb;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Replaces Screen.renderBackground inside a world. Returns true when it drew the background itself. */
    public static boolean worldBackground(Screen screen, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !enabled("world") || excluded(screen)) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            float p = open(screen);
            int w = ctx.getScaledWindowWidth();
            int h = ctx.getScaledWindowHeight();
            float blur = Interface.guiBlur();
            if (blur > 0.01f && Interface.shaderEffects()) {
                GuiFx.captureBlur(mc, ctx, blur);
                if (GuiFx.blurAvailable()) {
                    GuiFx.drawBlurred(ctx, 0, 0, w, h, 0, p);
                }
            }
            int base = base(th);
            int accent = th.accent() & 0xFFFFFF;
            ctx.fillGradient(0, 0, w, h, Ui.alpha(base, 0.42f * p), Ui.alpha(Ui.mix(opaque(base), opaque(accent), 0.22f), 0.74f * p));
            return true;
        } catch (Throwable e) {
            broke("world", e);
            return false;
        }
    }

    /** Replaces the dirt tile behind a menu that is not in a world. Returns true when it drew the background itself. */
    public static boolean menuBackground(Screen screen, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled("menu") || excluded(screen)) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            float p = open(screen);
            int w = ctx.getScaledWindowWidth();
            int h = ctx.getScaledWindowHeight();
            int base = base(th);
            int accent = th.accent() & 0xFFFFFF;
            int accent2 = th.accent2() & 0xFFFFFF;
            ctx.fillGradient(0, 0, w, h, opaque(Ui.mix(opaque(base), 0xFF000000, 0.45f)), opaque(Ui.mix(opaque(base), opaque(accent), 0.2f)));
            if (Interface.shaderEffects()) {
                GuiFx.overlay(mc, ctx, ThemeStyle.Overlay.AURORA.ordinal(), 0, 0, w, h, 0, opaque(accent), opaque(accent2), 0.6f * p);
            }
            return true;
        } catch (Throwable e) {
            broke("menu", e);
            return false;
        }
    }

    /**
     * Replaces one draw of the dirt tile (lists, headers, footers) with a themed panel. The game darkens the tile with the shader
     * colour before drawing it; the same brightness decides how light the panel is. Returns true when it drew the panel.
     */
    public static boolean dirt(DrawContext ctx, int x1, int x2, int y1, int y2) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen screen = mc.currentScreen;
        if (screen == null || !enabled("menu") || excluded(screen)) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            float[] sc = com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
            float k = Math.max(0f, Math.min(1.6f, sc[0] / 0.25f));
            int tinted = Ui.mix(opaque(base(th)), opaque(th.accent()), 0.04f + 0.07f * k) & 0xFFFFFF;
            ctx.fill(x1, y1, x2, y2, Ui.alpha(tinted, 0.5f + 0.3f * Math.min(1f, k)));
            return true;
        } catch (Throwable e) {
            broke("menu", e);
            return false;
        }
    }

    public static String describe() {
        return BROKEN.isEmpty() ? "ok" : "switched off: " + String.join(", ", BROKEN).toLowerCase(Locale.ROOT);
    }
}
