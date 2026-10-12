package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Billboard;
import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.Collections;
import net.minecraft.block.BeetrootsBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SweetBerryBushBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Look at a crop, stem, sapling, nether wart, cocoa pod or berry bush and see how far it has grown, in percent, and
 * about how long is left. The time is an average: plants grow on random ticks, using the same chances as the game.
 */
public final class CropInfo extends Module {
    private record Info(String name, float progress, int stage, int max, boolean done, double seconds, int light, int lightNeeded, boolean sapling) { }

    private final Setting panel = add(Setting.bool("Panel", true));
    private final Setting worldLabel = add(Setting.bool("Label above the plant", false));
    private final Setting eta = add(Setting.bool("Show time left", true));
    private final Setting tickSpeed = add(Setting.number("Random tick speed", 1, 20, 3));
    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private float anchorX = Float.NaN;
    private float anchorY = Float.NaN;
    private final Setting offset = add(Setting.number("Panel gap", 0, 120, 6));

    public CropInfo() {
        super("CropInfo", Category.WORLD, "Growth percent and time left for the plant you look at", false);
    }

    // ---- the game's growth rules --------------------------------------------------------------

    /** Chance that one random tick grows a crop, from the moisture and neighbour factor (same formula as the game). */
    static double cropChance(float moisture) {
        return 1.0 / ((int) (25.0f / moisture) + 1);
    }

