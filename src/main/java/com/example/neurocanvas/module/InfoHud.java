package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/** Time on this server, real-world clock, the direction you face, your biome and the in-game day time. */
public final class InfoHud extends Module {
    private static final int ROW_H = 12;
    private static final HudLayout.Element ELEMENT = HudLayout.register("info", "Info", false, 0.006, 0.3);

    static {
        ELEMENT.w = 100;
        ELEMENT.h = ROW_H * 3 + 6;
    }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.7));
    private final Setting session = add(Setting.bool("Time on server", true));
    private final Setting clock = add(Setting.bool("Clock", true));
    private final Setting direction = add(Setting.bool("Direction", true));
    private final Setting biome = add(Setting.bool("Biome", false));
    private final Setting gameTime = add(Setting.bool("In-game time", false));

    private long joinedAt;

    public InfoHud() {
        super("InfoHUD", Category.HUD, "Session time, clock, facing direction, biome and game time", false);
        hudSize();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> joinedAt = 0);
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (joinedAt == 0 && mc.player != null) {
            joinedAt = System.currentTimeMillis();
        }
    }

    private static String duration(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private static String arrow(Direction d) {
        return switch (d) {
            case NORTH -> "N  (-Z)";
            case SOUTH -> "S  (+Z)";
            case WEST -> "W  (-X)";
            default -> "E  (+X)";
        };
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        Theme th = Interface.theme();
        List<String[]> rows = new ArrayList<>();
        if (session.on()) {
            rows.add(new String[] {"Session", duration(System.currentTimeMillis() - joinedAt)});
        }
        if (clock.on()) {
            rows.add(new String[] {"Time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))});
        }
        if (direction.on()) {
            rows.add(new String[] {"Facing", arrow(mc.player.getHorizontalFacing())});
        }
        if (biome.on()) {
            String name = mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("?");
            rows.add(new String[] {"Biome", name.replace('_', ' ')});
        }
        if (gameTime.on()) {
            long t = mc.world.getTimeOfDay() % 24000L;
            long hour = (t / 1000 + 6) % 24;
            rows.add(new String[] {"Game", String.format("%02d:%02d", hour, (t % 1000) * 60 / 1000)});
        }
        float s = scale.f();
        int labelW = 0;
        int valueW = 0;
        for (String[] r : rows) {
            labelW = Math.max(labelW, Ui.width(mc.textRenderer, r[0]));
            valueW = Math.max(valueW, Ui.width(mc.textRenderer, r[1]));
        }
        int w = Math.max(70, labelW + valueW + 24);
        int h = Math.max(1, rows.size()) * ROW_H + 6;
        ELEMENT.w = Math.round(w * s);
        ELEMENT.h = Math.round(h * s);
        if (rows.isEmpty() || mc.options.hudHidden) {
            return;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        Ui.roundRect(ctx, 0, 0, w, h, 6, ((int) (255 * background.f()) << 24) | (th.panel() & 0xFFFFFF));
        int y = 4;
        for (String[] r : rows) {
            Ui.text(ctx, mc.textRenderer, r[0], 7, y, th.dim());
            Ui.text(ctx, mc.textRenderer, r[1], w - 7 - Ui.width(mc.textRenderer, r[1]), y, th.text());
            y += ROW_H;
        }
        ctx.getMatrices().pop();
    }
}
