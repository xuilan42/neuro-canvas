package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Fills;
import com.example.neurocanvas.fx.Lines;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/** Highlights the block you are looking at with a coloured fill and outline. */
public final class BlockOverlay extends Module {
    private final Setting style = add(Setting.choice("Style", 0, "Static", "Waves", "Scan", "Ripple", "Liquid", "Chase", "Sparkles", "Checker"));
    private final Setting styleSpeed = add(Setting.number("Style speed", 0.2, 4, 1));
    private final Setting fill = add(Setting.bool("Fill", true));
    private final Setting fillColor = add(Setting.color("Fill color", 0.4));
    private final Setting outline = add(Setting.bool("Outline", true));
    private final Setting outlineColor = add(Setting.color("Outline color", 0.4));
    private final Setting width = add(Setting.number("Outline width", 1, 6, 2));
    private final Setting hideVanilla = add(Setting.bool("Hide vanilla outline", true));
    private final Setting expand = add(Setting.number("Expand", 0, 0.1, 0.003));
    private final Setting pulse = add(Setting.number("Pulse", 0, 1, 0.25));
    private final Setting colorMode = add(Setting.choice("Color mode", 0, "Static", "Rainbow", "Gradient"));
    private final Setting gradientColor = add(Setting.color("Gradient color", 0.8));
    private final Setting colorSpeed = add(Setting.number("Color speed", 0.1, 5, 1));
    private final Setting colorFlow = add(Setting.number("Color flow", 0, 2, 0.6));

