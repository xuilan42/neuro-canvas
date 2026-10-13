package com.example.neurocanvas.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** What you have shared: for each item its code and the secret that lets you remove it again. Kept in neurocanvas/shared.json. */
public final class SharedStore {
    public record Item(String type, String name, String code, String token) { }

    private static final Gson GSON = new Gson();
    private static Path file;
    private static final List<Item> ITEMS = new ArrayList<>();

    private SharedStore() { }

    public static synchronized void init(Path path) {
        file = path;
        ITEMS.clear();
        try {
            if (Files.exists(path)) {
                List<Item> read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), new TypeToken<List<Item>>() { }.getType());
                if (read != null) {
                    ITEMS.addAll(read);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Start with an empty list.
        }
    }

    public static synchronized Item find(String type, String name) {
        for (Item i : ITEMS) {
            if (i.type().equals(type) && i.name().equals(name)) {
                return i;
            }
        }
        return null;
    }

    public static synchronized void add(Item item) {
        ITEMS.removeIf(i -> i.type().equals(item.type()) && i.name().equals(item.name()));
        ITEMS.add(item);
        save();
    }

    public static synchronized void remove(Item item) {
        ITEMS.remove(item);
        save();
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(ITEMS), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Kept in memory anyway.
        }
    }
}
