package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.gui.ImagePalette;
import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.ThemeDef;
import com.example.neurocanvas.gui.ThemePack;
import com.example.neurocanvas.gui.ThemeStyle;
import com.example.neurocanvas.gui.Themes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Everything about how the menu looks: the theme (palette + style), automatic themes, a live theme editor, fonts,
 * animations and sounds. Always on; it has no switch.
 */
public final class Interface extends Module {
    private static final String[] FONTS = {"Inter", "Nunito", "Montserrat", "Minecraft", "JetBrains Mono", "Theme default"};
    private static final String[] FONT_IDS = {"inter", "nunito", "montserrat", null, "jetbrainsmono", null};
    private static Interface instance;

    private final Setting theme = add(Setting.choice("Theme", 0, Themes.names()));
    private final Setting autoTheme = add(Setting.choice("Auto theme", 0, "Off", "Day / Night", "By server"));
    private final Setting dayTheme = add(Setting.choice("Day theme", 0, Themes.names()));
    private final Setting nightTheme = add(Setting.choice("Night theme", 1, Themes.names()));
    private final Setting dynamic = add(Setting.choice("Dynamic accent", 0, "Off", "Health", "Time of day", "Biome", "All"));
    private final Setting dynamicStrength = add(Setting.number("Dynamic strength", 0.1, 1, 0.65));
    private final Setting customAccent = add(Setting.bool("Use accent color", false));
    private final Setting accent = add(Setting.color("Accent color", 0.42));
    private final Setting applyAll = add(Setting.bool("Apply accent to all modules", false));
    private final Setting listSort = add(Setting.choice("Module list sort", 0, "Longest first", "Shortest first", "A to Z", "Z to A",
            "By category", "By name length, then A to Z", "Shuffled"));
    private final Setting font = add(Setting.choice("Font", FONTS.length - 1, FONTS));
    private final Setting animations = add(Setting.bool("Animations", true));
    private final Setting openAnimation = add(Setting.choice("Open animation", 0, "Slide", "Zoom", "Pop"));
    private final Setting background = add(Setting.choice("Background", 0, "Dim", "Gradient", "Glow"));
    private final Setting effects = add(Setting.bool("Shader effects", true));
    private final Setting drawOver = add(Setting.bool("Draw effects over models", true));
    private final Setting shaderVis = add(Setting.bool("Shader visuals", true));
    private final Setting visGlow = add(Setting.number("Visual glow", 0, 1, 0.7));
    private final Setting sounds = add(Setting.bool("Sounds", true));
    private final Setting updates = add(Setting.bool("Check for updates", true));
    private final Setting applyPack = add(Setting.button("Apply theme pack"));
    private final Setting fromImage = add(Setting.button("Theme from image"));
    private final Setting precompile = add(Setting.button("Precompile shaders"));
    private final Setting precompileOnJoin = add(Setting.bool("Precompile shaders on join", true));
    private final Setting reloadThemes = add(Setting.button("Reload themes"));
    private final Setting openThemes = add(Setting.button("Open themes folder"));

    private final Setting edBg = add(Setting.color("Editor: background", 0.6));
    private final Setting edPanel = add(Setting.color("Editor: panel", 0.6));
    private final Setting edPanel2 = add(Setting.color("Editor: panel 2", 0.6));
    private final Setting edAccent = add(Setting.color("Editor: accent", 0.42));
    private final Setting edAccent2 = add(Setting.color("Editor: accent 2", 0.55));
    private final Setting edText = add(Setting.color("Editor: text", 0.0));
    private final Setting edRadius = add(Setting.number("Editor: corner radius", 0, 2, 1));
    private final Setting edBorder = add(Setting.number("Editor: border", 0, 4, 0));
    private final Setting edGlow = add(Setting.number("Editor: glow", 0, 1, 0.2));
    private final Setting edBlur = add(Setting.number("Editor: blur", 0, 2, 0));
    private final Setting edOverlay = add(Setting.choice("Editor: effect", 0, "None", "Scanlines", "Grid", "Aurora"));
    private final Setting edOverlayStrength = add(Setting.number("Editor: effect strength", 0, 1, 0.5));
    private final Setting edSwitch = add(Setting.choice("Editor: switch style", 0, "Pill", "Brackets"));
    private final Setting saveTheme = add(Setting.button("Save editor theme"));
    private final Setting copyTheme = add(Setting.button("Copy theme code"));
    private final Setting pasteTheme = add(Setting.button("Import theme code from clipboard"));

