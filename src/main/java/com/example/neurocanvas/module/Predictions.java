package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Billboard;
import com.example.neurocanvas.fx.Depth;
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

    private record Trace(List<Vec3d> points, Vec3d landing, Vec3d normal, boolean entityHit, int ticks, int color) { }

    private final Setting pathStyle = add(Setting.choice("Path style", 0, "Line", "Dashed", "Dots", "Arrows", "Glow trail", "Rainbow", "Double", "None"));
    private final Setting markerStyle = add(Setting.choice("Landing style", 0, "Cross", "Ring", "Target", "Diamond", "Pulse", "Pin", "None"));
    private final Setting flow = add(Setting.number("Animation speed", 0, 4, 1));
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
        risky = true;
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
        Vec3d normal = new Vec3d(0, 1, 0);
        boolean entityHit = false;
        int ticks = 0;
        Entity context = ignore != null ? ignore : mc.player;
        for (int i = 0; i < (int) steps.value && landing == null; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult block = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, context));
            Vec3d blockPoint = block.getType() != HitResult.Type.MISS ? block.getPos() : null;
            Vec3d hitNormal = block.getType() != HitResult.Type.MISS
                    ? new Vec3d(block.getSide().getOffsetX(), block.getSide().getOffsetY(), block.getSide().getOffsetZ()) : null;
            Vec3d end = blockPoint != null ? blockPoint : next;
            for (Entity e : mc.world.getOtherEntities(ignore, new Box(pos, end).expand(0.3),
                    x -> x.canHit() && !x.isSpectator() && x != owner)) {
                Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(pos, end);
                if (hit.isPresent()) {
                    end = hit.get();
                    blockPoint = end;
                    hitNormal = new Vec3d(0, 1, 0);
                    entityHit = true;
                    break;
                }
            }
            points.add(end);
            ticks++;
            if (blockPoint != null) {
                landing = end;
                if (hitNormal != null) {
                    normal = hitNormal;
                }
            }
            vel = vel.multiply(0.99).subtract(0, gravity, 0);
            pos = next;
        }
        return new Trace(points, landing, normal, entityHit, ticks, color);
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

        for (boolean over : new boolean[] {false, true}) {
            Lines lines = null;
            for (Trace t : traces) {
                Vec3d end = t.landing() != null ? t.landing() : t.points().get(t.points().size() - 1);
                if (Depth.over(mc, end) != over) {
                    continue;
                }
                if (lines == null) {
                    Depth.state(over);
                    lines = Lines.begin(env, width.f());
                }
                if (pathStyle.index() != 7) {
                    path(lines, t, env.t());
                }
                Vec3d l = t.landing();
                if (l != null && marker.on() && markerStyle.index() != 6) {
                    landing(lines, l, t.normal(), t.entityHit() ? hitColor.argb() : landColor.argb(), env.t());
                }
            }
            if (lines != null) {
                lines.end();
            }
        }
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

    private static int withAlpha(int argb, double k) {
        return ((int) Math.max(0, Math.min(255, (argb >>> 24) * k)) << 24) | (argb & 0xFFFFFF);
    }

    private static int hue(double h, int alpha) {
        return (alpha << 24) | (java.awt.Color.HSBtoRGB((float) (((h % 1.0) + 1.0) % 1.0), 0.8f, 1f) & 0xFFFFFF);
    }

    /** Draws the flight path in the chosen style. Points are ordered from the start to the landing. */
    private void path(Lines lines, Trace t, double time) {
        List<Vec3d> pts = t.points();
        int n = pts.size();
        int col = t.color();
        int alpha = col >>> 24;
        double run = time * flow.value;
        int style = pathStyle.index();
        // Cumulative length, so dashes and arrows are evenly spaced whatever the step size.
        double[] len = new double[n];
        for (int i = 1; i < n; i++) {
            len[i] = len[i - 1] + pts.get(i).distanceTo(pts.get(i - 1));
        }
        for (int i = 0; i + 1 < n; i++) {
            Vec3d a = pts.get(i);
            Vec3d b = pts.get(i + 1);
            double f = n < 2 ? 0 : i / (double) (n - 1);
            switch (style) {
                case 1 -> {
                    if (((int) Math.floor((len[i] - run * 0.8) / 0.6)) % 2 == 0) {
                        lines.line(a.x, a.y, a.z, b.x, b.y, b.z, col);
                    }
                }
                case 2 -> {
                    double phase = ((len[i] - run * 0.8) / 0.5) % 1.0;
                    if (phase < 0) {
                        phase += 1.0;
                    }
                    double r = 0.045 * (1 + 0.6 * Math.max(0, 1 - phase * 3));
                    lines.line(a.x - r, a.y, a.z, a.x + r, a.y, a.z, col);
                    lines.line(a.x, a.y - r, a.z, a.x, a.y + r, a.z, col);
                    lines.line(a.x, a.y, a.z - r, a.x, a.y, a.z + r, col);
                }
                case 3 -> {
                    lines.line(a.x, a.y, a.z, b.x, b.y, b.z, withAlpha(col, 0.35));
                    double phase = ((len[i] - run * 0.9) / 0.9) % 1.0;
                    if (phase < 0) {
                        phase += 1.0;
                    }
                    if (phase < ((len[i + 1] - len[i]) / 0.9 + 1e-6)) {
                        Vec3d d = b.subtract(a);
                        double dl = d.length();
                        if (dl > 1e-6) {
                            Vec3d dir = d.multiply(1 / dl);
                            Vec3d side = Math.abs(dir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(-dir.z, 0, dir.x).normalize();
                            Vec3d up = dir.crossProduct(side);
                            Vec3d tip = b.add(dir.multiply(0.18));
                            Vec3d back = b.subtract(dir.multiply(0.12));
                            for (Vec3d w : new Vec3d[] {side, side.negate(), up, up.negate()}) {
                                Vec3d wing = back.add(w.multiply(0.12));
                                lines.line(tip.x, tip.y, tip.z, wing.x, wing.y, wing.z, col);
                            }
                        }
                    }
                }
                case 4 -> {
                    double head = (run * 0.35) % 1.0;
                    double behind = ((head - f) % 1.0 + 1.0) % 1.0;
                    double glow = Math.max(0, 1.0 - behind * 2.2);
                    lines.line(a.x, a.y, a.z, b.x, b.y, b.z, withAlpha(col, 0.15 + 0.85 * Math.max(glow, 1 - f * 0.8) * (0.4 + 0.6 * glow)));
                }
                case 5 -> lines.line(a.x, a.y, a.z, b.x, b.y, b.z, hue(f * 1.2 - run * 0.15, alpha));
                case 6 -> {
                    double off = 0.05 + 0.03 * Math.sin(run * 3 + len[i] * 4);
                    Vec3d d = b.subtract(a);
                    Vec3d side = new Vec3d(-d.z, 0, d.x);
                    side = side.lengthSquared() < 1e-9 ? new Vec3d(off, 0, 0) : side.normalize().multiply(off);
                    lines.line(a.x + side.x, a.y, a.z + side.z, b.x + side.x, b.y, b.z + side.z, col);
                    lines.line(a.x - side.x, a.y, a.z - side.z, b.x - side.x, b.y, b.z - side.z, withAlpha(col, 0.7));
                }
                default -> lines.line(a.x, a.y, a.z, b.x, b.y, b.z, col);
            }
        }
    }

    /** A flat frame on the surface that was hit: u and v lie in the surface, n points out of it. */
    private static final class Plane {
        final Vec3d o;
        final Vec3d u;
        final Vec3d v;
        final Vec3d n;

        Plane(Vec3d origin, Vec3d normal) {
            n = normal.lengthSquared() < 1e-9 ? new Vec3d(0, 1, 0) : normal.normalize();
            Vec3d tangent = Math.abs(n.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(-n.z, 0, n.x).normalize();
            u = tangent;
            v = n.crossProduct(u);
            o = origin.add(n.multiply(0.02));
        }

        Vec3d at(double a, double b, double c) {
            return o.add(u.multiply(a)).add(v.multiply(b)).add(n.multiply(c));
        }
    }

    private static void seg(Lines lines, Vec3d a, Vec3d b, int argb) {
        lines.line(a.x, a.y, a.z, b.x, b.y, b.z, argb);
    }

    private static void ring(Lines lines, Plane p, double r, int argb, int segs, double lift) {
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            seg(lines, p.at(Math.cos(a0) * r, Math.sin(a0) * r, lift), p.at(Math.cos(a1) * r, Math.sin(a1) * r, lift), argb);
        }
    }

    /** The mark where the projectile lands, lying on the surface it hits (a wall gets a vertical mark, a ceiling a hanging one). */
    private void landing(Lines lines, Vec3d l, Vec3d normal, int color, double time) {
        double run = time * flow.value;
        Plane p = new Plane(l, normal);
        switch (markerStyle.index()) {
            case 1 -> ring(lines, p, 0.4, color, 24, 0);
            case 2 -> {
                ring(lines, p, 0.5, color, 24, 0);
                ring(lines, p, 0.25, withAlpha(color, 0.8), 18, 0);
                seg(lines, p.at(-0.7, 0, 0), p.at(-0.35, 0, 0), color);
                seg(lines, p.at(0.35, 0, 0), p.at(0.7, 0, 0), color);
                seg(lines, p.at(0, -0.7, 0), p.at(0, -0.35, 0), color);
                seg(lines, p.at(0, 0.35, 0), p.at(0, 0.7, 0), color);
            }
            case 3 -> {
                double r = 0.38;
                double[][] corner = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
                for (int i = 0; i < 4; i++) {
                    double[] a = corner[i], b = corner[(i + 1) % 4];
                    seg(lines, p.at(a[0], a[1], 0.22), p.at(b[0], b[1], 0.22), color);
                    seg(lines, p.at(0, 0, 0.45), p.at(a[0], a[1], 0.22), color);
                    seg(lines, p.at(0, 0, 0), p.at(a[0], a[1], 0.22), color);
                }
            }
            case 4 -> {
                for (int i = 0; i < 3; i++) {
                    double f = (run * 0.5 + i / 3.0) % 1.0;
                    ring(lines, p, 0.12 + 0.6 * f, withAlpha(color, 1 - f), 24, 0);
                }
            }
            case 5 -> {
                double head = 0.55 + 0.06 * Math.sin(run * 3);
                seg(lines, p.at(0, 0, 0.03), p.at(0, 0, head - 0.12), color);
                ring(lines, p, 0.12, color, 12, head);
                ring(lines, p, 0.2, withAlpha(color, 0.5), 12, 0);
            }
            case 6 -> { }
            default -> {
                double r = 0.3;
                seg(lines, p.at(-r, 0, 0), p.at(r, 0, 0), color);
                seg(lines, p.at(0, -r, 0), p.at(0, r, 0), color);
                seg(lines, p.at(0, 0, -r), p.at(0, 0, r), color);
            }
        }
    }
}
