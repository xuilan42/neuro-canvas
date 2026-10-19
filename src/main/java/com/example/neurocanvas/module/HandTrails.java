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
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Vector3f;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A glowing ribbon dragged behind your hand and held item as you move and turn. */
public final class HandTrails extends Module {
    private static final class P {
        double x, y, z, vx, vy, vz;
        float age, life, size, rot, spin, seed;
    }

    private final Setting hand = add(Setting.choice("Hand", 0, "Main", "Off", "Both"));
    private final Setting emit = add(Setting.choice("Emit", 0, "Always", "While the hand moves"));
    private final Setting density = add(Setting.number("Puffs per second", 2, 120, 30));
    private final Setting lifetime = add(Setting.number("Lifetime (s)", 0.3, 6, 1.6));
    private final Setting size = add(Setting.number("Puff size", 0.03, 0.6, 0.16));
    private final Setting grow = add(Setting.number("Growth", 0, 4, 1.8));
    private final Setting rise = add(Setting.number("Rise speed", -0.5, 1.5, 0.35));
    private final Setting turbulence = add(Setting.number("Turbulence", 0, 2, 0.6));
    private final Setting followSwing = add(Setting.bool("Follow swing", true));
    private final Setting color1 = add(Setting.color("Color 1", 0.75));
    private final Setting color2 = add(Setting.color("Color 2", 0.55));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.5));
    private final Setting glow = add(Setting.bool("Glow", true));
    private final Setting glowStrength = add(Setting.number("Glow strength", 0.3, 3, 1.4));

    /** One hand as it was drawn last frame: the item's model-to-view matrix (view model and swing animation included) and where on the item smoke may start. */
    private static final class Slot {
        Matrix4f matrix;
        List<Vector3f> points;
        boolean solid;
        long time;
    }

    private final Slot[] slots = new Slot[2];
    private Item cachedItem;
    private Sprite cachedSprite;
    private List<Vector3f> cachedPoints;
    private boolean cachedSolid;

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

    /** Called while the game draws the held item: the matrix on the stack is exactly where the item is on screen. */
    public void captureItem(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || entity != mc.player || mc.world == null || stack.isEmpty()
                || mode != ModelTransformationMode.FIRST_PERSON_RIGHT_HAND && mode != ModelTransformationMode.FIRST_PERSON_LEFT_HAND) {
            return;
        }
        try {
            boolean main = (mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) == (mc.player.getMainArm() == Arm.RIGHT);
            int want = hand.index();
            if (want == 0 && !main || want == 1 && main) {
                return;
            }
            BakedModel model = mc.getItemRenderer().getModel(stack, mc.world, mc.player, 0);
            MatrixStack ms = new MatrixStack();
            ms.multiplyPositionMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
            model.getTransformation().getTransformation(mode).apply(leftHanded, ms);
            ms.translate(-0.5f, -0.5f, -0.5f);
            Slot slot = new Slot();
            slot.matrix = new Matrix4f(ms.peek().getPositionMatrix());
            Sprite sprite = model.getParticleSprite();
            if (stack.getItem() != cachedItem || sprite != cachedSprite) {
                cachedItem = stack.getItem();
                cachedSprite = sprite;
                cachedSolid = model.hasDepth() && !model.isBuiltin();
                cachedPoints = cachedSolid ? List.of() : flatPoints(sprite, model.isBuiltin());
            }
            slot.points = cachedPoints;
            slot.solid = cachedSolid;
            slot.time = System.nanoTime() / 1_000_000L;
            slots[main ? 0 : 1] = slot;
        } catch (Throwable ignored) {
            // The ribbon falls back to the estimated hand position.
        }
    }

    /** The opaque pixels of a flat item's texture, as points of the model (the sprite is drawn one pixel thick in the middle of the block). */
    private static List<Vector3f> flatPoints(Sprite sprite, boolean builtin) {
        List<Vector3f> out = new ArrayList<>();
        try {
            if (!builtin && sprite != null) {
                NativeImage img = ((com.example.neurocanvas.mixin.SpriteContentsAccessor) sprite.getContents()).neurocanvas$image();
                int w = sprite.getContents().getWidth();
                int h = sprite.getContents().getHeight();
                if (img != null) {
                    for (int y = 0; y < Math.min(h, img.getHeight()); y++) {
                        for (int x = 0; x < Math.min(w, img.getWidth()); x++) {
                            if (((img.getColor(x, y) >>> 24) & 0xFF) > 40) {
                                out.add(new Vector3f((x + 0.5f) / w, 1f - (y + 0.5f) / h, 0.5f));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            out.clear();
        }
        if (out.isEmpty()) {
            for (int i = 0; i <= 8; i++) {
                out.add(new Vector3f(0.2f + 0.075f * i, 0.2f + 0.075f * i, 0.5f));
            }
        }
        return out;
    }

    private Vec3d toWorld(Slot s, Vector3f local, Camera camera) {
        Vector3f v = s.matrix.transformPosition(local, new Vector3f());
        v.rotate(camera.getRotation());
        Vec3d c = camera.getPos();
        return new Vec3d(c.x + v.x, c.y + v.y, c.z + v.z);
    }

    private Vec3d sample(Slot s, Camera camera) {
        Vector3f local = s.solid ? new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat())
                : s.points.get(random.nextInt(s.points.size()));
        return toWorld(s, local, camera);
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            list.clear();
            lastHand = null;
            slots[0] = null;
            slots[1] = null;
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
        Camera camera = mc.gameRenderer.getCamera();
        long nowMs = System.nanoTime() / 1_000_000L;
        List<Slot> live = new ArrayList<>(2);
        for (Slot s : slots) {
            if (s != null && nowMs - s.time < 120) {
                live.add(s);
            }
        }
        Vec3d[] seg = handSegment(player, env.tickDelta());
        Vec3d mid = live.isEmpty() ? seg[0].add(seg[1]).multiply(0.5) : toWorld(live.get(0), new Vector3f(0.5f, 0.5f, 0.5f), camera);
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
        for (int i = 0; i < spawn && list.size() < 700; i++) {
            P q = new P();
            Vec3d at;
            if (live.isEmpty()) {
                double f = random.nextDouble() * 0.6;
                at = new Vec3d(seg[0].x + along.x * f, seg[0].y + along.y * f, seg[0].z + along.z * f);
            } else {
                at = sample(live.get(random.nextInt(live.size())), camera);
            }
            q.x = at.x + (random.nextDouble() - 0.5) * 0.03;
            q.y = at.y + (random.nextDouble() - 0.5) * 0.03;
            q.z = at.z + (random.nextDouble() - 0.5) * 0.03;
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
