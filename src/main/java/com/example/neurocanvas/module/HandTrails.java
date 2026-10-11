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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A glowing ribbon dragged behind your hand and held item as you move and turn. */
public final class HandTrails extends Module {
    private record Seg(Vec3d base, Vec3d tip) { }

    private final Setting hand = add(Setting.choice("Hand", 0, "Main", "Off"));
    private final Setting length = add(Setting.number("Length (ticks)", 4, 60, 18));
    private final Setting size = add(Setting.number("Item size", 0.2, 1.5, 0.7));
    private final Setting color1 = add(Setting.color("Color 1", 0.55));
    private final Setting color2 = add(Setting.color("Color 2", 0.8));
    private final Setting alpha = add(Setting.number("Alpha", 0.05, 1, 0.8));

    private final List<Seg> segs = new ArrayList<>();

    public HandTrails() {
        super("HandTrails", Category.VISUALS, "Ribbon behind your hand and item", false);
    }

    private Seg handSegment(ClientPlayerEntity p, float tickDelta) {
        Vec3d eye = p.getCameraPosVec(tickDelta);
        double yaw = Math.toRadians(p.getYaw(tickDelta));
        double pitch = Math.toRadians(p.getPitch(tickDelta));
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double side = p.getMainArm() == Arm.RIGHT ? 1 : -1;
        if (hand.index() == 1) {
            side = -side;
        }
        Vec3d base = eye.add(rx * 0.36 * side + fx * 0.5 - ux * 0.28, -uy * 0.28 + fy * 0.5, rz * 0.36 * side + fz * 0.5 - uz * 0.28);
        double s = size.value;
        Vec3d tip = base.add((fx * 0.55 + ux * 0.45) * s, (fy * 0.55 + uy * 0.45) * s, (fz * 0.55 + uz * 0.45) * s);
        return new Seg(base, tip);
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.player == null) {
            segs.clear();
            return;
        }
        segs.add(handSegment(mc.player, 1f));
        while (segs.size() > (int) length.value) {
            segs.remove(0);
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        if (env.mc().player == null || segs.isEmpty()) {
            return;
        }
        List<Seg> all = new ArrayList<>(segs);
        all.add(handSegment(env.mc().player, env.tickDelta()));
        EffectSpec spec = new EffectSpec();
        spec.color1 = color1.argb() & 0xFFFFFF;
        spec.color2 = color2.argb() & 0xFFFFFF;
        float a = alpha.f() * ((color1.argb() >>> 24) / 255f);

        Matrix4f m = env.m();
        Depth.state(Depth.over(env.mc(), all.get(all.size() - 1).base));
        RenderSystem.depthMask(false);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        int n = all.size();
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1);
            float[] c = EffectDraw.blend(spec, 1f - f, a * f);
            Seg s = all.get(i);
            b.vertex(m, (float) s.base.x, (float) s.base.y, (float) s.base.z).color(c[0], c[1], c[2], c[3]).next();
            b.vertex(m, (float) s.tip.x, (float) s.tip.y, (float) s.tip.z).color(c[0], c[1], c[2], c[3] * 0.35f).next();
        }
        Tessellator.getInstance().draw();
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
    }
}