    public BlockOverlay() {
        super("BlockOverlay", Category.WORLD, "Coloured highlight on the block you look at", false);
        gradientColor.setArgb(0xFFB06BFF);
        fillColor.setArgb(0x4D3DDC97);
        outlineColor.setArgb(0xFF3DDC97);
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outlineContext) -> !(enabled && hideVanilla.on()));
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos, ShapeContext.of(mc.player));
        if (shape.isEmpty()) {
            return;
        }
        List<Box> boxes = new ArrayList<>();
        for (Box b : shape.getBoundingBoxes()) {
            boxes.add(b.offset(pos).expand(expand.value));
        }
        float breathe = 1f - pulse.f() * (0.5f + 0.5f * (float) Math.sin(env.t() * 3.0));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        double t = env.t();
        boolean animated = colorMode.index() != 0;
        int st = style.index();
        Vec3d hitPos = hit.getPos();
        if (fill.on()) {
            if (st == 1 || st == 2 || st == 3 || st == 4 || st == 7) {
                gridFill(env, boxes, hitPos, t, breathe, st);
            } else {
                Fills fills = Fills.begin(env);
                int base = scaleAlpha(fillColor.argb(), breathe);
                for (Box b : boxes) {
                    if (animated) {
                        double ph = b.minX + b.minZ;
                        fills.box(b, scaleAlpha(shade(fillColor.argb(), t, ph), breathe), scaleAlpha(shade(fillColor.argb(), t, ph + colorFlow.value), breathe));
                    } else {
                        fills.box(b, base);
                    }
                }
                fills.end();
            }
        }
        if (outline.on()) {
            Lines lines = Lines.begin(env, width.f());
            for (Box b : boxes) {
                if (st == 5) {
                    chase(lines, b, t, breathe);
                } else if (!animated) {
                    lines.box(b, scaleAlpha(outlineColor.argb(), breathe));
                } else {
                    double[] xs = {b.minX, b.maxX};
                    double[] ys = {b.minY, b.maxY};
                    double[] zs = {b.minZ, b.maxZ};
                    for (int i = 0; i < 2; i++) {
                        for (int j = 0; j < 2; j++) {
                            edge(lines, t, breathe, xs[0], ys[i], zs[j], xs[1], ys[i], zs[j]);
                            edge(lines, t, breathe, xs[i], ys[0], zs[j], xs[i], ys[1], zs[j]);
                            edge(lines, t, breathe, xs[i], ys[j], zs[0], xs[i], ys[j], zs[1]);
                        }
                    }
                }
                if (st == 6) {
                    sparkles(lines, b, t, breathe);
                }
            }
            lines.end();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
    }

    // ---- animated styles ------------------------------------------------------------------------

    private static double smooth(double x) {
        double c = Math.max(0, Math.min(1, x));
        return c * c * (3 - 2 * c);
    }

    private static final int GRID = 8;

    /** How bright (a) and how far along the colour range (k) a point of the box is for the grid styles. lx, ly, lz run 0..1 over the box. */
    private double[] cell(int st, double lx, double ly, double lz, Vec3d at, Vec3d hit, double t, double dx, double dy, double dz) {
        double sp = styleSpeed.value;
        double a;
        double k;
        switch (st) {
            case 1 -> {
                double ph = (lx * dx + lz * dz) * 5.0 + ly * dy * 3.0 - t * 2.4 * sp;
                double w = 0.6 * Math.sin(ph) + 0.4 * Math.sin(ph * 0.47 + (lx - lz) * 2.0 + t * 0.9 * sp);
                a = 0.3 + 0.7 * (0.5 + 0.5 * w);
                k = 0.5 + 0.5 * Math.sin(ph + 1.2);
            }
            case 2 -> {
                double scan = (t * 0.45 * sp) % 1.0;
                double band = Math.exp(-Math.pow((ly - scan) / 0.1, 2));
                double band2 = Math.exp(-Math.pow((ly - (1.0 - scan)) / 0.06, 2)) * 0.5;
                a = 0.12 + 0.88 * Math.min(1.0, band + band2);
                k = ly;
            }
            case 3 -> {
                double d = at.distanceTo(hit);
                double ring = Math.max(0, Math.sin(d * 7.0 - t * 4.0 * sp));
                a = (0.18 + 0.82 * ring) * Math.exp(-d * 0.55);
                k = (d * 0.6 - t * 0.3 * sp) % 1.0;
            }
            case 4 -> {
                double level = 0.5 + 0.38 * Math.sin(t * 0.9 * sp);
                double surface = level + 0.035 * Math.sin((lx * dx + lz * dz) * 9.0 + t * 3.5 * sp);
                double inside = smooth((surface - ly) / 0.05);
                double edge = Math.exp(-Math.pow((ly - surface) / 0.025, 2));
                a = Math.min(1.0, 0.05 + 0.5 * inside * (0.7 + 0.3 * (1 - ly / Math.max(0.1, level))) + edge);
                k = ly;
            }
            default -> {
                int parity = (int) (Math.floor(lx * 4) + Math.floor(ly * 4) + Math.floor(lz * 4));
                double wave = 0.5 + 0.5 * Math.sin(t * 2.2 * sp + (parity % 2 == 0 ? 0 : Math.PI));
                a = 0.12 + 0.88 * wave;
                k = (parity % 2 == 0) ? 0.2 : 0.8;
            }
        }
        return new double[] {Math.max(0, Math.min(1, a)), k};
    }

    private void gridFill(RenderEnv env, List<Box> boxes, Vec3d hit, double t, float breathe, int st) {
        Matrix4f m = env.m();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int base = fillColor.argb();
        int alpha = base >>> 24;
        for (Box bx : boxes) {
            double dx = bx.maxX - bx.minX, dy = bx.maxY - bx.minY, dz = bx.maxZ - bx.minZ;
            double[][] faces = {
                {bx.minX, bx.maxY, bx.minZ, dx, 0, 0, 0, 0, dz},
                {bx.minX, bx.minY, bx.minZ, dx, 0, 0, 0, 0, dz},
                {bx.maxX, bx.minY, bx.minZ, 0, 0, dz, 0, dy, 0},
                {bx.minX, bx.minY, bx.minZ, 0, 0, dz, 0, dy, 0},
                {bx.minX, bx.minY, bx.maxZ, dx, 0, 0, 0, dy, 0},
                {bx.minX, bx.minY, bx.minZ, dx, 0, 0, 0, dy, 0}};
            for (double[] f : faces) {
                for (int i = 0; i < GRID; i++) {
                    for (int j = 0; j < GRID; j++) {
                        int[][] corner = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                        for (int[] c : corner) {
                            double u = c[0] / (double) GRID, v = c[1] / (double) GRID;
                            double x = f[0] + f[3] * u + f[6] * v;
                            double y = f[1] + f[4] * u + f[7] * v;
                            double z = f[2] + f[5] * u + f[8] * v;
                            double cu = st == 7 ? (i + 0.5) / GRID : u;
                            double cv = st == 7 ? (j + 0.5) / GRID : v;
                            double sx = st == 7 ? f[0] + f[3] * cu + f[6] * cv : x;
                            double sy = st == 7 ? f[1] + f[4] * cu + f[7] * cv : y;
                            double sz = st == 7 ? f[2] + f[5] * cu + f[8] * cv : z;
                            double lx = dx < 1e-9 ? 0 : (sx - bx.minX) / dx;
                            double ly = dy < 1e-9 ? 0 : (sy - bx.minY) / dy;
                            double lz = dz < 1e-9 ? 0 : (sz - bx.minZ) / dz;
                            double[] r = cell(st, lx, ly, lz, new Vec3d(sx, sy, sz), hit, t, dx, dy, dz);
                            int col = shade(base, t, r[1] * Math.max(0.2, colorFlow.value) / 0.6);
                            int a = (int) (alpha * r[0] * breathe);
                            b.vertex(m, (float) x, (float) y, (float) z)
                                    .color(((col >> 16) & 0xFF) / 255f, ((col >> 8) & 0xFF) / 255f, (col & 0xFF) / 255f, Math.min(255, a) / 255f).next();
                        }
                    }
                }
            }
        }
        tess.draw();
        com.example.neurocanvas.fx.Shaders.finish();
    }

    /** Bright dashes that run along the edges of the box. */
    private void chase(Lines lines, Box b, double t, float breathe) {
        double[] xs = {b.minX, b.maxX};
        double[] ys = {b.minY, b.maxY};
        double[] zs = {b.minZ, b.maxZ};
        List<double[]> edges = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                edges.add(new double[] {xs[0], ys[i], zs[j], xs[1], ys[i], zs[j]});
                edges.add(new double[] {xs[i], ys[0], zs[j], xs[i], ys[1], zs[j]});
                edges.add(new double[] {xs[i], ys[j], zs[0], xs[i], ys[j], zs[1]});
            }
        }
        int segs = 12;
        int base = outlineColor.argb();
        int alpha = base >>> 24;
        for (int e = 0; e < edges.size(); e++) {
            double[] ed = edges.get(e);
            double head = (t * 0.55 * styleSpeed.value + e * 0.083) % 1.0;
            for (int sgm = 0; sgm < segs; sgm++) {
                double f0 = sgm / (double) segs, f1 = (sgm + 1) / (double) segs;
                double behind = ((head - (f0 + f1) / 2) % 1.0 + 1.0) % 1.0;
                double glow = Math.max(0, 1.0 - behind * 3.0);
                int col = shade(base, t, f0 + e * 0.1);
                int a = (int) (alpha * (0.22 + 0.78 * glow) * breathe);
                lines.line(ed[0] + (ed[3] - ed[0]) * f0, ed[1] + (ed[4] - ed[1]) * f0, ed[2] + (ed[5] - ed[2]) * f0,
                        ed[0] + (ed[3] - ed[0]) * f1, ed[1] + (ed[4] - ed[1]) * f1, ed[2] + (ed[5] - ed[2]) * f1, (Math.min(255, a) << 24) | (col & 0xFFFFFF));
            }
        }
    }

    /** Small sparks that rise from the edges and fade. */
    private void sparkles(Lines lines, Box b, double t, float breathe) {
        int base = outlineColor.argb();
        int alpha = base >>> 24;
        for (int i = 0; i < 14; i++) {
            double h1 = Math.abs(Math.sin(i * 12.9898) * 43758.5453) % 1.0;
            double h2 = Math.abs(Math.sin(i * 78.233 + 1.0) * 12345.6789) % 1.0;
            double life = (t * 0.5 * styleSpeed.value + h1) % 1.0;
            int corner = (int) (h2 * 4) % 4;
            double x = (corner & 1) == 0 ? b.minX : b.maxX;
            double z = (corner & 2) == 0 ? b.minZ : b.maxZ;
            double along = (h1 * 7.0) % 1.0;
            if ((i & 1) == 0) {
                x = b.minX + (b.maxX - b.minX) * along;
            } else {
                z = b.minZ + (b.maxZ - b.minZ) * along;
            }
            double y = b.minY + (b.maxY - b.minY + 0.3) * life;
            int col = shade(base, t, h1);
            int a = (int) (alpha * Math.sin(Math.PI * life) * breathe);
            int argb = (Math.min(255, Math.max(0, a)) << 24) | (col & 0xFFFFFF);
            double s = 0.05;
            lines.line(x, y - s, z, x, y + s, z, argb);
            lines.line(x - s, y, z, x + s, y, z, argb);
            lines.line(x, y, z - s, x, y, z + s, argb);
        }
    }

    /** One edge of the box; its colour depends on where it is, so the colours flow around the outline. */
    private void edge(Lines lines, double t, float breathe, double x1, double y1, double z1, double x2, double y2, double z2) {
        double ph = ((x1 + x2) + (y1 + y2) + (z1 + z2)) * 0.5;
        lines.line(x1, y1, z1, x2, y2, z2, scaleAlpha(shade(outlineColor.argb(), t, ph), breathe));
    }

    /** The colour at time t and position phase: a rainbow, or a swing between the base colour and the gradient colour. */
    private int shade(int base, double t, double phase) {
        if (colorMode.index() == 0) {
            return base;
        }
        double p = t * colorSpeed.value * 0.25 + phase * colorFlow.value;
        int alpha = base >>> 24;
        if (colorMode.index() == 1) {
            int rgb = java.awt.Color.HSBtoRGB((float) (((p % 1.0) + 1.0) % 1.0), 0.75f, 1f) & 0xFFFFFF;
            return (alpha << 24) | rgb;
        }
        float k = (float) (0.5 + 0.5 * Math.sin(Math.PI * 2 * p));
        int other = gradientColor.argb();
        int r = (int) (((base >> 16) & 0xFF) * (1 - k) + ((other >> 16) & 0xFF) * k);
        int g = (int) (((base >> 8) & 0xFF) * (1 - k) + ((other >> 8) & 0xFF) * k);
        int b = (int) ((base & 0xFF) * (1 - k) + (other & 0xFF) * k);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int scaleAlpha(int argb, float k) {
        return ((int) ((argb >>> 24) * k) << 24) | (argb & 0xFFFFFF);
    }
}
