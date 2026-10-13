package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import java.nio.file.Path;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** A small companion that walks beside you and turns to face you when it stops. */
public final class Pets extends CosmeticModule {
    private final Setting distance = add(Setting.number("Distance", 0.6, 4, 1.4));
    private Vec3d petPos;
    private float petYaw;
    private long lastNano = System.nanoTime();

    public Pets(Path root) {
        super("Pets", "A companion from neurocanvas/wardrobe/pets that follows you", CosmeticPack.Type.PET, root, false);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        Body b = body(env);
        if (b == null || main.model == null) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = now;
        double fy = Math.toRadians(b.bodyYaw());
        Vec3d fwd = new Vec3d(-Math.sin(fy), 0, Math.cos(fy));
        Vec3d right = new Vec3d(-Math.cos(fy), 0, -Math.sin(fy));
        Vec3d target = b.pos().add(right.multiply(distance.value * 0.8)).add(fwd.multiply(-distance.value * 0.7));
        if (petPos == null || petPos.squaredDistanceTo(target) > 144) {
            petPos = target;
        }
        Vec3d before = petPos;
        petPos = petPos.add(target.subtract(petPos).multiply(1.0 - Math.exp(-dt * 4.0)));
        double moved = petPos.distanceTo(before) / Math.max(dt, 1e-3);
        float heading = moved > 0.4 ? (float) Math.toDegrees(Math.atan2(-(petPos.x - before.x), petPos.z - before.z))
                : (float) Math.toDegrees(Math.atan2(-(b.pos().x - petPos.x), b.pos().z - petPos.z));
        petYaw = MathHelper.lerpAngleDegrees(0.2f, petYaw, heading);
        pose(main, moved > 0.4, now);
        main.model.draw(base(env, new Vec3d(petPos.x, b.pos().y, petPos.z), petYaw, main), b.light(), shading.f());
    }
}
