package com.example.neurocanvas.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Owns all modules and persists enabled state, settings, keybinds and HUD layout to
 * {@code modules.json}. The same snapshot format backs named presets and shareable codes.
 */
public final class ModuleManager {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new Gson();
    private static final Type CONFIG_TYPE = new TypeToken<Map<String, Map<String, Double>>>() { }.getType();
    private static final String ENABLED_KEY = "__enabled";
    private static final String HUD_KEY = "__hud";
    private static final String CODE_PREFIX = "NC1:";
    private static final int MAX_CODE_LENGTH = 40_000;

    private final List<Module> modules = new ArrayList<>();
    private final Path file;

    public ModuleManager(Path file) {
        this.file = file;
    }

    public void register(Module m) {
        modules.add(m);
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) {
            if (type.isInstance(m)) {
                return type.cast(m);
            }
        }
        throw new IllegalStateException(type.getSimpleName() + " is not registered");
    }

    public List<Module> all() {
        return modules;
    }

    public Path presetsDir() {
        return file.resolveSibling("presets");
    }

    // ---- snapshot -------------------------------------------------------------------------

    public Map<String, Map<String, Double>> snapshot() {
        Map<String, Map<String, Double>> config = new LinkedHashMap<>();
        for (Module m : modules) {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put(ENABLED_KEY, m.enabled ? 1.0 : 0.0);
            values.put("__key", (double) m.key);
            values.put("__hold", m.hold ? 1.0 : 0.0);
            for (Setting s : m.settings) {
                values.put(s.name, s.value);
            }
            config.put(m.name, values);
        }
        config.put(HUD_KEY, HudLayout.snapshot());
        return config;
    }

    public void apply(Map<String, Map<String, Double>> config) {
        for (Module m : modules) {
            Map<String, Double> saved = config.get(m.name);
            if (saved == null && m.name.equals("Model")) {
                saved = config.get("Cosmetics");
            }
            if (saved == null) {
                continue;
            }
            Double en = saved.get(ENABLED_KEY);
            if (en != null) {
                m.enabled = en >= 0.5;
            }
            if (saved.get("__key") != null) {
                m.key = saved.get("__key").intValue();
            }
            if (saved.get("__hold") != null) {
                m.hold = saved.get("__hold") >= 0.5;
            }
            for (Setting s : m.settings) {
                Double v = saved.get(s.name);
                if (v != null) {
                    s.set(v);
                }
            }
        }
        Map<String, Double> hud = config.get(HUD_KEY);
        if (hud != null) {
            HudLayout.apply(hud);
        }
    }

    private static Map<String, Map<String, Double>> read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, Map<String, Double>> config = PRETTY.fromJson(reader, CONFIG_TYPE);
            if (config == null) {
                throw new IOException("Empty config");
            }
            return config;
        } catch (RuntimeException e) {
            throw new IOException("Corrupt config: " + e.getMessage(), e);
        }
    }

    private static void write(Path path, Map<String, Map<String, Double>> config) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            PRETTY.toJson(config, CONFIG_TYPE, writer);
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---- main config ----------------------------------------------------------------------

    public void load() throws IOException {
        if (Files.exists(file)) {
            apply(read(file));
        }
    }

    public void save() throws IOException {
        write(file, snapshot());
    }

    // ---- presets --------------------------------------------------------------------------

    public static String cleanName(String raw) {
        String s = raw.trim().replaceAll("[^A-Za-z0-9 _-]", "");
        return s.length() > 24 ? s.substring(0, 24).trim() : s;
    }

    public List<String> presets() {
        if (!Files.isDirectory(presetsDir())) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(presetsDir())) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public void savePreset(String name) throws IOException {
        write(presetsDir().resolve(cleanName(name) + ".json"), snapshot());
    }

    public void loadPreset(String name) throws IOException {
        apply(read(presetsDir().resolve(cleanName(name) + ".json")));
        save();
    }

    public boolean deletePreset(String name) throws IOException {
        return Files.deleteIfExists(presetsDir().resolve(cleanName(name) + ".json"));
    }

    // ---- share codes ----------------------------------------------------------------------

    public String exportCode() {
        byte[] json = COMPACT.toJson(snapshot(), CONFIG_TYPE).getBytes(StandardCharsets.UTF_8);
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    public void importCode(String code) throws IOException {
        String c = code.trim();
        if (!c.startsWith(CODE_PREFIX) || c.length() > MAX_CODE_LENGTH) {
            throw new IOException("Not a NeuroCanvas code");
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(c.substring(CODE_PREFIX.length())), StandardCharsets.UTF_8);
            Map<String, Map<String, Double>> config = COMPACT.fromJson(json, CONFIG_TYPE);
            if (config == null) {
                throw new IOException("Empty code");
            }
            apply(config);
        } catch (RuntimeException e) {
            throw new IOException("Broken code: " + e.getMessage(), e);
        }
        save();
    }
}
