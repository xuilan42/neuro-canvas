package com.example.neurocanvas.avatar;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Where each kind of cosmetic sits on a player and how it moves. The same code places a cosmetic on you and on the other
 * players who wear one, so it looks the same everywhere.
 */
public final class Attach {
    private Attach() { }

    /** The matrix that puts a model made for a player's body at {@code at}, turned to {@code yaw}; {@code scale} is world units per model unit. */
    public static Matrix4f base(Matrix4f world, Vec3d at, float yaw, float scale) {
        Matrix4f m = new Matrix4f(world);
        m.translate((float) at.x, (float) at.y, (float) at.z);
        m.rotateY((float) Math.toRadians(-yaw + 180f));
        m.scale(scale);
        return m;
    }

    /** Poses a model with its idle animation, or its walk animation while {@code moving}. */
    public static void pose(BbInstance m, boolean moving, long nowNano, double animSpeed) {
        m.resetPose();
        BbModel.Anim a = moving ? m.find("walk") : null;
        if (a == null) {
            a = m.find("idle");
        }
        if (a != null) {
            m.apply(a, BbInstance.time(a, 0L, nowNano, animSpeed));
        }
    }

    /** A cape hangs from the shoulders and swings back as the player moves. */
    public static void cape(BbInstance m, Matrix4f base, double speed, double t, float light, float shading) {
        float swing = (float) (0.06 + Math.min(speed * 0.14, 0.9) + 0.03 * Math.sin(t * 2.2));
        Matrix4f swung = new Matrix4f(base).translate(0f, 24f, 2f).rotateX(-swing).translate(0f, -24f, -2f);
        m.draw(swung, light, shading);
    }

    public static void wings(BbInstance m, Matrix4f base, float light, float shading) {
        m.draw(base, light, shading);
    }

    public static void outfit(BbInstance m, Matrix4f base, float light, float shading) {
        m.draw(base, light, shading);
    }

    /** A hat turns and nods with the head. */
    public static void hat(BbInstance m, Matrix4f base, float bodyYaw, float headYaw, float pitch, float light, float shading) {
        Matrix4f turned = new Matrix4f(base).translate(0f, 24f, 0f)
                .rotateY((float) Math.toRadians(-MathHelper.wrapDegrees(headYaw - bodyYaw)))
                .rotateX((float) Math.toRadians(-pitch)).translate(0f, -24f, 0f);
        m.draw(turned, light, shading);
    }
}
