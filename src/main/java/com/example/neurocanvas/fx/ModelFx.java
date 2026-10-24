package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;

/**
 * The way to draw a creature's own model with a look of our choosing, wherever and whenever we want (not only as the current target). An effect
 * that needs the model of a player or mob (a hologram copy of someone who just died, a dissolve, a freeze) calls {@link #draw} with a
 * {@link Look}: the look picks the render layer (and so the shader) for every part of the model, and the model keeps its pose, armour and skin.
 *
 * <p>The creature object stays usable after it has died or been removed, so a kill effect can keep a reference and draw it for a while.
 * Nothing in the mod calls this yet except {@link #drawHologram}, which is the first look made with it.
 */
public final class ModelFx {
    /** Chooses the render layer for one texture of the model; return null to keep the game's own layer for it. */
    @FunctionalInterface
    public interface Look {
        RenderLayer layer(LivingEntity entity, Identifier texture);
    }

    private static Look current;

    private ModelFx() { }

    /** Used by the renderer hook: the layer the model being drawn right now should use, or null. */
    public static RenderLayer override(LivingEntity entity, Identifier texture) {
        return current == null ? null : current.layer(entity, texture);
    }

    /**
     * Draws a creature at an absolute world position with the given look, right now, in the current world pass.
     * The caller's matrices must be camera-relative ({@code env.view()}), which is what the pass gives.
     */
    public static void draw(LivingEntity entity, RenderEnv env, Vec3d pos, float yaw, Look look) {
        MinecraftClient mc = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MatrixStack ms = new MatrixStack();
        ms.peek().getPositionMatrix().set(env.view());
        ms.peek().getNormalMatrix().set(new Matrix3f().set(env.view()));
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        dispatcher.setRenderShadows(false);
        current = look;
        try {
            dispatcher.render(entity, pos.x - env.cam().x, pos.y - env.cam().y, pos.z - env.cam().z, yaw, env.tickDelta(), ms, consumers,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE);
            consumers.draw();
        } finally {
            current = null;
            dispatcher.setRenderShadows(mc.options.getEntityShadows().getValue());
        }
    }

    /** The hologram look (glowing rim and scan bands) on any creature: {@code ticks} is the animation clock in game ticks. */
    public static void drawHologram(LivingEntity entity, RenderEnv env, Vec3d pos, EffectSpec colors, float ticks) {
        GhostLayer.override = new GhostLayer.State(ticks, (float) (pos.y - env.cam().y), colors.alpha, colors.color1, colors.color2);
        try {
            draw(entity, env, pos, entity.getYaw(), (e, texture) -> GhostLayer.layer(texture));
        } finally {
            GhostLayer.override = null;
        }
    }
}
