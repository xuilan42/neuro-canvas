package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.client.NeuroCanvasClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Sprays a picture on the face of the block you look at. Set the module's Keybind to the spray key. */
public final class Graffiti extends CosmeticModule {
    private static final class Spray {
        Vec3d pos;
        Vec3d normal;
        float size;
        Identifier image;
        long created;
    }

    private final Setting lifetime = add(Setting.number("Lifetime (s)", 20, 900, 300));
    private final Setting reach = add(Setting.number("Reach (blocks)", 2, 12, 6));
    private final List<Spray> sprays = new ArrayList<>();
    private boolean keyWasDown;

    public Graffiti(Path root) {
        super("Graffiti", "A picture from neurocanvas/wardrobe/graffiti sprayed on a wall; the Keybind row is the spray key", CosmeticPack.Type.GRAFFITI, root, false);
        customKey = true;
    }

    @Override
    protected void tickCosmetic(MinecraftClient mc) {
        boolean down = key >= 0 && mc.currentScreen == null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
        if (down && !keyWasDown) {
            spray(mc);
        }
        keyWasDown = down;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        super.tickAlways(mc);
        if (!enabled) {
            sprays.clear();
        }
    }

    /** Sprays the picture on the block face you are looking at. */
    public void spray(MinecraftClient mc) {
        if (!enabled || mc.player == null) {
            return;
        }
        if (main.image == null) {
            say("Pick a graffiti first");
            return;
        }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > reach.value * reach.value) {
            say("Too far from the wall");
            return;
        }
        Spray s = new Spray();
        s.normal = new Vec3d(hit.getSide().getOffsetX(), hit.getSide().getOffsetY(), hit.getSide().getOffsetZ());
        s.pos = hit.getPos().add(s.normal.multiply(0.012));
        s.size = (main.config.size == null ? 1.5f : main.config.size) * size.f();
        s.image = main.image;
        s.created = System.currentTimeMillis();
        sprays.removeIf(o -> o.pos.squaredDistanceTo(s.pos) < 0.25 && o.normal.equals(s.normal));
        sprays.add(s);
        NeuroCanvasClient.modules().get(Showcase.class).announceSpray(this, main.setting.option(), s.pos, s.normal, s.size / size.f());
        while (sprays.size() > 24) {
            sprays.remove(0);
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        long now = System.currentTimeMillis();
        sprays.removeIf(s -> now - s.created > lifetime.value * 1000);
        if (sprays.isEmpty()) {
            return;
        }
        Matrix4f m = env.m();
        com.example.neurocanvas.avatar.SprayDraw.begin();
        for (Spray s : sprays) {
            float fade = (float) Math.min(1.0, (lifetime.value * 1000 - (now - s.created)) / 8000.0);
            com.example.neurocanvas.avatar.SprayDraw.quad(m, s.pos, s.normal, s.size, s.image, fade);
        }
        com.example.neurocanvas.avatar.SprayDraw.end();
    }
}
