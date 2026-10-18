package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.Interface;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.util.hit.BlockHitResult;
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
        return clear(mc, mc.gameRenderer.getCamera().getPos(), point);
    }

    /** Glass, ice, slime and panes let the eye through. */
    private static boolean seeThrough(BlockState state) {
        Block b = state.getBlock();
        return b instanceof TransparentBlock || b instanceof PaneBlock;
    }

    /** True when no block that hides things is between the two points; glass and the like do not count. */
    public static boolean clear(MinecraftClient mc, Vec3d from, Vec3d to) {
        Vec3d dir = to.subtract(from);
        double len = dir.length();
        if (len < 1e-6) {
            return true;
        }
        Vec3d step = dir.multiply(1.0 / len);
        Vec3d start = from;
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = mc.world.raycast(new RaycastContext(start, to, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (!Interface.glassThrough() || !seeThrough(mc.world.getBlockState(hit.getBlockPos()))) {
                return false;
            }
            start = hit.getPos().add(step.multiply(0.02));
            if (start.subtract(from).dotProduct(step) >= len) {
                return true;
            }
        }
        return false;
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
