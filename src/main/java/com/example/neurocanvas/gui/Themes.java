package com.example.neurocanvas.gui;

import com.example.neurocanvas.gui.ThemeStyle.Overlay;
import com.example.neurocanvas.gui.ThemeStyle.SwitchStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** All themes: the built-in ones, the ones saved in neurocanvas/themes/*.json, and the per-server theme table. */
public final class Themes {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final String CODE_PREFIX = "NT1:";
    private static final List<ThemeDef> BUILTIN = new ArrayList<>();
    private static final List<ThemeDef> USER = new ArrayList<>();
    private static final Map<String, String> SERVERS = new TreeMap<>();
    private static Path dir;
    private static Path serversFile;

    private static final String SUNSET = """
            {"CustomSky":{"enabled":true,"settings":{"Sky color":"#FF7A3D","Sky strength":0.85,"Cloud color":"#FFB070","Cloud strength":0.8}},
             "TimeChanger":{"enabled":true,"settings":{"Mode":"Fixed","Time":12400}},
             "CustomFog":{"enabled":true,"settings":{"Custom color":true,"Color":"#FF9A5C","Start (%)":6,"End (%)":55}},
             "ColorGrading":{"enabled":true,"settings":{"Tint color":"#FF8A4A","Tint strength":0.1,"Vignette":true,"Vignette strength":0.5}},
             "TargetESP":{"settings":{"Color 1":"#FF5A2B","Color 2":"#FFC14D"}},
             "Particles":{"settings":{"Color 1":"#FFB347","Color 2":"#FF5A2B"}},
             "HitParticles":{"settings":{"Color 1":"#FFB347","Color 2":"#FF4D2B"}},
             "Crosshair":{"settings":{"Color":"#FFE2B8"}}}""";
    private static final String FROST = """
            {"CustomSky":{"enabled":true,"settings":{"Sky color":"#8EC9FF","Sky strength":0.8,"Cloud color":"#EAF6FF","Cloud strength":0.8}},
             "TimeChanger":{"enabled":true,"settings":{"Mode":"Fixed","Time":6000}},
             "CustomFog":{"enabled":true,"settings":{"Custom color":true,"Color":"#CDE9FF","Start (%)":5,"End (%)":50}},
             "ColorGrading":{"enabled":true,"settings":{"Tint color":"#7FC4FF","Tint strength":0.09,"Vignette":true,"Vignette strength":0.35}},
             "TargetESP":{"settings":{"Color 1":"#5AC8FF","Color 2":"#E8F7FF"}},
             "Particles":{"settings":{"Color 1":"#BFEAFF","Color 2":"#7FD4FF"}},
             "HitParticles":{"settings":{"Shape":"Star","Color 1":"#E8F7FF","Color 2":"#5AC8FF"}},
             "Crosshair":{"settings":{"Color":"#E8F7FF"}}}""";
    private static final String BLOOD = """
            {"CustomSky":{"enabled":true,"settings":{"Sky color":"#5A0A12","Sky strength":0.85,"Cloud color":"#3A0A10","Cloud strength":0.8}},
             "TimeChanger":{"enabled":true,"settings":{"Mode":"Fixed","Time":18000}},
             "CustomFog":{"enabled":true,"settings":{"Custom color":true,"Color":"#7A0F18","Start (%)":4,"End (%)":45}},
             "ColorGrading":{"enabled":true,"settings":{"Tint color":"#AA0010","Tint strength":0.12,"Vignette":true,"Vignette strength":0.7}},
             "TargetESP":{"settings":{"Color 1":"#FF1A1A","Color 2":"#7A0000"}},
             "Particles":{"settings":{"Color 1":"#FF2B3D","Color 2":"#7A0F1C"}},
             "HitParticles":{"settings":{"Shape":"Heart","Color 1":"#FF2B3D","Color 2":"#7A0F1C"}},
             "Crosshair":{"settings":{"Color":"#FFB0B5"}}}""";

