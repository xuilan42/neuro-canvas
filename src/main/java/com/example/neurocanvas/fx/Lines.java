package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A batch of lines with real thickness (uses the same shader as the vanilla block outline). */
public final class Lines {
    private final Matrix4f m;
    private final BufferBuilder b;
    private final Vector3f dir = new Vector3f();

    private Lines(Matrix4f m, BufferBuilder b) {
        this.m = m;
        this.b = b;
    }

    public static Lines begin(RenderEnv env, float width) {
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(width);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        return new Lines(env.m(), b);
    }

    public void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) {
            return;
        }
        m.transformDirection(dx / len, dy / len, dz / len, dir);
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float bl = (argb & 0xFF) / 255f;
        b.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, bl, a).normal(dir.x, dir.y, dir.z).next();
        b.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, bl, a).normal(dir.x, dir.y, dir.z).next();
    }

    public void box(Box bx, int argb) {
        double x0 = bx.minX, y0 = bx.minY, z0 = bx.minZ, x1 = bx.maxX, y1 = bx.maxY, z1 = bx.maxZ;
        line(x0, y0, z0, x1, y0, z0, argb);
        line(x1, y0, z0, x1, y0, z1, argb);
        line(x1, y0, z1, x0, y0, z1, argb);
        line(x0, y0, z1, x0, y0, z0, argb);
        line(x0, y1, z0, x1, y1, z0, argb);
        line(x1, y1, z0, x1, y1, z1, argb);
        line(x1, y1, z1, x0, y1, z1, argb);
        line(x0, y1, z1, x0, y1, z0, argb);
        line(x0, y0, z0, x0, y1, z0, argb);
        line(x1, y0, z0, x1, y1, z0, argb);
        line(x1, y0, z1, x1, y1, z1, argb);
        line(x0, y0, z1, x0, y1, z1, argb);
    }

    /**
     * A box seen from the camera: an edge is drawn only if at least one of the two faces it joins faces the camera,
     * so the far edges stay hidden. When the camera is inside the box every edge is drawn.
     */
    public void boxFront(Box bx, int argb, Vec3d cam) {
        double x0 = bx.minX, y0 = bx.minY, z0 = bx.minZ, x1 = bx.maxX, y1 = bx.maxY, z1 = bx.maxZ;
        boolean fxn = cam.x < x0, fxp = cam.x > x1, fyn = cam.y < y0, fyp = cam.y > y1, fzn = cam.z < z0, fzp = cam.z > z1;
        if (!(fxn || fxp || fyn || fyp || fzn || fzp)) {
            box(bx, argb);
            return;
        }
        if (fyn || fzn) line(x0, y0, z0, x1, y0, z0, argb);
        if (fyn || fxp) line(x1, y0, z0, x1, y0, z1, argb);
        if (fyn || fzp) line(x1, y0, z1, x0, y0, z1, argb);
        if (fyn || fxn) line(x0, y0, z1, x0, y0, z0, argb);
        if (fyp || fzn) line(x0, y1, z0, x1, y1, z0, argb);
        if (fyp || fxp) line(x1, y1, z0, x1, y1, z1, argb);
        if (fyp || fzp) line(x1, y1, z1, x0, y1, z1, argb);
        if (fyp || fxn) line(x0, y1, z1, x0, y1, z0, argb);
        if (fxn || fzn) line(x0, y0, z0, x0, y1, z0, argb);
        if (fxp || fzn) line(x1, y0, z0, x1, y1, z0, argb);
        if (fxp || fzp) line(x1, y0, z1, x1, y1, z1, argb);
        if (fxn || fzp) line(x0, y0, z1, x0, y1, z1, argb);
    }

    /** Sends the batch and restores the shader and line width other modules expect. */
    public void end() {
        Tessellator.getInstance().draw();
        RenderSystem.lineWidth(1f);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
