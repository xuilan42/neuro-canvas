package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Depth;
import com.example.neurocanvas.fx.EffectDraw;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A coloured glow over the whole body of the entity you hit, fading out. */
public final class HitColor extends Module {
    private record Flash(LivingEntity entity, long start) { }

    private final Setting color1 = add(Setting.color("Bottom color", 0.97));
    private final Setting color2 = add(Setting.color("Top color", 0.08));
    private final Setting fill = add(Setting.number("Strength", 0.05, 1, 0.45));
    private final Setting duration = add(Setting.number("Duration (s)", 0.1, 1.5, 0.45));
    private final Setting expand = add(Setting.number("Expand", 0, 0.5, 0.05));

    private final List<Flash> flashes = new ArrayList<>();

    public HitColor() {
        super("HitColor", Category.COMBAT, "Coloured glow on the entity you hit", false);
        WorldState.addHitListener(e -> {
            if (enabled) {
                flashes.add(new Flash(e, System.currentTimeMillis()));
            }
        });
    }

    @Override
    public void renderWorld(RenderEnv env) {
        long now = System.currentTimeMillis();
        long lifeMs = (long) (duration.f() * 1000);
        flashes.removeIf(f -> now - f.start > lifeMs);
        if (flashes.isEmpty()) {
            return;
        }
        int bottom = color1.argb();
        int top = color2.argb();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        for (Flash f : flashes) {
            float progress = (now - f.start) / (float) lifeMs;
            float a = fill.f() * (1f - progress);
            Vec3d p = EffectDraw.lerpPos(f.entity, env.tickDelta());
            float e = expand.f() * (1f + progress);
            float w = f.entity.getWidth() / 2f + e;
            Depth.state(Depth.over(env.mc(), p.add(0, f.entity.getHeight() / 2, 0)));
            drawBox(env.m(), p, w, f.entity.getHeight() + e, e, bottom, top, a);
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
    }

    private static void drawBox(Matrix4f m, Vec3d p, float w, float h, float e, int bottom, int top, float a) {
        float x0 = (float) p.x - w, x1 = (float) p.x + w;
        float y0 = (float) p.y - e / 2f, y1 = (float) p.y + h;
        float z0 = (float) p.z - w, z1 = (float) p.z + w;
        float[] cb = {((bottom >> 16) & 0xFF) / 255f, ((bottom >> 8) & 0xFF) / 255f, (bottom & 0xFF) / 255f, a * ((bottom >>> 24) / 255f)};
        float[] ct = {((top >> 16) & 0xFF) / 255f, ((top >> 8) & 0xFF) / 255f, (top & 0xFF) / 255f, a * ((top >>> 24) / 255f)};
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        side(b, m, x0, z0, x1, z0, y0, y1, cb, ct);
        side(b, m, x1, z0, x1, z1, y0, y1, cb, ct);
        side(b, m, x1, z1, x0, z1, y0, y1, cb, ct);
        side(b, m, x0, z1, x0, z0, y0, y1, cb, ct);
        cap(b, m, x0, x1, y1, z0, z1, ct);
        cap(b, m, x0, x1, y0, z0, z1, cb);
        Tessellator.getInstance().draw();
    }

    private static void side(BufferBuilder b, Matrix4f m, float xa, float za, float xb, float zb, float y0, float y1,
                             float[] cb, float[] ct) {
        b.vertex(m, xa, y0, za).color(cb[0], cb[1], cb[2], cb[3]).next();
        b.vertex(m, xb, y0, zb).color(cb[0], cb[1], cb[2], cb[3]).next();
        b.vertex(m, xb, y1, zb).color(ct[0], ct[1], ct[2], ct[3]).next();
        b.vertex(m, xa, y1, za).color(ct[0], ct[1], ct[2], ct[3]).next();
    }

    private static void cap(BufferBuilder b, Matrix4f m, float x0, float x1, float y, float z0, float z1, float[] c) {
        b.vertex(m, x0, y, z0).color(c[0], c[1], c[2], c[3]).next();
        b.vertex(m, x1, y, z0).color(c[0], c[1], c[2], c[3]).next();
        b.vertex(m, x1, y, z1).color(c[0], c[1], c[2], c[3]).next();
        b.vertex(m, x0, y, z1).color(c[0], c[1], c[2], c[3]).next();
    }
}
