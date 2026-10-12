package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * A dummy player that exists only on your screen, for testing combat modules. It has health, armour,
 * takes damage with the vanilla formula, gets knocked back, plays a death animation and can respawn.
 * The server never hears about it, so it is safe to use anywhere.
 */
public final class FakePlayer extends Module {
    private static final int ID_BASE = 0x6FFF0000;
    private static final Item[][] ARMOR = {
        {},
        {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
        {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
        {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
        {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}};
    private static final float[] ARMOR_POINTS = {0, 7, 15, 20, 20};
    private static final float[] ARMOR_TOUGHNESS = {0, 0, 0, 8, 12};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Setting maxHealth = add(Setting.number("Max health", 1, 100, 20));
    private final Setting armor = add(Setting.choice("Armor", 0, "None", "Leather", "Iron", "Diamond", "Netherite"));
    private final Setting behavior = add(Setting.choice("Behavior", 0, "Idle", "Strafe", "Follow"));
    private final Setting speed = add(Setting.number("Move speed", 0.1, 1, 0.5));
    private final Setting spawnAt = add(Setting.choice("Spawn at", 0, "In front of me", "Where I look", "On me"));
    private final Setting distance = add(Setting.number("Spawn distance", 1, 10, 3));
    private final Setting reanchor = add(Setting.number("Re-anchor distance", 8, 100, 24));
    private final Setting knockback = add(Setting.bool("Knockback", true));
    private final Setting nameTag = add(Setting.bool("NameTag", false));
    private final Setting instant = add(Setting.bool("Instant respawn", false));
    private final Setting autoRespawn = add(Setting.bool("Auto respawn", true));
    private final Setting respawnDelay = add(Setting.number("Respawn delay (s)", 0, 10, 2));

    private OtherClientPlayerEntity fake;
    private Vec3d spawnPos;
    private ClientWorld spawnWorld;
    private String name = "Dummy";
    private int idCounter;
    private int hurtCooldown;
    private int deadTicks;
    private int respawnTicks = -1;
    private boolean waitingManual;
    private boolean wasEnabled;
    private long ticks;
    private int appliedArmor = -1;
    private double appliedMaxHealth = -1;

    public FakePlayer() {
        super("FakePlayer", Category.COMBAT, "A test dummy only you can see: has health, takes hits, dies and respawns", false);
        aiControllable = false;
    }

    /** Draws the NameTags plate above the dummy (look is set in the NameTags module) when the NameTag switch is on. */
    @Override
    public void renderWorld(RenderEnv env) {
        if (nameTag.on() && fake != null && !fake.isDead()) {
            NameTags tags = NeuroCanvasClient.modules().get(NameTags.class);
            tags.plate(env, fake);
            tags.restoreState();
        }
    }

    public boolean owns(Entity entity) {
        return fake != null && entity == fake;
    }

    public void setName(String newName) {
        name = newName.isBlank() ? "Dummy" : newName;
    }

    /** Removes the dummy and spawns a fresh one in front of you on the next tick. */
    public void respawnHere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        removeFake(mc);
        spawnPos = null;
        waitingManual = false;
        respawnTicks = -1;
    }

    public void healFully() {
        if (fake != null && !fake.isDead()) {
            fake.setHealth((float) maxHealth.value);
        }
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            fake = null;
            wasEnabled = false;
            spawnPos = null;
            return;
        }
        if (!enabled) {
            removeFake(mc);
            wasEnabled = false;
            return;
        }
        if (!wasEnabled) {
            wasEnabled = true;
            waitingManual = false;
            respawnTicks = -1;
            spawnPos = null;
        }
        if (spawnWorld != mc.world) {
            spawnWorld = mc.world;
            spawnPos = null;
        }
        ticks++;
        if (hurtCooldown > 0) {
            hurtCooldown--;
        }
        if (fake != null && (fake.getWorld() != mc.world || fake.isRemoved())) {
            fake = null;
        }

        if (fake == null) {
            if (respawnTicks >= 0) {
                if (--respawnTicks <= 0) {
                    respawnTicks = -1;
                    spawn(mc);
                }
            } else if (!waitingManual) {
                spawn(mc);
            }
            return;
        }

        if (fake.isDead()) {
            if (instant.on()) {
                revive();
            } else if (++deadTicks >= 22) {
                removeFake(mc);
                if (autoRespawn.on()) {
                    respawnTicks = Math.max(1, (int) (respawnDelay.value * 20));
                } else {
                    waitingManual = true;
                }
                return;
            }
        } else {
            deadTicks = 0;
            applyLive();
            behave(mc);
        }
        physics();
    }

    /** Settings that describe the dummy itself take effect on the living dummy right away, without respawning it. */
    private void applyLive() {
        if (appliedArmor != armor.index()) {
            appliedArmor = armor.index();
            Item[] set = ARMOR[appliedArmor];
            for (int i = 0; i < SLOTS.length; i++) {
                fake.equipStack(SLOTS[i], i < set.length ? new ItemStack(set[i]) : ItemStack.EMPTY);
            }
        }
        if (appliedMaxHealth != maxHealth.value) {
            boolean full = appliedMaxHealth < 0 || fake.getHealth() >= appliedMaxHealth - 0.01f;
            appliedMaxHealth = maxHealth.value;
            fake.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(maxHealth.value);
            fake.setHealth(full ? (float) maxHealth.value : Math.min(fake.getHealth(), (float) maxHealth.value));
        }
    }

    private void spawn(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (spawnPos != null && spawnPos.distanceTo(mc.player.getPos()) > reanchor.value) {
            spawnPos = null;
        }
        if (spawnPos == null) {
            spawnPos = pickSpawn(mc);
            mc.player.sendMessage(Text.literal(String.format("[FakePlayer] %s spawned at %.1f %.1f %.1f",
                    name, spawnPos.x, spawnPos.y, spawnPos.z)), false);
        }
        OtherClientPlayerEntity e = new OtherClientPlayerEntity(world, new GameProfile(UUID.randomUUID(), name));
        // The vanilla constructor turns collisions off because network packets normally move such players.
        // We move this one ourselves, so it must collide with the world or it falls through the ground.
        e.noClip = false;
        e.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, mc.player.getYaw() + 180f, 0f);
        e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(maxHealth.value);
        e.setHealth((float) maxHealth.value);
        Item[] set = ARMOR[armor.index()];
        for (int i = 0; i < set.length; i++) {
            e.equipStack(SLOTS[i], new ItemStack(set[i]));
        }
        int id;
        do {
            id = ID_BASE + (idCounter++ & 0xFFFF);
        } while (world.getEntityById(id) != null);
        world.addEntity(id, e);
        fake = e;
        appliedArmor = armor.index();
        appliedMaxHealth = maxHealth.value;
        deadTicks = 0;
        hurtCooldown = 0;
    }

