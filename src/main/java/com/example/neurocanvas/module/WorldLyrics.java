package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.EffectDraw;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Sing along in the world: the lines of a song appear as text in the 3D space around you, in time with the song.
 *
 * <p>Songs are files in neurocanvas/lyrics/. A plain .txt has one line per row and the lines come one after another every few seconds; an .lrc
 * file has a time in front of each line ([01:23.45] text) and plays exactly in time. The song starts when the module is switched on (bind it to a
 * key and press it when the music starts), and Restart starts it again.
 *
 * <p>Three layouts: lines <b>float</b> up around you as they come, lines stand in a <b>ring</b> around you that turns to the current one, or a
 * <b>stage</b> in front of you shows the current line big with the previous and the next one above and below it.
 */
public final class WorldLyrics extends Module {
    private static final String SAMPLE_TXT = """
            # One line per row. They come one after another (see "Seconds per line"). Rows that start with # and empty rows are skipped.
            the night is long and the lights are low
            we are the glow in the dark
            every step leaves a spark
            keep your head up, keep it bright
            """;
    private static final String SAMPLE_LRC = """
            [ti:Example]
            [00:01.00]this one is timed
            [00:04.00]each line has its moment
            [00:07.50]written in front of it
            [00:11.00]like [mm:ss.xx]
            """;
    private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");
    private static final VertexConsumerProvider.Immediate TEXT = VertexConsumerProvider.immediate(new BufferBuilder(4096));
    private static final Matrix4f WORK = new Matrix4f();

    private static final class Cue {
        final double time;
        final String text;
        float[] prefix;
        float width;

        Cue(double time, String text) {
            this.time = time;
            this.text = text;
        }

        /** Widths of the text up to each letter, so letters can be placed one by one and still sit where the whole line would put them. */
        void measure(TextRenderer tr) {
            if (prefix == null) {
                prefix = new float[text.length() + 1];
                for (int i = 0; i <= text.length(); i++) {
                    prefix[i] = tr.getWidth(text.substring(0, i));
                }
                width = prefix[text.length()];
            }
        }
    }

    private static final class Floater {
        Cue cue;
        double x, y, z;
        float age, life;
    }

    private final Setting song = add(Setting.choice("Song", 0, "(none)"));
    private final Setting layout = add(Setting.choice("Layout", 2, "Float around", "Ring", "Stage"));
    private final Setting letters = add(Setting.choice("Letters", 1, "Plain", "Wave", "Typewriter", "Rainbow wave"));
    private final Setting gap = add(Setting.number("Seconds per line", 1, 10, 3.5));
    private final Setting loop = add(Setting.bool("Loop", true));
    private final Setting distance = add(Setting.number("Distance", 2, 12, 5));
    private final Setting height = add(Setting.number("Height", -1, 4, 1));
    private final Setting size = add(Setting.number("Size", 0.01, 0.12, 0.04));
    private final Setting life = add(Setting.number("Float lifetime (s)", 1, 20, 6));
    private final Setting rise = add(Setting.number("Float rise speed", 0, 2, 0.3));
    private final Setting color1 = add(Setting.color("Color 1", 0.55));
    private final Setting color2 = add(Setting.color("Color 2", 0.9));
    private final Setting alpha = add(Setting.number("Alpha", 0.1, 1, 0.95));
    private final Setting glow = add(Setting.bool("Glow", true));
    private final Setting glowStrength = add(Setting.number("Glow strength", 0.2, 3, 1));
    private final Setting walls = add(Setting.bool("Through walls", true));
    private final Setting restart = add(Setting.button("Restart song"));
    private final Setting openFolder = add(Setting.button("Open songs folder"));
    private final Setting rescan = add(Setting.button("Reload songs"));

    private final Path folder;
    private final List<Cue> cues = new ArrayList<>();
    private boolean timed;
    private String loadedSong = "";
    private final List<Floater> floaters = new ArrayList<>();
    private final Random random = new Random();
    private double lastT;
    private double elapsed;
    private int lastIndex = -1;
    private double sinceCue;
    private float anchorYaw;
    private boolean wasOn;
    private boolean scanned;

