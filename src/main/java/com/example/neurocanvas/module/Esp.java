package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.Fills;
import com.example.neurocanvas.fx.Lines;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Highlights entities with a coloured box. By default it only shows what you could see anyway (hidden by blocks);
 * "Through walls" is a separate switch because it gives an advantage that many servers forbid.
 */
public final class Esp extends Module {
    private final Setting players = add(Setting.bool("Players", true));
    private final Setting hostile = add(Setting.bool("Hostile mobs", false));
    private final Setting passive = add(Setting.bool("Animals", false));
    private final Setting items = add(Setting.bool("Items", false));
    private final Setting skipTeam = add(Setting.bool("Skip teammates", true));
    private final Setting throughWalls = add(Setting.bool("Through walls", false));
    private final Setting outline = add(Setting.bool("Outline", true));
    private final Setting fill = add(Setting.bool("Fill", true));
    private final Setting fillStrength = add(Setting.number("Fill strength", 0.05, 0.6, 0.18));
    private final Setting width = add(Setting.number("Line width", 1, 6, 2));
    private final Setting healthColor = add(Setting.bool("Color by health", false));
    private final Setting playerColor = add(Setting.color("Player color", 0.0));
    private final Setting hostileColor = add(Setting.color("Hostile color", 0.0));
    private final Setting passiveColor = add(Setting.color("Animal color", 0.33));
    private final Setting itemColor = add(Setting.color("Item color", 0.15));
    private final Setting distance = add(Setting.number("Max distance", 8, 128, 64));

    public Esp() {
        super("ESP", Category.COMBAT, "Coloured boxes around players, mobs and items", false);
        aiControllable = false;
        playerColor.setArgb(0xFFFFFFFF);
        hostileColor.setArgb(0xFFFF4D4D);
        passiveColor.setArgb(0xFF4DFF88);
        itemColor.setArgb(0xFFFFD84D);
    }

    private Integer colorFor(Entity e) {
        if (e instanceof PlayerEntity) {
            return players.on() ? playerColor.argb() : null;
        }
        if (e instanceof HostileEntity) {
            return hostile.on() ? hostileColor.argb() : null;
        }
        if (e instanceof PassiveEntity) {
            return passive.on() ? passiveColor.argb() : null;
        }
        if (e instanceof ItemEntity) {
            return items.on() ? itemColor.argb() : null;
        }
        return null;
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        if (mc.world == null || mc.player == null) {
            return;
        }
        double maxSq = distance.value * distance.value;
        if (throughWalls.on()) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
        java.util.List<Box> boxes = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.isInvisible() || e.squaredDistanceTo(mc.player) > maxSq) {
                continue;
            }
            Integer base = colorFor(e);
            if (base == null || (skipTeam.on() && Teams.ignores(e))) {
                continue;
            }
            int color = base;
            if (healthColor.on() && e instanceof LivingEntity living) {
                float ratio = MathHelper.clamp(living.getHealth() / Math.max(1f, living.getMaxHealth()), 0f, 1f);
                color = 0xFF000000 | (java.awt.Color.HSBtoRGB(ratio * 0.33f, 0.85f, 1f) & 0xFFFFFF);
            }
            Vec3d shift = EffectDraw.lerpPos(e, env.tickDelta()).subtract(e.getPos());
            boxes.add(e.getBoundingBox().offset(shift).expand(0.02));
            colors.add(color);
        }
        // The game can build only one batch at a time, so the fill and the outline are drawn one after the other.
        if (!boxes.isEmpty() && fill.on()) {
            RenderSystem.depthMask(false);
            Fills fills = Fills.begin(env);
            for (int i = 0; i < boxes.size(); i++) {
                fills.box(boxes.get(i), ((int) (255 * fillStrength.f()) << 24) | (colors.get(i) & 0xFFFFFF));
            }
            fills.end();
            RenderSystem.depthMask(true);
        }
        if (!boxes.isEmpty() && outline.on()) {
            Lines lines = Lines.begin(env, width.f());
            for (int i = 0; i < boxes.size(); i++) {
                lines.box(boxes.get(i), colors.get(i));
            }
            lines.end();
        }
        RenderSystem.disableDepthTest();
    }
}
