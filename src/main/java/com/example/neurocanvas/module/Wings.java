package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import java.nio.file.Path;

/** Wings on your back; an animation named idle (and walk) in the model plays by itself. */
public final class Wings extends CosmeticModule {
    public Wings(Path root) {
        super("Wings", "Wings on your back from neurocanvas/wardrobe/wings", CosmeticPack.Type.WINGS, root, true);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        Body b = body(env);
        if (b == null || !b.visible() || main.model == null) {
            return;
        }
        pose(main, b.moving(), System.nanoTime());
        main.model.draw(base(env, b.pos(), b.bodyYaw(), main), b.light(), shading.f());
    }
}