    /** Moisture factor of a crop spot: wet farmland around it helps, the same crop in the neighbouring rows hurts. */
    static float moisture(ClientWorld world, Block block, BlockPos pos) {
        float f = 1.0f;
        BlockPos below = pos.down();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float g = 0.0f;
                BlockState s = world.getBlockState(below.add(i, 0, j));
                if (s.isOf(Blocks.FARMLAND)) {
                    g = 1.0f;
                    if (s.get(FarmlandBlock.MOISTURE) > 0) {
                        g = 3.0f;
                    }
                }
                if (i != 0 || j != 0) {
                    g /= 4.0f;
                }
                f += g;
            }
        }
        BlockPos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        boolean row = world.getBlockState(w).isOf(block) || world.getBlockState(e).isOf(block);
        boolean column = world.getBlockState(n).isOf(block) || world.getBlockState(s).isOf(block);
        if (row && column) {
            f /= 2.0f;
        } else {
            boolean diagonal = world.getBlockState(w.north()).isOf(block) || world.getBlockState(e.north()).isOf(block)
                    || world.getBlockState(e.south()).isOf(block) || world.getBlockState(w.south()).isOf(block);
            if (diagonal) {
                f /= 2.0f;
            }
        }
        return f;
    }

    /** Average seconds for {@code stages} growth steps when each random tick succeeds with {@code chance}. */
    static double seconds(int stages, double chance, double randomTickSpeed) {
        return stages / chance * (4096.0 / randomTickSpeed) / 20.0;
    }

    static String time(double seconds) {
        long s = Math.round(seconds);
        if (s >= 3600) {
            return String.format("%dh %02dm", s / 3600, s / 60 % 60);
        }
        if (s >= 60) {
            return String.format("%dm %02ds", s / 60, s % 60);
        }
        return s + "s";
    }

    private static IntProperty ageProperty(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntProperty ip && p.getName().equals("age")) {
                return ip;
            }
        }
        return null;
    }

    private Info inspect(MinecraftClient mc, BlockPos pos) {
        ClientWorld world = mc.world;
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        String name = block.getName().getString();
        double speed = tickSpeed.value;

        if (block instanceof SaplingBlock) {
            int stage = state.get(SaplingBlock.STAGE);
            int light = world.getLightLevel(pos.up());
            return new Info(name, stage / 2f, stage, 2, false, seconds(2 - stage, 1.0 / 7.0, speed), light, 9, true);
        }
        IntProperty age = ageProperty(state);
        if (age == null) {
            return null;
        }
        int value = state.get(age);
        int max = Collections.max(age.getValues());
        int left = max - value;
        boolean done = left <= 0;
        double chance;
        int needed = 0;
        int light = 15;
        if (block instanceof BeetrootsBlock) {
            chance = cropChance(moisture(world, block, pos)) / 3.0;
            needed = 9;
            light = world.getLightLevel(pos, 0);
        } else if (block instanceof CropBlock || block instanceof StemBlock) {
            chance = cropChance(moisture(world, block, pos));
            needed = 9;
            light = world.getLightLevel(pos, 0);
        } else if (block instanceof NetherWartBlock) {
            chance = 1.0 / 10.0;
        } else if (block instanceof CocoaBlock) {
            chance = 1.0 / 5.0;
        } else if (block instanceof SweetBerryBushBlock) {
            chance = 1.0 / 5.0;
            needed = 9;
            light = world.getLightLevel(pos.up(), 0);
        } else {
            return null;
        }
        return new Info(name, max == 0 ? 1f : value / (float) max, value, max, done, done ? 0 : seconds(left, chance, speed), light, needed, false);
    }

    private BlockPos target(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }

    // ---- drawing --------------------------------------------------------------------------------

    @Override
    public void renderWorld(RenderEnv env) {
        BlockPos pos = target(env.mc());
        anchorX = Float.NaN;
        if (pos != null && panel.on()) {
            org.joml.Vector4f v = new org.joml.Vector4f(pos.getX() + 0.5f, pos.getY() + 1.0f, pos.getZ() + 0.5f, 1f);
            v.mul(env.m());
            v.mul(RenderSystem.getProjectionMatrix());
            if (v.w > 0.05f) {
                anchorX = (v.x / v.w * 0.5f + 0.5f) * env.mc().getWindow().getScaledWidth();
                anchorY = (0.5f - v.y / v.w * 0.5f) * env.mc().getWindow().getScaledHeight();
            }
        }
        if (!worldLabel.on()) {
            return;
        }
        Info info = pos == null ? null : inspect(env.mc(), pos);
        if (info != null) {
            String text = info.sapling() ? "sapling " + Math.round(info.progress() * 100) + "%" : Math.round(info.progress() * 100) + "%";
            Billboard.draw(env, new Vec3d(pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5), text,
                    info.done() ? 0xFF6EE7A0 : 0xFFFFFFFF, 1f);
        }
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (!panel.on() || mc.options.hudHidden || mc.currentScreen != null) {
            return;
        }
        BlockPos pos = target(mc);
        Info info = pos == null ? null : inspect(mc, pos);
        if (info == null) {
            return;
        }
        Theme th = Interface.theme();
        int pct = Math.round(info.progress() * 100);
        String title = info.name();
        String growth = info.sapling() ? "Stage " + (info.stage() + 1) + " of 2   " + pct + "%" : "Growth " + info.stage() + "/" + info.max() + "   " + pct + "%";
        String line3;
        int line3Color = th.dim();
        if (info.done()) {
            line3 = info.sapling() ? "" : "Fully grown";
            line3Color = 0xFF6EE7A0;
        } else if (info.lightNeeded() > 0 && info.light() < info.lightNeeded()) {
            line3 = "Needs light " + info.lightNeeded() + "+ (now " + info.light() + ")";
            line3Color = 0xFFFFC040;
        } else if (eta.on()) {
            line3 = "About " + time(info.seconds()) + " left" + (info.sapling() ? " (to a tree)" : "");
        } else {
            line3 = "";
        }
        float s = scale.f();
        int w = Math.max(Ui.width(mc.textRenderer, title), Math.max(Ui.width(mc.textRenderer, growth), Ui.width(mc.textRenderer, line3))) + 24;
        w = Math.max(w, 130);
        int h = line3.isEmpty() ? 42 : 54;
        ctx.getMatrices().push();
        float px = ctx.getScaledWindowWidth() / 2f;
        float py = ctx.getScaledWindowHeight() / 2f + 34;
        if (!Float.isNaN(anchorX)) {
            px = Math.max(w * s / 2f + 4, Math.min(ctx.getScaledWindowWidth() - w * s / 2f - 4, anchorX));
            py = Math.max(4, Math.min(ctx.getScaledWindowHeight() - h * s - 4, anchorY - h * s - offset.f()));
        }
        ctx.getMatrices().translate(px, py, 0);
        ctx.getMatrices().scale(s, s, 1f);
        int x = -w / 2;
        Ui.roundRect(ctx, x, 0, w, h, 6, (th.panel() & 0xFFFFFF) | 0xCC000000);
        Ui.text(ctx, mc.textRenderer, title, x + 8, 6, th.text());
        Ui.text(ctx, mc.textRenderer, growth, x + 8, 18, th.dim());
        int bx = x + 8;
        int bw = w - 16;
        Ui.roundRect(ctx, bx, 31, bw, 5, 2, th.hover());
        if (info.progress() > 0.001f) {
            int fill = Math.max(3, Math.round(bw * info.progress()));
            Ui.roundRect(ctx, bx, 31, fill, 5, 2, info.done() ? 0xFF6EE7A0 : th.accent());
        }
        if (!line3.isEmpty()) {
            Ui.text(ctx, mc.textRenderer, line3, x + 8, 41, line3Color);
        }
        ctx.getMatrices().pop();
    }
}
