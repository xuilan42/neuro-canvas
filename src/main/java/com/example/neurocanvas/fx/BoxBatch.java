package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Draws sets of boxes (translucent fill and outline) and decides which of them may be drawn on top of entities. */
public final class BoxBatch {
    private BoxBatch() { }

    /**
     * True when a line from the camera to the box is not blocked by blocks (checked at head, middle and feet height).
     * Such boxes can safely be drawn without a depth test, so the entity's own model cannot hide them.
     */
    public static boolean inLineOfSight(MinecraftClient mc, Box box) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d from = cam.getPos();
        double h = box.maxY - box.minY;
        double cx = (box.minX + box.maxX) / 2;
        double cz = (box.minZ + box.maxZ) / 2;
        for (double f : new double[] {0.85, 0.5, 0.15}) {
            Vec3d to = new Vec3d(cx, box.minY + h * f, cz);
            if (Depth.clear(mc, from, to)) {
                return true;
            }
        }
        return false;
    }

    /** Draws the boxes of one group with the given depth behaviour. Fill first, then outline (one batch at a time). */
    public static void draw(RenderEnv env, List<Box> boxes, List<Integer> colors, boolean fill, float fillAlpha,
                            boolean outline, float width, boolean depthTest, List<double[]> extraLines) {
        if (boxes.isEmpty()) {
            return;
        }
        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        if (fill) {
            RenderSystem.depthMask(false);
            Fills fills = Fills.begin(env);
            for (int i = 0; i < boxes.size(); i++) {
                fills.boxFront(boxes.get(i), ((int) (255 * fillAlpha) << 24) | (colors.get(i) & 0xFFFFFF), cam);
            }
            fills.end();
            RenderSystem.depthMask(true);
        }
        if (outline || (extraLines != null && !extraLines.isEmpty())) {
            Lines lines = Lines.begin(env, width);
            if (outline) {
                for (int i = 0; i < boxes.size(); i++) {
                    lines.boxFront(boxes.get(i), colors.get(i), cam);
                }
            }
            if (extraLines != null) {
                for (double[] l : extraLines) {
                    lines.line(l[0], l[1], l[2], l[3], l[4], l[5], (int) l[6]);
                }
            }
            lines.end();
        }
        RenderSystem.disableDepthTest();
    }
}
