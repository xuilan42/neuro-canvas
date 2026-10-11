package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A batch of translucent filled boxes. The caller has already set blending and the position-colour shader. */
public final class Fills {
    private final Matrix4f m;
    private final BufferBuilder b;

    private Fills(Matrix4f m, BufferBuilder b) {
        this.m = m;
        this.b = b;
    }

    public static Fills begin(RenderEnv env) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        return new Fills(env.m(), b);
    }

    public void box(Box bx, int argb) {
        box(bx, argb, argb);
    }

    /** Box with a vertical colour gradient from {@code bottom} to {@code top}. */
    public void box(Box bx, int bottom, int top) {
        float x0 = (float) bx.minX, y0 = (float) bx.minY, z0 = (float) bx.minZ;
        float x1 = (float) bx.maxX, y1 = (float) bx.maxY, z1 = (float) bx.maxZ;
        side(x0, z0, x1, z0, y0, y1, bottom, top);
        side(x1, z0, x1, z1, y0, y1, bottom, top);
        side(x1, z1, x0, z1, y0, y1, bottom, top);
        side(x0, z1, x0, z0, y0, y1, bottom, top);
        cap(x0, x1, y1, z0, z1, top);
        cap(x0, x1, y0, z0, z1, bottom);
    }

    /** Only the faces that point towards the camera (all of them when the camera is inside the box). */
    public void boxFront(Box bx, int argb, Vec3d cam) {
        float x0 = (float) bx.minX, y0 = (float) bx.minY, z0 = (float) bx.minZ;
        float x1 = (float) bx.maxX, y1 = (float) bx.maxY, z1 = (float) bx.maxZ;
        boolean fxn = cam.x < x0, fxp = cam.x > x1, fyn = cam.y < y0, fyp = cam.y > y1, fzn = cam.z < z0, fzp = cam.z > z1;
        boolean inside = !(fxn || fxp || fyn || fyp || fzn || fzp);
        if (inside || fzn) side(x0, z0, x1, z0, y0, y1, argb, argb);
        if (inside || fxp) side(x1, z0, x1, z1, y0, y1, argb, argb);
        if (inside || fzp) side(x1, z1, x0, z1, y0, y1, argb, argb);
        if (inside || fxn) side(x0, z1, x0, z0, y0, y1, argb, argb);
        if (inside || fyp) cap(x0, x1, y1, z0, z1, argb);
        if (inside || fyn) cap(x0, x1, y0, z0, z1, argb);
    }

    private void side(float xa, float za, float xb, float zb, float y0, float y1, int bottom, int top) {
        vertex(xa, y0, za, bottom);
        vertex(xb, y0, zb, bottom);
        vertex(xb, y1, zb, top);
        vertex(xa, y1, za, top);
    }

    private void cap(float x0, float x1, float y, float z0, float z1, int argb) {
        vertex(x0, y, z0, argb);
        vertex(x1, y, z0, argb);
        vertex(x1, y, z1, argb);
        vertex(x0, y, z1, argb);
    }

    private void vertex(float x, float y, float z, int argb) {
        b.vertex(m, x, y, z)
                .color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f)
                .next();
    }

    public void end() {
        Tessellator.getInstance().draw();
    }
}
