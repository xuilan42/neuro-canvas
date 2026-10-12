package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

/**
 * Ambient particles around you. They appear at random spots inside a radius, drift wherever they like, bounce off
 * blocks, and fade out once they get too far from you or too old. Their shapes are drawn by a shader.
 */
public final class Particles extends Module {
    private static final String[] SHAPES = {"Glow (firefly)", "Star", "Sparkle", "Dot", "Ring", "Diamond", "Heart", "Snowflake",
        "Mix: glow + star + sparkle", "Mix: all"};

    private static final class P {
        double x, y, z, vx, vy, vz;
        double dirX, dirY, dirZ;
        float age, life, size, rot, spin, phase, rate, mix, turn;
        int shape;
    }

    private final Setting shape = add(Setting.choice("Shape", 0, SHAPES));
    private final Setting count = add(Setting.number("Count", 1, 400, 70));
    private final Setting spawnRadius = add(Setting.number("Spawn radius", 1, 20, 6));
    private final Setting fadeRadius = add(Setting.number("Fade-out radius", 2, 30, 9));
    private final Setting size = add(Setting.number("Size", 0.02, 0.6, 0.12));
    private final Setting sizeVariation = add(Setting.number("Size variation", 0, 1, 0.5));
    private final Setting speed = add(Setting.number("Speed", 0, 4, 0.7));
    private final Setting wander = add(Setting.number("Wander", 0, 3, 1));
    private final Setting gravity = add(Setting.number("Gravity", -1, 1, 0));
    private final Setting lifetime = add(Setting.number("Lifetime (s)", 1, 40, 10));
    private final Setting collide = add(Setting.bool("Bounce off blocks", true));
    private final Setting bounce = add(Setting.number("Bounciness", 0, 1, 0.7));
    private final Setting colorMode = add(Setting.choice("Color mode", 0, "Gradient", "Random mix", "Rainbow", "Single"));
    private final Setting color1 = add(Setting.color("Color 1", 0.12));
    private final Setting color2 = add(Setting.color("Color 2", 0.6));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.9));
    private final Setting twinkle = add(Setting.number("Twinkle", 0, 1, 0.4));
    private final Setting spin = add(Setting.bool("Spin", true));
    private final Setting glow = add(Setting.bool("Glow blending", true));

    private final List<P> list = new ArrayList<>();
    private final Random random = new Random();
    private ShaderProgram program;
    private ShaderProgram programAdd;
    private boolean failed;
    private boolean reported;
    private double lastT;
    private double spawnCarry;
    private Vec3d lastCenter;

    public Particles() {
        super("Particles", Category.VISUALS, "Glowing particles drifting around you: stars, fireflies, sparkles and more", false);
    }

    private int pickShape() {
        int s = shape.index();
        return switch (s) {
            case 8 -> random.nextInt(3);
            case 9 -> random.nextInt(8);
            default -> s;
        };
    }

    private Vec3d randomDirection() {
        double x, y, z, len;
        do {
            x = random.nextGaussian();
            y = random.nextGaussian();
            z = random.nextGaussian();
            len = Math.sqrt(x * x + y * y + z * z);
        } while (len < 1e-6);
        return new Vec3d(x / len, y / len, z / len);
    }

    private void spawn(ClientWorld world, Vec3d center) {
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3d dir = randomDirection();
            double r = spawnRadius.value * (0.2 + 0.8 * random.nextDouble());
            Vec3d at = center.add(dir.multiply(r));
            BlockPos bp = BlockPos.ofFloored(at);
            BlockState state = world.getBlockState(bp);
            if (!state.getCollisionShape(world, bp).isEmpty()) {
                continue;
            }
            P p = new P();
            p.x = at.x;
            p.y = at.y;
            p.z = at.z;
            Vec3d v = randomDirection().multiply(speed.value * 0.5);
            p.vx = v.x;
            p.vy = v.y;
            p.vz = v.z;
            p.life = (float) (lifetime.value * (0.7 + 0.6 * random.nextDouble()));
            p.size = (float) (size.value * (1.0 + sizeVariation.value * (random.nextDouble() - 0.5) * 1.6));
            p.rot = random.nextFloat() * 6.2832f;
            p.spin = (float) ((random.nextDouble() - 0.5) * 2.0);
            p.phase = random.nextFloat() * 6.2832f;
            p.rate = 1.5f + random.nextFloat() * 4f;
            p.mix = random.nextFloat();
            p.shape = pickShape();
            p.turn = 0f;
            list.add(p);
            return;
        }
    }

    private void step(ClientWorld world, ClientPlayerEntity player, Vec3d center, double dt) {
        int target = (int) count.value;
        double life = Math.max(1.0, lifetime.value);
        double rate = target / (life * 0.6);
        if (list.size() < target * 0.3) {
            rate *= 5;
        }
        spawnCarry += rate * dt;
        while (spawnCarry >= 1.0) {
            spawnCarry -= 1.0;
            if (list.size() < target) {
                spawn(world, center);
            }
        }
        double fade = Math.max(fadeRadius.value, spawnRadius.value + 0.5);
        double fadeSq = fade * fade;
        double bounceK = bounce.value;
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.age += (float) dt;
            double dx = p.x - center.x, dy = p.y - center.y, dz = p.z - center.z;
            if (p.age >= p.life || dx * dx + dy * dy + dz * dz > fadeSq || list.size() > target + 20) {
                list.remove(i);
                continue;
            }
            p.turn -= (float) dt;
            if (p.turn <= 0f) {
                Vec3d d = randomDirection();
                p.dirX = d.x;
                p.dirY = d.y * 0.7;
                p.dirZ = d.z;
                p.turn = wander.value <= 0.01 ? 1e9f : (float) ((0.6 + random.nextDouble() * 1.6) / (0.5 + wander.value * 0.5));
            }
            double want = speed.value * (0.35 + 0.65 * p.mix);
            double k = Math.min(1.0, dt * (0.6 + wander.value * 1.4));
            p.vx += (p.dirX * want - p.vx) * k;
            p.vy += (p.dirY * want - p.vy) * k - gravity.value * dt * 2.0;
            p.vz += (p.dirZ * want - p.vz) * k;
            if (spin.on()) {
                p.rot += p.spin * (float) dt;
            }
            Vec3d from = new Vec3d(p.x, p.y, p.z);
            Vec3d to = from.add(p.vx * dt, p.vy * dt, p.vz * dt);
            if (collide.on()) {
                BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    Vec3d n = Vec3d.of(hit.getSide().getVector());
                    double dot = p.vx * n.x + p.vy * n.y + p.vz * n.z;
                    if (dot < 0) {
                        // Mirror the velocity about the surface; "bounciness" keeps that share of the speed.
                        p.vx -= n.x * (1 + bounceK) * dot;
                        p.vy -= n.y * (1 + bounceK) * dot;
                        p.vz -= n.z * (1 + bounceK) * dot;
                    }
                    Vec3d at = hit.getPos().add(n.multiply(0.04));
                    p.x = at.x;
                    p.y = at.y;
                    p.z = at.z;
                    // Head off in a new direction soon, so a particle does not keep pushing into the wall.
                    p.turn = Math.min(p.turn, 0.2f);
                    continue;
                }
            }
            p.x = to.x;
            p.y = to.y;
            p.z = to.z;
        }
    }

    private float[] colorOf(P p, float ageFrac, double t) {
        int c1 = color1.argb();
        int c2 = color2.argb();
        float[] a = {((c1 >> 16) & 0xFF) / 255f, ((c1 >> 8) & 0xFF) / 255f, (c1 & 0xFF) / 255f, (c1 >>> 24) / 255f};
        float[] b = {((c2 >> 16) & 0xFF) / 255f, ((c2 >> 8) & 0xFF) / 255f, (c2 & 0xFF) / 255f, (c2 >>> 24) / 255f};
        switch (colorMode.index()) {
            case 3:
                return a;
            case 2: {
                int rgb = java.awt.Color.HSBtoRGB((float) ((p.mix + t * 0.08) % 1.0), 0.75f, 1f);
                return new float[] {((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a[3]};
            }
            default: {
                float f = colorMode.index() == 1 ? p.mix : ageFrac;
                return new float[] {MathHelper.lerp(f, a[0], b[0]), MathHelper.lerp(f, a[1], b[1]),
                    MathHelper.lerp(f, a[2], b[2]), MathHelper.lerp(f, a[3], b[3])};
            }
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientWorld world = mc.world;
        ClientPlayerEntity player = mc.player;
        if (world == null || player == null || failed) {
            return;
        }
        double dt = MathHelper.clamp(env.t() - lastT, 0.0, 0.1);
        lastT = env.t();
        Vec3d center = EffectDraw.lerpPos(player, env.tickDelta()).add(0, player.getHeight() * 0.5, 0);
        double fade = Math.max(fadeRadius.value, spawnRadius.value + 0.5);
        if (lastCenter != null && lastCenter.squaredDistanceTo(center) > fade * fade * 4) {
            list.clear();
        }
        lastCenter = center;
        step(world, player, center, dt);
        if (list.isEmpty()) {
            return;
        }
        {
            program = com.example.neurocanvas.fx.Shaders.get("neuro_particle");
            programAdd = com.example.neurocanvas.fx.Shaders.get("neuro_particle_add");
        }
        ShaderProgram shader = glow.on() ? programAdd : program;
        if (shader == null) {
            if (!reported) {
                reported = true;
                String why = com.example.neurocanvas.fx.Shaders.error(glow.on() ? "neuro_particle_add" : "neuro_particle");
                player.sendMessage(Text.literal("[Particles] the shader could not be built (" + why
                        + "). Showing plain squares instead. Press \"Precompile shaders\" in Interface for the full report."), false);
            }
            drawFallback(env, center, fade);
            return;
        }
        try {
            draw(env, center, fade, shader);
        } catch (Throwable e) {
            program = null;
            programAdd = null;
            if (!reported) {
                reported = true;
                player.sendMessage(Text.literal("[Particles] drawing failed (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")."), false);
            }
        }
    }

    /** Used when the shader cannot be built: every particle is a plain coloured square, so something is always visible. */
    private void drawFallback(RenderEnv env, Vec3d center, double fade) {
        double t = env.t();
        double yaw = Math.toRadians(env.camYaw());
        double pitch = Math.toRadians(env.camPitch());
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float fx = -sy * cp, fy = -sp, fz = cy * cp;
        float rx = cy, rz = sy;
        float ux = fy * sy, uy = fz * cy - fx * sy, uz = -fy * cy;
        Matrix4f m = env.m();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        double fadeStart = fade * 0.72;
        for (P p : list) {
            float ageFrac = p.age / p.life;
            double dist = Math.sqrt((p.x - center.x) * (p.x - center.x) + (p.y - center.y) * (p.y - center.y) + (p.z - center.z) * (p.z - center.z));
            float a = alpha.f() * MathHelper.clamp(ageFrac / 0.15f, 0f, 1f) * MathHelper.clamp((1f - ageFrac) / 0.25f, 0f, 1f);
            a *= (float) (1.0 - MathHelper.clamp((dist - fadeStart) / (fade - fadeStart), 0.0, 1.0));
            float[] c = colorOf(p, ageFrac, t);
            a *= c[3];
            if (a <= 0.004f) {
                continue;
            }
            for (int k = 0; k < 4; k++) {
                float cu = k == 0 || k == 3 ? -1f : 1f;
                float cv = k < 2 ? -1f : 1f;
                b.vertex(m, (float) p.x + (rx * cu + ux * cv) * p.size, (float) p.y + (uy * cv) * p.size,
                        (float) p.z + (rz * cu + uz * cv) * p.size).color(c[0], c[1], c[2], a).next();
            }
        }
        tess.draw();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        com.example.neurocanvas.fx.Shaders.finish();
    }

    private void draw(RenderEnv env, Vec3d center, double fade, ShaderProgram shader) {
        double t = env.t();
        double yaw = Math.toRadians(env.camYaw());
        double pitch = Math.toRadians(env.camPitch());
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float fx = -sy * cp, fy = -sp, fz = cy * cp;
        float rx = cy, rz = sy;
        float ux = fy * sy, uy = fz * cy - fx * sy, uz = -fy * cy;
        Vec3d cam = env.mc().gameRenderer.getCamera().getPos();
        Matrix4f m = env.m();

        List<List<P>> byShape = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            byShape.add(new ArrayList<>());
        }
        for (P p : list) {
            byShape.get(p.shape).add(p);
        }

        // The blend mode comes from the shader's own json (alpha or additive); the game applies it when the shader is bound.
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        GlUniform shapeUniform = shader.getUniform("Shape");
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        double fadeStart = fade * 0.72;
        for (int s = 0; s < 8; s++) {
            List<P> group = byShape.get(s);
            if (group.isEmpty()) {
                continue;
            }
            if (!glow.on()) {
                group.sort(Comparator.comparingDouble((P p) -> -((p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z))));
            }
            if (shapeUniform != null) {
                shapeUniform.set((float) s);
            }
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (P p : group) {
                float ageFrac = p.age / p.life;
                double dist = Math.sqrt((p.x - center.x) * (p.x - center.x) + (p.y - center.y) * (p.y - center.y) + (p.z - center.z) * (p.z - center.z));
                float a = alpha.f();
                a *= MathHelper.clamp(ageFrac / 0.15f, 0f, 1f) * MathHelper.clamp((1f - ageFrac) / 0.25f, 0f, 1f);
                a *= (float) (1.0 - MathHelper.clamp((dist - fadeStart) / (fade - fadeStart), 0.0, 1.0));
                double camDist = Math.sqrt((p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z));
                a *= (float) MathHelper.clamp((camDist - 0.4) / 0.8, 0.0, 1.0);
                if (twinkle.value > 0.001) {
                    a *= 1f - twinkle.f() * (0.5f + 0.5f * (float) Math.sin(t * p.rate + p.phase));
                }
                if (a <= 0.004f) {
                    continue;
                }
                float[] c = colorOf(p, ageFrac, t);
                a *= c[3];
                float cr = (float) Math.cos(p.rot), sr = (float) Math.sin(p.rot);
                for (int k = 0; k < 4; k++) {
                    float cu = k == 0 || k == 3 ? -1f : 1f;
                    float cv = k < 2 ? -1f : 1f;
                    float px = (float) p.x + (rx * cu + ux * cv) * p.size;
                    float py = (float) p.y + (uy * cv) * p.size;
                    float pz = (float) p.z + (rz * cu + uz * cv) * p.size;
                    b.vertex(m, px, py, pz).texture(cu * cr - cv * sr, cu * sr + cv * cr).color(c[0], c[1], c[2], a).next();
                }
            }
            tess.draw();
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        com.example.neurocanvas.fx.Shaders.finish();
    }
}
