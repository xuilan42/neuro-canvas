package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.Interface;
import com.example.neurocanvas.module.VolumetricFog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/**
 * Glass, ice and water are drawn after everything solid, but they also write the depth buffer, so anything of ours that is drawn
 * later and tested against depth (the fog, the particles, the block overlay) believes that such a block is a wall and is hidden behind it.
 * The depth of the solid world and the entities is copied before those layers are drawn and put back when our effects start, so they
 * see through glass the way the eye does.
 */
public final class OpaqueDepth {
    private static Framebuffer snapshot;
    private static boolean valid;
    private static boolean broken;

    private OpaqueDepth() { }

    /** Called after the entities and before the see-through layers of the world. */
    public static void capture(MinecraftClient mc) {
        valid = false;
        if (broken || !Interface.glassThrough() || VolumetricFog.shaderPackActive()) {
            return;
        }
        try {
            Framebuffer main = mc.getFramebuffer();
            if (snapshot == null) {
                snapshot = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
            } else if (snapshot.textureWidth != main.textureWidth || snapshot.textureHeight != main.textureHeight) {
                snapshot.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }
            snapshot.copyDepthFrom(main);
            main.beginWrite(false);
            valid = true;
        } catch (Throwable e) {
            broken = true;
            org.slf4j.LoggerFactory.getLogger("neurocanvas").error("Could not copy the depth buffer, effects will not see through glass", e);
        }
    }

    /** Called when our effects start: the depth buffer goes back to what it was before the see-through layers. */
    public static void restore(MinecraftClient mc) {
        if (!valid) {
            return;
        }
        valid = false;
        try {
            Framebuffer main = mc.getFramebuffer();
            main.copyDepthFrom(snapshot);
            main.beginWrite(false);
        } catch (Throwable e) {
            broken = true;
            org.slf4j.LoggerFactory.getLogger("neurocanvas").error("Could not restore the depth buffer", e);
        }
    }
}
