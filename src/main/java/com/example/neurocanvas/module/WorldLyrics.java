package com.example.neurocanvas.module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Lines of a song (or any text) float up around you in the world, each one turned to face the camera, fading in and out, with an optional glow.
 * The text lives in neurocanvas/lyrics.txt: one line per row, empty rows and rows starting with # are skipped.
 */
public final class WorldLyrics extends Module {
    private static final String SAMPLE = """
            # One line per row; these lines float around you. Replace them with your own text and press Reload.
            # Rows that start with # and empty rows are skipped.
            the night is long and the lights are low
            we are the glow in the dark
            every step leaves a spark
            keep your head up, keep it bright
            """;
    private static final VertexConsumerProvider.Immediate TEXT = VertexConsumerProvider.immediate(new BufferBuilder(4096));
    private static final Matrix4f WORK = new Matrix4f();

    private static final class Line {
        String text;
        double x, y, z;
        float age, life;
        float width;
    }

    private final Setting order = add(Setting.choice("Order", 0, "In order", "Random"));
    private final Setting rate = add(Setting.number("Lines per second", 0.1, 4, 0.7));
    private final Setting life = add(Setting.number("Lifetime (s)", 1, 20, 6));
    private final Setting radius = add(Setting.number("Radius", 1, 14, 5));
    private final Setting height = add(Setting.number("Height spread", 0, 6, 2.5));
    private final Setting rise = add(Setting.number("Rise speed", 0, 2, 0.3));
    private final Setting size = add(Setting.number("Size", 0.01, 0.12, 0.035));
    private final Setting maxLines = add(Setting.number("Max lines", 3, 60, 24));
    private final Setting color1 = add(Setting.color("Color 1", 0.55));
    private final Setting color2 = add(Setting.color("Color 2", 0.9));
    private final Setting alpha = add(Setting.number("Alpha", 0.1, 1, 0.95));
    private final Setting glow = add(Setting.bool("Glow", true));
    private final Setting glowStrength = add(Setting.number("Glow strength", 0.2, 3, 1));
    private final Setting walls = add(Setting.bool("Through walls", true));
    private final Setting openFile = add(Setting.button("Open lyrics file"));
    private final Setting reload = add(Setting.button("Reload lyrics"));

    private final Path file;
    private final List<String> lines = new ArrayList<>();
    private final List<Line> live = new ArrayList<>();
    private final Random random = new Random();
    private double lastT;
    private double carry;
    private int next;
    private boolean loaded;

    public WorldLyrics(Path dir) {
        super("WorldLyrics", Category.WORLD, "Lines of a song floating up around you in the world (the text is in neurocanvas/lyrics.txt)", false);
        this.file = dir.resolve("lyrics.txt");
    }

    private void load() {
        loaded = true;
        lines.clear();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);
            }
            for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = l.strip();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    lines.add(t.length() > 90 ? t.substring(0, 90) : t);
                }
            }
        } catch (IOException ignored) {
            // No lines: nothing floats.
        }
        next = 0;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (openFile.on()) {
            openFile.set(0);
            if (!Files.exists(file)) {
                load();
            }
            net.minecraft.util.Util.getOperatingSystem().open(file.toFile());
        }
        if (reload.on()) {
            reload.set(0);
            load();
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("[WorldLyrics] " + lines.size() + " line(s) loaded"), false);
            }
        }
        if (mc.world == null) {
            live.clear();
        }
    }

    private void spawn(PlayerEntity p, TextRenderer tr) {
        String text = order.index() == 0 ? lines.get(next++ % lines.size()) : lines.get(random.nextInt(lines.size()));
        Line l = new Line();
        l.text = text;
        double a = random.nextDouble() * Math.PI * 2;
        double r = 0.8 + Math.sqrt(random.nextDouble()) * Math.max(0.2, radius.value - 0.8);
        l.x = p.getX() + Math.cos(a) * r;
        l.z = p.getZ() + Math.sin(a) * r;
        l.y = p.getY() + 0.3 + random.nextDouble() * height.value;
        l.life = (float) (life.value * (0.85 + 0.3 * random.nextDouble()));
        l.width = tr.getWidth(text);
        live.add(l);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            return;
        }
        if (!loaded) {
            load();
        }
        double dt = MathHelper.clamp(env.t() - lastT, 0.0, 0.1);
        lastT = env.t();
        TextRenderer tr = mc.textRenderer;
        if (!lines.isEmpty()) {
            carry += rate.value * dt;
            while (carry >= 1.0) {
                carry -= 1.0;
                if (live.size() < (int) maxLines.value) {
                    spawn(p, tr);
                }
            }
        }
        for (int i = live.size() - 1; i >= 0; i--) {
            Line l = live.get(i);
            l.age += (float) dt;
            if (l.age >= l.life) {
                live.remove(i);
            }
        }
        if (live.isEmpty()) {
            return;
        }
        TextRenderer.TextLayerType layer = walls.on() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL;
        int c1 = color1.argb();
        int c2 = color2.argb();
        float s = size.f();
        float g = glowStrength.f();
        for (Line l : live) {
            float f = l.age / l.life;
            float a = MathHelper.clamp(f / 0.15f, 0f, 1f) * MathHelper.clamp((1f - f) / 0.3f, 0f, 1f) * alpha.f();
            if (a < 0.02f) {
                continue;
            }
            int r = (int) MathHelper.lerp(f, (c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF);
            int gr = (int) MathHelper.lerp(f, (c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF);
            int b = (int) MathHelper.lerp(f, c1 & 0xFF, c2 & 0xFF);
            int rgb = (r << 16) | (gr << 8) | b;
            // Faces the camera, upright like a name tag; rises as it ages.
            WORK.set(env.view()).translate((float) (l.x - env.cam().x), (float) (l.y + l.age * rise.value - env.cam().y), (float) (l.z - env.cam().z))
                    .rotate(mc.gameRenderer.getCamera().getRotation()).scale(-s, -s, s);
            float x = -l.width / 2f;
            if (glow.on()) {
                int gc = ((int) (MathHelper.clamp(a * 0.2f * g, 0f, 1f) * 255) << 24) | rgb;
                for (int k = 0; k < 8; k++) {
                    double ang = k * Math.PI / 4;
                    float dx = (float) Math.cos(ang) * (k % 2 == 0 ? 1.1f : 0.8f);
                    float dy = (float) Math.sin(ang) * (k % 2 == 0 ? 1.1f : 0.8f);
                    tr.draw(l.text, x + dx, dy, gc, false, WORK, TEXT, layer, 0, 0xF000F0);
                }
            }
            tr.draw(l.text, x, 0, ((int) (a * 255) << 24) | 0xFFFFFF & (rgb | 0x303030), false, WORK, TEXT, layer, 0, 0xF000F0);
        }
        TEXT.draw();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
    }
}
