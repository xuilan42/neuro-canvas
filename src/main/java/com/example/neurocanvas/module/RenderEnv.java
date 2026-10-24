package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Everything a module needs to draw one world frame. {@code m} places absolute world coordinates (the camera offset is already in it);
 * {@code view} has no camera offset, for effects that compute camera-relative coordinates themselves, and {@code cam} is the camera position.
 */
public record RenderEnv(Matrix4f m, Matrix4f view, Vec3d cam, float tickDelta, double t, float camYaw, float camPitch, MinecraftClient mc, WorldState state) {
}
