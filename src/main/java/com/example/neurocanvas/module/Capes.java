package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import java.nio.file.Path;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import com.example.neurocanvas.avatar.Attach;

/** A cape that hangs from the shoulders and swings as you move. The game's own cape is hidden while one is worn. */
public final class Capes extends CosmeticModule {
    public Capes(Path root) {
        super("Capes", "A cape from neurocanvas/wardrobe/capes that swings as you move (hides the game's own cape)", CosmeticPack.Type.CAPE, root, true);
    }

    public boolean hidesCape(Entity entity) {
        return enabled && main.model != null && entity == MinecraftClient.getInstance().player;
    }

    @Override
    public void renderWorld(RenderEnv env) {
        Body b = body(env);
        if (b == null || !b.visible() || main.model == null) {
            return;
        }
        pose(main, b.moving(), System.nanoTime());
        Attach.cape(main.model, base(env, b.pos(), b.bodyYaw(), main), b.speed(), env.t(), b.light(), shading.f());
    }
}
