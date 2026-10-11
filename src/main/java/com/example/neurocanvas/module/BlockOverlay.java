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

    public BlockOverlay() {
        super("BlockOverlay", Category.WORLD, "Coloured highlight on the block you look at", false);
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
        if (fill.on()) {
            Fills fills = Fills.begin(env);
            int c = scaleAlpha(fillColor.argb(), breathe);
            for (Box b : boxes) {
                fills.box(b, c);
            }
            fills.end();
        }
        if (outline.on()) {
            Lines lines = Lines.begin(env, width.f());
            int c = scaleAlpha(outlineColor.argb(), breathe);
            for (Box b : boxes) {
                lines.box(b, c);
            }
            lines.end();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
    }

    private static int scaleAlpha(int argb, float k) {
        return ((int) ((argb >>> 24) * k) << 24) | (argb & 0xFFFFFF);
    }
}