    private Vec3d pickSpawn(MinecraftClient mc) {
        Vec3d eye = mc.player.getEyePos();
        double x;
        double z;
        if (spawnAt.index() == 2) {
            x = mc.player.getX();
            z = mc.player.getZ();
        } else {
            Vec3d look = Vec3d.fromPolar(0f, mc.player.getYaw());
            x = mc.player.getX() + look.x * distance.value;
            z = mc.player.getZ() + look.z * distance.value;
            if (spawnAt.index() == 1) {
                HitResult hit = mc.player.raycast(60.0, 1f, false);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    x = hit.getPos().x;
                    z = hit.getPos().z;
                }
            }
        }
        return new Vec3d(x, groundY(mc, x, z, eye.y), z);
    }

    /** Finds the floor under (x, z) near eye height so the dummy never starts inside or far above the ground. */
    private double groundY(MinecraftClient mc, double x, double z, double eyeY) {
        Vec3d from = new Vec3d(x, eyeY + 1.0, z);
        Vec3d to = new Vec3d(x, eyeY - 12.0, z);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? mc.player.getY() : hit.getPos().y;
    }

    private void removeFake(MinecraftClient mc) {
        if (fake != null && mc.world != null) {
            mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        }
        fake = null;
    }

    /** Instant respawn: stands the same body back up where it fell, with full health. */
    private void revive() {
        fake.setHealth((float) maxHealth.value);
        applyLive();
        fake.deathTime = 0;
        fake.hurtTime = 0;
        fake.setVelocity(Vec3d.ZERO);
        deadTicks = 0;
    }

    private void behave(MinecraftClient mc) {
        Vec3d me = mc.player.getPos();
        Vec3d pos = fake.getPos();
        double dx = me.x - pos.x;
        double dz = me.z - pos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float turned = MathHelper.lerpAngleDegrees(0.35f, fake.getYaw(), yaw);
        fake.setYaw(turned);
        fake.setHeadYaw(turned);
        fake.bodyYaw = turned;

        Vec3d goal = null;
        switch (behavior.index()) {
            case 1 -> {
                double a = ticks * 0.05 * (0.5 + speed.value);
                goal = new Vec3d(me.x + Math.cos(a) * 3.0, pos.y, me.z + Math.sin(a) * 3.0);
            }
            case 2 -> {
                if (Math.hypot(dx, dz) > 2.2) {
                    goal = new Vec3d(me.x, pos.y, me.z);
                }
            }
            default -> { }
        }
        if (goal == null) {
            return;
        }
        double gx = goal.x - pos.x;
        double gz = goal.z - pos.z;
        double len = Math.hypot(gx, gz);
        if (len < 0.15) {
            return;
        }
        double accel = 0.09 * speed.value;
        Vec3d v = fake.getVelocity();
        double vx = v.x + gx / len * accel;
        double vz = v.z + gz / len * accel;
        double cap = 0.12 + 0.2 * speed.value;
        double h = Math.hypot(vx, vz);
        if (h > cap) {
            vx = vx / h * cap;
            vz = vz / h * cap;
        }
        double vy = fake.horizontalCollision && fake.isOnGround() ? 0.42 : v.y;
        fake.setVelocity(vx, vy, vz);
    }

    /** A client-side entity gets no movement from the game, so gravity and collisions are done here. */
    private void physics() {
        Vec3d v = fake.getVelocity();
        double friction = fake.isOnGround() ? 0.546 : 0.91;
        double vy = fake.isOnGround() ? -0.0784 : (v.y - 0.08) * 0.98;
        fake.setVelocity(v.x * friction, vy, v.z * friction);
        fake.move(MovementType.SELF, fake.getVelocity());
    }

    /** Called when the player left-clicks the dummy. Mirrors the vanilla melee damage formula. */
    public void onAttack(PlayerEntity attacker) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (fake == null || fake.isDead() || hurtCooldown > 0 || mc.world == null) {
            return;
        }
        float cooldown = attacker.getAttackCooldownProgress(0.5f);
        float damage = (float) attacker.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * (0.2f + cooldown * cooldown * 0.8f);
        damage += EnchantmentHelper.getAttackDamage(attacker.getMainHandStack(), EntityGroup.DEFAULT) * cooldown;
        boolean crit = cooldown > 0.9f && attacker.fallDistance > 0f && !attacker.isOnGround() && !attacker.isClimbing()
                && !attacker.isTouchingWater() && !attacker.hasStatusEffect(StatusEffects.BLINDNESS)
                && !attacker.hasVehicle() && !attacker.isSprinting();
        if (crit) {
            damage *= 1.5f;
        }
        int level = armor.index();
        damage = DamageUtil.getDamageLeft(damage, ARMOR_POINTS[level], ARMOR_TOUGHNESS[level]);

        hurtCooldown = 10;
        fake.setHealth(Math.max(0f, fake.getHealth() - damage));
        fake.hurtTime = 10;
        fake.maxHurtTime = 10;

        if (knockback.on()) {
            double kx = attacker.getX() - fake.getX();
            double kz = attacker.getZ() - fake.getZ();
            double len = Math.max(0.0001, Math.hypot(kx, kz));
            double strength = 0.4 + (attacker.isSprinting() ? 0.5 : 0.0);
            Vec3d v = fake.getVelocity();
            fake.setVelocity(v.x / 2 - kx / len * strength, fake.isOnGround() ? Math.min(0.4, v.y / 2 + strength) : v.y,
                    v.z / 2 - kz / len * strength);
        }
        mc.world.playSound(fake.getX(), fake.getY(), fake.getZ(),
                fake.isDead() ? SoundEvents.ENTITY_PLAYER_DEATH : SoundEvents.ENTITY_PLAYER_HURT,
                SoundCategory.PLAYERS, 1f, 1f, false);
    }
}
