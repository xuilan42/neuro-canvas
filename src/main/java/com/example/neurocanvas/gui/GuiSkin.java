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
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.toast.Toast;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

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

    // ---- widgets: buttons, sliders, text fields, tooltips ----------------------------------------

    private static final class Anim {
        float value;
        long stamp = System.nanoTime();
    }

    private static final java.util.Map<Object, Anim> ANIMS = new java.util.WeakHashMap<>();

    /** A value that follows {@code on} smoothly (about a tenth of a second), kept per widget. */
    private static float ease(Object widget, boolean on) {
        Anim a = ANIMS.computeIfAbsent(widget, k -> new Anim());
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - a.stamp) / 1_000_000_000f);
        a.stamp = now;
        float target = on ? 1f : 0f;
        a.value = Interface.animations() ? a.value + Math.max(-1f, Math.min(1f, (target - a.value) * Math.min(1f, dt * 14f) * 1.4f)) : target;
        a.value = Math.max(0f, Math.min(1f, a.value));
        return a.value;
    }

    private static boolean widgetsOn(String area) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (NeuroCanvasClient.modules() == null || !Interface.guiSkin() || !Interface.guiWidgets() || BROKEN.contains(area)) {
            return false;
        }
        Screen s = mc.currentScreen;
        return s != null && !excluded(s);
    }

    private static int panelRgb(Theme th, float hover, boolean active) {
        return Ui.mix(opaque(base(th)), opaque(th.accent()), active ? 0.10f + 0.32f * hover : 0.03f) & 0xFFFFFF;
    }

    /** Draws the body of a button; the caller draws its label. Returns true when it did. */
    public static boolean button(PressableWidget w, DrawContext ctx, float alpha) {
        if (!widgetsOn("button")) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            int x = w.getX();
            int y = w.getY();
            int bw = w.getWidth();
            int bh = w.getHeight();
            boolean on = w.active && (w.isHovered() || w.isFocused());
            float hv = ease(w, on);
            int r = Math.min(5, bh / 2);
            if (hv > 0.04f) {
                Ui.glow(ctx, x, y, bw, bh, r, th.accent() & 0xFFFFFF, 0.55f * hv * alpha, 6f);
            }
            Ui.roundRect(ctx, x, y, bw, bh, r, Ui.alpha(panelRgb(th, hv, w.active), alpha * (w.active ? 0.82f + 0.12f * hv : 0.5f)));
            int rim = Ui.mix(Ui.alpha(0xFFFFFF, w.active ? 0.16f : 0.08f), Ui.alpha(th.accent() & 0xFFFFFF, 0.95f), hv);
            rim = (rim & 0xFFFFFF) | ((int) (((rim >>> 24) & 0xFF) * alpha) << 24);
            Ui.outline(ctx, x, y, bw, bh, r, -1f, 0f, rim, rim);
            return true;
        } catch (Throwable e) {
            broke("button", e);
            return false;
        }
    }

    /** Draws the track and the handle of a slider (the value is 0..1); the caller draws its label. Returns true when it did. */
    public static boolean slider(SliderWidget w, DrawContext ctx, double value, float alpha) {
        if (!widgetsOn("slider")) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            int x = w.getX();
            int y = w.getY();
            int bw = w.getWidth();
            int bh = w.getHeight();
            boolean on = w.active && (w.isHovered() || w.isFocused());
            float hv = ease(w, on);
            int r = Math.min(5, bh / 2);
            Ui.roundRect(ctx, x, y, bw, bh, r, Ui.alpha(panelRgb(th, hv * 0.5f, w.active), alpha * (w.active ? 0.82f : 0.5f)));
            int hx = x + (int) (value * (bw - 8));
            if (hx > x) {
                Ui.roundRect(ctx, x, y, hx - x + 4, bh, r, Ui.alpha(th.accent() & 0xFFFFFF, alpha * 0.34f));
            }
            int rim = Ui.alpha(0xFFFFFF, 0.14f);
            Ui.outline(ctx, x, y, bw, bh, r, -1f, 0f, rim, rim);
            if (hv > 0.04f) {
                Ui.glow(ctx, hx, y, 8, bh, 3, th.accent() & 0xFFFFFF, 0.6f * hv * alpha, 5f);
            }
            Ui.roundRect(ctx, hx, y, 8, bh, 3, Ui.alpha(Ui.mix(opaque(th.accent()), 0xFFFFFFFF, 0.35f * hv + 0.1f) & 0xFFFFFF, alpha * (w.active ? 1f : 0.5f)));
            return true;
        } catch (Throwable e) {
            broke("slider", e);
            return false;
        }
    }

    public static boolean fieldsOn() {
        return widgetsOn("field");
    }

    /** The rounded background of a text field (it replaces the two plain rectangles the game draws). The outer rectangle is passed in. */
    public static boolean fieldBackground(TextFieldWidget w, DrawContext ctx, int x1, int y1, int x2, int y2) {
        if (!fieldsOn()) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            float hv = ease(w, w.isFocused());
            int bw = x2 - x1;
            int bh = y2 - y1;
            int r = Math.min(4, bh / 2);
            Ui.roundRect(ctx, x1, y1, bw, bh, r, Ui.alpha(Ui.mix(opaque(base(th)), 0xFF000000, 0.35f) & 0xFFFFFF, 0.9f));
            int rim = Ui.mix(Ui.alpha(0xFFFFFF, 0.2f), Ui.alpha(th.accent() & 0xFFFFFF, 1f), hv);
            Ui.outline(ctx, x1, y1, bw, bh, r, -1f, 0f, rim, rim);
            return true;
        } catch (Throwable e) {
            broke("field", e);
            return false;
        }
    }

    /**
     * The panel behind a tooltip. The game passes the height of the tooltip layer (400) as a number and draws each of its own rectangles at
     * that height; our shapes use the matrix instead, so the matrix is lifted by it, otherwise items in the slots are drawn over the panel.
     * Returns true when it drew one.
     */
    public static boolean tooltip(DrawContext ctx, int x, int y, int width, int height, int z) {
        if (!widgetsOn("tooltip")) {
            return false;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0f, 0f, (float) z);
        try {
            Theme th = Interface.theme();
            int px = x - 4;
            int py = y - 4;
            int pw = width + 8;
            int ph = height + 8;
            Ui.roundRect(ctx, px, py, pw, ph, 5, Ui.alpha(Ui.mix(opaque(base(th)), 0xFF000000, 0.2f) & 0xFFFFFF, 0.94f));
            int rim = Ui.alpha(Ui.mix(opaque(th.accent()), opaque(th.accent2()), 0.5f) & 0xFFFFFF, 0.85f);
            Ui.outline(ctx, px, py, pw, ph, 5, -1f, 0f, rim, rim);
            return true;
        } catch (Throwable e) {
            broke("tooltip", e);
            return false;
        } finally {
            ctx.getMatrices().pop();
        }
    }

    // ---- scrollbars, checkboxes, tabs, toasts -------------------------------------------------------

    public static boolean scrollOn() {
        return widgetsOn("scroll");
    }

    /** The three rectangles of a list's scrollbar: 0 the track, 1 the handle, 2 its highlight (not needed). Returns true when it handled the part. */
    public static boolean scrollbar(DrawContext ctx, int part, int x1, int y1, int x2, int y2) {
        if (!scrollOn()) {
            return false;
        }
        if (part == 2) {
            return true;
        }
        try {
            Theme th = Interface.theme();
            int w = x2 - x1;
            int r = Math.max(1, w / 2);
            if (part == 0) {
                Ui.roundRect(ctx, x1, y1, w, y2 - y1, r, Ui.alpha(0xFFFFFF, 0.07f));
            } else {
                Ui.roundRect(ctx, x1, y1, w, y2 - y1, r, Ui.alpha(th.accent() & 0xFFFFFF, 0.85f));
            }
            return true;
        } catch (Throwable e) {
            broke("scroll", e);
            return false;
        }
    }

    public static boolean checkbox(CheckboxWidget w, DrawContext ctx, int x, int y, int bw, int bh) {
        if (!widgetsOn("checkbox")) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            boolean on = w.active && (w.isHovered() || w.isFocused());
            float hv = ease(w, on);
            int r = Math.min(4, bh / 2);
            if (hv > 0.04f) {
                Ui.glow(ctx, x, y, bw, bh, r, th.accent() & 0xFFFFFF, 0.45f * hv, 5f);
            }
            Ui.roundRect(ctx, x, y, bw, bh, r, Ui.alpha(panelRgb(th, hv, w.active), w.active ? 0.85f : 0.5f));
            int rim = Ui.mix(Ui.alpha(0xFFFFFF, 0.18f), Ui.alpha(th.accent() & 0xFFFFFF, 0.95f), Math.max(hv, w.isChecked() ? 1f : 0f));
            Ui.outline(ctx, x, y, bw, bh, r, -1f, 0f, rim, rim);
            if (w.isChecked()) {
                int in = Math.max(3, Math.min(bw, bh) / 4);
                Ui.roundRect(ctx, x + in, y + in, bw - 2 * in, bh - 2 * in, Math.max(1, r - 1), Ui.alpha(th.accent() & 0xFFFFFF, w.active ? 1f : 0.5f));
            }
            return true;
        } catch (Throwable e) {
            broke("checkbox", e);
            return false;
        }
    }

    public static boolean tab(TabButtonWidget w, DrawContext ctx, int x, int y, int bw, int bh) {
        if (!widgetsOn("tab")) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            boolean current = w.isCurrentTab();
            float hv = ease(w, w.isHovered() || w.isFocused() || current);
            int r = Math.min(5, bh / 2);
            if (current) {
                Ui.glow(ctx, x, y, bw, bh, r, th.accent() & 0xFFFFFF, 0.4f, 5f);
            }
            Ui.roundRect(ctx, x, y, bw, bh, r, Ui.alpha(Ui.mix(opaque(base(th)), opaque(th.accent()), 0.06f + 0.34f * hv) & 0xFFFFFF, 0.88f));
            int rim = Ui.mix(Ui.alpha(0xFFFFFF, 0.14f), Ui.alpha(th.accent() & 0xFFFFFF, 0.95f), current ? 1f : hv * 0.6f);
            Ui.outline(ctx, x, y, bw, bh, r, -1f, 0f, rim, rim);
            return true;
        } catch (Throwable e) {
            broke("tab", e);
            return false;
        }
    }

    private static boolean inToast;

    /** Draws the panel of a toast (advancement, recipe, system message) and hides the game's own background of it. Returns true when it did. */
    public static boolean toastStart(Toast toast, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (NeuroCanvasClient.modules() == null || !Interface.guiSkin() || !Interface.guiWidgets() || BROKEN.contains("toast")) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            int w = toast.getWidth();
            int h = toast.getHeight();
            Ui.roundRect(ctx, 0, 0, w, h, 5, Ui.alpha(Ui.mix(opaque(base(th)), 0xFF000000, 0.15f) & 0xFFFFFF, 0.93f));
            int rim = Ui.alpha(Ui.mix(opaque(th.accent()), opaque(th.accent2()), 0.5f) & 0xFFFFFF, 0.9f);
            Ui.outline(ctx, 0, 0, w, h, 5, -1f, 0f, rim, rim);
            inToast = true;
            return true;
        } catch (Throwable e) {
            broke("toast", e);
            return false;
        }
    }

    public static void toastEnd() {
        inToast = false;
    }

    /** While a toast is drawn, the large pieces of the game's toast texture (its background) are skipped; the small icons in it stay. */
    public static boolean suppressToastTexture(int x1, int x2, int y1, int y2) {
        return inToast && x2 - x1 >= 100;
    }

    // ---- the HUD: hotbar, experience bar, boss bars, chat -------------------------------------------------
    // Plain (not glass) panels. The hearts, armour and hunger are left to the game, so mods that sit on them (AppleSkin) keep working.

    private static boolean hudOn(String area) {
        return NeuroCanvasClient.modules() != null && Interface.guiSkin() && Interface.guiHud() && !BROKEN.contains(area);
    }

    private static float slideX = Float.NaN;
    private static long slideStamp;

    /**
     * One of the pieces of the hotbar the game draws from widgets.png (the bar, the selected slot, the off-hand slot). Returns true when the
     * piece was drawn here and the game's own sprite must be skipped. The coordinates of the sprite say where the slot cells are.
     */
    public static boolean hotbar(DrawContext ctx, Identifier texture, int x, int y, int u, int v, int w, int h) {
        if (!"textures/gui/widgets.png".equals(texture.getPath()) || !hudOn("hotbar")) {
            return false;
        }
        try {
            Theme th = Interface.theme();
            int accent = th.accent() & 0xFFFFFF;
            int base = base(th);
            if (u == 0 && v == 0 && w == 182 && h == 22) {
                Ui.roundRect(ctx, x, y, 182, 22, 6, Ui.alpha(Ui.mix(opaque(base), 0xFF000000, 0.25f) & 0xFFFFFF, 0.78f));
                int rim = Ui.alpha(0xFFFFFF, 0.13f);
                Ui.outline(ctx, x, y, 182, 22, 6, -1f, 0f, rim, rim);
                for (int m = 0; m < 9; m++) {
                    Ui.roundRect(ctx, x + 2 + 20 * m, y + 2, 18, 18, 4, Ui.alpha(0xFFFFFF, 0.055f));
                }
                return true;
            }
            if (u == 0 && v == 22 && w == 24 && h == 22) {
                // the selected slot: x is the sprite's, the cell is 3 pixels further in; it glides to the new slot
                float target = x + 2;
                long now = System.nanoTime();
                float dt = Math.min(0.1f, (now - slideStamp) / 1_000_000_000f);
                slideStamp = now;
                if (Float.isNaN(slideX) || !Interface.animations() || Math.abs(slideX - target) > 80) {
                    slideX = target;
                } else {
                    slideX += (target - slideX) * (1f - (float) Math.exp(-dt * 24f));
                }
                int sx = Math.round(slideX);
                int sy = y + 2;
                Ui.glow(ctx, sx, sy, 20, 20, 5, accent, 0.5f, 6f);
                Ui.roundRect(ctx, sx, sy, 20, 20, 5, Ui.alpha(accent, 0.22f));
                int rim = Ui.alpha(accent, 0.95f);
                Ui.outline(ctx, sx, sy, 20, 20, 5, -1f, 0f, rim, rim);
                return true;
            }
            if (v == 22 && w == 29 && h == 24 && (u == 24 || u == 53)) {
                // the off-hand slot, on the left or the right of the bar (the sprite's x is 29 left of the bar, or at its right end + 0)
                int px = u == 24 ? x - 1 : x + 4;
                int py = y + 1;
                Ui.roundRect(ctx, px, py, 26, 22, 6, Ui.alpha(Ui.mix(opaque(base), 0xFF000000, 0.25f) & 0xFFFFFF, 0.78f));
                int rim = Ui.alpha(0xFFFFFF, 0.13f);
                Ui.outline(ctx, px, py, 26, 22, 6, -1f, 0f, rim, rim);
                Ui.roundRect(ctx, px + (u == 24 ? 3 : 5), py + 2, 18, 18, 4, Ui.alpha(0xFFFFFF, 0.055f));
                return true;
            }
            return false;
        } catch (Throwable e) {
            broke("hotbar", e);
            return false;
        }
    }

    /** The two pieces of the experience bar (the empty bar and the filled part), drawn from icons.png by the game. */
    public static boolean experienceBar(DrawContext ctx, Identifier texture, int x, int y, int u, int v, int w, int h) {
        if (!"textures/gui/icons.png".equals(texture.getPath()) || !(v == 64 || v == 69) || !hudOn("xp")) {
            return false;
        }
        try {
            if (v == 64) {
                Ui.roundRect(ctx, x, y + 1, 182, 4, 2, Ui.alpha(0xFFFFFF, 0.13f));
            } else if (w > 0) {
                Ui.glow(ctx, x, y + 1, w, 4, 2, 0x80FF20, 0.35f, 5f);
                Ui.roundRect(ctx, x, y + 1, Math.max(4, w), 4, 2, Ui.alpha(0x80FF20, 0.95f));
            }
            return true;
        } catch (Throwable e) {
            broke("xp", e);
            return false;
        }
    }

    private static int bossColor(net.minecraft.entity.boss.BossBar.Color c) {
        return switch (c) {
            case PINK -> 0xF06BB0;
            case BLUE -> 0x4F8CFF;
            case RED -> 0xFF4B4B;
            case GREEN -> 0x4CD964;
            case YELLOW -> 0xFFD23F;
            case PURPLE -> 0xA855F7;
            default -> 0xF2F2F2;
        };
    }

    /** One draw of a boss bar: textureV 0 is the empty bar, 5 the filled part (width is how much of it). Returns true when it drew it. */
    public static boolean bossBar(DrawContext ctx, int x, int y, net.minecraft.entity.boss.BossBar bar, int width, int textureV) {
        if (!hudOn("boss")) {
            return false;
        }
        try {
            int color = bossColor(bar.getColor());
            if (textureV == 0) {
                Ui.roundRect(ctx, x, y, 182, 5, 2, Ui.alpha(0xFFFFFF, 0.14f));
            } else {
                Ui.roundRect(ctx, x, y, Math.max(3, width), 5, 2, Ui.alpha(color, 0.95f));
            }
            int notches = switch (bar.getStyle()) {
                case NOTCHED_6 -> 6;
                case NOTCHED_10 -> 10;
                case NOTCHED_12 -> 12;
                case NOTCHED_20 -> 20;
                default -> 0;
            };
            for (int k = 1; k < notches; k++) {
                int tx = x + Math.round(182f * k / notches);
                if (tx < x + width) {
                    ctx.fill(tx, y, tx + 1, y + 5, Ui.alpha(0x000000, 0.55f));
                }
            }
            return true;
        } catch (Throwable e) {
            broke("boss", e);
            return false;
        }
    }

    /** Chat backgrounds are black with some opacity; they take the theme's dark colour (same opacity). Other colours pass untouched. */
    public static int chatFill(int color) {
        if ((color & 0xFFFFFF) != 0 || (color >>> 24) == 0 || !hudOn("chat")) {
            return color;
        }
        try {
            return (color & 0xFF000000) | (Ui.mix(opaque(base(Interface.theme())), 0xFF000000, 0.3f) & 0xFFFFFF);
        } catch (Throwable e) {
            broke("chat", e);
            return color;
        }
    }

    // ---- the loading screen (startup and resource reloads) ----------------------------------------------

    private static final Identifier SPLASH_LOGO = new Identifier("neurocanvas", "splash_logo");
    private static boolean splashLoaded;
    private static boolean splashLogoOk;
    private static boolean splashDrew;

    private static boolean splashOn() {
        return Interface.guiSkin() && Interface.guiLoading() && !BROKEN.contains("splash");
    }

    /** base, accent, accent 2; the theme once it exists, the default purple otherwise (the screen can appear before the theme is ready). */
    private static int[] splashColors() {
        try {
            Theme th = Interface.theme();
            return new int[] {Ui.mix(opaque(base(th)), 0xFF000000, 0.45f) & 0xFFFFFF, th.accent() & 0xFFFFFF, th.accent2() & 0xFFFFFF};
        } catch (Throwable e) {
            return new int[] {0x07050F, 0xA855F7, 0x5B8CFF};
        }
    }

    /** The colour that replaces the red of the loading screen. Returns the game's own colour when the skin is off. */
    public static int splashBackground(int vanilla) {
        if (!splashOn()) {
            return vanilla;
        }
        return opaque(splashColors()[0]);
    }

    /**
     * Draws our logo (read from the mod's jar, not from the resource manager, which is busy while the screen is up) with a soft glow,
     * and reports whether it did. When it did, the game's own two halves of the Mojang logo are skipped.
     */
    public static boolean splashLogo(DrawContext ctx) {
        splashDrew = false;
        if (!splashOn()) {
            return false;
        }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!splashLoaded) {
                splashLoaded = true;
                Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("neurocanvas").orElseThrow()
                        .findPath("assets/neurocanvas/textures/logo_big.png").orElseThrow();
                try (java.io.InputStream in = Files.newInputStream(file)) {
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(NativeImage.read(in));
                    tex.setFilter(true, false);
                    mc.getTextureManager().registerTexture(SPLASH_LOGO, tex);
                    splashLogoOk = true;
                }
            }
            if (!splashLogoOk) {
                return false;
            }
            int w = ctx.getScaledWindowWidth();
            int h = ctx.getScaledWindowHeight();
            int s = Math.max(48, Math.min(128, (int) (h * 0.30f)));
            int x = (w - s) / 2;
            int y = (int) (h * 0.45f) - s / 2;
            int[] c = splashColors();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            Ui.glow(ctx, x, y, s, s, s / 4, c[1], 0.6f, 22f);
            ctx.drawTexture(SPLASH_LOGO, x, y, s, s, 0f, 0f, 512, 512, 512, 512);
            splashDrew = true;
            return true;
        } catch (Throwable e) {
            broke("splash", e);
            return false;
        }
    }

    public static boolean splashDrewLogo() {
        return splashDrew;
    }

    /** The progress bar of the loading screen: a thin rounded track with an accent fill. Returns true when it drew one. */
    public static boolean splashBar(DrawContext ctx, int x1, int y1, int x2, int y2, float opacity, float progress) {
        if (!splashOn() || !splashDrew) {
            return false;
        }
        try {
            int[] c = splashColors();
            int w = ctx.getScaledWindowWidth();
            int h = ctx.getScaledWindowHeight();
            int bw = Math.max(120, Math.min(260, (int) (Math.min(w, h * 2) * 0.32f)));
            int bx = (w - bw) / 2;
            int bh = 4;
            int by = (y1 + y2) / 2 - bh / 2;
            float p = Math.max(0f, Math.min(1f, progress));
            Ui.roundRect(ctx, bx, by, bw, bh, 2, Ui.alpha(0xFFFFFF, 0.12f * opacity));
            int fw = Math.max(bh, (int) (bw * p));
            int fill = Ui.mix(opaque(c[1]), opaque(c[2]), p) & 0xFFFFFF;
            Ui.glow(ctx, bx, by, fw, bh, 2, fill, 0.55f * opacity, 7f);
            Ui.roundRect(ctx, bx, by, fw, bh, 2, Ui.alpha(fill, opacity));
            return true;
        } catch (Throwable e) {
            broke("splash", e);
            return false;
        }
    }

    public static String describe() {
        return BROKEN.isEmpty() ? "ok" : "switched off: " + String.join(", ", BROKEN).toLowerCase(Locale.ROOT);
    }
}
