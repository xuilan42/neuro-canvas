package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.EffectSpec;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import java.util.Comparator;
import java.util.Random;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A glowing ribbon dragged behind your hand and held item as you move and turn. */
public final class HandTrails extends Module {
    private static final class P {
        double x, y, z, vx, vy, vz;
        float age, life, size, rot, spin, seed;
    }

    private final Setting hand = add(Setting.choice("Hand", 0, "Main", "Off"));
    private final Setting emit = add(Setting.choice("Emit", 0, "Always", "While the hand moves"));
    private final Setting density = add(Setting.number("Puffs per second", 2, 120, 30));
    private final Setting lifetime = add(Setting.number("Lifetime (s)", 0.3, 6, 1.6));
    private final Setting size = add(Setting.number("Puff size", 0.03, 0.6, 0.16));
    private final Setting grow = add(Setting.number("Growth", 0, 4, 1.8));
    private final Setting rise = add(Setting.number("Rise speed", -0.5, 1.5, 0.35));
    private final Setting turbulence = add(Setting.number("Turbulence", 0, 2, 0.6));
    private final Setting itemLength = add(Setting.number("Emit along item", 0, 1.5, 0.6));
    private final Setting followSwing = add(Setting.bool("Follow swing", true));
    private final Setting color1 = add(Setting.color("Color 1", 0.75));
    private final Setting color2 = add(Setting.color("Color 2", 0.55));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.5));
    private final Setting glow = add(Setting.bool("Glow", true));
    private final Setting glowStrength = add(Setting.number("Glow strength", 0.3, 3, 1.4));

    private final List<P> list = new ArrayList<>();
    private final Random random = new Random();
    private double lastT;
    private double carry;
    private Vec3d lastHand;

    public HandTrails() {
        super("HandTrails", Category.VISUALS, "Smoke that drifts off your hand and item, with an optional glow", false);
    }

    /**
     * Where the held item is, built the way the game places it in first person: a point to the side, down and in front of the camera,
     * pushed along the swing arc while you attack, and lagging a little behind your turning like the hand does.
     */
    private Vec3d[] handSegment(ClientPlayerEntity p, float tickDelta) {
        Vec3d eye = p.getCameraPosVec(tickDelta);
        double yaw = Math.toRadians(p.getYaw(tickDelta));
        double pitch = Math.toRadians(p.getPitch(tickDelta));
        Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(fwd);
        double side = p.getMainArm() == Arm.RIGHT ? 1 : -1;
        if (hand.index() == 1) {
            side = -side;
        }
        double swing = followSwing.on() && hand.index() == 0 ? p.getHandSwingProgress(tickDelta) : 0;
        double sq = Math.sqrt(swing);
        double sx = -0.4 * Math.sin(sq * Math.PI);
        double sy = 0.2 * Math.sin(sq * Math.PI * 2);
        double sz = -0.2 * Math.sin(swing * Math.PI);
        double lagYaw = Math.toRadians(MathHelper.wrapDegrees(p.getYaw(tickDelta) - MathHelper.lerp(tickDelta, p.lastRenderYaw, p.renderYaw))) * 0.1;
        double lagPitch = Math.toRadians(p.getPitch(tickDelta) - MathHelper.lerp(tickDelta, p.lastRenderPitch, p.renderPitch)) * 0.1;
        Vec3d base = eye
                .add(right.multiply(0.5 * side + sx * side + lagYaw * -1.2))
                .add(up.multiply(-0.42 + sy + lagPitch * -1.2))
                .add(fwd.multiply(0.62 - sz));
        double a = 0.75 + Math.sin(sq * Math.PI) * 1.1;
        Vec3d dir = up.multiply(Math.cos(a) * 0.8).add(fwd.multiply(Math.sin(a) * 0.6)).add(right.multiply(-0.15 * side));
        Vec3d tip = base.add(dir.multiply(0.7));
        return new Vec3d[] {base, tip};
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            list.clear();
            lastHand = null;
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) {
            return;
        }
        double dt = MathHelper.clamp(env.t() - lastT, 0.0, 0.1);
        lastT = env.t();
        Vec3d[] seg = handSegment(player, env.tickDelta());
        Vec3d mid = seg[0].add(seg[1]).multiply(0.5);
        double moved = lastHand == null ? 0 : mid.distanceTo(lastHand);
        lastHand = mid;
        double rate = density.value;
        if (emit.index() == 1) {
            rate *= MathHelper.clamp(moved / Math.max(dt, 1e-3) * 0.8, 0.0, 2.0);
        }
        carry += rate * dt;
        int spawn = Math.min((int) carry, 40);
        carry -= (int) carry;
        Vec3d along = seg[1].subtract(seg[0]).normalize();
        double len = itemLength.value;
        for (int i = 0; i < spawn && list.size() < 700; i++) {
            P q = new P();
            double f = random.nextDouble() * len;
            q.x = seg[0].x + along.x * f + (random.nextDouble() - 0.5) * 0.04;
            q.y = seg[0].y + along.y * f + (random.nextDouble() - 0.5) * 0.04;
            q.z = seg[0].z + along.z * f + (random.nextDouble() - 0.5) * 0.04;
            q.vx = (random.nextDouble() - 0.5) * 0.12;
            q.vy = (random.nextDouble() - 0.2) * 0.1;
            q.vz = (random.nextDouble() - 0.5) * 0.12;
            q.life = (float) (lifetime.value * (0.7 + 0.6 * random.nextDouble()));
            q.size = (float) (size.value * (0.7 + 0.6 * random.nextDouble()));
            q.rot = random.nextFloat() * 6.2832f;
            q.spin = (random.nextFloat() - 0.5f) * 1.2f;
            q.seed = random.nextFloat() * 100f;
            list.add(q);
        }
        double t = env.t();
        for (java.util.Iterator<P> it = list.iterator(); it.hasNext(); ) {
            P q = it.next();
            q.age += (float) dt;
            if (q.age >= q.life) {
                it.remove();
                continue;
            }
            double w = turbulence.value * 0.5;
            q.vx += Math.sin(t * 1.7 + q.seed) * w * dt;
            q.vz += Math.cos(t * 1.3 + q.seed * 1.7) * w * dt;
            q.vy += (rise.value - q.vy) * 1.2 * dt;
            q.vx *= Math.max(0.0, 1.0 - 1.5 * dt);
            q.vz *= Math.max(0.0, 1.0 - 1.5 * dt);
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.z += q.vz * dt;
            q.rot += q.spin * (float) dt;
        }
        if (list.isEmpty()) {
            return;
        }
        draw(env);
    }

    private void draw(RenderEnv env) {
        ShaderProgram shader = com.example.neurocanvas.fx.Shaders.get(glow.on() ? "neuro_particle_add" : "neuro_particle");
        if (shader == null) {
            return;
        }
        double yaw = Math.toRadians(env.camYaw());
        double pitch = Math.toRadians(env.camPitch());
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float fx = -sy * cp, fy = -sp, fz = cy * cp;
        float rx = cy, rz = sy;
        float ux = fy * sy, uy = fz * cy - fx * sy, uz = -fy * cy;
        Vec3d cam = env.mc().gameRenderer.getCamera().getPos();
        Matrix4f m = env.m();
        EffectSpec spec = new EffectSpec();
        spec.color1 = color1.argb() & 0xFFFFFF;
        spec.color2 = color2.argb() & 0xFFFFFF;
        float base = alpha.f() * ((color1.argb() >>> 24) / 255f);

        List<P> sorted = new ArrayList<>(list);
        if (!glow.on()) {
            sorted.sort(Comparator.comparingDouble((P p) -> -((p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z))));
        }
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        com.example.neurocanvas.fx.Shaders.setScale(glowStrength.f());
        com.example.neurocanvas.fx.Shaders.applyBrightness(shader);
        com.example.neurocanvas.fx.Shaders.setScale(1f);
        GlUniform shapeUniform = shader.getUniform("Shape");
        if (shapeUniform != null) {
            shapeUniform.set(8f);
        }
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (P p : sorted) {
            float f = p.age / p.life;
            float a = base * MathHelper.clamp(f / 0.12f, 0f, 1f) * (1f - f) * (1f - f);
            double camDist = Math.sqrt((p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z));
            a *= (float) MathHelper.clamp((camDist - 0.12) / 0.35, 0.0, 1.0);
            if (a <= 0.004f) {
                continue;
            }
            float[] c = EffectDraw.blend(spec, f, 1f);
            a *= c[3];
            float s = p.size * (1f + (float) grow.value * f);
            float cr = (float) Math.cos(p.rot), sr = (float) Math.sin(p.rot);
            for (int k = 0; k < 4; k++) {
                float cu = k == 0 || k == 3 ? -1f : 1f;
                float cv = k < 2 ? -1f : 1f;
                float px = (float) p.x + (rx * cu + ux * cv) * s;
                float py = (float) p.y + (uy * cv) * s;
                float pz = (float) p.z + (rz * cu + uz * cv) * s;
                b.vertex(m, px, py, pz).texture(cu * cr - cv * sr, cu * sr + cv * cr).color(c[0], c[1], c[2], a).next();
            }
        }
        tess.draw();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        com.example.neurocanvas.fx.Shaders.finish();
    }
}
