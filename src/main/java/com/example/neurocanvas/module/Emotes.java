package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.BbInstance;
import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.avatar.Worn;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Emotes: animations that move your own character. The bones of an emote model named head, body, right arm, left arm, right leg
 * and left leg are applied to your body while it plays (the model itself is not drawn); the PingWheel's Emotes wheel plays them.
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
        com.example.neurocanvas.client.NeuroCanvasClient.modules().get(Showcase.class).announceEmote(this, playing.setting.option());
        return true;
    }

    /** The pose the emote gives the player's body this moment, or null when none plays on this entity (the emote stops when it ends). */
    public float[][] poseFor(Entity entity) {
        if (!enabled || playing == null || playing.model == null || entity != MinecraftClient.getInstance().player) {
            return null;
        }
        BbInstance model = playing.model;
        BbModel.Anim a = playing.config.animation != null ? model.find(playing.config.animation) : null;
        if (a == null && !model.model.animations.isEmpty()) {
            a = model.model.animations.values().iterator().next();
        }
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0 * animSpeed.value;
        if (a == null || seconds > a.length) {
            playing = null;
            return null;
        }
        return com.example.neurocanvas.avatar.EmotePose.read(model, a, BbInstance.time(a, started, System.nanoTime(), animSpeed.value));
    }
}
