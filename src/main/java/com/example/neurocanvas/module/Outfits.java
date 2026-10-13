package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import java.nio.file.Path;

/** Clothes that sit on your body, a little wider than the body itself. */
public final class Outfits extends CosmeticModule {
    public Outfits(Path root) {
        super("Outfits", "Clothes from neurocanvas/wardrobe/outfits that sit on your body", CosmeticPack.Type.OUTFIT, root, true);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        Body b = body(env);
        if (b == null || !b.visible() || main.model == null) {
            return;
        }
        pose(main, b.moving(), System.nanoTime());
        com.example.neurocanvas.avatar.Attach.outfit(main.model, base(env, b.pos(), b.bodyYaw(), main), b.light(), shading.f());
    }
}
