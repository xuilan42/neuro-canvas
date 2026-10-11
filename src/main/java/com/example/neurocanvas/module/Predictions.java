package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Lines;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Draws where a bow shot, trident, pearl, snowball, egg or potion you are about to throw will land. */
public final class Predictions extends Module {
    private record Shot(double speed, double gravity, float roll) { }

    private final Setting width = add(Setting.number("Line width", 1, 6, 2));
    private final Setting steps = add(Setting.number("Max steps", 40, 300, 150));
    private final Setting pathColor = add(Setting.color("Path color", 0.12));
    private final Setting hitColor = add(Setting.color("Entity hit color", 0.0));
    private final Setting landColor = add(Setting.color("Landing color", 0.38));
    private final Setting marker = add(Setting.bool("Landing marker", true));

    public Predictions() {
        super("Predictions", Category.COMBAT, "Trajectory of the projectile you are about to shoot or throw", false);
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

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            return;
        }
        Shot shot = shotFor(p);
        if (shot == null) {
            return;
        }
        float td = env.tickDelta();
        double yaw = Math.toRadians(p.getYaw(td));
        double pitch = Math.toRadians(p.getPitch(td));
        double roll = Math.toRadians(shot.roll);
        Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch + roll), Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3d shooter = p.getVelocity();
        Vec3d vel = dir.multiply(shot.speed).add(shooter.x, p.isOnGround() ? 0 : shooter.y, shooter.z);
        Vec3d pos = new Vec3d(MathHelper.lerp(td, p.prevX, p.getX()), MathHelper.lerp(td, p.prevY, p.getY()) + p.getEyeHeight(p.getPose()) - 0.1,
                MathHelper.lerp(td, p.prevZ, p.getZ()));

        List<Vec3d> points = new ArrayList<>();
        points.add(pos);
        Vec3d landing = null;
        boolean entityHit = false;
        for (int i = 0; i < (int) steps.value && landing == null; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult block = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            Vec3d blockPoint = block.getType() != HitResult.Type.MISS ? block.getPos() : null;
            Vec3d end = blockPoint != null ? blockPoint : next;
            for (Entity e : mc.world.getOtherEntities(p, new Box(pos, end).expand(0.3), x -> x.canHit() && !x.isSpectator())) {
                Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(pos, end);
                if (hit.isPresent()) {
                    end = hit.get();
                    blockPoint = end;
                    entityHit = true;
                    break;
                }
            }
            points.add(end);
            if (blockPoint != null) {
                landing = end;
            }
            vel = vel.multiply(0.99).subtract(0, shot.gravity, 0);
            pos = next;
        }

        RenderSystem.enableDepthTest();
        Lines lines = Lines.begin(env, width.f());
        int pathArgb = pathColor.argb();
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);
            lines.line(a.x, a.y, a.z, b.x, b.y, b.z, pathArgb);
        }
        if (landing != null && marker.on()) {
            int c = entityHit ? hitColor.argb() : landColor.argb();
            double r = 0.3;
            lines.line(landing.x - r, landing.y, landing.z, landing.x + r, landing.y, landing.z, c);
            lines.line(landing.x, landing.y - r, landing.z, landing.x, landing.y + r, landing.z, c);
            lines.line(landing.x, landing.y, landing.z - r, landing.x, landing.y, landing.z + r, c);
        }
        lines.end();
        RenderSystem.disableDepthTest();
    }
}
