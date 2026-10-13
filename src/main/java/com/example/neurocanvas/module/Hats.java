package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import java.nio.file.Path;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

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
        Matrix4f m = base(env, b.pos(), b.bodyYaw(), main);
        m.translate(0f, 24f, 0f).rotateY((float) Math.toRadians(-MathHelper.wrapDegrees(b.headYaw() - b.bodyYaw())))
                .rotateX((float) Math.toRadians(-b.pitch())).translate(0f, -24f, 0f);
        main.model.draw(m, b.light(), shading.f());
    }
}
