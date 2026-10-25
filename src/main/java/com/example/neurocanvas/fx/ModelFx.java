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
    private static boolean drawing;
    private static final java.util.Map<Integer, Long> HIDDEN = new java.util.HashMap<>();

    /** Hides the real body of a creature (its own death animation) for a while, so a copy drawn by us replaces it. */
    public static void hide(LivingEntity e, long millis) {
        HIDDEN.put(e.getId(), System.currentTimeMillis() + millis);
    }

    public static void show(LivingEntity e) {
        HIDDEN.remove(e.getId());
    }

    /** True while the renderer must skip this creature (used by the renderer hook). */
    public static boolean hidden(LivingEntity e) {
        if (drawing || HIDDEN.isEmpty()) {
            return false;
        }
        Long until = HIDDEN.get(e.getId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            HIDDEN.remove(e.getId());
            return false;
        }
        return true;
    }

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
        draw(entity, env, pos, yaw, 1f, look);
    }

    /**
     * Same, with the whole model scaled around its feet. The pose is the creature's own, but a dying body is drawn standing (the fall and the red
     * hurt flash are switched off for the moment of drawing).
     */
    public static void draw(LivingEntity entity, RenderEnv env, Vec3d pos, float yaw, float scale, Look look) {
        MinecraftClient mc = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MatrixStack ms = new MatrixStack();
        ms.peek().getPositionMatrix().set(env.view());
        ms.peek().getNormalMatrix().set(new Matrix3f().set(env.view()));
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        dispatcher.setRenderShadows(false);
        current = look;
        drawing = true;
        int death = entity.deathTime;
        int hurt = entity.hurtTime;
        entity.deathTime = 0;
        entity.hurtTime = 0;
        try {
            ms.translate(pos.x - env.cam().x, pos.y - env.cam().y, pos.z - env.cam().z);
            ms.scale(scale, scale, scale);
            dispatcher.render(entity, 0, 0, 0, yaw, env.tickDelta(), ms, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            consumers.draw();
        } finally {
            entity.deathTime = death;
            entity.hurtTime = hurt;
            drawing = false;
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
