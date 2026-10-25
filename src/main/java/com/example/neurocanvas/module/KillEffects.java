package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.FxParticles;
import com.example.neurocanvas.fx.Lines;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;
import net.minecraft.entity.LivingEntity;

/** A flourish where the entity you killed dies: a burst, rising souls or a lightning bolt. */
public final class KillEffects extends Module implements WorldState.CombatListener {
    private static final long BOLT_MS = 500;

    private record Bolt(double[] x, double[] y, double[] z, long start) { }

    private final Setting style = add(Setting.choice("Style", 0, "Burst", "Souls", "Lightning", "All",
            "Ash", "Embers", "Shatter", "Confetti", "Black hole", "Fireworks", "Tornado", "Steam", "Petals", "Rain", "Shockwave", "Beam",
            "Voxels", "Melt", "Ice", "Bubbles", "Cubes", "Hologram", "Implode", "Random"));
    private final Setting whose = add(Setting.choice("Deaths", 0, "My kills", "Players", "Everyone"));
    private final Setting debug = add(Setting.bool("Debug (chat)", false));
    private final Setting natural = add(Setting.bool("Natural colours", true));
    private final Setting count = add(Setting.number("Count", 4, 200, 30));
    private final Setting size = add(Setting.number("Size", 0.04, 0.5, 0.14));
    private final Setting lifetime = add(Setting.number("Lifetime (s)", 0.3, 3, 1.2));
    private final Setting color1 = add(Setting.color("Color 1", 0.12));
    private final Setting color2 = add(Setting.color("Color 2", 0.0));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.95));

    private final FxParticles fx = new FxParticles(3000);
    private final com.example.neurocanvas.fx.KillModels models = new com.example.neurocanvas.fx.KillModels();
    private final com.example.neurocanvas.fx.EffectSpec modelColors = new com.example.neurocanvas.fx.EffectSpec();
    private boolean instantShards;
    private final List<Bolt> bolts = new ArrayList<>();
    private final Random random = new Random();
    private double lastT;

    public KillEffects() {
        super("KillEffects", Category.VISUALS, "Effect where the entity you killed dies", false);
        WorldState.addCombatListener(this);
    }

    private final java.util.Map<Integer, Long> seen = new java.util.HashMap<>();

    @Override
    public void onKill(LivingEntity e) {
        if (enabled && seen.putIfAbsent(e.getId(), e.getWorld().getTime()) == null) {
            trace(e, "your kill (death signal)");
            play(e);
        }
    }

    @Override
    public void onDeath(LivingEntity e, boolean mine) {
        int mode = whose.index();
        if (!enabled || mine || mode == 0 || e == net.minecraft.client.MinecraftClient.getInstance().player
                || e instanceof net.minecraft.entity.decoration.ArmorStandEntity
                || mode == 1 && !(e instanceof net.minecraft.entity.player.PlayerEntity) || Teams.ignores(e)) {
            return;
        }
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player != null && e.squaredDistanceTo(mc.player) <= 64 * 64 && seen.putIfAbsent(e.getId(), e.getWorld().getTime()) == null) {
            trace(e, "death signal from the server");
            play(e);
        }
    }

    /** With "Players" or "Everyone", every death in range plays the effect, not just the ones you caused. */
    @Override
    public void tick(net.minecraft.client.MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            seen.clear();
            return;
        }
        long now = mc.world.getTime();
        seen.values().removeIf(t -> now - t > 30 || now < t);
        int mode = whose.index();
        if (mode == 0) {
            watched.clear();
            return;
        }
        java.util.Map<Integer, LivingEntity> current = new java.util.HashMap<>();
        for (net.minecraft.entity.Entity en : mc.world.getEntities()) {
            if (!(en instanceof LivingEntity e) || e == mc.player || e instanceof net.minecraft.entity.decoration.ArmorStandEntity
                    || mode == 1 && !(e instanceof net.minecraft.entity.player.PlayerEntity)
                    || e.squaredDistanceTo(mc.player) > 48 * 48 || Teams.ignores(e)) {
                continue;
            }
            current.put(e.getId(), e);
            if (e.hurtTime > 0) {
                hurtAt.put(e.getId(), now);
            }
            if (e.isDead() && seen.putIfAbsent(e.getId(), now) == null) {
                trace(e, "health reached zero");
                play(e);
            }
        }
        // A player the server removed right after a hit never shows zero health: treat "hurt, then gone nearby" as a death.
        for (java.util.Map.Entry<Integer, LivingEntity> w : watched.entrySet()) {
            LivingEntity e = w.getValue();
            Long hurt = hurtAt.get(w.getKey());
            if (!current.containsKey(w.getKey()) && e.isRemoved() && hurt != null && now - hurt < 30
                    && e.squaredDistanceTo(mc.player) < 28 * 28 && seen.putIfAbsent(w.getKey(), now) == null) {
                trace(e, "removed right after a hit");
                play(e);
            }
        }
        hurtAt.values().removeIf(t -> now - t > 100);
        watched.clear();
        watched.putAll(current);
    }

    private final java.util.Map<Integer, LivingEntity> watched = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> hurtAt = new java.util.HashMap<>();

    private void play(LivingEntity e) {
        int s = style.index();
        if (s == 23) {
            s = random.nextInt(23);
            if (s == 3) {
                s = 4;
            }
        }
        // The effects that change the dead body itself keep its model; without the shader they fall back to particles.
        if ((s == 18 || s >= 20 && s <= 22) && com.example.neurocanvas.fx.KillModels.possible()) {
            models.attach(fx);
            models.start(e, s == 18 ? com.example.neurocanvas.fx.KillModels.ICE : s - 20 + 1);
            return;
        }
        if (s == 20 || s == 21 || s == 22) {
            s = s == 20 ? 16 : s == 21 ? 15 : 8;
        }
        if (s >= 4) {
            extra(s, e);
            return;
        }
        if (s == 0 || s == 3) {
            burst(e);
        }
        if (s == 1 || s == 3) {
            souls(e);
        }
        if (s == 2 || s == 3) {
            bolt(e);
        }
    }

    private FxParticles.P base(double x, double y, double z) {
        FxParticles.P p = new FxParticles.P();
        p.x = x;
        p.y = y;
        p.z = z;
        p.max = lifetime.f() * (0.7f + random.nextFloat() * 0.6f);
        p.size = size.f() * (0.7f + random.nextFloat() * 0.6f);
        p.alpha = alpha.f();
        p.c1 = color1.argb();
        p.c2 = color2.argb();
        return p;
    }

    private void burst(LivingEntity e) {
        for (int i = 0; i < (int) count.value; i++) {
            FxParticles.P p = base(e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
            double theta = random.nextDouble() * Math.PI * 2;
            double up = random.nextDouble() * 1.6 - 0.4;
            double flat = Math.sqrt(Math.max(0, 1 - up * up));
            double v = 2.5 + random.nextDouble() * 3.0;
            p.vx = Math.cos(theta) * flat * v;
            p.vy = up * v + 1.0;
            p.vz = Math.sin(theta) * flat * v;
            p.gravity = 1.4f;
            p.drag = 1.4f;
            p.rot = random.nextFloat() * 6.28f;
            p.spin = (random.nextFloat() - 0.5f) * 10f;
            p.shape = random.nextBoolean() ? FxParticles.Shape.STAR : FxParticles.Shape.SQUARE;
            fx.spawn(p);
        }
    }

    private void souls(LivingEntity e) {
        for (int i = 0; i < (int) count.value; i++) {
            FxParticles.P p = base(e.getX() + (random.nextDouble() - 0.5) * 0.8, e.getY() + random.nextDouble() * e.getHeight(),
                    e.getZ() + (random.nextDouble() - 0.5) * 0.8);
            p.vx = (random.nextDouble() - 0.5) * 0.4;
            p.vy = 0.8 + random.nextDouble() * 1.4;
            p.vz = (random.nextDouble() - 0.5) * 0.4;
            p.gravity = -0.3f;
            p.drag = 0.6f;
            p.shape = random.nextBoolean() ? FxParticles.Shape.CIRCLE : FxParticles.Shape.PIXEL;
            fx.spawn(p);
        }
        FxParticles.P ring = base(e.getX(), e.getY() + 0.05, e.getZ());
        ring.size = 0.4f;
        ring.grow = 4f;
        ring.max = lifetime.f();
        ring.shape = FxParticles.Shape.RING;
        fx.spawn(ring);
    }

    private void bolt(LivingEntity e) {
        int n = 14;
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double jitter = i == n - 1 ? 0 : 0.7 * (1 - t) + 0.15;
            xs[i] = e.getX() + (random.nextDouble() - 0.5) * jitter * 2;
            ys[i] = e.getY() + 14 * (1 - t);
            zs[i] = e.getZ() + (random.nextDouble() - 0.5) * jitter * 2;
        }
        bolts.add(new Bolt(xs, ys, zs, System.currentTimeMillis()));
    }

    /** With Debug on, says in chat which signal started the effect and how long after the hit it came. */
    private void trace(LivingEntity e, String how) {
        if (debug.on() && net.minecraft.client.MinecraftClient.getInstance().player != null) {
            net.minecraft.client.MinecraftClient.getInstance().player.sendMessage(
                    net.minecraft.text.Text.literal("[KillEffects] " + e.getName().getString() + ": " + how), false);
        }
    }

    private static boolean mcWorldGone() {
        return net.minecraft.client.MinecraftClient.getInstance().world == null;
    }

    private double rnd() {
        return random.nextDouble();
    }

    /** Own colours for the effect, or the two colours of the settings when natural colours are off. */
    private void tint(FxParticles.P p, int a, int b) {
        if (natural.on()) {
            p.c1 = a;
            p.c2 = b;
        }
    }

    private FxParticles.P inBody(LivingEntity e) {
        return base(e.getX() + (rnd() - 0.5) * e.getWidth(), e.getY() + rnd() * e.getHeight(), e.getZ() + (rnd() - 0.5) * e.getWidth());
    }

    private void outward(FxParticles.P p, LivingEntity e, double speed, double up) {
        double dx = p.x - e.getX(), dz = p.z - e.getZ();
        double len = Math.max(0.05, Math.hypot(dx, dz));
        double th = rnd() * Math.PI * 2;
        double v = speed * (0.6 + 0.8 * rnd());
        p.vx = (dx / len * 0.6 + Math.cos(th) * 0.4) * v;
        p.vz = (dz / len * 0.6 + Math.sin(th) * 0.4) * v;
        p.vy = up * (0.6 + 0.8 * rnd());
    }

    private int hsv(float h) {
        return 0xFF000000 | java.awt.Color.HSBtoRGB(h, 0.75f, 1f);
    }

    /** The effects that only use particles: the same bounding box, each with its own motion. */
    private void extra(int s, LivingEntity e) {
        int n = (int) count.value;
        double ex = e.getX(), ey = e.getY(), ez = e.getZ();
        double h = e.getHeight();
        switch (s) {
            case 4 -> { // ash: flakes that drift away on the wind, one after another
                for (int i = 0; i < n * 5; i++) {
                    FxParticles.P p = inBody(e);
                    p.life = -(float) (rnd() * 0.8);
                    p.size *= 0.55f;
                    p.max *= 1.5f;
                    p.vx = 0.7 + rnd() * 0.8;
                    p.vy = 0.15 + rnd() * 0.5;
                    p.vz = (rnd() - 0.5) * 0.9;
                    p.ax = (float) (Math.sin(p.y * 7) * 0.6);
                    p.gravity = 0.05f;
                    p.drag = 0.3f;
                    p.grow = -1f;
                    p.spin = (float) (rnd() - 0.5) * 6f;
                    p.shape = FxParticles.Shape.PIXEL;
                    tint(p, 0xFF9A9A9A, 0xFF1E1E1E);
                    fx.spawn(p);
                }
            }
            case 5 -> { // embers: sparks rising and cooling
                for (int i = 0; i < n * 5; i++) {
                    FxParticles.P p = inBody(e);
                    p.life = -(float) (rnd() * 0.5);
                    p.size *= 0.5f;
                    p.vx = (rnd() - 0.5) * 0.8;
                    p.vy = 1.0 + rnd() * 1.4;
                    p.vz = (rnd() - 0.5) * 0.8;
                    p.ax = (float) (Math.sin(p.y * 9 + rnd()) * 1.2);
                    p.gravity = -0.4f;
                    p.drag = 0.5f;
                    p.grow = -0.8f;
                    p.shape = FxParticles.Shape.CIRCLE;
                    tint(p, 0xFFFFE066, 0xFF9A1B05);
                    fx.spawn(p);
                }
                for (int i = 0; i < Math.max(4, n / 3); i++) {
                    FxParticles.P p = inBody(e);
                    p.size *= 2.2f;
                    p.alpha *= 0.35f;
                    p.vy = 0.8 + rnd() * 0.6;
                    p.grow = 1.5f;
                    p.drag = 0.8f;
                    p.max *= 1.4f;
                    p.c1 = 0xFF555555;
                    p.c2 = 0xFF222222;
                    fx.spawn(p);
                }
            }
            case 6, 18 -> { // shatter / ice: fragments fall and bounce
                boolean ice = s == 18;
                for (int i = 0; i < n * 3; i++) {
                    FxParticles.P p = inBody(e);
                    p.life = ice && !instantShards ? -0.55f : 0f;
                    outward(p, e, 3.2, 2.4);
                    p.gravity = 14f;
                    p.drag = 0.4f;
                    p.floorY = ey;
                    p.bounce = 0.35f;
                    p.spin = (float) (rnd() - 0.5) * 16f;
                    p.size *= 0.8f;
                    p.grow = -0.6f;
                    p.shape = FxParticles.Shape.SQUARE;
                    tint(p, ice ? 0xFFD6F4FF : 0xFFBFE9FF, ice ? 0xFF5FA8E8 : 0xFF7FB6E0);
                    fx.spawn(p);
                }
                if (ice) {
                    for (int i = 0; i < n; i++) {
                        FxParticles.P p = inBody(e);
                        p.life = instantShards ? 0f : -0.55f;
                        p.size *= 1.6f;
                        p.alpha *= 0.6f;
                        p.vy = 0.3 + rnd() * 0.5;
                        p.grow = 1f;
                        p.c1 = 0xFFFFFFFF;
                        p.c2 = 0xFFB6E4FF;
                        fx.spawn(p);
                    }
                }
            }
            case 7 -> { // confetti
                for (int i = 0; i < n * 5; i++) {
                    FxParticles.P p = base(ex, ey + h * 0.6, ez);
                    double th = rnd() * Math.PI * 2;
                    double flat = 0.25 + rnd() * 0.5;
                    double v = 4 + rnd() * 3;
                    p.vx = Math.cos(th) * flat * v;
                    p.vz = Math.sin(th) * flat * v;
                    p.vy = (0.6 + rnd() * 0.6) * v;
                    p.gravity = 5f;
                    p.drag = 1.6f;
                    p.spin = (float) (rnd() - 0.5) * 14f;
                    p.max = (float) (lifetime.value * (1.3 + rnd()));
                    p.floorY = ey;
                    p.size *= 0.7f;
                    p.shape = FxParticles.Shape.SQUARE;
                    if (natural.on()) {
                        p.c1 = hsv((float) rnd());
                        p.c2 = p.c1;
                    }
                    fx.spawn(p);
                }
            }
            case 8 -> { // black hole: pulled into a dark core, then a ring
                double cy = ey + h * 0.5;
                for (int i = 0; i < n * 4; i++) {
                    double th = rnd() * Math.PI * 2;
                    double ph = Math.acos(2 * rnd() - 1);
                    double r = 1.4 + rnd() * 0.8;
                    FxParticles.P p = base(ex + r * Math.sin(ph) * Math.cos(th), cy + r * Math.cos(ph), ez + r * Math.sin(ph) * Math.sin(th));
                    p.attract = 3.2f;
                    p.tx = ex;
                    p.ty = cy;
                    p.tz = ez;
                    p.cx = ex;
                    p.cz = ez;
                    p.swirl = 3.5f;
                    p.drag = 0.6f;
                    p.max = 1.0f + (float) rnd() * 0.3f;
                    p.grow = -0.85f;
                    p.size *= 0.7f;
                    tint(p, 0xFFB06BFF, 0xFF08020F);
                    fx.spawn(p);
                }
                FxParticles.P ring = base(ex, cy, ez);
                ring.life = -0.95f;
                ring.size = 0.3f;
                ring.grow = 9f;
                ring.max = 0.55f;
                ring.shape = FxParticles.Shape.RING;
                tint(ring, 0xFFFFFFFF, 0xFFB06BFF);
                fx.spawn(ring);
            }
            case 9 -> { // fireworks: two shells
                for (int shell = 0; shell < 2; shell++) {
                    int c1 = hsv((float) rnd());
                    int c2 = hsv((float) rnd());
                    double cy = ey + h + 1.2 + shell * 0.7;
                    for (int i = 0; i < n * 3; i++) {
                        FxParticles.P p = base(ex, cy, ez);
                        p.life = -(0.2f + shell * 0.45f);
                        double th = rnd() * Math.PI * 2;
                        double ph = Math.acos(2 * rnd() - 1);
                        double v = (shell == 0 ? 5.0 : 3.6) * (0.8 + 0.3 * rnd());
                        p.vx = Math.sin(ph) * Math.cos(th) * v;
                        p.vy = Math.cos(ph) * v;
                        p.vz = Math.sin(ph) * Math.sin(th) * v;
                        p.gravity = 4f;
                        p.drag = 2.2f;
                        p.shape = FxParticles.Shape.STAR;
                        p.spin = (float) (rnd() - 0.5) * 8f;
                        p.size *= 0.9f;
                        if (natural.on()) {
                            p.c1 = rnd() < 0.5 ? c1 : c2;
                            p.c2 = 0xFFFFE9A8;
                        }
                        fx.spawn(p);
                    }
                }
            }
            case 10 -> { // tornado: a rising helix
                for (int i = 0; i < n * 4; i++) {
                    double th = rnd() * Math.PI * 2;
                    double r = 0.35 + rnd() * 0.25;
                    FxParticles.P p = base(ex + Math.cos(th) * r, ey + rnd() * 0.3, ez + Math.sin(th) * r);
                    p.life = -(float) (rnd() * 0.7);
                    p.cx = ex;
                    p.cz = ez;
                    p.swirl = 7f;
                    p.vy = 1.8 + rnd() * 1.2;
                    p.vx = Math.cos(th) * 0.5;
                    p.vz = Math.sin(th) * 0.5;
                    p.size *= 0.7f;
                    p.max *= 1.1f;
                    p.grow = -0.4f;
                    tint(p, 0xFFB6F0FF, 0xFF6A5BFF);
                    fx.spawn(p);
                }
            }
            case 11 -> { // steam: soft white clouds
                for (int i = 0; i < n * 2; i++) {
                    FxParticles.P p = inBody(e);
                    p.size *= 2.6f;
                    p.alpha *= 0.4f;
                    p.vx = (rnd() - 0.5) * 0.5;
                    p.vy = 0.7 + rnd() * 0.8;
                    p.vz = (rnd() - 0.5) * 0.5;
                    p.drag = 0.6f;
                    p.grow = 1.4f;
                    p.max *= 1.2f;
                    tint(p, 0xFFFFFFFF, 0xFFB8BCC4);
                    fx.spawn(p);
                }
            }
            case 12 -> { // petals
                for (int i = 0; i < n * 4; i++) {
                    FxParticles.P p = inBody(e);
                    p.life = -(float) (rnd() * 0.5);
                    p.cx = ex;
                    p.cz = ez;
                    p.swirl = (float) (0.8 + rnd() * 1.2);
                    p.vy = 0.9 + rnd() * 0.8;
                    p.vx = (rnd() - 0.5) * 0.8;
                    p.vz = (rnd() - 0.5) * 0.8;
                    p.gravity = 0.8f;
                    p.drag = 0.7f;
                    p.spin = (float) (rnd() - 0.5) * 6f;
                    p.max = (float) (lifetime.value * (1.6 + rnd()));
                    p.floorY = ey;
                    p.shape = FxParticles.Shape.SQUARE;
                    p.size *= 0.6f;
                    tint(p, 0xFFFFC2DC, 0xFFFF86B3);
                    fx.spawn(p);
                }
            }
            case 13 -> { // a shower of hearts, stars and coins
                for (int i = 0; i < n * 2; i++) {
                    double th = rnd() * Math.PI * 2;
                    double r = Math.sqrt(rnd()) * 1.6;
                    FxParticles.P p = base(ex + Math.cos(th) * r, ey + 2.6 + rnd() * 0.6, ez + Math.sin(th) * r);
                    p.life = -(float) (rnd() * 0.8);
                    p.vy = -1.0;
                    p.gravity = 6f;
                    p.floorY = ey;
                    p.bounce = 0.4f;
                    p.spin = (float) (rnd() - 0.5) * 8f;
                    int kind = random.nextInt(3);
                    p.shape = kind == 0 ? FxParticles.Shape.HEART : kind == 1 ? FxParticles.Shape.STAR : FxParticles.Shape.CIRCLE;
                    tint(p, kind == 0 ? 0xFFFF5C8A : kind == 1 ? 0xFFFFF08A : 0xFFFFC83D, kind == 0 ? 0xFFB3173F : kind == 1 ? 0xFFFFA81F : 0xFFB3820F);
                    fx.spawn(p);
                }
            }
            case 14 -> { // shockwave: flash, two rings and smoke
                FxParticles.P flash = base(ex, ey + h * 0.5, ez);
                flash.size = 0.5f;
                flash.grow = 3f;
                flash.max = 0.2f;
                flash.c1 = 0xFFFFFFFF;
                flash.c2 = 0xFFFFD27A;
                fx.spawn(flash);
                for (int i = 0; i < 2; i++) {
                    FxParticles.P ring = base(ex, ey + 0.1, ez);
                    ring.life = -0.1f * i;
                    ring.size = 0.3f;
                    ring.grow = 11f;
                    ring.max = 0.6f;
                    ring.shape = FxParticles.Shape.RING;
                    tint(ring, 0xFFFFE0A0, 0xFFFF6A2A);
                    fx.spawn(ring);
                }
                for (int i = 0; i < n; i++) {
                    FxParticles.P p = base(ex, ey + 0.3, ez);
                    double th = rnd() * Math.PI * 2;
                    double v = 2 + rnd() * 3;
                    p.vx = Math.cos(th) * v;
                    p.vz = Math.sin(th) * v;
                    p.vy = rnd() * 1.2;
                    p.drag = 2f;
                    p.size *= 2f;
                    p.alpha *= 0.45f;
                    p.grow = 1.2f;
                    p.c1 = 0xFF8A8A8A;
                    p.c2 = 0xFF303030;
                    fx.spawn(p);
                }
            }
            case 15 -> { // a beam of light to the sky
                for (int i = 0; i < 40; i++) {
                    FxParticles.P p = base(ex, ey + i * 0.4, ez);
                    p.size = 0.32f;
                    p.max = 0.7f;
                    p.shape = FxParticles.Shape.SQUARE;
                    p.grow = -0.7f;
                    p.life = -i * 0.008f;
                    tint(p, 0xFFFFFFFF, 0xFFFFD27A);
                    fx.spawn(p);
                }
                for (int i = 0; i < n * 2; i++) {
                    FxParticles.P p = base(ex + (rnd() - 0.5) * 0.5, ey + rnd() * 0.5, ez + (rnd() - 0.5) * 0.5);
                    p.vy = 4 + rnd() * 5;
                    p.drag = 0.3f;
                    p.shape = FxParticles.Shape.STAR;
                    p.size *= 0.7f;
                    tint(p, 0xFFFFFFFF, 0xFFFFC94D);
                    fx.spawn(p);
                }
            }
            case 16 -> { // voxels: a grid of little cubes floats away, bottom first
                double step = 0.16;
                int made = 0;
                for (double y = 0; y < h && made < 900; y += step) {
                    for (double dx = -e.getWidth() / 2; dx < e.getWidth() / 2 && made < 900; dx += step) {
                        for (double dz = -e.getWidth() / 2; dz < e.getWidth() / 2 && made < 900; dz += step) {
                            if (rnd() > Math.min(1.0, n / 40.0)) {
                                continue;
                            }
                            FxParticles.P p = base(ex + dx, ey + y, ez + dz);
                            p.life = -(float) (y / h * 0.5 + rnd() * 0.15);
                            p.vy = 0.8 + rnd() * 2.2;
                            p.vx = (rnd() - 0.5) * 0.6;
                            p.vz = (rnd() - 0.5) * 0.6;
                            p.gravity = -0.3f;
                            p.drag = 0.5f;
                            p.size = (float) (step * 0.62);
                            p.grow = -1f;
                            p.shape = FxParticles.Shape.PIXEL;
                            float t = (float) (y / h);
                            tint(p, 0xFF000000 | ((int) (80 + 120 * t) << 16) | ((int) (200 - 80 * t) << 8) | 0xFF, 0xFFFFFFFF);
                            fx.spawn(p);
                            made++;
                        }
                    }
                }
            }
            case 17 -> { // melt: drips run down from the top
                for (int i = 0; i < n * 3; i++) {
                    FxParticles.P p = inBody(e);
                    double rel = (p.y - ey) / Math.max(h, 0.1);
                    p.life = -(float) ((1 - rel) * 0.9 + rnd() * 0.2);
                    p.vx = (rnd() - 0.5) * 0.2;
                    p.vz = (rnd() - 0.5) * 0.2;
                    p.gravity = 9f;
                    p.floorY = ey;
                    p.size *= 0.55f;
                    p.max *= 1.2f;
                    p.shape = FxParticles.Shape.CIRCLE;
                    tint(p, 0xFFB6F25C, 0xFF3F8A22);
                    fx.spawn(p);
                }
                for (int i = 0; i < n / 2; i++) {
                    FxParticles.P p = base(ex + (rnd() - 0.5) * 0.9, ey + 0.05, ez + (rnd() - 0.5) * 0.9);
                    p.life = -(float) (0.5 + rnd() * 0.5);
                    p.size *= 1.8f;
                    p.alpha *= 0.7f;
                    p.max *= 1.6f;
                    p.shape = FxParticles.Shape.CIRCLE;
                    tint(p, 0xFF7CC23E, 0xFF2F6A1C);
                    fx.spawn(p);
                }
            }
            case 19 -> { // bubbles
                for (int i = 0; i < n * 2; i++) {
                    FxParticles.P p = inBody(e);
                    p.life = -(float) (rnd() * 0.4);
                    p.cx = ex;
                    p.cz = ez;
                    p.swirl = (float) (rnd() - 0.5) * 2f;
                    p.vy = 0.6 + rnd() * 0.8;
                    p.vx = (rnd() - 0.5) * 0.5;
                    p.vz = (rnd() - 0.5) * 0.5;
                    p.size = (float) (size.value * (0.9 + rnd() * 1.6));
                    p.shape = FxParticles.Shape.RING;
                    p.grow = 0.4f;
                    p.max *= 1.3f;
                    tint(p, 0xFFCFF3FF, 0xFF8ED8FF);
                    fx.spawn(p);
                }
            }
            default -> { }
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        float dt = (float) Math.max(0, Math.min(0.1, env.t() - lastT));
        lastT = env.t();
        fx.update(dt);
        if (mcWorldGone()) {
            models.clear();
        }
        if (models.busy()) {
            modelColors.color1 = color1.argb() & 0xFFFFFF;
            modelColors.color2 = color2.argb() & 0xFFFFFF;
            models.render(env, dt, modelColors, (x, y, z) -> base(x, y, z), (st, e, pos) -> {
                if (st == com.example.neurocanvas.fx.KillModels.ICE) {
                    instantShards = true;
                    try {
                        extra(18, e);
                    } finally {
                        instantShards = false;
                    }
                } else if (st == com.example.neurocanvas.fx.KillModels.IMPLODE) {
                    extra(14, e);
                }
            });
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        fx.render(env);
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();

        long now = System.currentTimeMillis();
        bolts.removeIf(b -> now - b.start > BOLT_MS);
        if (bolts.isEmpty()) {
            return;
        }
        for (float[] pass : new float[][] {{5f, 0.55f}, {1.8f, 1f}}) {
            Lines lines = Lines.begin(env, pass[0]);
            for (Bolt b : bolts) {
                float fade = 1f - (now - b.start) / (float) BOLT_MS;
                float flicker = 0.7f + 0.3f * (float) Math.sin((now - b.start) * 0.08);
                int a = Math.round(255 * fade * flicker * pass[1] * alpha.f());
                int col = pass[0] > 3f ? (color1.argb() & 0xFFFFFF) : 0xFFFFFF;
                for (int i = 0; i + 1 < b.x.length; i++) {
                    lines.line(b.x[i], b.y[i], b.z[i], b.x[i + 1], b.y[i + 1], b.z[i + 1], (a << 24) | col);
                }
            }
            lines.end();
        }
    }
}
