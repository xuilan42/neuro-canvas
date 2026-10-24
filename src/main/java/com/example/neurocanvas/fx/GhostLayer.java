package com.example.neurocanvas.fx;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.TargetEsp;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** The render layer that draws the target's own model as a hologram (Fresnel rim and scan bands) instead of its skin. */
public final class GhostLayer {
    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();

    private GhostLayer() { }

    /** The layer to draw {@code entity} with, or null to keep the game's own. */
    public static RenderLayer forEntity(LivingEntity entity, Identifier texture) {
        if (NeuroCanvasClient.modules() == null || entity != NeuroCanvasClient.state().target) {
            return null;
        }
        TargetEsp esp = NeuroCanvasClient.modules().get(TargetEsp.class);
        if (!esp.enabled || !esp.phantomOn() || Shaders.get("neuro_ghost") == null) {
            return null;
        }
        return layer(texture);
    }

    /** The values the hologram shader uses when a model is drawn by {@link ModelFx} instead of as the target. */
    public record State(float ticks, float feetY, float alpha, int color1, int color2) { }

    static State override;

    /** The hologram layer for a texture (created once per texture). */
    public static RenderLayer layer(Identifier texture) {
        return LAYERS.computeIfAbsent(texture, t -> RenderLayer.of("neurocanvas_phantom", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS, 1536, true, true,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(new RenderPhase.ShaderProgram(GhostLayer::program))
                        .texture(new RenderPhase.Texture(t, false, false))
                        .transparency(RenderPhase.ADDITIVE_TRANSPARENCY)
                        .cull(RenderPhase.DISABLE_CULLING)
                        .writeMaskState(RenderPhase.COLOR_MASK)
                        .build(false)));
    }

    /** Chosen when the layer starts drawing: puts this frame's values into the shader. */
    private static ShaderProgram program() {
        ShaderProgram p = Shaders.get("neuro_ghost");
        State o = override;
        if (p != null && o != null) {
            Shaders.gameTime(p, o.ticks());
            set(p, "FeetY", o.feetY());
            set(p, "Alpha", o.alpha());
            rgb(p, "Color1", o.color1());
            rgb(p, "Color2", o.color2());
            Shaders.applyBrightness(p);
            return p;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity target = NeuroCanvasClient.state().target;
        if (p == null || target == null || mc.gameRenderer == null) {
            return p;
        }
        EffectSpec s = NeuroCanvasClient.modules().get(TargetEsp.class).phantomSpec();
        float td = mc.getTickDelta();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        Shaders.gameTime(p, target.age + td);
        set(p, "FeetY", (float) (net.minecraft.util.math.MathHelper.lerp(td, target.lastRenderY, target.getY()) - cam.y));
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