    public WorldLyrics(Path dir) {
        super("WorldLyrics", Category.WORLD, "Lines of a song in the 3D world around you, in time with the music (songs are in neurocanvas/lyrics)", false);
        this.folder = dir.resolve("lyrics");
    }

    // ---- songs ------------------------------------------------------------------------------------------------

    private void writeSamples() {
        try {
            if (!Files.isDirectory(folder)) {
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("example.txt"), SAMPLE_TXT, StandardCharsets.UTF_8);
                Files.writeString(folder.resolve("example (timed).lrc"), SAMPLE_LRC, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // Without samples the list is just empty.
        }
    }

    private void rescanSongs() {
        scanned = true;
        writeSamples();
        String current = song.option();
        List<String> names = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(folder)) {
            files.map(p -> p.getFileName().toString()).filter(n -> {
                String l = n.toLowerCase();
                return l.endsWith(".txt") || l.endsWith(".lrc");
            }).sorted(String.CASE_INSENSITIVE_ORDER).forEach(names::add);
        } catch (IOException ignored) {
            // No songs found.
        }
        if (names.isEmpty()) {
            names.add("(none)");
        }
        song.setOptions(names.toArray(new String[0]));
        song.value = Math.max(0, names.indexOf(current));
        loadedSong = "\u0000";
    }

