package com.example.neurocanvas.module;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;

/** Stops chosen things from being drawn: particles, weather, screen overlays, dropped items and more. Helps FPS and clarity. */
public final class NoRender extends Module {
    private final Setting particles = add(Setting.bool("Particles", true));
    private final Setting weather = add(Setting.bool("Weather", true));
    private final Setting fire = add(Setting.bool("Fire overlay", true));
    private final Setting water = add(Setting.bool("Underwater overlay", false));
    private final Setting items = add(Setting.bool("Dropped items", false));
    private final Setting xp = add(Setting.bool("Experience orbs", false));
    private final Setting armorStands = add(Setting.bool("Armor stands", false));

    public NoRender() {
        super("NoRender", Category.WORLD, "Hide particles, weather, overlays and some entities", false);
    }

    public boolean hidesParticles() { return enabled && particles.on(); }
    public boolean hidesWeather() { return enabled && weather.on(); }
    public boolean hidesFire() { return enabled && fire.on(); }
    public boolean hidesWater() { return enabled && water.on(); }

    public boolean hides(Entity e) {
        return enabled && (items.on() && e instanceof ItemEntity
                || xp.on() && e instanceof ExperienceOrbEntity
                || armorStands.on() && e instanceof ArmorStandEntity);
    }
}