    private ThemeDef current;
    private long computedAt;
    private ThemeDef shownBase;
    private final float[] dynAccent = new float[3];
    private final float[] dynAccent2 = new float[3];
    private boolean dynInit;
    private long dynLast;
    private int imageCounter;
    private boolean joined;

    public Interface(Path themesDir, Path serversFile) {
        super("Interface", Category.MISC, "Theme, accent colour, auto themes, theme editor, font, animations and sounds", true);
        alwaysOn = true;
        aiControllable = false;
        instance = this;
        Themes.init(themesDir, serversFile);
        refreshNames();
        edBg.setArgb(0xF2101412);
        edPanel.setArgb(0xFF171C1A);
        edPanel2.setArgb(0xFF1F2623);
        edAccent.setArgb(0xFF3DDC97);
        edAccent2.setArgb(0xFF2BB7A3);
        edText.setArgb(0xFFEAF2EE);
    }

    private void refreshNames() {
        String[] names = Themes.names();
        theme.setOptions(names);
        dayTheme.setOptions(names);
        nightTheme.setOptions(names);
        theme.set(theme.value);
        dayTheme.set(dayTheme.value);
        nightTheme.set(nightTheme.value);
    }

    // ---- what the rest of the mod asks for ----------------------------------------------------

    private static ThemeDef base() {
        Interface i = instance;
        java.util.List<ThemeDef> all = Themes.all();
        int index = i.theme.index();
        MinecraftClient mc = MinecraftClient.getInstance();
        int mode = i.autoTheme.index();
        if (mode == 1) {
            boolean night;
            if (mc.world != null) {
                long t = mc.world.getTimeOfDay() % 24000L;
                night = t >= 13000L && t < 23000L;
            } else {
                int hour = java.time.LocalTime.now().getHour();
                night = hour < 6 || hour >= 18;
            }
            index = (night ? i.nightTheme : i.dayTheme).index();
        } else if (mode == 2) {
            String bound = Themes.serverTheme(NameTags.serverKey(mc));
            if (bound != null) {
                for (int k = 0; k < all.size(); k++) {
                    if (all.get(k).name().equalsIgnoreCase(bound)) {
                        index = k;
                    }
                }
            }
        }
        if (index >= all.size()) {
            return i.editorTheme();
        }
        return all.get(index);
    }

    private ThemeDef editorTheme() {
        Theme t = new Theme("Custom", edBg.argb(), edPanel.argb(), edPanel2.argb(), Ui_mix(edPanel2.argb(), 0xFFFFFFFF, 0.08f),
                edAccent.argb(), edAccent2.argb(), edText.argb(), Ui_mix(edText.argb(), edPanel.argb(), 0.45f));
        ThemeStyle s = new ThemeStyle(edRadius.f(), edBorder.f(), ThemeStyle.BORDER_DIM, edGlow.f(), true, edBlur.f(),
                ThemeStyle.Overlay.values()[edOverlay.index()], edOverlayStrength.f(),
                edSwitch.index() == 1 ? ThemeStyle.SwitchStyle.BRACKET : ThemeStyle.SwitchStyle.PILL, null);
        return new ThemeDef("Custom", t, s, null, false);
    }

    private static int Ui_mix(int a, int b, float t) {
        return com.example.neurocanvas.gui.Ui.mix(a, b, t);
    }

