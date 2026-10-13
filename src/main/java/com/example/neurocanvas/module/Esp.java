package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.BoxBatch;
import com.example.neurocanvas.fx.EffectDraw;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Highlights entities in one of three ways:
 * Box = a 3D box drawn over everything, 2D = a rectangle on the screen with a health bar (always on top),
 * Outline = the game's own glow outline around the body shape, in your colour.
 */
public final class Esp extends Module {
    private record Mark(Box box, int color, float health) { }

    private final Setting mode = add(Setting.choice("Mode", 0, "Box", "2D", "Outline"));
    private final Setting players = add(Setting.bool("Players", true));
    private final Setting hostile = add(Setting.bool("Hostile mobs", false));
    private final Setting passive = add(Setting.bool("Animals", false));
    private final Setting items = add(Setting.bool("Items", false));
    private final Setting skipTeam = add(Setting.bool("Skip teammates", true));
    private final Setting throughWalls = add(Setting.bool("Through walls", true));
    private final Setting outline = add(Setting.bool("Outline", true));
    private final Setting fill = add(Setting.bool("Fill", true));
    private final Setting fillStrength = add(Setting.number("Fill strength", 0.05, 0.6, 0.18));
    private final Setting width = add(Setting.number("Line width", 1, 6, 2));
    private final Setting healthBar = add(Setting.bool("Health bar (2D)", true));
    private final Setting healthColor = add(Setting.bool("Color by health", false));
    private final Setting playerColor = add(Setting.color("Player color", 0.0));
    private final Setting hostileColor = add(Setting.color("Hostile color", 0.0));
    private final Setting passiveColor = add(Setting.color("Animal color", 0.33));
    private final Setting itemColor = add(Setting.color("Item color", 0.15));
    private final Setting distance = add(Setting.number("Max distance", 8, 128, 64));

    private final List<Mark> marks2d = new ArrayList<>();
    private Matrix4f viewProj;
    /** Entities that get the game's glow outline, with their colour (rebuilt every tick in Outline mode). */
    private final Map<Integer, Integer> glow = new HashMap<>();

