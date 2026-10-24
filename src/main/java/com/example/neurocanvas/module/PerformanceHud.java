package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.Vec3d;

/** FPS, ping, memory, CPU, coordinates and speed in a small panel you can move. */
public final class PerformanceHud extends Module {
    private static final int ROW_H = 12;
    private static final HudLayout.Element ELEMENT = HudLayout.register("performance", "Performance", false, 0.006, 0.08);

    static {
        ELEMENT.w = 90;
        ELEMENT.h = ROW_H * 3;
    }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.7));
    private final Setting fps = add(Setting.bool("FPS", true));
    private final Setting ping = add(Setting.bool("Ping", true));
    private final Setting memory = add(Setting.bool("Memory", true));
    private final Setting cpu = add(Setting.bool("CPU", false));
    private final Setting coords = add(Setting.bool("Coordinates", false));
    private final Setting speed = add(Setting.bool("Speed", false));

    private long lastCpuSample;
    private double cpuLoad = -1;
    private Vec3d lastPos;
    private double blocksPerSecond;

    public PerformanceHud() {
        super("PerformanceHUD", Category.HUD, "FPS, ping, memory, CPU, coordinates and speed", false);
        hudSize();
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.player == null) {
            lastPos = null;
            return;
        }
        Vec3d pos = mc.player.getPos();
        if (lastPos != null) {
            blocksPerSecond = Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z) * 20.0;
        }
        lastPos = pos;
    }

    /** The JDK's process CPU load, read by reflection so a missing management module never breaks the mod. */
    private double processCpu() {
        long now = System.currentTimeMillis();
        if (now - lastCpuSample > 1000) {
            lastCpuSample = now;
            try {
                Object bean = ManagementFactory.getOperatingSystemMXBean();
                Method m = bean.getClass().getMethod("getProcessCpuLoad");
                m.setAccessible(true);
                cpuLoad = (Double) m.invoke(bean);
            } catch (ReflectiveOperationException | RuntimeException e) {
                cpuLoad = -1;
            }
        }
        return cpuLoad;
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        Theme th = Interface.theme();
        List<String[]> rows = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (fps.on()) {
            int f = mc.getCurrentFps();
            rows.add(new String[] {"FPS", String.valueOf(f)});
            colors.add(f >= 60 ? 0xFF6EE7A0 : (f >= 30 ? 0xFFFFC040 : 0xFFFF6B6B));
        }
        if (ping.on() && mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                int p = entry.getLatency();
                rows.add(new String[] {"Ping", p + " ms"});
                colors.add(p < 80 ? 0xFF6EE7A0 : (p < 200 ? 0xFFFFC040 : 0xFFFF6B6B));
            }
        }
        if (memory.on()) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long max = rt.maxMemory() / (1024 * 1024);
            rows.add(new String[] {"Memory", used + " / " + max + " MB"});
            colors.add(used > max * 0.9 ? 0xFFFF6B6B : th.text());
        }
        if (cpu.on()) {
            double c = processCpu();
            rows.add(new String[] {"CPU", c < 0 ? "n/a" : Math.round(c * 100) + "%"});
            colors.add(th.text());
        }
        if (coords.on() && mc.player != null) {
            rows.add(new String[] {"XYZ", String.format("%.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())});
            colors.add(th.text());
        }
        if (speed.on()) {
            rows.add(new String[] {"Speed", String.format("%.1f b/s", blocksPerSecond)});
            colors.add(th.text());
        }

        float s = scale.f();
        int labelW = 0;
        int valueW = 0;
        for (String[] r : rows) {
            labelW = Math.max(labelW, Ui.width(mc.textRenderer, r[0]));
            valueW = Math.max(valueW, Ui.width(mc.textRenderer, r[1]));
        }
        int w = Math.max(60, labelW + valueW + 24);
        int h = Math.max(1, rows.size()) * ROW_H + 6;
        ELEMENT.w = Math.round(w * s);
        ELEMENT.h = Math.round(h * s);
        if (rows.isEmpty() || mc.options.hudHidden) {
            return;
        }

        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        int bg = ((int) (255 * background.f()) << 24) | (th.panel() & 0xFFFFFF);
        Ui.roundRect(ctx, 0, 0, w, h, 6, bg);
        int y = 4;
        for (int i = 0; i < rows.size(); i++) {
            Ui.text(ctx, mc.textRenderer, rows.get(i)[0], 7, y, th.dim());
            Ui.text(ctx, mc.textRenderer, rows.get(i)[1], w - 7 - Ui.width(mc.textRenderer, rows.get(i)[1]), y, colors.get(i));
            y += ROW_H;
        }
        ctx.getMatrices().pop();
    }
}
