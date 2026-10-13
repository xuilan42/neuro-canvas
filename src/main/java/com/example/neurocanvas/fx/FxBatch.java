package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.Interface;
import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/** One draw call of the shared visuals shader: choose a mode, add vertices (as quads), then {@link #end()}. */
public final class FxBatch {
    public static final int LINE = 0;
    public static final int FACE = 1;
    public static final int RING = 2;
    public static final int RIBBON = 3;

    private final Matrix4f m;
    private final BufferBuilder b;
    private final Tessellator tess = Tessellator.getInstance();

    private FxBatch(Matrix4f m, BufferBuilder b) {
        this.m = m;
        this.b = b;
    }

    /** @return the batch, or null when shader visuals are off or the shader cannot be built (callers then use the old path) */
    public static FxBatch begin(RenderEnv env, int mode, float p0, float p1, float p2, int color2) {
        if (!Interface.shaderVisuals()) {
            return null;
        }
        ShaderProgram program = Shaders.get("neuro_fx");
        if (program == null) {
            return null;
        }
        set(program, "Mode", mode);
        set(program, "Glow", Interface.visualGlow());
        set(program, "Time", (float) env.t());
        Shaders.applyBrightness(program);
        GlUniform params = program.getUniform("Params");
        if (params != null) {
            params.set(p0, p1, p2, 0f);
        }
        GlUniform c2 = program.getUniform("Color2");
        if (c2 != null) {
            c2.set(((color2 >> 16) & 0xFF) / 255f, ((color2 >> 8) & 0xFF) / 255f, (color2 & 0xFF) / 255f);
        }
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> program);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        return new FxBatch(env.m(), buf);
    }

    private static void set(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }

    public void vertex(double x, double y, double z, float u, float v, int argb) {
        b.vertex(m, (float) x, (float) y, (float) z).texture(u, v)
                .color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f).next();
    }

    public void end() {
        tess.draw();
        Shaders.finish();
    }
}
