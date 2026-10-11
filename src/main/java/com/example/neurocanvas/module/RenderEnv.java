package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;

/** Everything a module needs to draw one world frame. Coordinates are camera-relative via {@code m}. */
public record RenderEnv(Matrix4f m, float tickDelta, double t, float camYaw, float camPitch, MinecraftClient mc, WorldState state) {
}
