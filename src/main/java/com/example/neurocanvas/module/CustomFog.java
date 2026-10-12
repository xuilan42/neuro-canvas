package com.example.neurocanvas.module;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;

/** Changes how far you see before fog hides the world, and the colour of that fog. */
public final class CustomFog extends Module {
    private final Setting disable = add(Setting.bool("Remove fog", false));
    private final Setting start = add(Setting.number("Start (%)", 0, 100, 10));
    private final Setting end = add(Setting.number("End (%)", 5, 150, 60));
    private final Setting shape = add(Setting.choice("Shape", 0, "Sphere", "Cylinder"));
    private final Setting useColor = add(Setting.bool("Custom color", false));
    private final Setting color = add(Setting.color("Color", 0.6));

    public CustomFog() {
        super("CustomFog", Category.WORLD, "Fog distance, shape and colour", false);
    }

    /** Fog stays vanilla underwater, in lava, in powder snow and while blind or darkened. */
    static boolean specialFog(Camera camera) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return true;
        }
        Entity e = camera.getFocusedEntity();
        return e instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS));
    }

    public boolean overridesColor() {
        return useColor.on();
    }

    public void apply(Camera camera, BackgroundRenderer.FogType type, float viewDistance) {
        if (specialFog(camera)) {
            return;
        }
        if (type == BackgroundRenderer.FogType.FOG_TERRAIN) {
            if (disable.on()) {
                RenderSystem.setShaderFogStart(Float.MAX_VALUE);
                RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            } else {
                float s = viewDistance * start.f() / 100f;
                float e = Math.max(s + 1f, viewDistance * end.f() / 100f);
                RenderSystem.setShaderFogStart(s);
                RenderSystem.setShaderFogEnd(e);
            }
            RenderSystem.setShaderFogShape(shape.index() == 0 ? FogShape.SPHERE : FogShape.CYLINDER);
        }
        if (useColor.on()) {
            int c = color.argb();
            RenderSystem.setShaderFogColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
        }
    }

    /** Background colour behind the sky; matches the fog colour so the horizon blends. */
    public void applyClearColor(Camera camera) {
        if (useColor.on() && !specialFog(camera)) {
            int c = color.argb();
            RenderSystem.clearColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 0f);
        }
    }
}
