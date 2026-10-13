package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import java.nio.file.Path;

/** A hat that follows the turn and the nod of your head. */
public final class Hats extends CosmeticModule {
    public Hats(Path root) {
        super("Hats", "A hat from neurocanvas/wardrobe/hats that follows your head", CosmeticPack.Type.HAT, root, true);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        Body b = body(env);
        if (b == null || !b.visible() || main.model == null) {
            return;
        }
        pose(main, b.moving(), System.nanoTime());
        com.example.neurocanvas.avatar.Attach.hat(main.model, base(env, b.pos(), b.bodyYaw(), main), b.bodyYaw(), b.headYaw(), b.pitch(), b.light(), shading.f());
    }
}
