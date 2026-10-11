package com.example.neurocanvas.module;

import java.lang.reflect.Field;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;

/**
 * Reads the exact time left of an item cooldown. The game keeps the end tick in a private field, so it is read by
 * reflection using the names of the running game (the mapping resolver translates them), and falls back to "unknown".
 */
final class CooldownReader {
    private static boolean tried;
    private static Field entriesField;
    private static Field tickField;
    private static Field endField;
    private static String entryName;

    private CooldownReader() { }

    private static void init() {
        tried = true;
        try {
            MappingResolver r = FabricLoader.getInstance().getMappingResolver();
            String manager = r.mapClassName("named", "net.minecraft.entity.player.ItemCooldownManager");
            entryName = r.mapClassName("named", "net.minecraft.entity.player.ItemCooldownManager$Entry");
            entriesField = ItemCooldownManager.class.getDeclaredField(r.mapFieldName("named", manager, "entries", "Ljava/util/Map;"));
            tickField = ItemCooldownManager.class.getDeclaredField(r.mapFieldName("named", manager, "tick", "I"));
            entriesField.setAccessible(true);
            tickField.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            entriesField = null;
        }
    }

    /** @return seconds left, or -1 when there is no cooldown or the exact time cannot be read */
    static float secondsLeft(ItemCooldownManager manager, Item item) {
        if (!tried) {
            init();
        }
        if (entriesField == null) {
            return -1f;
        }
        try {
            Object entry = ((Map<?, ?>) entriesField.get(manager)).get(item);
            if (entry == null) {
                return -1f;
            }
            if (endField == null) {
                MappingResolver r = FabricLoader.getInstance().getMappingResolver();
                endField = entry.getClass().getDeclaredField(r.mapFieldName("named", entryName, "endTick", "I"));
                endField.setAccessible(true);
            }
            return Math.max(0, endField.getInt(entry) - tickField.getInt(manager)) / 20f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            entriesField = null;
            return -1f;
        }
    }
}
