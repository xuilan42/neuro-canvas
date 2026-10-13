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
    private final Worn dance;
    private Worn playing;
    private long started;

    public Emotes(Path root) {
        super("Emotes", "Emotes from neurocanvas/wardrobe/emotes, played from the PingWheel (Wave: the first slot, Dance: the second)", CosmeticPack.Type.EMOTE, "Wave emote", root, true);
        // The first slot is the wave; the second one is added after the shared settings.
        Setting second = add(Setting.choice("Dance emote", 0, Worn.NONE));
        dance = wear(second);
        rescan();
    }

    /** Plays the wave (slot 1) or the dance (slot 2). False when that slot is empty. */
    public boolean play(String name) {
        Worn w = name.equals("dance") ? dance : main;
        if (!enabled || w.model == null) {
            return false;
        }
        playing = w;
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