    static {
        for (Theme t : Theme.ALL) {
            BUILTIN.add(new ThemeDef(t.name(), t, ThemeStyle.CLASSIC, null, true));
        }
        BUILTIN.add(def("Glass", 0x6612161F, 0x33FFFFFF, 0x44FFFFFF, 0x59FFFFFF, 0xFF7CC4FF, 0xFFB38CFF, 0xFFFFFFFF, 0xC8FFFFFF,
                new ThemeStyle(1.3f, 1f, 0x66FFFFFF, 0.25f, true, 1.0f, Overlay.NONE, 0f, SwitchStyle.PILL, null), null));
        BUILTIN.add(def("Terminal", 0xF2050A05, 0xFF0A140A, 0xFF0F1F0F, 0xFF173317, 0xFF33FF66, 0xFF00CC44, 0xFFB8FFC8, 0xFF4A9A5E,
                new ThemeStyle(0f, 1f, 0xFF1F5A2C, 0.15f, false, 0f, Overlay.SCANLINES, 0.55f, SwitchStyle.BRACKET, "jetbrainsmono"), null));
        BUILTIN.add(def("Retro", 0xFF2B2B2B, 0xFF3C3C3C, 0xFF555555, 0xFF6B6B6B, 0xFF55FF55, 0xFFFFAA00, 0xFFFFFFFF, 0xFFB0B0B0,
                new ThemeStyle(0f, 2f, 0xFF000000, 0f, false, 0f, Overlay.NONE, 0f, SwitchStyle.PILL, "minecraft"), null));
        BUILTIN.add(def("Neon", 0xF2070412, 0xFF0D0820, 0xFF140C30, 0xFF1E1445, 0xFFFF2BD6, 0xFF2BF0FF, 0xFFF5ECFF, 0xFF9C84C8,
                new ThemeStyle(0.8f, 1f, ThemeStyle.BORDER_ACCENT, 0.8f, true, 0f, Overlay.GRID, 0.4f, SwitchStyle.PILL, null), null));
        BUILTIN.add(def("Paper", 0xFFF7F4EC, 0xFFEFEBE0, 0xFFE6E1D3, 0xFFDAD4C3, 0xFF2F2F2F, 0xFF6B6B6B, 0xFF1E1E1E, 0xFF7A766B,
                new ThemeStyle(0.35f, 1f, 0xFFCFC9B8, 0f, false, 0f, Overlay.NONE, 0f, SwitchStyle.PILL, null), null));
        BUILTIN.add(def("Aurora", 0xF2080C16, 0xCC0F1626, 0xCC16203A, 0xCC20304F, 0xFF5CFFC8, 0xFFB26CFF, 0xFFEAF6FF, 0xFF8CA3C0,
                new ThemeStyle(1.2f, 0f, ThemeStyle.BORDER_DIM, 0.3f, true, 0.6f, Overlay.AURORA, 0.7f, SwitchStyle.PILL, null), null));
        BUILTIN.add(def("High Contrast", 0xFF000000, 0xFF000000, 0xFF1C1C1C, 0xFF383838, 0xFFFFFF00, 0xFF00FFFF, 0xFFFFFFFF, 0xFFD8D8D8,
                new ThemeStyle(0.3f, 2f, 0xFFFFFFFF, 0f, false, 0f, Overlay.NONE, 0f, SwitchStyle.PILL, null), null));
        BUILTIN.add(def("Color-blind safe", 0xF2101216, 0xFF181B20, 0xFF21252B, 0xFF2D323A, 0xFFE69F00, 0xFF56B4E9, 0xFFF2F2F2, 0xFFA0A6AD,
                new ThemeStyle(0.8f, 1f, ThemeStyle.BORDER_DIM, 0f, true, 0f, Overlay.NONE, 0f, SwitchStyle.PILL, null), null));

        BUILTIN.add(def("Sunset", 0xF21A0E10, 0xFF241418, 0xFF301C20, 0xFF3F262B, 0xFFFF8A4A, 0xFFFFC14D, 0xFFFFF0E6, 0xFFB89A8E,
                new ThemeStyle(1.1f, 0f, ThemeStyle.BORDER_DIM, 0.25f, true, 0f, Overlay.NONE, 0f, SwitchStyle.PILL, null), SUNSET));
        BUILTIN.add(def("Frost", 0xCC0C1620, 0xAA14222F, 0xAA1B2D3E, 0xAA254058, 0xFF7FD4FF, 0xFFE8F7FF, 0xFFF2FAFF, 0xFF9CB8CC,
                new ThemeStyle(1.2f, 1f, 0x667FD4FF, 0.3f, true, 0.7f, Overlay.NONE, 0f, SwitchStyle.PILL, null), FROST));
        BUILTIN.add(def("Blood Moon", 0xF2160608, 0xFF200A0D, 0xFF2D0F13, 0xFF40151B, 0xFFFF2B3D, 0xFF8A0F1C, 0xFFFFE9EA, 0xFFB08A8D,
                new ThemeStyle(0.7f, 1f, 0xFF7A0F1C, 0.5f, true, 0f, Overlay.NONE, 0f, SwitchStyle.PILL, null), BLOOD));
    }


