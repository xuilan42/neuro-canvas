package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * A batch of translucent boxes. With the shader every face is drawn with a glowing rim; without it (shader visuals
 * off or the shader failed) plain translucent faces are drawn with the game's basic colour shader.
 */
public final class Fills {
    private final Matrix4f m;
    private final BufferBuilder b;
    private final FxBatch fx;

    private Fills(Matrix4f m, BufferBuilder b, FxBatch fx) {
        this.m = m;
        this.b = b;
        this.fx = fx;
    }

    public static Fills begin(RenderEnv env) {
        FxBatch batch = FxBatch.begin(env, FxBatch.FACE, 0f, 0f, 0f, 0);
        if (batch != null) {
            return new Fills(env.m(), null, batch);
        }
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        return new Fills(env.m(), b, null);
    }

    public void box(Box bx, int argb) {
        box(bx, argb, argb);
    }

    /** Box with a vertical colour gradient from {@code bottom} to {@code top}. */
    public void box(Box bx, int bottom, int top) {
        face(bx, bottom, top, true, true, true, true, true, true);
    }

    /** Only the faces that point towards the camera (all of them when the camera is inside the box). */
    public void boxFront(Box bx, int argb, Vec3d cam) {
        boolean fxn = cam.x < bx.minX, fxp = cam.x > bx.maxX, fyn = cam.y < bx.minY, fyp = cam.y > bx.maxY;
        boolean fzn = cam.z < bx.minZ, fzp = cam.z > bx.maxZ;
        boolean inside = !(fxn || fxp || fyn || fyp || fzn || fzp);
        face(bx, argb, argb, inside || fzn, inside || fxp, inside || fzp, inside || fxn, inside || fyp, inside || fyn);
    }

    private void face(Box bx, int bottom, int top, boolean z0f, boolean x1f, boolean z1f, boolean x0f, boolean yTop, boolean yBottom) {
        double x0 = bx.minX, y0 = bx.minY, z0 = bx.minZ, x1 = bx.maxX, y1 = bx.maxY, z1 = bx.maxZ;
        if (z0f) quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, bottom, top);
        if (x1f) quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, bottom, top);
        if (z1f) quad(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, bottom, top);
        if (x0f) quad(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, bottom, top);
        if (yTop) quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top, top);
        if (yBottom) quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom, bottom);
    }

    /** Corners in order: (u0,v0), (u1,v0), (u1,v1), (u0,v1); the first two use {@code low}, the last two {@code high}. */
    private void quad(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                      double dx, double dy, double dz, int low, int high) {
        if (fx != null) {
            fx.vertex(ax, ay, az, 0f, 0f, low);
            fx.vertex(bx, by, bz, 1f, 0f, low);
            fx.vertex(cx, cy, cz, 1f, 1f, high);
            fx.vertex(dx, dy, dz, 0f, 1f, high);
            return;
        }
        plain(ax, ay, az, low);
        plain(bx, by, bz, low);
        plain(cx, cy, cz, high);
        plain(dx, dy, dz, high);
    }

    private void plain(double x, double y, double z, int argb) {
        b.vertex(m, (float) x, (float) y, (float) z)
                .color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f)
                .next();
    }

    public void end() {
        if (fx != null) {
            fx.end();
        } else {
            Tessellator.getInstance().draw();
        }
    }
}