    public Esp() {
        super("ESP", Category.COMBAT, "Boxes, 2D frames or outlines around players, mobs and items", false);
        risky = true;
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

    /** Colour of the entity after the "by health" option. */
    private int shade(Entity e, int base) {
        if (healthColor.on() && e instanceof LivingEntity living) {
            float ratio = MathHelper.clamp(living.getHealth() / Math.max(1f, living.getMaxHealth()), 0f, 1f);
            return 0xFF000000 | (java.awt.Color.HSBtoRGB(ratio * 0.33f, 0.85f, 1f) & 0xFFFFFF);
        }
        return base;
    }

    private boolean wanted(MinecraftClient mc, Entity e, double maxSq) {
        return e != mc.player && !e.isInvisible() && e.squaredDistanceTo(mc.player) <= maxSq
                && colorFor(e) != null && !(skipTeam.on() && Teams.ignores(e));
    }

    // ---- Outline mode: the game's own glow, switched on per entity -----------------------------

    @Override
    public void tickAlways(MinecraftClient mc) {
        glow.clear();
        if (!enabled || mode.index() != 2 || mc.world == null || mc.player == null) {
            return;
        }
        double maxSq = distance.value * distance.value;
        for (Entity e : mc.world.getEntities()) {
            if (!wanted(mc, e, maxSq)) {
                continue;
            }
            if (!throughWalls.on() && !BoxBatch.inLineOfSight(mc, e.getBoundingBox())) {
                continue;
            }
            glow.put(e.getId(), shade(e, colorFor(e)) & 0xFFFFFF);
        }
    }

    /** RGB colour of the glow for this entity, or null when it should not glow. Asked by the renderer hooks. */
    public Integer glowColor(Entity entity) {
        return enabled && mode.index() == 2 ? glow.get(entity.getId()) : null;
    }

    // ---- Box and 2D modes ------------------------------------------------------------------------

    @Override
    public void renderWorld(RenderEnv env) {
        marks2d.clear();
        MinecraftClient mc = env.mc();
        int m = mode.index();
        if (m == 2 || mc.world == null || mc.player == null) {
            return;
        }
        double maxSq = distance.value * distance.value;
        List<Mark> marks = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (!wanted(mc, e, maxSq)) {
                continue;
            }
            Vec3d shift = EffectDraw.lerpPos(e, env.tickDelta()).subtract(e.getPos());
            Box box = e.getBoundingBox().offset(shift).expand(0.02);
            if (!throughWalls.on() && !BoxBatch.inLineOfSight(mc, box)) {
                continue;
            }
            float health = e instanceof LivingEntity living ? MathHelper.clamp(living.getHealth() / Math.max(1f, living.getMaxHealth()), 0f, 1f) : -1f;
            marks.add(new Mark(box, shade(e, colorFor(e)), health));
        }
        if (m == 1) {
            viewProj = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(env.m());
            marks2d.addAll(marks);
            return;
        }
        List<Box> boxes = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Mark mark : marks) {
            boxes.add(mark.box());
            colors.add(mark.color());
        }
        // Drawn without a depth test, so the entity's own model can never hide the box.
        BoxBatch.draw(env, boxes, colors, fill.on(), fillStrength.f(), outline.on(), width.f(), false, null);
    }

    /** 2D mode: projects the corners of each box to the screen and draws a rectangle with a health bar. */
    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mode.index() != 1 || viewProj == null || marks2d.isEmpty() || mc.options.hudHidden) {
            return;
        }
        float sw = ctx.getScaledWindowWidth();
        float sh = ctx.getScaledWindowHeight();
        int thick = Math.max(1, Math.round(width.f() / 2f));
        Vector4f v = new Vector4f();
        for (Mark mark : marks2d) {
            Box b = mark.box();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean ok = true;
            for (int i = 0; i < 8 && ok; i++) {
                // viewProj already contains the shift by -camera, so the corners are plain world coordinates.
                v.set((float) ((i & 1) == 0 ? b.minX : b.maxX), (float) ((i & 2) == 0 ? b.minY : b.maxY),
                        (float) ((i & 4) == 0 ? b.minZ : b.maxZ), 1f);
                viewProj.transform(v);
                if (v.w <= 0.01f) {
                    ok = false;
                    break;
                }
                float x = (v.x / v.w * 0.5f + 0.5f) * sw;
                float y = (1f - (v.y / v.w * 0.5f + 0.5f)) * sh;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
            if (!ok || maxX < 0 || maxY < 0 || minX > sw || minY > sh || maxX - minX < 2 || maxY - minY < 2) {
                continue;
            }
            int x0 = Math.round(minX), y0 = Math.round(minY), x1 = Math.round(maxX), y1 = Math.round(maxY);
            int color = mark.color();
            if (fill.on()) {
                ctx.fill(x0, y0, x1, y1, ((int) (255 * fillStrength.f()) << 24) | (color & 0xFFFFFF));
            }
            if (outline.on()) {
                int dark = 0xCC000000;
                ctx.fill(x0 - 1, y0 - 1, x1 + 1, y0 + thick + 1, dark);
                ctx.fill(x0 - 1, y1 - thick - 1, x1 + 1, y1 + 1, dark);
                ctx.fill(x0 - 1, y0, x0 + thick + 1, y1, dark);
                ctx.fill(x1 - thick - 1, y0, x1 + 1, y1, dark);
                ctx.fill(x0, y0, x1, y0 + thick, color);
                ctx.fill(x0, y1 - thick, x1, y1, color);
                ctx.fill(x0, y0, x0 + thick, y1, color);
                ctx.fill(x1 - thick, y0, x1, y1, color);
            }
            if (healthBar.on() && mark.health() >= 0f) {
                int barX = x0 - thick - 5;
                int h = y1 - y0;
                ctx.fill(barX - 1, y0 - 1, barX + 3, y1 + 1, 0xCC000000);
                int top = y1 - Math.round(h * mark.health());
                int hc = 0xFF000000 | (java.awt.Color.HSBtoRGB(mark.health() * 0.33f, 0.85f, 1f) & 0xFFFFFF);
                ctx.fill(barX, top, barX + 2, y1, hc);
            }
        }
    }
}
