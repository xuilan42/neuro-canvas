package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.BbInstance;
import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.avatar.ExamplePack;
import com.example.neurocanvas.avatar.Worn;
import com.example.neurocanvas.fx.EffectDraw;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Base of the cosmetic modules (capes, wings, hats, outfits, emotes, graffiti). Each is its own module with a list of the
 * items of its kind found in neurocanvas/wardrobe/&lt;type&gt;/ and the settings every cosmetic shares. Only you see them for now.
 */
public abstract class CosmeticModule extends Module {
    /** Where the player and the camera are this frame. */
    protected record Body(Vec3d pos, float bodyYaw, float headYaw, float pitch, double speed, boolean moving, float light, boolean visible) { }

    private static boolean examplesWritten;

    private final Path root;
    private final CosmeticPack.Type type;
    private final List<Worn> worn = new ArrayList<>();
    protected final Setting item;
    protected final Setting size = add(Setting.number("Size", 0.4, 3, 1));
    protected final Setting shading = add(Setting.number("Shading", 0, 1, 0.5));
    protected final Setting animSpeed = add(Setting.number("Animation speed", 0.2, 3, 1));
    protected final Setting firstPerson;
    private final Setting editor;
    private final Setting share;
    private final Setting unshare;
    private final Setting reload = add(Setting.button("Reload"));
    private final Setting openFolder = add(Setting.button("Open folder"));
    protected final Worn main;

    protected CosmeticModule(String name, String description, CosmeticPack.Type type, Path root, boolean wornOnBody) {
        this(name, description, type, type.label, root, wornOnBody);
    }

    protected CosmeticModule(String name, String description, CosmeticPack.Type type, String slotLabel, Path root, boolean wornOnBody) {
        super(name, Category.COSMETICS, description, false);
        aiControllable = false;
        this.root = root;
        this.type = type;
        item = add(Setting.choice(slotLabel, 0, Worn.NONE));
        settings.add(0, settings.remove(settings.size() - 1));
        firstPerson = wornOnBody ? add(Setting.bool("Show in first person", false)) : null;
        editor = com.example.neurocanvas.avatar.CosmeticBuilder.hasEditor(type) ? add(Setting.button("Editor")) : null;
        share = add(Setting.button("Share"));
        unshare = add(Setting.button("Unshare"));
        main = wear(item);
        writeExamples(root);
        rescan();
    }

    private static synchronized void writeExamples(Path root) {
        if (examplesWritten) {
            return;
        }
        examplesWritten = true;
        try {
            if (!Files.isDirectory(root) || com.example.neurocanvas.avatar.ExampleAvatar.outdated(root.resolve("emotes/Example Wave"))) {
                ExamplePack.write(root);
            }
        } catch (IOException ignored) {
            // The folder is optional; items can still be added by hand.
        }
    }

    protected final Worn wear(Setting slot) {
        Worn w = new Worn(type, slot);
        worn.add(w);
        return w;
    }

    /** The name of the item worn in the first slot while the module is on, or null. */
    public String wornName() {
        return enabled && main.model != null ? main.setting.option() : null;
    }

    public CosmeticPack.Type type() {
        return type;
    }

    public Path folder() {
        return root.resolve(type.folder);
    }

    /** Looks for items again and refreshes every slot's list. */
    public void rescan() {
        List<CosmeticPack.Item> found = CosmeticPack.scan(root).get(type);
        for (Worn w : worn) {
            w.setItems(found);
        }
    }

    /** Wears an item by its name (and switches the module on). False when there is no such item. */
    public boolean wear(String name) {
        if (!main.has(name)) {
            return false;
        }
        // An item goes into the first free slot, or into the first slot when all are taken.
        Worn target = main;
        for (Worn w : worn) {
            if (w.setting.option().equals(Worn.NONE)) {
                target = w;
                break;
            }
        }
        target.setting.set(java.util.Arrays.asList(target.setting.options).indexOf(name));
        enabled = true;
        return true;
    }

