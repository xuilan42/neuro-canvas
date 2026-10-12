package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Fills;
import com.example.neurocanvas.fx.Lines;
import com.mojang.blaze3d.systems.RenderSystem;
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
        if (fill.on()) {
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
        if (outline.on()) {
            Lines lines = Lines.begin(env, width.f());
            for (Box b : boxes) {
                if (!animated) {
                    lines.box(b, scaleAlpha(outlineColor.argb(), breathe));
                    continue;
                }
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
            lines.end();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
    }

    /** One edge of the box; its colour depends on where it is, so the colours flow around the outline. */
    private void edge(Lines lines, double t, float breathe, double x1, double y1, double z1, double x2, double y2, double z2) {
        double ph = ((x1 + x2) + (y1 + y2) + (z1 + z2)) * 0.5;
        lines.line(x1, y1, z1, x2, y2, z2, scaleAlpha(shade(outlineColor.argb(), t, ph), breathe));
    }

    /** The colour at time t and position phase: a rainbow, or a swing between the base colour and the gradient colour. */
    private int shade(int base, double t, double phase) {
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
