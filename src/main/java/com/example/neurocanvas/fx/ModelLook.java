package com.example.neurocanvas.fx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** The surface looks of a creature drawn by {@link ModelFx} (ice, dissolve, tint), all one shader; {@link #STATE} holds the values of the draw in progress. */
public final class ModelLook {
    public static final int ICE = 0, DISSOLVE = 1, TINT = 2;

    public static final class State {
        public int mode;
        public float progress;
        public float ticks;
        public float feetY;
        public float height = 1.8f;
        public float alpha = 1f;
        public int color1 = 0x9FE6FF;
        public int color2 = 0xFFFFFF;
    }

    public static final State STATE = new State();
    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();

    private ModelLook() { }

    public static boolean available() {
        return Shaders.get("neuro_model") != null;
    }

    public static RenderLayer layer(Identifier texture) {
        return LAYERS.computeIfAbsent(texture, t -> RenderLayer.of("neurocanvas_model", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS, 1536, true, true,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(new RenderPhase.ShaderProgram(ModelLook::program))
                        .texture(new RenderPhase.Texture(t, false, false))
                        .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                        .build(true)));
    }

    private static ShaderProgram program() {
        ShaderProgram p = Shaders.get("neuro_model");
        if (p == null) {
            return null;
        }
        State s = STATE;
        Shaders.gameTime(p, s.ticks);
        set(p, "Mode", s.mode);
        set(p, "Progress", s.progress);
        set(p, "FeetY", s.feetY);
        set(p, "Height", s.height);
        set(p, "Alpha", s.alpha);
        rgb(p, "Color1", s.color1);
        rgb(p, "Color2", s.color2);
        Shaders.applyBrightness(p);
        return p;
    }

    private static void set(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }

    private static void rgb(ShaderProgram p, String name, int c) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