    protected static void say(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Cosmetics] " + text), false);
        }
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (openFolder.on()) {
            openFolder.set(0);
            try {
                Files.createDirectories(folder());
            } catch (IOException ignored) {
                // Opening a missing folder just fails quietly.
            }
            net.minecraft.util.Util.getOperatingSystem().open(folder().toFile());
        }
        if (editor != null && editor.on()) {
            editor.set(0);
            mc.setScreen(com.example.neurocanvas.gui.CosmeticEditor.forCosmetic(this, mc.currentScreen));
            return;
        }
        if (share.on()) {
            share.set(0);
            shareSelected(mc);
        }
        if (unshare.on()) {
            unshare.set(0);
            unshareSelected();
        }
        if (reload.on()) {
            reload.set(0);
            rescan();
            say(name + ": " + (main.setting.options.length - 1) + " item(s) found");
        }
        if (!enabled) {
            for (Worn w : worn) {
                w.unload(mc);
            }
            return;
        }
        if (mc.player == null) {
            return;
        }
        for (Worn w : worn) {
            w.sync(mc, CosmeticModule::say);
        }
        tickCosmetic(mc);
    }

    protected void tickCosmetic(MinecraftClient mc) { }

    /** Every item of this kind in the wardrobe, once, in the order the slots list them. */
    private List<String> allItems() {
        List<String> names = new ArrayList<>();
        for (Worn w : worn) {
            for (String n : w.setting.options) {
                if (!n.equals(Worn.NONE) && !names.contains(n)) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    private boolean isWorn(String name) {
        for (Worn w : worn) {
            if (w.setting.option().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Uploads the items worn in this module's slots without asking (for the Showcase's "share all"). */
    public void shareWorn(MinecraftClient mc) {
        for (Worn w : worn) {
            String n = w.setting.option();
            if (!n.equals(Worn.NONE)) {
                shareItem(mc, n);
            }
        }
    }

    /** Asks which items to upload (the ones you are wearing are ticked), then uploads those that are not shared yet. */
    public void shareSelected(MinecraftClient mc) {
        List<com.example.neurocanvas.gui.ItemPickerScreen.Row> rows = new ArrayList<>();
        for (String n : allItems()) {
            boolean shared = com.example.neurocanvas.client.SharedStore.find(type.folder, n) != null;
            rows.add(new com.example.neurocanvas.gui.ItemPickerScreen.Row(n, shared ? "shared" : isWorn(n) ? "worn" : "", !shared && isWorn(n)));
        }
        if (rows.isEmpty()) {
            say("Nothing to share: the wardrobe has no items of this kind");
            return;
        }
        mc.setScreen(new com.example.neurocanvas.gui.ItemPickerScreen(mc.currentScreen, "Share " + name,
                "Tick what to upload to the Market (Community)", "Share", rows, names -> {
                    for (String n : names) {
                        shareItem(MinecraftClient.getInstance(), n);
                    }
                }));
    }

    private void shareItem(MinecraftClient mc, String name) {
        com.example.neurocanvas.client.SharedStore.Item known = com.example.neurocanvas.client.SharedStore.find(type.folder, name);
        if (known != null) {
            mc.keyboard.setClipboard(known.code());
            say("\"" + name + "\" is already shared: code " + known.code() + " (copied)");
            return;
        }
        String author = mc.player == null ? "" : mc.player.getName().getString();
        java.util.Map<String, byte[]> files;
        try {
            files = com.example.neurocanvas.client.CommunityClient.readFolder(folder().resolve(name));
        } catch (IOException e) {
            say("Cannot share \"" + name + "\": " + e.getMessage());
            return;
        }
        say("Sharing \"" + name + "\"...");
        com.example.neurocanvas.client.CommunityClient.upload(type.folder, name, author, files).whenComplete((shared, error) ->
                MinecraftClient.getInstance().execute(() -> {
                    if (error != null) {
                        say("Could not share \"" + name + "\": " + com.example.neurocanvas.client.MarketClient.reason(error));
                        return;
                    }
                    com.example.neurocanvas.client.SharedStore.add(new com.example.neurocanvas.client.SharedStore.Item(type.folder, name, shared.code(), shared.token()));
                    MinecraftClient.getInstance().keyboard.setClipboard(shared.code());
                    say("\"" + name + "\" is shared: code " + shared.code() + " (copied). Others find it in the Market, Source: Community.");
                }));
    }

    private void unshareSelected() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<com.example.neurocanvas.gui.ItemPickerScreen.Row> rows = new ArrayList<>();
        for (String n : allItems()) {
            if (com.example.neurocanvas.client.SharedStore.find(type.folder, n) != null) {
                rows.add(new com.example.neurocanvas.gui.ItemPickerScreen.Row(n, isWorn(n) ? "worn" : "", isWorn(n)));
            }
        }
        if (rows.isEmpty()) {
            say("None of your " + name + " items is shared");
            return;
        }
        mc.setScreen(new com.example.neurocanvas.gui.ItemPickerScreen(mc.currentScreen, "Unshare " + name,
                "Tick what to take down from the Market", "Unshare", rows, names -> {
                    for (String n : names) {
                        unshareItem(n);
                    }
                }));
    }

    private void unshareItem(String name) {
        com.example.neurocanvas.client.SharedStore.Item known = com.example.neurocanvas.client.SharedStore.find(type.folder, name);
        if (known == null) {
            return;
        }
        com.example.neurocanvas.client.CommunityClient.remove(known.code(), known.token()).whenComplete((v, error) ->
                MinecraftClient.getInstance().execute(() -> {
                    if (error != null) {
                        say("Could not remove \"" + name + "\": " + com.example.neurocanvas.client.MarketClient.reason(error));
                        return;
                    }
                    com.example.neurocanvas.client.SharedStore.remove(known);
                    say("\"" + name + "\" is no longer shared");
                }));
    }

    // ---- helpers for drawing ----------------------------------------------------------------

    protected final Body body(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            return null;
        }
        float td = env.tickDelta();
        Vec3d pos = EffectDraw.lerpPos(p, td);
        double speed = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ) * 20.0;
        float light = 0.35f + 0.65f * mc.world.getLightLevel(BlockPos.ofFloored(pos.x, pos.y + 1, pos.z)) / 15f;
        boolean visible = !mc.options.getPerspective().isFirstPerson() || (firstPerson != null && firstPerson.on());
        return new Body(pos, MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw), MathHelper.lerpAngleDegrees(td, p.prevHeadYaw, p.headYaw),
                MathHelper.lerp(td, p.prevPitch, p.getPitch()), speed, speed > 0.3, light, visible);
    }

    protected final Matrix4f base(RenderEnv env, Vec3d at, float yaw, Worn w) {
        float itemScale = w.config.scale == null ? 1f : w.config.scale;
        return com.example.neurocanvas.avatar.Attach.base(env.m(), at, yaw, size.f() * itemScale / 16f);
    }

    /** Poses a model with its idle animation, or its walk animation while {@code moving}. */
    protected final void pose(Worn w, boolean moving, long nowNano) {
        com.example.neurocanvas.avatar.Attach.pose(w.model, moving, nowNano, animSpeed.value);
    }
}