    private void loadSong(String name) {
        loadedSong = name;
        cues.clear();
        timed = false;
        floaters.clear();
        restartClock();
        if (name.equals("(none)")) {
            return;
        }
        try {
            List<String> rows = Files.readAllLines(folder.resolve(name), StandardCharsets.UTF_8);
            if (name.toLowerCase().endsWith(".lrc")) {
                timed = true;
                for (String row : rows) {
                    Matcher m = STAMP.matcher(row);
                    List<Double> stamps = new ArrayList<>();
                    int end = 0;
                    while (m.find() && m.start() == end) {
                        stamps.add(Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2)));
                        end = m.end();
                    }
                    String text = row.substring(end).strip();
                    if (!stamps.isEmpty() && !text.isEmpty()) {
                        for (double t : stamps) {
                            cues.add(new Cue(t, clip(text)));
                        }
                    }
                }
                cues.sort(Comparator.comparingDouble(c -> c.time));
            } else {
                for (String row : rows) {
                    String t = row.strip();
                    if (!t.isEmpty() && !t.startsWith("#")) {
                        cues.add(new Cue(cues.size(), clip(t)));
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            cues.clear();
        }
    }

    private static String clip(String s) {
        return s.length() > 90 ? s.substring(0, 90) : s;
    }

    private double timeOf(int i) {
        return timed ? cues.get(i).time : i * gap.value;
    }

    private double total() {
        if (cues.isEmpty()) {
            return 0;
        }
        return timeOf(cues.size() - 1) + (timed ? 4.0 : gap.value);
    }

    private void restartClock() {
        elapsed = 0;
        lastIndex = -1;
        sinceCue = 0;
        floaters.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        anchorYaw = mc.player == null ? 0f : mc.player.getYaw();
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (!scanned || rescan.on()) {
            if (rescan.on()) {
                rescan.set(0);
            }
            rescanSongs();
        }
        if (openFolder.on()) {
            openFolder.set(0);
            writeSamples();
            net.minecraft.util.Util.getOperatingSystem().open(folder.toFile());
        }
        String want = song.option();
        if (!want.equals(loadedSong)) {
            loadSong(want);
        }
        if (restart.on()) {
            restart.set(0);
            restartClock();
        }
        if (enabled && !wasOn) {
            restartClock();
        }
        wasOn = enabled;
        if (mc.world == null) {
            floaters.clear();
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------

    private static float ease(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || cues.isEmpty()) {
            return;
        }
        double dt = MathHelper.clamp(env.t() - lastT, 0.0, 0.1);
        lastT = env.t();
        elapsed += dt;
        double len = total();
        if (elapsed >= len) {
            if (!loop.on()) {
                return;
            }
            elapsed -= len;
            lastIndex = -1;
        }
        int idx = -1;
        for (int i = 0; i < cues.size(); i++) {
            if (timeOf(i) <= elapsed) {
                idx = i;
            }
        }
        if (idx != lastIndex) {
            lastIndex = idx;
            sinceCue = 0;
            if (idx >= 0 && layout.index() == 0) {
                spawnFloater(p, mc.textRenderer, cues.get(idx));
            }
        }
        sinceCue += dt;
        TextRenderer tr = mc.textRenderer;
        for (Cue c : cues) {
            c.measure(tr);
        }
        Vec3d pos = EffectDraw.lerpPos(p, env.tickDelta());
        switch (layout.index()) {
            case 0 -> drawFloaters(env, tr, dt);
            case 1 -> drawRing(env, tr, pos, idx);
            default -> drawStage(env, tr, pos, idx);
        }
        TEXT.draw();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
    }

    private void spawnFloater(PlayerEntity p, TextRenderer tr, Cue cue) {
        Floater f = new Floater();
        f.cue = cue;
        double a = random.nextDouble() * Math.PI * 2;
        double r = 1.0 + Math.sqrt(random.nextDouble()) * Math.max(0.3, distance.value - 1.0);
        f.x = p.getX() + Math.cos(a) * r;
        f.z = p.getZ() + Math.sin(a) * r;
        f.y = p.getY() + 0.5 + random.nextDouble() * 2.5 + height.value * 0.3;
        f.life = (float) (life.value * (0.85 + 0.3 * random.nextDouble()));
        floaters.add(f);
    }

    private void drawFloaters(RenderEnv env, TextRenderer tr, double dt) {
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Floater f = floaters.get(i);
            f.age += (float) dt;
            if (f.age >= f.life) {
                floaters.remove(i);
                continue;
            }
            float t = f.age / f.life;
            float a = MathHelper.clamp(t / 0.15f, 0f, 1f) * MathHelper.clamp((1f - t) / 0.3f, 0f, 1f);
            drawLine(env, tr, f.cue, f.x - env.cam().x, f.y + f.age * rise.value - env.cam().y, f.z - env.cam().z, size.f(), a * alpha.f(),
                    MathHelper.clamp(f.age / 1.2f, 0f, 1f), t);
        }
    }

    /** A direction on the ground for a yaw in degrees (the game's yaw 0 looks along +Z). */
    private static double dirX(double yawDeg) {
        return -Math.sin(Math.toRadians(yawDeg));
    }

    private static double dirZ(double yawDeg) {
        return Math.cos(Math.toRadians(yawDeg));
    }

    private void drawRing(RenderEnv env, TextRenderer tr, Vec3d pos, int idx) {
        double r = distance.value;
        float e = idx <= 0 ? 1f : ease((float) (sinceCue / 0.55));
        Cue cur = cues.get(Math.max(idx, 0));
        // One line takes this much of the ring, so neighbours do not overlap.
        double step = Math.max(40.0, Math.toDegrees(cur.width * size.f() * 1.1 / r));
        for (int j = Math.max(0, idx - 2); j <= Math.min(cues.size() - 1, idx + 3); j++) {
            double slot = j - (Math.max(idx, 0) - 1 + e);
            double yaw = anchorYaw + slot * -step + step;
            float near = (float) Math.abs(slot - 1.0);
            float a = MathHelper.clamp(1f - near * 0.4f, 0f, 1f);
            float sc = size.f() * (1f - 0.18f * Math.min(near, 1.5f));
            drawLine(env, tr, cues.get(j), pos.x + dirX(yaw) * r - env.cam().x, pos.y + 1.2 + height.value - env.cam().y,
                    pos.z + dirZ(yaw) * r - env.cam().z, sc, a * alpha.f(), j == idx ? MathHelper.clamp((float) (sinceCue / 1.2), 0f, 1f) : 1f,
                    j == idx ? 0f : 0.7f);
        }
    }

    private void drawStage(RenderEnv env, TextRenderer tr, Vec3d pos, int idx) {
        double r = distance.value;
        float e = idx <= 0 ? 1f : ease((float) (sinceCue / 0.4));
        float lineH = size.f() * 9f * 2.4f;
        double cx = pos.x + dirX(anchorYaw) * r;
        double cz = pos.z + dirZ(anchorYaw) * r;
        double baseY = pos.y + 1.6 + height.value;
        for (int j = Math.max(0, idx - 1); j <= Math.min(cues.size() - 1, idx + 2); j++) {
            double slot = j - (Math.max(idx, 0) - 1 + e);
            float near = (float) Math.abs(slot - 1.0);
            float a = MathHelper.clamp(1f - near * 0.55f, 0f, 1f);
            float sc = size.f() * (1.35f - 0.4f * Math.min(near, 1.5f));
            drawLine(env, tr, cues.get(j), cx - env.cam().x, baseY - (slot - 1.0) * lineH - env.cam().y, cz - env.cam().z, sc, a * alpha.f(),
                    j == idx ? MathHelper.clamp((float) (sinceCue / 1.0), 0f, 1f) : 1f, j == idx ? 0f : 0.7f);
        }
    }

    private int mix(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * One line of text turned to face the camera, upright like a name tag. {@code reveal} (0..1) is how much of it is written so far (for the
     * typewriter), {@code tone} (0..1) picks the colour between the two.
     */
    private void drawLine(RenderEnv env, TextRenderer tr, Cue cue, double rx, double ry, double rz, float scale, float a, float reveal, float tone) {
        if (a < 0.02f) {
            return;
        }
        WORK.set(env.view()).translate((float) rx, (float) ry, (float) rz).rotate(env.mc().gameRenderer.getCamera().getRotation())
                .scale(-scale, -scale, scale);
        TextRenderer.TextLayerType layer = walls.on() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL;
        int c1 = color1.argb() & 0xFFFFFF;
        int c2 = color2.argb() & 0xFFFFFF;
        float g = glowStrength.f();
        int mode = letters.index();
        float x0 = -cue.width / 2f;
        double t = env.t();
        if (mode == 0 || cue.text.length() < 2) {
            int rgb = mix(c1, c2, tone);
            paint(tr, cue.text, x0, 0f, rgb, a, g, layer, 8);
            return;
        }
        int n = cue.text.length();
        int shown = mode == 2 ? Math.round(n * reveal) : n;
        for (int i = 0; i < shown; i++) {
            char ch = cue.text.charAt(i);
            if (ch == ' ') {
                continue;
            }
            float dy = mode == 2 ? 0f : (float) Math.sin(t * 3.2 + i * 0.55) * 1.8f;
            int rgb = mode == 3 ? (java.awt.Color.HSBtoRGB((float) ((i * 0.045 + t * 0.2) % 1.0), 0.65f, 1f) & 0xFFFFFF)
                    : mix(c1, c2, MathHelper.clamp(tone + i / (float) n * 0.6f, 0f, 1f));
            paint(tr, String.valueOf(ch), x0 + cue.prefix[i], dy, rgb, a, g, layer, 4);
        }
    }

    private void paint(TextRenderer tr, String s, float x, float y, int rgb, float a, float g, TextRenderer.TextLayerType layer, int copies) {
        if (glow.on()) {
            int gc = ((int) (MathHelper.clamp(a * 0.2f * g, 0f, 1f) * 255) << 24) | rgb;
            for (int k = 0; k < copies; k++) {
                double ang = k * Math.PI * 2 / copies;
                float dx = (float) Math.cos(ang) * 1.1f;
                float dy = (float) Math.sin(ang) * 1.1f;
                tr.draw(s, x + dx, y + dy, gc, false, WORK, TEXT, layer, 0, 0xF000F0);
            }
        }
        tr.draw(s, x, y, ((int) (MathHelper.clamp(a, 0f, 1f) * 255) << 24) | (rgb | 0x303030) & 0xFFFFFF, false, WORK, TEXT, layer, 0, 0xF000F0);
    }
}
