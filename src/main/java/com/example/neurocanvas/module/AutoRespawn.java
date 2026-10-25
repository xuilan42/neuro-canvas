package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;

/** Presses "Respawn" for you after you die. */
public final class AutoRespawn extends Module {
    private final Setting delay = add(Setting.number("Delay (s)", 0, 5, 0.5));

    private int deadTicks;

    public AutoRespawn() {
        super("AutoRespawn", Category.MISC, "Respawn automatically after dying", false);
        risky = true;
        aiControllable = false;
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.player == null) {
            deadTicks = 0;
            return;
        }
        if (mc.player.isDead()) {
            if (++deadTicks >= (int) (delay.value * 20)) {
                mc.player.requestRespawn();
                deadTicks = 0;
            }
        } else {
            deadTicks = 0;
        }
    }
}
