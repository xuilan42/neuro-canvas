package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Depth;
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
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A glowing ribbon dragged behind your hand and held item as you move and turn. */
public final class HandTrails extends Module {
    private record Seg(Vec3d base, Vec3d tip, double time) { }

    private final Setting hand = add(Setting.choice("Hand", 0, "Main", "Off"));
    private final Setting length = add(Setting.number("Length (ticks)", 4, 60, 18));
    private final Setting size = add(Setting.number("Item size", 0.2, 1.5, 0.7));
    private final Setting taper = add(Setting.number("Taper", 0, 1, 0.7));
    private final Setting smooth = add(Setting.bool("Smooth curve", true));
    private final Setting followSwing = add(Setting.bool("Follow swing", true));
    private final Setting color1 = add(Setting.color("Color 1", 0.55));
    private final Setting color2 = add(Setting.color("Color 2", 0.8));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.8));

    /** Samples are taken every frame (not every tick) so the ribbon is a smooth curve instead of a polyline of 20 Hz points. */
    private final List<Seg> segs = new ArrayList<>();

    public HandTrails() {
        super("HandTrails", Category.VISUALS, "Ribbon behind your hand and item", false);
    }

    /**
     * Where the held item is, built the way the game places it in first person: a point to the side, down and in front of the camera,
     * pushed along the swing arc while you attack, and lagging a little behind your turning like the hand does.
     */
    private Seg handSegment(ClientPlayerEntity p, float tickDelta, double time) {
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
        Vec3d tip = base.add(dir.multiply(size.value));
        return new Seg(base, tip, time);
    }

    private static double now(MinecraftClient mc, float tickDelta) {
        return (mc.world.getTime() + tickDelta) * 50.0;
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            segs.clear();
        }
    }

    private static Vec3d catmull(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3d(
                0.5 * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                0.5 * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
                0.5 * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3));
    }

    private List<Seg> smoothed(List<Seg> in) {
        int n = in.size();
        if (!smooth.on() || n < 3) {
            return in;
        }
        List<Seg> out = new ArrayList<>(n * 3);
        for (int i = 0; i + 1 < n; i++) {
            Seg p0 = in.get(Math.max(i - 1, 0));
            Seg p1 = in.get(i);
            Seg p2 = in.get(i + 1);
            Seg p3 = in.get(Math.min(i + 2, n - 1));
            for (int k = 0; k < 3; k++) {
                double t = k / 3.0;
                out.add(new Seg(catmull(p0.base, p1.base, p2.base, p3.base, t), catmull(p0.tip, p1.tip, p2.tip, p3.tip, t),
                        p1.time + (p2.time - p1.time) * t));
            }
        }
        out.add(in.get(n - 1));
        return out;
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        if (mc.player == null || mc.world == null) {
            return;
        }
        double now = now(mc, env.tickDelta());
        double life = Math.max(4, (int) length.value) * 50.0;
        Seg live = handSegment(mc.player, env.tickDelta(), now);
        if (segs.isEmpty() || now < segs.get(segs.size() - 1).time) {
            segs.clear();
            segs.add(live);
        } else if (now - segs.get(segs.size() - 1).time >= 8.0) {
            segs.add(live);
        }
        while (segs.size() > 1 && now - segs.get(0).time > life) {
            segs.remove(0);
        }
        List<Seg> raw = new ArrayList<>(segs);
        raw.add(live);
        if (raw.size() < 2) {
            return;
        }
        List<Seg> all = smoothed(raw);
        EffectSpec spec = new EffectSpec();
        spec.color1 = color1.argb() & 0xFFFFFF;
        spec.color2 = color2.argb() & 0xFFFFFF;
        float a = alpha.f() * ((color1.argb() >>> 24) / 255f);
        float tp = taper.f();

        Depth.state(Depth.over(mc, live.base));
        RenderSystem.depthMask(false);
        int n = all.size();
        float[] fs = new float[n];
        Vec3d[] tips = new Vec3d[n];
        for (int i = 0; i < n; i++) {
            Seg s = all.get(i);
            fs[i] = (float) Math.max(0.0, Math.min(1.0, 1.0 - (now - s.time) / life));
            tips[i] = s.base.add(s.tip.subtract(s.base).multiply(1.0 - tp * (1.0 - fs[i])));
        }
        com.example.neurocanvas.fx.FxBatch fx = com.example.neurocanvas.fx.FxBatch.begin(env, com.example.neurocanvas.fx.FxBatch.RIBBON, 0f, 0f, 0f, 0);
        if (fx != null) {
            for (int i = 0; i + 1 < n; i++) {
                Seg sa = all.get(i);
                Seg sb = all.get(i + 1);
                float[] ca = EffectDraw.blend(spec, 1f - fs[i], a * fs[i]);
                float[] cb = EffectDraw.blend(spec, 1f - fs[i + 1], a * fs[i + 1]);
                int colA = (Math.round(ca[3] * 255) << 24) | (Math.round(ca[0] * 255) << 16) | (Math.round(ca[1] * 255) << 8) | Math.round(ca[2] * 255);
                int colB = (Math.round(cb[3] * 255) << 24) | (Math.round(cb[0] * 255) << 16) | (Math.round(cb[1] * 255) << 8) | Math.round(cb[2] * 255);
                fx.vertex(sa.base.x, sa.base.y, sa.base.z, 0f, 0f, colA);
                fx.vertex(tips[i].x, tips[i].y, tips[i].z, 0f, 1f, colA);
                fx.vertex(tips[i + 1].x, tips[i + 1].y, tips[i + 1].z, 1f, 1f, colB);
                fx.vertex(sb.base.x, sb.base.y, sb.base.z, 1f, 0f, colB);
            }
            fx.end();
        } else {
            Matrix4f m = env.m();
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < n; i++) {
                float[] c = EffectDraw.blend(spec, 1f - fs[i], a * fs[i]);
                Seg s2 = all.get(i);
                b.vertex(m, (float) s2.base.x, (float) s2.base.y, (float) s2.base.z).color(c[0], c[1], c[2], c[3]).next();
                b.vertex(m, (float) tips[i].x, (float) tips[i].y, (float) tips[i].z).color(c[0], c[1], c[2], c[3] * 0.35f).next();
            }
            Tessellator.getInstance().draw();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
    }
}