    /** The resolved theme: chosen or scheduled base, then the accent override, then the dynamic accent shift. Cached per frame. */
    private static ThemeDef resolve() {
        Interface i = instance;
        long now = System.nanoTime();
        if (i.current != null && now - i.computedAt < 6_000_000L) {
            return i.current;
        }
        i.computedAt = now;
        ThemeDef base = base();
        i.shownBase = base;
        Theme t = base.palette();
        if (i.customAccent.on()) {
            Theme a = Theme.fromAccent(i.accent.argb());
            t = new Theme(t.name(), t.bg(), t.panel(), t.panel2(), t.hover(), a.accent(), a.accent2(), t.text(), t.dim());
        }
        t = i.applyDynamic(t);
        i.current = base.withPalette(t);
        return i.current;
    }

    public static Theme theme() {
        return resolve().palette();
    }

    public static ThemeStyle style() {
        ThemeStyle s = resolve().style();
        return s;
    }

    /** The theme the player picked (before accent and dynamic changes); used to bind a theme to a server. */
    public static String baseThemeName() {
        resolve();
        return instance.shownBase.name();
    }

    /** @return the bundled font to use, or null for the vanilla font */
    public static Identifier font() {
        int i = instance.font.index();
        String id = i == FONTS.length - 1 ? (style().font() == null ? "inter" : style().font()) : FONT_IDS[i];
        return id == null || id.equals("minecraft") ? null : new Identifier("neurocanvas", id);
    }

    public static int listSort() { return instance == null ? 0 : instance.listSort.index(); }
    public static boolean animations() { return instance.animations.on(); }
    public static boolean shaderEffects() { return instance.effects.on(); }
    /** World visuals (lines, boxes, rings, ribbons) drawn by the mod's own shader instead of the game's basic shapes. */
    public static boolean shaderVisuals() { return instance == null || instance.shaderVis.on(); }
    public static float visualGlow() { return instance == null ? 0.7f : instance.visGlow.f(); }
    /** When on, world effects that the camera can see are not hidden by player and mob models. */
    public static boolean drawOverModels() { return instance == null || instance.drawOver.on(); }
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

    // ---- dynamic accent ---------------------------------------------------------------------

