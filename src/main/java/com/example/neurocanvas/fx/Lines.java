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
    /** Shader path: glowing quads. Null when the old GL lines are used. */
    private FxBatch fx;
    private Vec3d cam = Vec3d.ZERO;
    private double pixelScale;
    private float width;

    private Lines(Matrix4f m, BufferBuilder b) {
        this.m = m;
        this.b = b;
    }

    public static Lines begin(RenderEnv env, float width) {
        FxBatch batch = FxBatch.begin(env, FxBatch.LINE, 0.263f, 0f, 0f, 0);
        if (batch != null) {
            Lines lines = new Lines(env.m(), null);
            lines.fx = batch;
            lines.cam = env.mc().gameRenderer.getCamera().getPos();
            lines.width = Math.max(width, 1.5f);
            lines.pixelScale = 2.0 / (RenderSystem.getProjectionMatrix().m11() * Math.max(1, env.mc().getWindow().getFramebufferHeight()));
            return lines;
        }
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(width);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        return new Lines(env.m(), b);
    }

    /** Half width of the glowing quad, in world units, at the given distance from the camera (about constant on screen). */
    private double half(double distance) {
        return (width * 0.5 + width * 1.4) * distance * pixelScale;
    }

    private void glowLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double vx = (x1 + x2) / 2 - cam.x, vy = (y1 + y2) / 2 - cam.y, vz = (z1 + z2) / 2 - cam.z;
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-9) {
            return;
        }
        sx /= len;
        sy /= len;
        sz /= len;
        double ha = half(Math.sqrt((x1 - cam.x) * (x1 - cam.x) + (y1 - cam.y) * (y1 - cam.y) + (z1 - cam.z) * (z1 - cam.z)));
        double hb = half(Math.sqrt((x2 - cam.x) * (x2 - cam.x) + (y2 - cam.y) * (y2 - cam.y) + (z2 - cam.z) * (z2 - cam.z)));
        fx.vertex(x1 + sx * ha, y1 + sy * ha, z1 + sz * ha, 0f, 1f, argb);
        fx.vertex(x1 - sx * ha, y1 - sy * ha, z1 - sz * ha, 0f, -1f, argb);
        fx.vertex(x2 - sx * hb, y2 - sy * hb, z2 - sz * hb, 1f, -1f, argb);
        fx.vertex(x2 + sx * hb, y2 + sy * hb, z2 + sz * hb, 1f, 1f, argb);
    }

    public void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (fx != null) {
            glowLine(x1, y1, z1, x2, y2, z2, argb);
            return;
        }
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
        if (fx != null) {
            fx.end();
            return;
        }
        Tessellator.getInstance().draw();
        RenderSystem.lineWidth(1f);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
