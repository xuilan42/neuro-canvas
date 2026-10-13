package com.example.neurocanvas.avatar;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The cosmetics on disk. One folder per item: neurocanvas/wardrobe/&lt;type&gt;/&lt;name&gt;/ with a .bbmodel (or, for graffiti, a .png)
 * and an optional cosmetic.json. Models are drawn in the same space as an Avatar made for the player's own body: 16 units
 * are one block, the feet are at y = 0, the head at y 24 to 32, the front faces north (-z), the back is +z.
 */
public final class CosmeticPack {
    public enum Type {
        CAPE("capes", "Cape"), WINGS("wings", "Wings"), HAT("hats", "Hat"), OUTFIT("outfits", "Outfit"),
        PET("pets", "Pet"), EMOTE("emotes", "Emote"), GRAFFITI("graffiti", "Graffiti");

        public final String folder;
        public final String label;

        Type(String folder, String label) {
            this.folder = folder;
            this.label = label;
        }
    }

    /** cosmetic.json */
    public static final class Config {
        /** The model file, when the folder has several. */
        public String model;
        /** Extra size on top of the Size setting. */
        public Float scale;
        /** Emotes: the animation to play; the first one when missing. */
        public String animation;
        /** Graffiti: the picture and its width in blocks. */
        public String image;
        public Float size;
    }

    public record Item(Type type, String name, Path folder, Config config) {
        public Path file() throws IOException {
            String ext = type == Type.GRAFFITI ? ".png" : ".bbmodel";
            String wanted = type == Type.GRAFFITI ? config.image : config.model;
            if (wanted != null && Files.exists(folder.resolve(wanted))) {
                return folder.resolve(wanted);
            }
            try (Stream<Path> s = Files.list(folder)) {
                return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)).sorted().findFirst().orElse(null);
            }
        }
    }

    private CosmeticPack() { }

    public static Map<Type, List<Item>> scan(Path root) {
        Map<Type, List<Item>> out = new EnumMap<>(Type.class);
        for (Type t : Type.values()) {
            List<Item> items = new ArrayList<>();
            Path dir = root.resolve(t.folder);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    s.filter(Files::isDirectory).sorted().forEach(p -> items.add(new Item(t, p.getFileName().toString(), p, readConfig(p))));
                } catch (IOException ignored) {
                    // An unreadable folder has no items.
                }
            }
            items.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            out.put(t, items);
        }
        return out;
    }

    private static Config readConfig(Path folder) {
        try {
            Path json = folder.resolve("cosmetic.json");
            if (Files.exists(json)) {
                Config c = new Gson().fromJson(Files.readString(json), Config.class);
                if (c != null) {
                    return c;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A broken cosmetic.json is treated as an empty one.
        }
        return new Config();
    }
}