    private static float[] rgb(int argb) {
        return new float[] {((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }

    private static int pack(float[] c) {
        return 0xFF000000 | (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
    }

    /** @return {r, g, b, weight} of the colour the accent should lean towards right now, weight 0 = none */
    private float[] dynamicTarget(MinecraftClient mc) {
        int mode = dynamic.index();
        if (mode == 0 || mc.player == null || mc.world == null) {
            return new float[] {0, 0, 0, 0};
        }
        boolean health = mode == 1 || mode == 4;
        boolean time = mode == 2 || mode == 4;
        boolean biome = mode == 3 || mode == 4;
        if (health) {
            float ratio = MathHelper.clamp(mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth()), 0f, 1f);
            if (ratio < 0.6f) {
                float[] c = rgb(0xFFFF3B4D);
                return new float[] {c[0], c[1], c[2], MathHelper.clamp((0.6f - ratio) / 0.4f, 0f, 1f)};
            }
        }
        if (biome) {
            String name = mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("");
            int color = biomeColor(name);
            if (color != 0) {
                float[] c = rgb(color);
                return new float[] {c[0], c[1], c[2], 1f};
            }
        }
        if (time) {
            long t = mc.world.getTimeOfDay() % 24000L;
            if (t >= 13000L && t < 22500L) {
                float[] c = rgb(0xFF6C7CFF);
                return new float[] {c[0], c[1], c[2], 0.85f};
            }
            if ((t >= 11500L && t < 13000L) || t >= 22500L || t < 700L) {
                float[] c = rgb(0xFFFF9A4D);
                return new float[] {c[0], c[1], c[2], 0.75f};
            }
        }
        return new float[] {0, 0, 0, 0};
    }

    /** A colour for a biome by the words in its name, or 0 when there is no special colour. */
    static int biomeColor(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("cherry")) return 0xFFFF9CC8;
        if (n.contains("nether") || n.contains("basalt") || n.contains("crimson") || n.contains("soul")) return 0xFFFF5A3C;
        if (n.contains("warped")) return 0xFF3CD8C0;
        if (n.contains("end")) return 0xFFC07CFF;
        if (n.contains("desert") || n.contains("badlands") || n.contains("mesa") || n.contains("savanna")) return 0xFFE8B04C;
        if (n.contains("snow") || n.contains("ice") || n.contains("frozen") || n.contains("peaks") || n.contains("glacier")) return 0xFF9AD8FF;
        if (n.contains("ocean") || n.contains("river") || n.contains("beach")) return 0xFF4CA8FF;
        if (n.contains("swamp") || n.contains("mangrove")) return 0xFF7FA05A;
        if (n.contains("jungle") || n.contains("bamboo")) return 0xFF3CDC6A;
        if (n.contains("forest") || n.contains("taiga") || n.contains("plains") || n.contains("meadow") || n.contains("grove")) return 0xFF5CD07A;
        if (n.contains("cave") || n.contains("dripstone") || n.contains("deep_dark")) return 0xFF8A7CFF;
        return 0;
    }

    private Theme applyDynamic(Theme t) {
        if (dynamic.index() == 0) {
            dynInit = false;
            return t;
        }
        long now = System.nanoTime();
        float dt = dynInit ? Math.min((now - dynLast) / 1_000_000_000f, 0.2f) : 1f;
        dynLast = now;
        float[] target = dynamicTarget(MinecraftClient.getInstance());
        float[] a = rgb(t.accent());
        float[] a2 = rgb(t.accent2());
        float s = dynamicStrength.f() * target[3];
        float[] wantA = new float[3];
        float[] wantA2 = new float[3];
        for (int k = 0; k < 3; k++) {
            wantA[k] = a[k] + (target[k] - a[k]) * s;
            wantA2[k] = a2[k] + ((target[k] * 0.75f + 0.25f) - a2[k]) * s;
        }
        if (!dynInit) {
            System.arraycopy(wantA, 0, dynAccent, 0, 3);
            System.arraycopy(wantA2, 0, dynAccent2, 0, 3);
            dynInit = true;
        }
        float k = 1f - (float) Math.exp(-dt * 2.2f);
        for (int i = 0; i < 3; i++) {
            dynAccent[i] += (wantA[i] - dynAccent[i]) * k;
            dynAccent2[i] += (wantA2[i] - dynAccent2[i]) * k;
        }
        return t.withAccents(pack(dynAccent), pack(dynAccent2));
    }

    // ---- buttons ----------------------------------------------------------------------------

    private void say(MinecraftClient mc, String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Interface] " + text), false);
        }
    }

