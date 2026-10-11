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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Draws entity hitboxes in your own colours. Hidden behind walls, like the vanilla F3+B boxes. */
public final class CustomHitbox extends Module {
    private final Setting players = add(Setting.bool("Players", true));
    private final Setting hostile = add(Setting.bool("Hostile mobs", true));
    private final Setting other = add(Setting.bool("Other living", false));
    private final Setting items = add(Setting.bool("Items", false));
    private final Setting playerColor = add(Setting.color("Player color", 0));
    private final Setting teamColor = add(Setting.color("Teammate color", 0.5));
    private final Setting hostileColor = add(Setting.color("Hostile color", 0));
    private final Setting otherColor = add(Setting.color("Other color", 0.33));
    private final Setting itemColor = add(Setting.color("Item color", 0.15));
    private final Setting width = add(Setting.number("Line width", 1, 6, 2));
    private final Setting fill = add(Setting.bool("Fill", false));
    private final Setting fillAlpha = add(Setting.number("Fill strength", 0.05, 0.6, 0.18));
    private final Setting eyeLine = add(Setting.bool("Eye line", true));
    private final Setting distance = add(Setting.number("Max distance", 8, 128, 48));

    public CustomHitbox() {
        super("CustomHitbox", Category.COMBAT, "Entity hitboxes in your own colours", false);
        playerColor.setArgb(0xFFFFFFFF);
        hostileColor.setArgb(0xFFFF4D4D);
        otherColor.setArgb(0xFF4DFF88);
        itemColor.setArgb(0xFFFFD84D);
        teamColor.setArgb(0xFF4DE0FF);
    }

    private Integer colorFor(Entity e) {
        if (e instanceof PlayerEntity) {
            if (!players.on()) {
                return null;
            }
            return Teams.ignores(e) ? teamColor.argb() : playerColor.argb();
        }
        if (e instanceof HostileEntity) {
            return hostile.on() ? hostileColor.argb() : null;
        }
        if (e instanceof LivingEntity) {
            return other.on() ? otherColor.argb() : null;
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
        RenderSystem.enableDepthTest();

        java.util.List<Box> boxes = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        java.util.List<LivingEntity> eyes = new java.util.ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.squaredDistanceTo(mc.player) > maxSq) {
                continue;
            }
            Integer color = colorFor(e);
            if (color == null) {
                continue;
            }
            Vec3d shift = EffectDraw.lerpPos(e, env.tickDelta()).subtract(e.getPos());
            boxes.add(e.getBoundingBox().offset(shift));
            colors.add(color);
            eyes.add(e instanceof LivingEntity living ? living : null);
        }
        // The game can build only one batch at a time, so the fill and the lines are drawn one after the other.
        if (!boxes.isEmpty() && fill.on()) {
            RenderSystem.depthMask(false);
            Fills fills = Fills.begin(env);
            for (int i = 0; i < boxes.size(); i++) {
                fills.box(boxes.get(i), ((int) (255 * fillAlpha.f()) << 24) | (colors.get(i) & 0xFFFFFF));
            }
            fills.end();
            RenderSystem.depthMask(true);
        }
        if (!boxes.isEmpty()) {
            Lines lines = Lines.begin(env, width.f());
            for (int i = 0; i < boxes.size(); i++) {
                Box box = boxes.get(i);
                lines.box(box, colors.get(i));
                LivingEntity living = eyes.get(i);
                if (eyeLine.on() && living != null) {
                    double eyeY = box.minY + living.getEyeHeight(living.getPose());
                    lines.line(box.minX, eyeY, (box.minZ + box.maxZ) / 2, box.maxX, eyeY, (box.minZ + box.maxZ) / 2, 0xFFFF3030);
                }
            }
            lines.end();
        }
        RenderSystem.disableDepthTest();
    }
}
