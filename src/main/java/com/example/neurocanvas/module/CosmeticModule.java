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
 * Base of the cosmetic modules (capes, wings, hats, outfits, pets, emotes, graffiti). Each is its own module with a list of the
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
            if (!Files.isDirectory(root)) {
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
        main.setting.set(java.util.Arrays.asList(main.setting.options).indexOf(name));
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
            mc.setScreen(new com.example.neurocanvas.gui.CosmeticEditor(this, mc.currentScreen));
            return;
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
        float scale = size.f() * itemScale / 16f;
        Matrix4f m = new Matrix4f(env.m());
        m.translate((float) at.x, (float) at.y, (float) at.z);
        m.rotateY((float) Math.toRadians(-yaw + 180f));
        m.scale(scale);
        return m;
    }

    /** Poses a model with its idle animation, or its walk animation while {@code moving}. */
    protected final void pose(Worn w, boolean moving, long nowNano) {
        w.model.resetPose();
        BbModel.Anim a = moving ? w.model.find("walk") : null;
        if (a == null) {
            a = w.model.find("idle");
        }
        if (a != null) {
            w.model.apply(a, BbInstance.time(a, 0L, nowNano, animSpeed.value));
        }
    }
}
