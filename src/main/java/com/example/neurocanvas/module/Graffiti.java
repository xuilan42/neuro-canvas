package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.avatar.Worn;
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
    private final Worn[] slots = new Worn[4];
    private int active;
    private boolean keyWasDown;

    public Graffiti(Path root) {
        super("Graffiti", "Pictures from neurocanvas/wardrobe/graffiti sprayed on a wall: pick up to four, spray the last one with the Keybind or choose from the PingWheel", CosmeticPack.Type.GRAFFITI, "Graffiti 1", root, false);
        customKey = true;
        slots[0] = main;
        for (int i = 1; i < slots.length; i++) {
            slots[i] = wear(add(Setting.choice("Graffiti " + (i + 1), 0, Worn.NONE)));
        }
        rescan();
    }

    /** The name of the item in each slot, or an empty text when the slot is empty. */
    public String[] names() {
        String[] out = new String[slots.length];
        for (int i = 0; i < slots.length; i++) {
            out[i] = slots[i].image != null ? slots[i].setting.option() : "";
        }
        return out;
    }

    @Override
    protected void tickCosmetic(MinecraftClient mc) {
        sprays.removeIf(sp -> java.util.Arrays.stream(slots).noneMatch(w -> sp.image.equals(w.image)));
        boolean down = key >= 0 && mc.currentScreen == null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
        if (down && !keyWasDown) {
            spray(mc, active);
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

    /** Sprays the picture of a slot (0 to 3) on the block face you are looking at; it also becomes the one the key sprays. */
    public void spray(MinecraftClient mc, int slot) {
        if (!enabled || mc.player == null) {
            return;
        }
        int use = slot;
        if (slots[use].image == null) {
            use = -1;
            for (int i = 0; i < slots.length && use < 0; i++) {
                if (slots[i].image != null) {
                    use = i;
                }
            }
            if (use < 0) {
                say("Pick a graffiti first");
                return;
            }
        }
        active = use;
        Worn w = slots[use];
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
        s.size = (w.config.size == null ? 1.5f : w.config.size) * size.f();
        s.image = w.image;
        s.created = System.currentTimeMillis();
        sprays.removeIf(o -> o.pos.squaredDistanceTo(s.pos) < 0.25 && o.normal.equals(s.normal));
        sprays.add(s);
        NeuroCanvasClient.modules().get(Showcase.class).announceSpray(this, w.setting.option(), s.pos, s.normal, s.size / size.f());
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
