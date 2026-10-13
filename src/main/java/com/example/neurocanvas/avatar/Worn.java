package com.example.neurocanvas.avatar;

import com.example.neurocanvas.module.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** One slot of a cosmetic module: a choice setting and the item chosen in it, loaded into the game. */
public final class Worn {
    public static final String NONE = "None";

    public final Setting setting;
    private final CosmeticPack.Type type;
    private List<CosmeticPack.Item> items = new ArrayList<>();
    private String loaded = "\u0000";
    public BbInstance model;
    public Identifier image;
    public CosmeticPack.Config config = new CosmeticPack.Config();

    public Worn(CosmeticPack.Type type, Setting setting) {
        this.type = type;
        this.setting = setting;
    }

    /** The items found on disk; the slot's list is rebuilt and the chosen name is kept when it still exists. */
    public void setItems(List<CosmeticPack.Item> found) {
        items = found;
        String current = setting.option();
        List<String> options = new ArrayList<>();
        options.add(NONE);
        for (CosmeticPack.Item i : found) {
            options.add(i.name());
        }
        setting.setOptions(options.toArray(new String[0]));
        setting.value = Math.max(0, options.indexOf(current));
        loaded = "\u0000";
    }

    public boolean has(String name) {
        return items.stream().anyMatch(i -> i.name().equals(name));
    }

    public boolean active() {
        return model != null || image != null;
    }

    /** Loads the chosen item when the choice changed. */
    public void sync(MinecraftClient mc, Consumer<String> say) {
        String want = setting.option();
        if (want.equals(loaded)) {
            return;
        }
        unload(mc);
        loaded = want;
        if (want.equals(NONE)) {
            return;
        }
        CosmeticPack.Item item = items.stream().filter(i -> i.name().equals(want)).findFirst().orElse(null);
        if (item == null) {
            return;
        }
        try {
            Path file = item.file();
            if (file == null) {
                say.accept(type.label + " \"" + want + "\": no " + (type == CosmeticPack.Type.GRAFFITI ? ".png" : ".bbmodel") + " file in its folder");
                return;
            }
            config = item.config();
            if (type == CosmeticPack.Type.GRAFFITI) {
                try (InputStream in = Files.newInputStream(file)) {
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(NativeImage.read(in));
                    Identifier id = new Identifier("neurocanvas", "graffiti/" + UUID.randomUUID());
                    mc.getTextureManager().registerTexture(id, tex);
                    tex.setFilter(true, false);
                    image = id;
                }
            } else {
                model = new BbInstance(mc, BbModel.parse(Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException | RuntimeException e) {
            say.accept("Could not load " + type.label.toLowerCase() + " \"" + want + "\": " + e.getMessage());
        }
    }

    public void unload(MinecraftClient mc) {
        if (model != null) {
            model.close(mc);
            model = null;
        }
        if (image != null) {
            mc.getTextureManager().destroyTexture(image);
            image = null;
        }
        loaded = "\u0000";
    }
}
