package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.Interface;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * One rule for every effect that is drawn around entities: if the camera can see the point (no blocks in the way),
 * the effect is drawn over everything, so a player's model never hides it; if a wall is in between, the normal depth
 * test applies and the wall hides it.
 */
public final class Depth {
    private Depth() { }

    /** True when the camera has a clear line to {@code point}. */
    public static boolean visible(MinecraftClient mc, Vec3d point) {
        if (mc.world == null || mc.player == null) {
            return false;
        }
        Vec3d from = mc.gameRenderer.getCamera().getPos();
        return mc.world.raycast(new RaycastContext(from, point, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    /** Whether an effect around {@code point} should ignore the depth buffer. Respects the global switch in Interface. */
    public static boolean over(MinecraftClient mc, Vec3d point) {
        return Interface.drawOverModels() && visible(mc, point);
    }

    /** Sets the depth test for the next batch: off when drawing over models, on otherwise. */
    public static void state(boolean over) {
        if (over) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
    }
}
