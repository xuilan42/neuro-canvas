package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;

/** Keeps you sprinting while you walk forward, so you do not have to hold the sprint key. */
public final class ToggleSprint extends Module {
    private final Setting inWater = add(Setting.bool("Sprint in water", false));

    public ToggleSprint() {
        super("ToggleSprint", Category.MISC, "Sprint automatically while moving forward", false);
        risky = true;
        aiControllable = false;
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null) {
            return;
        }
        boolean canSprint = p.input.movementForward > 0.8f
                && !p.isSneaking()
                && !p.horizontalCollision
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying)
                && (inWater.on() || !p.isTouchingWater());
        if (canSprint) {
            p.setSprinting(true);
        }
    }
}
