package com.example.neurocanvas.module;

import com.example.neurocanvas.client.LyricsClient;
import com.example.neurocanvas.client.NowPlaying;
import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
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

        /** Where each word starts (as a letter index) and how far into the line, 0..1 by letters, it is sung; every letter knows its word. */
        int[] wordStart;
        float[] wordFrac;
        int[] wordOf;

        /** Widths of the text up to each letter, so letters and words can be placed one by one and still sit where the whole line would put them. */
        void measure(TextRenderer tr) {
            if (prefix != null) {
                return;
            }
            prefix = new float[text.length() + 1];
            for (int i = 0; i <= text.length(); i++) {
                prefix[i] = tr.getWidth(text.substring(0, i));
            }
            width = prefix[text.length()];
            List<Integer> starts = new ArrayList<>();
            wordOf = new int[text.length()];
            boolean inWord = false;
            int letters = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == ' ') {
                    inWord = false;
                    wordOf[i] = Math.max(0, starts.size() - 1);
                    continue;
                }
                if (!inWord) {
                    inWord = true;
                    starts.add(i);
                }
                wordOf[i] = starts.size() - 1;
                letters++;
            }
            wordStart = new int[starts.size()];
            wordFrac = new float[starts.size()];
            int before = 0;
            for (int k = 0; k < starts.size(); k++) {
                wordStart[k] = starts.get(k);
                wordFrac[k] = letters == 0 ? 0f : before / (float) letters;
                int end = wordStart[k];
                while (end < text.length() && text.charAt(end) != ' ') {
                    end++;
                    before++;
                }
            }
        }
    }

    private static final class Floater {
        Cue cue;
        int index;
        double x, y, z;
        /** The way it faces, fixed when it appears: toward where you stood. */
        double face;
        float age, life;
    }

    /** One word hanging in the air: where it appeared (fixed in the world), when, and how long it stays. */
    private static final class Word {
        String text;
        float width;
        double x, y, z;
        double face;
        float tone;
        float age, life;
    }

    private final java.util.List<Word> words = new java.util.ArrayList<>();
    private int wordsDone;

    private static final HudLayout.Element PLAYER = HudLayout.register("musicplayer", "Music player", false, 0.006, 0.6);

    private final Setting source = add(Setting.choice("Source", 0, "Browser / player (automatic)", "Songs folder"));
    private final Setting offset = add(Setting.number("Lyrics offset (s)", -5, 5, 0));
    private final Setting showPlayer = add(Setting.bool("Show player", true));
    private final Setting song = add(Setting.choice("Song (songs folder)", 0, "(none)"));
    private final Setting layout = add(Setting.choice("Layout", 3, "Float around", "Ring", "Stage", "Scattered words"));
    private final Setting reveal = add(Setting.choice("Reveal", 1, "Whole line", "Word by word"));
    private final Setting spread = add(Setting.number("Spread (degrees)", 5, 60, 28));
    private final Setting wordLife = add(Setting.number("Word lifetime (s)", 1, 15, 4));
    private final Setting facing = add(Setting.choice("Facing", 0, "Fixed in the world", "Toward the camera"));
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
    private final Setting detectInfo = add(Setting.button("Music detection info"));
    private final Setting copyScript = add(Setting.button("Copy browser script"));
    private final Setting openScript = add(Setting.button("Open browser script file"));
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
        this.scriptFile = dir.resolve("neurocanvas-nowplaying.user.js");
        hudSize();
    }

    // ---- the music the browser or the system player is playing ----------------------------------------------

    private static final String WAITING = "waiting";
    private static final String LOOKING = "looking";
    private static final String SYNCED = "synced";
    private static final String SPREAD = "spread";
    private static final String MISSING = "missing";

    private final Path scriptFile;
    private boolean wasLive;
    private String liveKey = "";
    private String liveStatus = WAITING;
    private String artKey = "";
    private Identifier artId;

    private void pollLive(MinecraftClient mc) {
        NowPlaying.Track t = NowPlaying.current();
        String key = t == null ? "" : t.key();
        if (key.equals(liveKey)) {
            return;
        }
        liveKey = key;
        cues.clear();
        floaters.clear();
        timed = true;
        lastIndex = -1;
        releaseArt(mc);
        if (t == null) {
            liveStatus = WAITING;
            return;
        }
        liveStatus = LOOKING;
        LyricsClient.find(t).whenComplete((found, error) -> mc.execute(() -> {
            if (!key.equals(liveKey)) {
                return;
            }
            if (error != null || found == null) {
                liveStatus = MISSING;
                return;
            }
            cues.clear();
            for (LyricsClient.Line l : found.lines()) {
                cues.add(new Cue(l.time(), l.text()));
            }
            lastIndex = -1;
            liveStatus = found.synced() ? SYNCED : SPREAD;
        }));
        loadCover(mc, t);
    }

    private void releaseArt(MinecraftClient mc) {
        artKey = "";
        if (artId != null) {
            mc.getTextureManager().destroyTexture(artId);
            artId = null;
        }
    }

    /** Covers come only from https hosts with a real name (never an address on this computer or the local network) or from a file the player wrote. */
    private static boolean coverAllowed(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            if ("file".equals(u.getScheme())) {
                String path = u.getPath() == null ? "" : u.getPath().toLowerCase();
                return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp");
            }
            String host = u.getHost();
            return "https".equals(u.getScheme()) && u.getUserInfo() == null && host != null && host.contains(".") && !host.matches("[0-9.]+")
                    && !host.contains(":") && !host.endsWith(".local") && !host.endsWith(".internal") && !host.endsWith(".lan");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static final java.net.http.HttpClient COVER_HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(6)).build();

    private void loadCover(MinecraftClient mc, NowPlaying.Track t) {
        String key = t.key();
        artKey = key;
        if (t.art().isEmpty() || !coverAllowed(t.art())) {
            return;
        }
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data;
                if (t.art().startsWith("file:")) {
                    Path f = Path.of(java.net.URI.create(t.art()));
                    if (Files.size(f) > 3_000_000) {
                        return null;
                    }
                    data = Files.readAllBytes(f);
                } else {
                    for (java.net.InetAddress a : java.net.InetAddress.getAllByName(java.net.URI.create(t.art()).getHost())) {
                        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()
                                || (a.getAddress().length == 16 && (a.getAddress()[0] & 0xFE) == 0xFC)) {
                            return null;
                        }
                    }
                    java.net.http.HttpResponse<java.io.InputStream> r = COVER_HTTP.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(t.art())).timeout(java.time.Duration.ofSeconds(10)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                    if (r.statusCode() != 200) {
                        return null;
                    }
                    data = r.body().readNBytes(3_000_001);
                    if (data.length > 3_000_000) {
                        return null;
                    }
                }
                return com.example.neurocanvas.client.IconDecoder.decodeCover(data, 128);
            } catch (IOException | RuntimeException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }).thenAccept(px -> mc.execute(() -> {
            if (px == null || !key.equals(artKey)) {
                return;
            }
            NativeImage img = new NativeImage(128, 128, false);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int argb = px[y * 128 + x];
                    img.setColor(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
                }
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            tex.setFilter(true, false);
            Identifier id = new Identifier("neurocanvas", "nowplaying/" + java.util.UUID.randomUUID());
            mc.getTextureManager().registerTexture(id, tex);
            if (artId != null) {
                mc.getTextureManager().destroyTexture(artId);
            }
            artId = id;
        }));
    }

    /** The browser script is a file inside the mod; it is also written next to the settings so it can be opened or installed from there. */
    private String scriptText() {
        try (java.io.InputStream in = WorldLyrics.class.getResourceAsStream("/neurocanvas-nowplaying.user.js")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void writeScript() {
        try {
            Files.createDirectories(scriptFile.getParent());
            Files.writeString(scriptFile, scriptText(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // The copy to the clipboard still works.
        }
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
        if (detectInfo.on()) {
            detectInfo.set(0);
            NowPlaying.start();
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("[WorldLyrics] " + NowPlaying.describe()), false);
            }
        }
        if (copyScript.on()) {
            copyScript.set(0);
            writeScript();
            mc.keyboard.setClipboard(scriptText());
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("[WorldLyrics] The browser script is copied. In Tampermonkey (or Violentmonkey) create a "
                        + "new script and paste it, then play music in the browser."), false);
            }
        }
        if (openScript.on()) {
            openScript.set(0);
            writeScript();
            net.minecraft.util.Util.getOperatingSystem().open(scriptFile.toFile());
        }
        boolean live = source.index() == 0;
        if (live != wasLive) {
            wasLive = live;
            loadedSong = "\u0000";
            liveKey = "\u0000";
            cues.clear();
            floaters.clear();
            timed = live;
        }
        if (live) {
            if (enabled) {
                NowPlaying.start();
                pollLive(mc);
            }
        } else {
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
        }
        restart.set(0);
        wasOn = enabled;
        if (mc.world == null) {
            floaters.clear();
            words.clear();
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
        if (source.index() == 0) {
            if (NowPlaying.current() == null) {
                return;
            }
            elapsed = NowPlaying.position() + offset.value;
        } else {
            elapsed += dt;
            double len = total();
            if (elapsed >= len) {
                if (!loop.on()) {
                    return;
                }
                elapsed -= len;
                lastIndex = -1;
            }
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
            wordsDone = 0;
            if (idx >= 0 && layout.index() == 0) {
                spawnFloater(p, idx);
            }
        }
        sinceCue += dt;
        TextRenderer tr = mc.textRenderer;
        for (Cue c : cues) {
            c.measure(tr);
        }
        Vec3d pos = EffectDraw.lerpPos(p, env.tickDelta());
        if (layout.index() == 1 || layout.index() == 2) {
            // The lines stand where you were looking; when you look more than ~55 degrees away they glide round to where you look now.
            double diff = MathHelper.wrapDegrees(p.getYaw() - anchorYaw);
            if (Math.abs(diff) > 55) {
                anchorYaw += (diff - Math.signum(diff) * 55) * (1 - Math.exp(-dt * 3));
            }
        }
        switch (layout.index()) {
            case 3 -> drawWords(env, tr, idx, dt);
            case 0 -> drawFloaters(env, tr, dt);
            case 1 -> drawRing(env, tr, pos, idx);
            default -> drawStage(env, tr, pos, idx);
        }
        TEXT.draw();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
    }

    /**
     * Words come one by one, each at the moment it is sung, at a random spot in the air in front of you: inside a cone around where the camera
     * looks (so never behind you or out of sight), at a random distance. They stay where they appeared, fade in, drift up a little and fade out.
     */
    private void drawWords(RenderEnv env, TextRenderer tr, int idx, double dt) {
        if (idx >= 0) {
            Cue cue = cues.get(idx);
            float sec = lineSeconds(idx);
            while (wordsDone < cue.wordStart.length) {
                double at = timeOf(idx) + cue.wordFrac[wordsDone] * sec * 0.9;
                if (elapsed < at) {
                    break;
                }
                // Joining in the middle of a line: only words of the last second still appear.
                if (elapsed - at < 1.0 && words.size() < 80) {
                    spawnWord(env, tr, cue, wordsDone);
                }
                wordsDone++;
            }
        }
        TextRenderer.TextLayerType layer = walls.on() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL;
        int c1 = color1.argb() & 0xFFFFFF;
        int c2 = color2.argb() & 0xFFFFFF;
        for (int i = words.size() - 1; i >= 0; i--) {
            Word w = words.get(i);
            w.age += (float) dt;
            if (w.age >= w.life) {
                words.remove(i);
                continue;
            }
            float t = w.age / w.life;
            float a = MathHelper.clamp(w.age / 0.25f, 0f, 1f) * MathHelper.clamp((1f - t) / 0.35f, 0f, 1f) * alpha.f();
            if (a < 0.02f) {
                continue;
            }
            float pop = 0.6f + 0.4f * MathHelper.clamp(w.age / 0.3f, 0f, 1f);
            WORK.set(env.view()).translate((float) (w.x - env.cam().x), (float) (w.y + w.age * rise.value - env.cam().y), (float) (w.z - env.cam().z));
            if (facing.index() == 1) {
                WORK.rotate(env.mc().gameRenderer.getCamera().getRotation());
            } else {
                WORK.rotateY((float) readable(w.face, w.x - env.cam().x, w.z - env.cam().z));
            }
            float sc = size.f() * pop;
            WORK.scale(-sc, -sc, sc);
            paint(tr, w.text, -w.width / 2f, 0f, mix(c1, c2, w.tone), a, glowStrength.f(), layer, 8);
        }
    }

    private void spawnWord(RenderEnv env, TextRenderer tr, Cue cue, int k) {
        int start = cue.wordStart[k];
        int end = start;
        while (end < cue.text.length() && cue.text.charAt(end) != ' ') {
            end++;
        }
        Word w = new Word();
        w.text = cue.text.substring(start, end);
        w.width = tr.getWidth(w.text);
        double yaw = Math.toRadians(env.camYaw() + (random.nextDouble() * 2 - 1) * spread.value);
        double pitch = Math.toRadians(env.camPitch() + (random.nextDouble() * 2 - 1) * spread.value * 0.6 - 4);
        double dist = distance.value * (0.55 + 0.45 * random.nextDouble());
        w.x = env.cam().x + -Math.sin(yaw) * Math.cos(pitch) * dist;
        w.y = env.cam().y + -Math.sin(pitch) * dist + height.value * 0.15;
        w.z = env.cam().z + Math.cos(yaw) * Math.cos(pitch) * dist;
        w.face = Math.atan2(env.cam().x - w.x, env.cam().z - w.z);
        w.tone = random.nextFloat();
        w.life = (float) (wordLife.value * (0.85 + 0.3 * random.nextDouble()));
        words.add(w);
    }

    private void spawnFloater(PlayerEntity p, int index) {
        Floater f = new Floater();
        f.cue = cues.get(index);
        f.index = index;
        // In the cone the player is looking into, never behind them.
        double yaw = Math.toRadians(p.getYaw() + (random.nextDouble() * 2 - 1) * Math.min(spread.value, 40));
        double pitch = Math.toRadians(p.getPitch() + (random.nextDouble() * 2 - 1) * 14 - 4);
        double r = 2.5 + random.nextDouble() * Math.max(0.5, distance.value - 2.5);
        Vec3d eye = p.getEyePos();
        f.x = eye.x - Math.sin(yaw) * Math.cos(pitch) * r;
        f.y = eye.y - Math.sin(pitch) * r + height.value * 0.15;
        f.z = eye.z + Math.cos(yaw) * Math.cos(pitch) * r;
        f.life = (float) (life.value * (0.85 + 0.3 * random.nextDouble()));
        f.face = Math.atan2(eye.x - f.x, eye.z - f.z);
        floaters.add(f);
    }

    /** How long the line is sung, in seconds: from its time to the next line's. */
    private float lineSeconds(int j) {
        double next = j + 1 < cues.size() ? timeOf(j + 1) : timeOf(j) + (timed ? 4.0 : gap.value);
        return (float) MathHelper.clamp(next - timeOf(j), 0.8, 8.0);
    }

    /** How much of line j is out (0..1): lines before the current one are complete, the current one fills in as it is sung, the next ones wait. */
    private float progressOf(int j, int idx) {
        boolean timedReveal = reveal.index() == 1 || letters.index() == 2;
        if (!timedReveal || j < idx) {
            return 1f;
        }
        if (j > idx) {
            return 0f;
        }
        return MathHelper.clamp((float) ((elapsed - timeOf(j)) / (lineSeconds(j) * 0.9)), 0f, 1f);
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
            boolean timedReveal = reveal.index() == 1 || letters.index() == 2;
            float sec = lineSeconds(f.index);
            float shown = timedReveal ? MathHelper.clamp(f.age / (sec * 0.9f), 0f, 1f) : 1f;
            drawLine(env, tr, f.cue, f.x - env.cam().x, f.y + f.age * rise.value - env.cam().y, f.z - env.cam().z, f.face, size.f(), a * alpha.f(),
                    shown, sec, t);
        }
    }

    /**
     * A text fixed in the world has a readable side and a mirrored one. In this drawing the readable side is the one the text's own -Z points to
     * (seen in the game: facing +Z at the camera gave mirrored letters), so the text is turned half a turn whenever that side is not toward the
     * camera. It then reads correctly from wherever you stand while its place stays fixed. (rx, rz) is the text's position relative to the camera.
     */
    private static double readable(double yawRad, double rx, double rz) {
        double toCameraX = -rx;
        double toCameraZ = -rz;
        double frontX = Math.sin(yawRad);
        double frontZ = Math.cos(yawRad);
        return toCameraX * frontX + toCameraZ * frontZ > 0 ? yawRad + Math.PI : yawRad;
    }

    /** A direction on the ground for a yaw in degrees (the game's yaw 0 looks along +Z). */
    private static double dirX(double yawDeg) {
        return -Math.sin(Math.toRadians(yawDeg));
    }

    private static double dirZ(double yawDeg) {
        return Math.cos(Math.toRadians(yawDeg));
    }

    /** The angle (around the vertical axis) that makes the front of a text face the player when the text stands in the direction of yawDeg from them. */
    private static double faceBack(double yawDeg) {
        return Math.PI - Math.toRadians(yawDeg);
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
                    pos.z + dirZ(yaw) * r - env.cam().z, faceBack(yaw), sc, a * alpha.f(), progressOf(j, idx), lineSeconds(j), j == idx ? 0f : 0.7f);
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
            drawLine(env, tr, cues.get(j), cx - env.cam().x, baseY - (slot - 1.0) * lineH - env.cam().y, cz - env.cam().z, faceBack(anchorYaw), sc,
                    a * alpha.f(), progressOf(j, idx), lineSeconds(j), j == idx ? 0f : 0.7f);
        }
    }

    private int mix(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * One line of text, upright. By default it stands in the world with a fixed front (yawRad, around the vertical axis), so turning the camera
     * only changes how you see it, like a sign; with "Toward the camera" it turns to you like a name tag. {@code reveal} (0..1) is how much of
     * the line is out: with word by word the words come one after another in the time the line is sung ({@code lineSec} seconds), each fading
     * in and rising a little, the latest one bright. {@code tone} (0..1) picks the colour between the two.
     */
    private void drawLine(RenderEnv env, TextRenderer tr, Cue cue, double rx, double ry, double rz, double yawRad, float scale, float a, float reveal,
                          float lineSec, float tone) {
        if (a < 0.02f) {
            return;
        }
        WORK.set(env.view()).translate((float) rx, (float) ry, (float) rz);
        if (facing.index() == 1) {
            WORK.rotate(env.mc().gameRenderer.getCamera().getRotation());
        } else {
            WORK.rotateY((float) readable(yawRad, rx, rz));
        }
        WORK.scale(-scale, -scale, scale);
        TextRenderer.TextLayerType layer = walls.on() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL;
        int c1 = color1.argb() & 0xFFFFFF;
        int c2 = color2.argb() & 0xFFFFFF;
        float g = glowStrength.f();
        int mode = letters.index();
        boolean byWord = reveal() && cue.wordStart.length > 0;
        float x0 = -cue.width / 2f;
        double t = env.t();
        int n = cue.text.length();

        if (!byWord && (mode == 0 || n < 2)) {
            paint(tr, cue.text, x0, 0f, mix(c1, c2, tone), a, g, layer, 8);
            return;
        }
        // The word that was reached last: it is the one being sung.
        int current = -1;
        for (int k = 0; k < cue.wordStart.length; k++) {
            if (reveal >= cue.wordFrac[k]) {
                current = k;
            }
        }
        if (byWord && mode == 0) {
            for (int k = 0; k <= current; k++) {
                float fade = MathHelper.clamp((reveal - cue.wordFrac[k]) * lineSec / 0.25f, 0f, 1f);
                int start = cue.wordStart[k];
                int end = start;
                while (end < n && cue.text.charAt(end) != ' ') {
                    end++;
                }
                int rgb = k == current ? mix(mix(c1, c2, tone), 0xFFFFFF, 0.45f * (1f - fade * 0.5f)) : mix(c1, c2, tone);
                paint(tr, cue.text.substring(start, end), x0 + cue.prefix[start], (1f - fade) * 3f, rgb, a * fade, g, layer, 8);
            }
            return;
        }
        int shown = mode == 2 && !byWord ? Math.round(n * reveal) : n;
        for (int i = 0; i < shown; i++) {
            char ch = cue.text.charAt(i);
            if (ch == ' ') {
                continue;
            }
            float fade = 1f;
            int word = cue.wordOf[i];
            if (byWord) {
                if (reveal < cue.wordFrac[word]) {
                    continue;
                }
                fade = MathHelper.clamp((reveal - cue.wordFrac[word]) * lineSec / 0.25f, 0f, 1f);
            }
            float dy = (mode == 2 ? 0f : (float) Math.sin(t * 3.2 + i * 0.55) * 1.8f) + (1f - fade) * 3f;
            int rgb = mode == 3 ? (java.awt.Color.HSBtoRGB((float) ((i * 0.045 + t * 0.2) % 1.0), 0.65f, 1f) & 0xFFFFFF)
                    : mix(c1, c2, MathHelper.clamp(tone + i / (float) n * 0.6f, 0f, 1f));
            if (byWord && word == current) {
                rgb = mix(rgb, 0xFFFFFF, 0.4f);
            }
            paint(tr, String.valueOf(ch), x0 + cue.prefix[i], dy, rgb, a * fade, g, layer, 4);
        }
    }

    private boolean reveal() {
        return reveal.index() == 1;
    }

    private void paint(TextRenderer tr, String s, float x, float y, int rgb, float a, float g, TextRenderer.TextLayerType layer, int copies) {
        if (glow.on() && a > 0.12f) {
            // The text shader throws away anything below about 10% opacity, so a glow that fades by its opacity would vanish in one jump. It keeps a
            // steady opacity and fades by showing fewer of its copies instead.
            int gc = ((int) (MathHelper.clamp(0.16f + 0.1f * g, 0.14f, 0.5f) * 255) << 24) | rgb;
            int shown = Math.min(copies, (int) Math.ceil(copies * MathHelper.clamp((a - 0.12f) / 0.6f, 0f, 1f)));
            for (int k = 0; k < shown; k++) {
                double ang = k * Math.PI * 2 / copies;
                float dx = (float) Math.cos(ang) * 1.1f;
                float dy = (float) Math.sin(ang) * 1.1f;
                tr.draw(s, x + dx, y + dy, gc, false, WORK, TEXT, layer, 0, 0xF000F0);
            }
        }
        tr.draw(s, x, y, ((int) (MathHelper.clamp(a, 0f, 1f) * 255) << 24) | (rgb | 0x303030) & 0xFFFFFF, false, WORK, TEXT, layer, 0, 0xF000F0);
    }

    // ---- the player card on the HUD ---------------------------------------------------------------------------

    private static String clock(double seconds) {
        int t = (int) Math.max(0, seconds);
        return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (!showPlayer.on() || source.index() != 0 || mc.options.hudHidden) {
            return;
        }
        NowPlaying.Track t = NowPlaying.current();
        Theme th = Interface.theme();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        PLAYER.w = 214;
        PLAYER.h = 54;
        int x = PLAYER.x(sw);
        int y = PLAYER.y(sh);
        Ui.surface(ctx, x, y, PLAYER.w, PLAYER.h, 8, th.panel());
        TextRenderer tr = mc.textRenderer;
        if (t == null) {
            Ui.text(ctx, tr, "Waiting for music...", x + 10, y + 12, th.text());
            Ui.text(ctx, tr, tr.trimToWidth("Play something in the browser or a player", PLAYER.w - 20), x + 10, y + 26, th.dim());
            Ui.text(ctx, tr, tr.trimToWidth("(Copy browser script in the module's settings)", PLAYER.w - 20), x + 10, y + 38, th.dim());
            return;
        }
        int cover = 42;
        if (artId != null) {
            Ui.texturedRoundUv(ctx, x + 6, y + 6, cover, cover, 7, mc.getTextureManager().getTexture(artId).getGlId(), 0f, 0f, 1f, 1f);
        } else {
            Ui.roundRect(ctx, x + 6, y + 6, cover, cover, 7, Ui.alpha(th.accent() & 0xFFFFFF, 0.3f));
            Ui.textCentered(ctx, tr, t.title().substring(0, 1).toUpperCase(), x + 6 + cover / 2, y + 6 + cover / 2 - 4, th.text());
        }
        int tx = x + cover + 14;
        int tw = PLAYER.w - cover - 24;
        Ui.text(ctx, tr, tr.trimToWidth(t.title(), tw), tx, y + 8, th.text());
        Ui.text(ctx, tr, tr.trimToWidth(t.artist().isEmpty() ? t.source() : t.artist(), tw), tx, y + 20, th.accent());
        double pos = NowPlaying.position();
        double dur = t.duration();
        int barY = y + 33;
        Ui.roundRect(ctx, tx, barY, tw, 3, 1, Ui.alpha(0xFFFFFF, 0.15f));
        if (dur > 0) {
            int fill = (int) (tw * MathHelper.clamp(pos / dur, 0.0, 1.0));
            Ui.roundRect(ctx, tx, barY, Math.max(3, fill), 3, 1, th.accent());
        }
        String time = clock(pos) + (dur > 0 ? " / " + clock(dur) : "") + (t.playing() ? "" : "  ||");
        Ui.text(ctx, tr, time, tx, y + 40, th.dim());
        String status = switch (liveStatus) {
            case LOOKING -> "looking for lyrics...";
            case SYNCED -> "";
            case SPREAD -> "lyrics: not timed";
            case MISSING -> "no lyrics found";
            default -> "";
        };
        if (!status.isEmpty()) {
            Ui.text(ctx, tr, status, x + PLAYER.w - 8 - Ui.width(tr, status), y + 40, th.dim());
        }
    }
}
