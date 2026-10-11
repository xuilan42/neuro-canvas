package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Billboard;
import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.Lines;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Draws where a projectile will land: the one you are about to shoot or throw, and the ones already flying
 * (ender pearls, arrows, tridents, splash potions), each with the time left until it lands.
 */
public final class Predictions extends Module {
    private record Shot(double speed, double gravity, float roll) { }

    private record Trace(List<Vec3d> points, Vec3d landing, boolean entityHit, int ticks, int color) { }

    private final Setting width = add(Setting.number("Line width", 1, 6, 2));
    private final Setting steps = add(Setting.number("Max steps", 40, 300, 150));
    private final Setting pathColor = add(Setting.color("Path color", 0.12));
    private final Setting thrownColor = add(Setting.color("Flying color", 0.06));
    private final Setting hitColor = add(Setting.color("Entity hit color", 0.0));
    private final Setting landColor = add(Setting.color("Landing color", 0.38));
    private final Setting marker = add(Setting.bool("Landing marker", true));
    private final Setting flying = add(Setting.bool("Flying projectiles", true));
    private final Setting others = add(Setting.bool("Other players' projectiles", true));
    private final Setting landingTime = add(Setting.bool("Landing time", true));
    private final Setting range = add(Setting.number("Projectile range", 16, 128, 64));

    public Predictions() {
        super("Predictions", Category.COMBAT, "Trajectory and landing time of projectiles you aim and projectiles already in the air", false);
    }

    private static Shot shotFor(ClientPlayerEntity p) {
        ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
        if (active.isOf(Items.BOW)) {
            float pull = BowItem.getPullProgress(p.getItemUseTime());
            return pull < 0.1f ? null : new Shot(pull * 3.0, 0.05, 0f);
        }
        if (active.isOf(Items.TRIDENT)) {
            return new Shot(2.5, 0.05, 0f);
        }
        for (Hand h : Hand.values()) {
            ItemStack s = p.getStackInHand(h);
            if (s.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(s)) {
                return new Shot(3.15, 0.05, 0f);
            }
            if (s.isOf(Items.ENDER_PEARL) || s.isOf(Items.SNOWBALL) || s.isOf(Items.EGG)) {
                return new Shot(1.5, 0.03, 0f);
            }
            if (s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION)) {
                return new Shot(0.5, 0.05, -20f);
            }
            if (s.isOf(Items.EXPERIENCE_BOTTLE)) {
                return new Shot(0.7, 0.07, -20f);
            }
        }
        return null;
    }

    /** Steps a projectile forward tick by tick until it hits a block or an entity. */
    private Trace trace(MinecraftClient mc, Entity ignore, Entity owner, Vec3d start, Vec3d velocity, double gravity, int color) {
        List<Vec3d> points = new ArrayList<>();
        points.add(start);
        Vec3d pos = start;
        Vec3d vel = velocity;
        Vec3d landing = null;
        boolean entityHit = false;
        int ticks = 0;
        Entity context = ignore != null ? ignore : mc.player;
        for (int i = 0; i < (int) steps.value && landing == null; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult block = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, context));
            Vec3d blockPoint = block.getType() != HitResult.Type.MISS ? block.getPos() : null;
            Vec3d end = blockPoint != null ? blockPoint : next;
            for (Entity e : mc.world.getOtherEntities(ignore, new Box(pos, end).expand(0.3),
                    x -> x.canHit() && !x.isSpectator() && x != owner)) {
                Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(pos, end);
                if (hit.isPresent()) {
                    end = hit.get();
                    blockPoint = end;
                    entityHit = true;
                    break;
                }
            }
            points.add(end);
            ticks++;
            if (blockPoint != null) {
                landing = end;
            }
            vel = vel.multiply(0.99).subtract(0, gravity, 0);
            pos = next;
        }
        return new Trace(points, landing, entityHit, ticks, color);
    }

    private Trace ownShot(RenderEnv env, ClientPlayerEntity p, Shot shot) {
        float td = env.tickDelta();
        double yaw = Math.toRadians(p.getYaw(td));
        double pitch = Math.toRadians(p.getPitch(td));
        double roll = Math.toRadians(shot.roll);
        Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch + roll), Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3d shooter = p.getVelocity();
        Vec3d vel = dir.multiply(shot.speed).add(shooter.x, p.isOnGround() ? 0 : shooter.y, shooter.z);
        Vec3d pos = new Vec3d(MathHelper.lerp(td, p.prevX, p.getX()), MathHelper.lerp(td, p.prevY, p.getY()) + p.getEyeHeight(p.getPose()) - 0.1,
                MathHelper.lerp(td, p.prevZ, p.getZ()));
        return trace(env.mc(), null, null, pos, vel, shot.gravity, pathColor.argb());
    }

    /** Gravity of a projectile already in the air, or NaN when it is not one we track. */
    private static double gravityOf(Entity e) {
        if (e instanceof EnderPearlEntity) {
            return 0.03;
        }
        if (e instanceof PersistentProjectileEntity || e instanceof PotionEntity) {
            return 0.05;
        }
        return Double.NaN;
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            return;
        }
        List<Trace> traces = new ArrayList<>();
        Shot shot = shotFor(p);
        if (shot != null) {
            traces.add(ownShot(env, p, shot));
        }
        if (flying.on()) {
            double maxSq = range.value * range.value;
            for (Entity e : mc.world.getEntities()) {
                double g = gravityOf(e);
                if (Double.isNaN(g) || e.squaredDistanceTo(p) > maxSq) {
                    continue;
                }
                Entity owner = e instanceof ProjectileEntity projectile ? projectile.getOwner() : null;
                if (!others.on() && owner != p) {
                    continue;
                }
                Vec3d vel = e.getVelocity();
                if (vel.lengthSquared() < 0.01) {
                    continue;
                }
                Vec3d pos = EffectDraw.lerpPos(e, env.tickDelta());
                traces.add(trace(mc, e, owner, pos, vel, g, thrownColor.argb()));
            }
        }
        if (traces.isEmpty()) {
            return;
        }

        RenderSystem.enableDepthTest();
        Lines lines = Lines.begin(env, width.f());
        for (Trace t : traces) {
            for (int i = 0; i + 1 < t.points().size(); i++) {
                Vec3d a = t.points().get(i);
                Vec3d b = t.points().get(i + 1);
                lines.line(a.x, a.y, a.z, b.x, b.y, b.z, t.color());
            }
            Vec3d l = t.landing();
            if (l != null && marker.on()) {
                int c = t.entityHit() ? hitColor.argb() : landColor.argb();
                double r = 0.3;
                lines.line(l.x - r, l.y, l.z, l.x + r, l.y, l.z, c);
                lines.line(l.x, l.y - r, l.z, l.x, l.y + r, l.z, c);
                lines.line(l.x, l.y, l.z - r, l.x, l.y, l.z + r, c);
            }
        }
        lines.end();
        RenderSystem.disableDepthTest();

        if (landingTime.on()) {
            for (Trace t : traces) {
                if (t.landing() != null) {
                    Billboard.draw(env, t.landing().add(0, 0.55, 0), String.format("%.1fs", t.ticks() * 0.05),
                            t.entityHit() ? hitColor.argb() : landColor.argb(), 1f);
                }
            }
        }
    }
}