    private Themes() { }

    private static ThemeDef def(String name, int bg, int panel, int panel2, int hover, int accent, int accent2, int text, int dim,
                                ThemeStyle style, String pack) {
        return new ThemeDef(name, new Theme(name, bg, panel, panel2, hover, accent, accent2, text, dim), style,
                pack == null ? null : JsonParser.parseString(pack).getAsJsonObject(), true);
    }

    // ---- lists ----------------------------------------------------------------------------

    public static void init(Path themesDir, Path serversTable) {
        dir = themesDir;
        serversFile = serversTable;
        reload();
    }

    public static Path dir() {
        return dir;
    }

    public static void reload() {
        USER.clear();
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted().forEach(p -> {
                    try {
                        USER.add(parse(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject(), false));
                    } catch (IOException | RuntimeException ignored) {
                        // A broken theme file is skipped, the others still load.
                    }
                });
            } catch (IOException ignored) {
                // No themes folder yet.
            }
        }
        SERVERS.clear();
        try {
            if (serversFile != null && Files.exists(serversFile)) {
                JsonObject o = JsonParser.parseString(Files.readString(serversFile, StandardCharsets.UTF_8)).getAsJsonObject();
                o.entrySet().forEach(e -> SERVERS.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString()));
            }
        } catch (IOException | RuntimeException ignored) {
            // Start with no per-server themes.
        }
    }

    public static List<ThemeDef> all() {
        List<ThemeDef> out = new ArrayList<>(BUILTIN);
        out.addAll(USER);
        return out;
    }

    /** Names for the theme choice: every theme, then the live editor entry. */
    public static String[] names() {
        List<ThemeDef> list = all();
        String[] out = new String[list.size() + 1];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i).name();
        }
        out[list.size()] = "Custom (editor)";
        return out;
    }

    public static ThemeDef byName(String name) {
        for (ThemeDef d : all()) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    // ---- per-server themes ----------------------------------------------------------------

    public static String serverTheme(String server) {
        return server == null ? null : SERVERS.get(server.toLowerCase(Locale.ROOT));
    }

    public static void bindServer(String server, String theme) throws IOException {
        if (theme == null) {
            SERVERS.remove(server.toLowerCase(Locale.ROOT));
        } else {
            SERVERS.put(server.toLowerCase(Locale.ROOT), theme);
        }
        JsonObject o = new JsonObject();
        SERVERS.forEach(o::addProperty);
        Files.createDirectories(serversFile.getParent());
        Files.writeString(serversFile, PRETTY.toJson(o), StandardCharsets.UTF_8);
    }

    // ---- files and codes ------------------------------------------------------------------

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static int color(String s, int fallback) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) {
                t = t.substring(1);
            }
            long v = Long.parseLong(t, 16);
            return t.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float num(JsonObject o, String key, float fallback, float min, float max) {
        try {
            return o.has(key) ? Math.max(min, Math.min(max, o.get(key).getAsFloat())) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static ThemeDef parse(JsonObject o, boolean builtin) {
        String name = o.has("name") ? o.get("name").getAsString() : "Theme";
        name = name.length() > 28 ? name.substring(0, 28) : name;
        JsonObject p = o.has("palette") ? o.getAsJsonObject("palette") : new JsonObject();
        Theme base = Theme.ALL.get(0);
        java.util.function.BiFunction<String, Integer, Integer> c = (k, d) -> p.has(k) ? color(p.get(k).getAsString(), d) : d;
        Theme theme = new Theme(name, c.apply("bg", base.bg()), c.apply("panel", base.panel()), c.apply("panel2", base.panel2()),
                c.apply("hover", base.hover()), c.apply("accent", base.accent()), c.apply("accent2", base.accent2()),
                c.apply("text", base.text()), c.apply("dim", base.dim()));
        JsonObject s = o.has("style") ? o.getAsJsonObject("style") : new JsonObject();
        ThemeStyle d = ThemeStyle.CLASSIC;
        int border = d.borderColor();
        if (s.has("borderColor")) {
            String v = s.get("borderColor").getAsString();
            border = v.equalsIgnoreCase("accent") ? ThemeStyle.BORDER_ACCENT : v.equalsIgnoreCase("dim") ? ThemeStyle.BORDER_DIM : color(v, border);
        }
        Overlay overlay = Overlay.NONE;
        SwitchStyle sw = SwitchStyle.PILL;
        try {
            if (s.has("overlay")) {
                overlay = Overlay.valueOf(s.get("overlay").getAsString().toUpperCase(Locale.ROOT));
            }
            if (s.has("switch")) {
                sw = SwitchStyle.valueOf(s.get("switch").getAsString().toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown names fall back to none / pill.
        }
        ThemeStyle style = new ThemeStyle(num(s, "radius", 1f, 0f, 2f), num(s, "border", 0f, 0f, 4f), border, num(s, "glow", 0f, 0f, 1f),
                !s.has("shadow") || s.get("shadow").getAsBoolean(), num(s, "blur", 0f, 0f, 2f), overlay, num(s, "overlayStrength", 0.5f, 0f, 1f),
                sw, s.has("font") ? s.get("font").getAsString() : null);
        JsonObject pack = o.has("pack") && o.get("pack").isJsonObject() ? o.getAsJsonObject("pack") : null;
        return new ThemeDef(name, theme, style, pack, builtin);
    }

    public static JsonObject toJson(ThemeDef def) {
        JsonObject o = new JsonObject();
        o.addProperty("name", def.name());
        JsonObject p = new JsonObject();
        Theme t = def.palette();
        p.addProperty("bg", hex(t.bg()));
        p.addProperty("panel", hex(t.panel()));
        p.addProperty("panel2", hex(t.panel2()));
        p.addProperty("hover", hex(t.hover()));
        p.addProperty("accent", hex(t.accent()));
        p.addProperty("accent2", hex(t.accent2()));
        p.addProperty("text", hex(t.text()));
        p.addProperty("dim", hex(t.dim()));
        o.add("palette", p);
        ThemeStyle s = def.style();
        JsonObject st = new JsonObject();
        st.addProperty("radius", s.radius());
        st.addProperty("border", s.border());
        st.addProperty("borderColor", s.borderColor() == ThemeStyle.BORDER_ACCENT ? "accent" : s.borderColor() == ThemeStyle.BORDER_DIM ? "dim" : hex(s.borderColor()));
        st.addProperty("glow", s.glow());
        st.addProperty("shadow", s.shadow());
        st.addProperty("blur", s.blur());
        st.addProperty("overlay", s.overlay().name().toLowerCase(Locale.ROOT));
        st.addProperty("overlayStrength", s.overlayStrength());
        st.addProperty("switch", s.switchStyle().name().toLowerCase(Locale.ROOT));
        if (s.font() != null) {
            st.addProperty("font", s.font());
        }
        o.add("style", st);
        if (def.hasPack()) {
            o.add("pack", def.pack());
        }
        return o;
    }

    /** Saves a theme into the themes folder under a free file name and reloads the list. */
    public static Path save(ThemeDef def) throws IOException {
        Files.createDirectories(dir);
        String base = def.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isEmpty()) {
            base = "theme";
        }
        Path file = dir.resolve(base + ".json");
        for (int n = 2; Files.exists(file); n++) {
            file = dir.resolve(base + "-" + n + ".json");
        }
        Files.writeString(file, PRETTY.toJson(toJson(def)), StandardCharsets.UTF_8);
        reload();
        return file;
    }

    public static String exportCode(ThemeDef def) {
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(toJson(def).toString().getBytes(StandardCharsets.UTF_8));
    }

    public static ThemeDef importCode(String code) throws IOException {
        String c = code.trim();
        if (!c.startsWith(CODE_PREFIX) || c.length() > 60_000) {
            throw new IOException("Not a theme code");
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(c.substring(CODE_PREFIX.length())), StandardCharsets.UTF_8);
            return parse(JsonParser.parseString(json).getAsJsonObject(), false);
        } catch (RuntimeException e) {
            throw new IOException("Broken theme code");
        }
    }
}