    public void useThemeByName(String name) {
        String[] names = Themes.names();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(name)) {
                theme.set(i);
            }
        }
    }

    /** Builds a theme from a picture file, saves it and selects it. */
    public String themeFromImage(Path file) {
        try {
            Theme palette = ImagePalette.fromFile(file);
            String name = "From image" + (++imageCounter > 1 ? " " + imageCounter : "");
            ThemeDef def = new ThemeDef(name, palette, ThemeStyle.CLASSIC, null, false);
            Themes.save(def);
            refreshNames();
            useThemeByName(name);
            return "Theme \"" + name + "\" made from " + file.getFileName();
        } catch (IOException | RuntimeException e) {
            return "Could not make a theme: " + e.getMessage();
        }
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (applyAll.on()) {
            applyAll.set(0);
            try {
                NeuroCanvasClient.modules().savePreset("Before accent");
            } catch (IOException ignored) {
                // The sync still runs; only the safety copy is missing.
            }
            Theme t = Theme.fromAccent(accent.argb());
            int[] palette = {t.accent(), t.accent2(), Ui_mix(t.accent(), 0xFFFFFFFF, 0.35f), Ui_mix(t.accent2(), 0xFFFFFFFF, 0.35f)};
            int changed = 0;
            for (Module m : NeuroCanvasClient.modules().all()) {
                if (m == this) {
                    continue;
                }
                int index = 0;
                for (Setting s : m.settings) {
                    if (s.kind == Setting.Kind.COLOR) {
                        s.setArgb((s.argb() & 0xFF000000) | (palette[Math.min(index, palette.length - 1)] & 0xFFFFFF));
                        index++;
                        changed++;
                    }
                }
            }
            say(mc, "accent applied to " + changed + " colours. The old look is saved as preset \"Before accent\".");
        }
        if (applyPack.on()) {
            applyPack.set(0);
            ThemeDef def = resolve();
            ThemeDef base = shownBase == null ? def : shownBase;
            if (!base.hasPack()) {
                say(mc, "\"" + base.name() + "\" has no pack. Packs: Sunset, Frost, Blood Moon, or a theme file with a \"pack\" section.");
            } else {
                try {
                    NeuroCanvasClient.modules().savePreset("Before theme pack");
                } catch (IOException ignored) {
                    // Continue without the safety copy.
                }
                int n = ThemePack.apply(NeuroCanvasClient.modules(), base.pack());
                say(mc, "pack of \"" + base.name() + "\" applied (" + n + " changes). The old look is saved as preset \"Before theme pack\".");
            }
        }
        if (fromImage.on()) {
            fromImage.set(0);
            Path file = Themes.dir().resolveSibling("theme-image.png");
            if (!Files.exists(file)) {
                try {
                    Files.createDirectories(file.getParent());
                } catch (IOException ignored) {
                    // Reported below by the missing file.
                }
                say(mc, "put a picture at " + file + " (PNG or JPG named theme-image.png), or use /theme image <path>, then press this again.");
            } else {
                say(mc, themeFromImage(file));
            }
        }
        if (precompile.on()) {
            precompile.set(0);
            say(mc, "compiling shaders...");
            for (String line : com.example.neurocanvas.fx.Shaders.compileAll(true)) {
                say(mc, "  " + line);
            }
        }
        if (mc.player != null && !joined) {
            joined = true;
            if (precompileOnJoin.on() && !com.example.neurocanvas.fx.Shaders.allReady()) {
                for (String line : com.example.neurocanvas.fx.Shaders.compileAll(false)) {
                    if (line.contains("FAILED")) {
                        say(mc, line);
                    }
                }
            }
        } else if (mc.player == null) {
            joined = false;
        }
        if (reloadThemes.on()) {
            reloadThemes.set(0);
            Themes.reload();
            refreshNames();
            say(mc, Themes.all().size() + " themes loaded.");
        }
        if (openThemes.on()) {
            openThemes.set(0);
            try {
                Files.createDirectories(Themes.dir());
            } catch (IOException ignored) {
                // Opening still tries.
            }
            net.minecraft.util.Util.getOperatingSystem().open(Themes.dir().toFile());
        }
        if (saveTheme.on()) {
            saveTheme.set(0);
            try {
                int n = 1;
                String name;
                do {
                    name = "My theme " + n++;
                } while (Themes.byName(name) != null);
                ThemeDef ed = editorTheme();
                Themes.save(new ThemeDef(name, ed.palette(), ed.style(), null, false));
                refreshNames();
                useThemeByName(name);
                say(mc, "saved as \"" + name + "\" in the themes folder.");
            } catch (IOException e) {
                say(mc, "could not save: " + e.getMessage());
            }
        }
        if (copyTheme.on()) {
            copyTheme.set(0);
            ThemeDef def = base();
            mc.keyboard.setClipboard(Themes.exportCode(def));
            say(mc, "code of \"" + def.name() + "\" copied to the clipboard.");
        }
        if (pasteTheme.on()) {
            pasteTheme.set(0);
            try {
                ThemeDef def = Themes.importCode(mc.keyboard.getClipboard());
                String name = def.name();
                int n = 2;
                while (Themes.byName(name) != null) {
                    name = def.name() + " " + n++;
                }
                Themes.save(new ThemeDef(name, def.palette(), def.style(), def.pack(), false));
                refreshNames();
                useThemeByName(name);
                say(mc, "theme \"" + name + "\" imported.");
            } catch (IOException e) {
                say(mc, "import failed: " + e.getMessage());
            }
        }
        if (reloadRequested) {
            reloadRequested = false;
            refreshNames();
        }
    }

    private boolean reloadRequested;

    public void reloadFromCommand() {
        Themes.reload();
        reloadRequested = true;
    }
}
