package com.example.neurocanvas.module;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/** Gameplay facts shared by all modules: last attacked target, jump ripples, recent player path. */
public final class WorldState {
    /** How long a hit entity stays the target; TargetHUD lets the player change it. */
    public static volatile long targetLifetimeMs = 6000;
    public static final long RIPPLE_LIFETIME_MS = 900;
    public static final int TRAIL_MAX = 64;

    public record Ripple(Vec3d pos, long start) { }

    public LivingEntity target;
    public long targetTime;
    public final List<Ripple> ripples = new ArrayList<>();
    public final List<Vec3d> trail = new ArrayList<>();
    private boolean wasOnGround = true;

    private static final class PendingHit {
        final LivingEntity entity;
        int lastHurt;
        int ticksLeft = 10;

        PendingHit(LivingEntity entity) {
            this.entity = entity;
            this.lastHurt = entity.hurtTime;
        }
    }

    /** Combat events derived from health and inventory changes; all methods are optional. */
    public interface CombatListener {
        default void onDamage(String name, float amount, boolean dealt) { }

        default void onKill(LivingEntity entity) { }

        default void onTotemUsed() { }

        default void onArmorBreak(String piece) { }
    }

    private static final List<CombatListener> COMBAT_LISTENERS = new ArrayList<>();

    public static void addCombatListener(CombatListener listener) {
        COMBAT_LISTENERS.add(listener);
    }

    private LivingEntity trackedTarget;
    private float trackedHealth;
    private float playerHealth = -1f;
    private int prevTotems = -1;
    private int hurtAge = 999;
    private final int[] armorLeft = new int[4];
    private final String[] armorName = new String[4];
    private final boolean[] armorHad = new boolean[4];

    private static final List<Consumer<LivingEntity>> HIT_LISTENERS = new ArrayList<>();
    private final List<PendingHit> pendingHits = new ArrayList<>();

    /** Called when an entity you attacked actually takes damage (its hurt timer jumps up). */
    public static void addHitListener(Consumer<LivingEntity> listener) {
        HIT_LISTENERS.add(listener);
    }

    public void onAttack(Entity entity) {
        if (Teams.ignores(entity)) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            target = living;
            targetTime = System.currentTimeMillis();
            pendingHits.add(new PendingHit(living));
            attacked.removeIf(a -> a.entity == living);
            attacked.add(new Attacked(living, targetTime));
            while (attacked.size() > 12) {
                attacked.remove(0);
            }
        }
    }

    /** Everything you hit in the last few seconds, so a kill counts even when you hit someone else afterwards. */
    private record Attacked(LivingEntity entity, long time) { }

    private final List<Attacked> attacked = new ArrayList<>();

    private void trackCombat(PlayerEntity p) {
        if (target != null) {
            float h = target.getHealth() + target.getAbsorptionAmount();
            if (target != trackedTarget) {
                trackedTarget = target;
                trackedHealth = h;
            } else {
                if (h < trackedHealth - 0.01f) {
                    String name = target.getName().getString();
                    float lost = trackedHealth - h;
                    COMBAT_LISTENERS.forEach(l -> l.onDamage(name, lost, true));
                }
                trackedHealth = h;
            }
        } else {
            trackedTarget = null;
        }

        float ph = p.getHealth() + p.getAbsorptionAmount();
        hurtAge++;
        if (p.hurtTime > 0) {
            hurtAge = 0;
        }
        if (playerHealth >= 0 && ph < playerHealth - 0.01f) {
            float lost = playerHealth - ph;
            hurtAge = 0;
            COMBAT_LISTENERS.forEach(l -> l.onDamage("You", lost, false));
        }
        playerHealth = ph;

        int totems = 0;
        for (ItemStack s : p.getInventory().main) {
            if (s.isOf(Items.TOTEM_OF_UNDYING)) totems += s.getCount();
        }
        for (ItemStack s : p.getInventory().offHand) {
            if (s.isOf(Items.TOTEM_OF_UNDYING)) totems += s.getCount();
        }
        if (prevTotems >= 0 && totems < prevTotems && hurtAge < 40) {
            COMBAT_LISTENERS.forEach(CombatListener::onTotemUsed);
        }
        prevTotems = totems;

        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getInventory().getArmorStack(i);
            if (!s.isEmpty() && s.isDamageable()) {
                armorLeft[i] = s.getMaxDamage() - s.getDamage();
                armorName[i] = s.getName().getString();
                armorHad[i] = true;
            } else {
                if (armorHad[i] && s.isEmpty() && armorLeft[i] <= 2) {
                    String piece = armorName[i];
                    COMBAT_LISTENERS.forEach(l -> l.onArmorBreak(piece));
                }
                armorHad[i] = false;
            }
        }
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null) {
            ripples.clear();
            trail.clear();
            pendingHits.clear();
            attacked.clear();
            target = null;
            return;
        }
        pendingHits.removeIf(h -> {
            if (h.entity.hurtTime > h.lastHurt) {
                HIT_LISTENERS.forEach(l -> l.accept(h.entity));
                return true;
            }
            h.lastHurt = h.entity.hurtTime;
            return --h.ticksLeft <= 0 || !h.entity.isAlive();
        });
        long now = System.currentTimeMillis();
        boolean onGround = mc.player.isOnGround();
        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0) {
            ripples.add(new Ripple(mc.player.getPos(), now));
        }
        wasOnGround = onGround;
        ripples.removeIf(r -> now - r.start > RIPPLE_LIFETIME_MS);
        trackCombat(mc.player);
        long nowMs = System.currentTimeMillis();
        attacked.removeIf(a -> {
            boolean dead = a.entity.isDead();
            // Servers often remove a dead player at once, before its health ever reads zero: gone soon after a hit and close by counts.
            boolean vanished = a.entity.isRemoved() && nowMs - a.time < 4000 && a.entity.squaredDistanceTo(mc.player) < 32 * 32;
            if (dead || vanished) {
                COMBAT_LISTENERS.forEach(l -> l.onKill(a.entity));
                return true;
            }
            return nowMs - a.time > 15000 || a.entity.isRemoved();
        });
        if (target != null) {
            if (target.isDead() || target.isRemoved()) {
                target = null;
            } else if (!target.isAlive() || now - targetTime > targetLifetimeMs) {
                target = null;
            }
        }
        trail.add(mc.player.getPos());
        while (trail.size() > TRAIL_MAX) {
            trail.remove(0);
        }
    }
}
