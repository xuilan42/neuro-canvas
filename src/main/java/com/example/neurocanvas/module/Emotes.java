package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.BbInstance;
import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.avatar.Worn;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Full-body emotes: an avatar with an animation that replaces your body while it plays. The PingWheel's Wave plays the first
 * slot and its Dance plays the second.
 */
public final class Emotes extends CosmeticModule {
    private final Worn[] slots = new Worn[4];
    private Worn playing;
    private long started;

    public Emotes(Path root) {
        super("Emotes", "Emotes from neurocanvas/wardrobe/emotes: pick up to four, then play them from the PingWheel's Emotes", CosmeticPack.Type.EMOTE, "Emote 1", root, true);
        slots[0] = main;
        for (int i = 1; i < slots.length; i++) {
            slots[i] = wear(add(Setting.choice("Emote " + (i + 1), 0, Worn.NONE)));
        }
        rescan();
    }

    /** The name of the item in each slot, or an empty text when the slot is empty. */
    public String[] names() {
        String[] out = new String[slots.length];
        for (int i = 0; i < slots.length; i++) {
            out[i] = slots[i].model != null ? slots[i].setting.option() : "";
        }
        return out;
    }

    /** Plays the emote of a slot (0 to 3). False when the module is off or that slot is empty. */
    public boolean play(int slot) {
        if (!enabled || slot < 0 || slot >= slots.length || slots[slot].model == null) {
            return false;
        }
        playing = slots[slot];
        started = System.nanoTime();
        return true;
    }

    /** True while an emote plays on this player: its model replaces the body. */
    public boolean hidesBody(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return enabled && playing != null && entity == mc.player && (!mc.options.getPerspective().isFirstPerson() || firstPerson.on());
    }

    @Override
    public void renderWorld(RenderEnv env) {
        Body b = body(env);
        if (b == null || playing == null || playing.model == null || !b.visible()) {
            return;
        }
        BbInstance model = playing.model;
        BbModel.Anim a = playing.config.animation != null ? model.find(playing.config.animation) : null;
        if (a == null && !model.model.animations.isEmpty()) {
            a = model.model.animations.values().iterator().next();
        }
        long now = System.nanoTime();
        if (a == null || (now - started) / 1_000_000_000.0 * animSpeed.value > a.length) {
            playing = null;
            return;
        }
        model.resetPose();
        model.apply(a, BbInstance.time(a, started, now, animSpeed.value));
        model.draw(base(env, b.pos(), b.bodyYaw(), playing), b.light(), shading.f());
    }
}
