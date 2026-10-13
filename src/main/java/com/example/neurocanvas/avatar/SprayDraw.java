package com.example.neurocanvas.avatar;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws graffiti pictures lying on block faces. Used for your own sprays and for the ones other users make. */
public final class SprayDraw {
    private SprayDraw() { }

    public static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
    }

    /** One picture, {@code size} blocks wide, flat against the face with the given normal, centred on {@code pos}. */
    public static void quad(Matrix4f world, Vec3d pos, Vec3d normal, float size, Identifier image, float alpha) {
        // u points to the viewer's right and v up in the picture, for someone looking at the face from outside:
        // on a wall right = forward x up, on a floor the picture's top is north, on a ceiling it is south.
        boolean flat = Math.abs(normal.y) > 0.9;
        Vec3d u = flat ? new Vec3d(1, 0, 0) : new Vec3d(normal.z, 0, -normal.x).normalize();
        Vec3d v = flat ? new Vec3d(0, 0, normal.y > 0 ? -1 : 1) : new Vec3d(0, 1, 0);
        double half = size / 2.0;
        RenderSystem.setShaderTexture(0, image);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        double[][] c = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            Vec3d q = pos.add(u.multiply(c[i][0] * half)).add(v.multiply(c[i][1] * half));
            b.vertex(world, (float) q.x, (float) q.y, (float) q.z).texture(uv[i][0], uv[i][1]).color(1f, 1f, 1f, alpha).next();
        }
        tess.draw();
    }

    public static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
