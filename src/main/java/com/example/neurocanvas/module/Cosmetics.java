package com.example.neurocanvas.module;

import com.example.neurocanvas.cosmetics.Decimator;
import com.example.neurocanvas.cosmetics.Mesh;
import com.example.neurocanvas.cosmetics.ObjLoader;
import com.example.neurocanvas.cosmetics.Phantom;
import com.example.neurocanvas.fx.EffectDraw;
import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * A 3D model that hovers behind you like a stand: the built-in Phantom or any .obj model you put into
 * neurocanvas/cosmetics/<name>/. Models are optimised on load (triangle budget and texture size) so they stay cheap.
 */
public final class Cosmetics extends Module {
    private static final Gson GSON = new Gson();
    private static final String BUILT_IN = "Phantom (built-in)";
    private static final String[] TEXTURE_CAPS = {"Original", "1024", "512", "256"};

    /** Optional cosmetic.json inside a model folder. */
    private static final class Config {
        String model;
        String texture;
        Map<String, String> partTextures;
        Float yawOffset;
        Boolean flipFront;
    }

    private record Result(Mesh mesh, Map<String, NativeImage> images, float yawOffset, String info, boolean poseable) { }

    private final Setting model;
    private final Setting size = add(Setting.number("Size", 0.5, 6, 2.2));
    private final Setting back = add(Setting.number("Distance behind", 0, 4, 1.0));
    private final Setting side = add(Setting.number("Side offset", -3, 3, 0.9));
    private final Setting height = add(Setting.number("Height", -1, 3, 0.3));
    private final Setting follow = add(Setting.number("Follow speed", 1, 20, 7));
    private final Setting sway = add(Setting.number("Idle sway", 0, 2, 1));
    private final Setting hitReaction = add(Setting.bool("Strike on hit", true));
    private final Setting lunge = add(Setting.number("Strike reach", 0, 2, 1));
    private final Setting opacity = add(Setting.number("Opacity", 0.1, 1, 1));
    private final Setting shading = add(Setting.number("Shading", 0, 1, 0.6));
    private final Setting flip = add(Setting.bool("Flip front", false));
    private final Setting yawFix = add(Setting.number("Rotation fix", -180, 180, 0));
    private final Setting optimized = add(Setting.bool("Optimized", false));
    private final Setting budget = add(Setting.number("Triangle budget", 200, 30000, 6000));
    private final Setting textureCap = add(Setting.choice("Texture size", 1, TEXTURE_CAPS));
    private final Setting applyOptimization = add(Setting.button("Apply optimization"));
    private final Setting restoreOriginal = add(Setting.button("Restore original"));
    private final Setting armPose = add(Setting.choice("Arm pose", 1, "Off", "Down", "Ready"));
    private final Setting shoulderX = add(Setting.number("Shoulder width", 0.05, 0.45, 0.2));
    private final Setting shoulderY = add(Setting.number("Shoulder height", 0.3, 1, 0.78));
    private final Setting firstPerson = add(Setting.bool("Show in first person", true));

    private final Path root;
    private final List<String> folders = new ArrayList<>();

    private volatile Result pending;
    private Mesh mesh;
    private final List<Identifier> textures = new ArrayList<>();
    private float modelYaw;
    private boolean poseable;
    private String info = "No model loaded yet";
    private String loadedKey = "";
    private String wantKey = "";
    private int stable;
    private int generation;
    private boolean loading;

    private Vec3d pos;
    private float faceYaw;
    private long lastNano = System.nanoTime();
    private long punchStart;
    private Vec3d punchTarget = Vec3d.ZERO;

    public Cosmetics(Path root) {
        super("Cosmetics", Category.VISUALS, "A 3D model that hovers behind you: built-in Phantom or your own .obj in neurocanvas/cosmetics/", false);
        aiControllable = false;
        this.root = root;
        model = add(Setting.choice("Model", 0, BUILT_IN));
        settings.add(0, settings.remove(settings.size() - 1));
        rescan();
        WorldState.addHitListener(e -> {
            if (enabled && hitReaction.on()) {
                punchStart = System.currentTimeMillis();
                punchTarget = e.getPos().add(0, e.getHeight() * 0.55, 0);
            }
        });
    }

    public String info() {
        return info;
    }

    /** Looks for model folders again and reloads the current one. */
    public void rescan() {
        String current = model.option();
        folders.clear();
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.list(root)) {
                s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted(String.CASE_INSENSITIVE_ORDER).forEach(folders::add);
            } catch (IOException ignored) {
                // No models found.
            }
        }
        // Your own models come first, so they are the default; the built-in one is the last choice.
        List<String> options = new ArrayList<>(folders);
        options.add(BUILT_IN);
        model.setOptions(options.toArray(new String[0]));
        int index = options.indexOf(current);
        model.value = Math.max(0, index);
        loadedKey = "";
        wantKey = "";
    }

    // ---- loading ----------------------------------------------------------------------------

    /** The slider values are deliberately not part of the key: they only count when "Apply optimization" is pressed. */
    private String key() {
        return model.option() + "|" + optimized.on();
    }

    private void request(String key, String option, int triangleBudget, int cap) {
        loadedKey = key;
        loading = true;
        int gen = ++generation;
        Thread t = new Thread(() -> {
            Result r;
            try {
                r = build(option, triangleBudget, cap);
            } catch (IOException | RuntimeException e) {
                r = new Result(null, Map.of(), 0f, "Could not load \"" + option + "\": " + e.getMessage(), false);
            }
            if (gen == generation) {
                pending = r;
            }
        }, "neurocanvas-cosmetics");
        t.setDaemon(true);
        t.start();
    }

    private Result build(String option, int triangleBudget, int cap) throws IOException {
        if (option.equals(BUILT_IN)) {
            Mesh m = Phantom.create();
            return new Result(m, Map.of(), 0f, "Phantom (built-in): " + m.triangles() + " triangles, no texture", false);
        }
        Path folder = root.resolve(option);
        Config cfg = new Config();
        Path json = folder.resolve("cosmetic.json");
        if (Files.exists(json)) {
            Config read = GSON.fromJson(Files.readString(json), Config.class);
            if (read != null) {
                cfg = read;
            }
        }
        Path obj = cfg.model != null ? folder.resolve(cfg.model) : firstWith(folder, ".obj");
        if (obj == null || !Files.exists(obj)) {
            throw new IOException("no .obj file in the folder");
        }
        Mesh mesh = ObjLoader.load(obj);
        int before = mesh.triangles();

        Map<String, NativeImage> images = new LinkedHashMap<>();
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()::iterator) {
                try (InputStream in = Files.newInputStream(p)) {
                    images.put(p.getFileName().toString(), NativeImage.read(in));
                }
            }
        }
        String main = cfg.texture;
        String small = null;
        if (!images.isEmpty()) {
            long bigArea = -1;
            long smallArea = Long.MAX_VALUE;
            String big = null;
            for (Map.Entry<String, NativeImage> e : images.entrySet()) {
                long a = (long) e.getValue().getWidth() * e.getValue().getHeight();
                if (a > bigArea) {
                    bigArea = a;
                    big = e.getKey();
                }
                if (a < smallArea) {
                    smallArea = a;
                    small = e.getKey();
                }
            }
            if (main == null) {
                main = big;
            }
            if (small != null && small.equals(main)) {
                small = null;
            }
        }
        for (Mesh.Part p : mesh.parts) {
            String file = null;
            if (cfg.partTextures != null) {
                for (Map.Entry<String, String> e : cfg.partTextures.entrySet()) {
                    if (p.name.toLowerCase(Locale.ROOT).contains(e.getKey().toLowerCase(Locale.ROOT))) {
                        file = e.getValue();
                    }
                }
            } else if (small != null && p.name.toLowerCase(Locale.ROOT).contains("eye")) {
                file = small;
            }
            p.texture = file != null ? file : main;
            if (p.texture != null && !images.containsKey(p.texture)) {
                p.texture = main;
            }
        }
        mesh = Decimator.simplify(mesh, triangleBudget);
        mesh.computeShading();

        Map<String, NativeImage> used = new LinkedHashMap<>();
        StringBuilder tex = new StringBuilder();
        for (Map.Entry<String, NativeImage> e : images.entrySet()) {
            boolean inUse = false;
            for (Mesh.Part p : mesh.parts) {
                inUse |= e.getKey().equals(p.texture);
            }
            if (!inUse) {
                e.getValue().close();
                continue;
            }
            NativeImage img = e.getValue();
            int w = img.getWidth();
            int h = img.getHeight();
            NativeImage scaled = shrink(img, cap == 0 ? Integer.MAX_VALUE : Integer.parseInt(TEXTURE_CAPS[cap]));
            used.put(e.getKey(), scaled);
            tex.append(tex.length() == 0 ? "" : ", ").append(e.getKey()).append(' ').append(w).append('x').append(h);
            if (scaled.getWidth() != w) {
                tex.append(" -> ").append(scaled.getWidth()).append('x').append(scaled.getHeight());
            }
        }
        float yaw = (cfg.yawOffset == null ? 0f : cfg.yawOffset) + (Boolean.TRUE.equals(cfg.flipFront) ? 180f : 0f);
        return new Result(mesh, used, yaw, option + ": " + before + " -> " + mesh.triangles() + " triangles"
                + (tex.length() > 0 ? "; textures " + tex : "; no texture"), true);
    }

    private static Path firstWith(Path folder, String ext) throws IOException {
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)).sorted().findFirst().orElse(null);
        }
    }

    /** Halves the image until its longer side is at most {@code cap}. Returns the same image when it already fits. */
    private static NativeImage shrink(NativeImage src, int cap) {
        NativeImage img = src;
        while (Math.max(img.getWidth(), img.getHeight()) > cap && img.getWidth() > 1 && img.getHeight() > 1) {
            NativeImage half = new NativeImage(img.getWidth() / 2, img.getHeight() / 2, false);
            for (int y = 0; y < half.getHeight(); y++) {
                for (int x = 0; x < half.getWidth(); x++) {
                    int a = 0, b = 0, g = 0, r = 0;
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int c = img.getColor(x * 2 + dx, y * 2 + dy);
                            a += c >>> 24;
                            b += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            r += c & 0xFF;
                        }
                    }
                    half.setColor(x, y, ((a / 4) << 24) | ((b / 4) << 16) | ((g / 4) << 8) | (r / 4));
                }
            }
            if (img != src) {
                img.close();
            }
            img = half;
        }
        if (img != src) {
            src.close();
        }
        return img;
    }

    /** Runs on the game thread: uploads the new textures and swaps the model in. */
    private void install(MinecraftClient mc, Result r) {
        loading = false;
        for (Identifier id : textures) {
            mc.getTextureManager().destroyTexture(id);
        }
        textures.clear();
        info = r.info();
        if (r.mesh() == null) {
            return;
        }
        Map<String, Identifier> ids = new HashMap<>();
        int n = 0;
        for (Map.Entry<String, NativeImage> e : r.images().entrySet()) {
            Identifier id = new Identifier("neurocanvas", "cosmetic/" + generation + "_" + (n++));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(e.getValue());
            mc.getTextureManager().registerTexture(id, tex);
            tex.setFilter(true, false);
            textures.add(id);
            ids.put(e.getKey(), id);
        }
        for (Mesh.Part p : r.mesh().parts) {
            p.texId = p.texture == null ? null : ids.get(p.texture);
        }
        mesh = r.mesh();
        modelYaw = r.yawOffset();
        poseable = r.poseable();
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (!enabled) {
            return;
        }
        Result r = pending;
        if (r != null) {
            pending = null;
            install(mc, r);
        }
        boolean force = false;
        if (applyOptimization.on()) {
            applyOptimization.set(0);
            optimized.set(1);
            force = true;
        }
        if (restoreOriginal.on()) {
            restoreOriginal.set(0);
            optimized.set(0);
        }
        String key = key();
        if (force || !key.equals(loadedKey)) {
            if (force) {
                loadedKey = "";
            }
            if (!key.equals(wantKey) && !force) {
                wantKey = key;
                stable = 0;
            } else if ((force || ++stable >= 12) && !loading) {
                wantKey = key;
                boolean opt = optimized.on();
                request(key, model.option(), opt ? (int) budget.value : Integer.MAX_VALUE, opt ? textureCap.index() : 0);
            }
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientPlayerEntity p = mc.player;
        if (mesh == null || p == null || (!firstPerson.on() && mc.options.getPerspective().isFirstPerson())) {
            return;
        }
        long nowNano = System.nanoTime();
        float dt = Math.min((nowNano - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = nowNano;
        float td = env.tickDelta();

        Vec3d player = EffectDraw.lerpPos(p, td);
        float bodyYaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
        float k = 1f - (float) Math.exp(-dt * follow.value);
        faceYaw = MathHelper.lerpAngleDegrees(k, faceYaw, bodyYaw);
        double fy = Math.toRadians(faceYaw);
        Vec3d fwd = new Vec3d(-Math.sin(fy), 0, Math.cos(fy));
        Vec3d right = new Vec3d(-Math.cos(fy), 0, -Math.sin(fy));
        Vec3d target = player.add(right.multiply(side.value)).add(fwd.multiply(-back.value)).add(0, height.value, 0);
        if (pos == null || pos.squaredDistanceTo(target) > 256) {
            pos = target;
        } else {
            pos = pos.add(target.subtract(pos).multiply(k));
        }

        double t = env.t();
        Vec3d draw = pos.add(0, Math.sin(t * 1.7) * 0.08 * sway.value, 0);
        float yaw = faceYaw;
        float flash = 0f;
        float strike = (System.currentTimeMillis() - punchStart) / 400f;
        if (hitReaction.on() && strike >= 0f && strike < 1f) {
            float w = (float) Math.sin(Math.PI * strike);
            Vec3d reach = punchTarget.subtract(draw);
            Vec3d dir = reach.lengthSquared() > 1e-4 ? reach.normalize() : fwd;
            Vec3d hit = punchTarget.subtract(dir.multiply(0.9));
            draw = draw.add(hit.subtract(draw).multiply(w * lunge.value));
            draw = draw.add(Math.sin(t * 90) * 0.04 * w, Math.cos(t * 83) * 0.03 * w, 0);
            float toward = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            yaw = MathHelper.lerpAngleDegrees(w, yaw, toward);
            flash = w * 0.5f;
        }

        Matrix4f mm = new Matrix4f(env.m());
        mm.translate((float) draw.x, (float) draw.y, (float) draw.z);
        mm.rotateY((float) Math.toRadians(-(yaw + modelYaw + yawFix.f() + (flip.on() ? 180f : 0f))));
        mm.rotateZ((float) Math.sin(t * 1.3) * 0.05f * sway.f());
        mm.scale(size.f());
        // Arm angles: lowered by the pose, swung forward and alternated while striking.
        float down = armPose.index() == 1 ? 1.3f : armPose.index() == 2 ? 0.95f : 0f;
        float ahead = armPose.index() == 2 ? 0.9f : 0f;
        float punch = 0f;
        if (hitReaction.on() && strike >= 0f && strike < 1f && armPose.index() != 0) {
            punch = (float) Math.sin(Math.PI * strike);
        }
        float rightFwd = ahead + punch * 1.25f * Math.max(0f, (float) Math.sin(t * 38));
        float leftFwd = ahead + punch * 1.25f * Math.max(0f, (float) -Math.sin(t * 38));
        draw(mm, opacity.f(), flash, poseable && armPose.index() != 0, down, rightFwd, leftFwd);
    }

    private static float smooth(float x, float a, float b) {
        float t = MathHelper.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private void draw(Matrix4f mm, float alpha, float flash, boolean arms, float down, float rightFwd, float leftFwd) {
        float sx = shoulderX.f();
        float sy = shoulderY.f();
        float cd = (float) Math.cos(down), sd = (float) Math.sin(down);
        float[] cf = {(float) Math.cos(rightFwd), (float) Math.cos(leftFwd)};
        float[] sf = {(float) Math.sin(rightFwd), (float) Math.sin(leftFwd)};
        float shade = shading.f();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        for (Mesh.Part p : mesh.parts) {
            boolean textured = p.texId != null;
            float cr = ((p.color >> 16) & 0xFF) / 255f;
            float cg = ((p.color >> 8) & 0xFF) / 255f;
            float cb = (p.color & 0xFF) / 255f;
            if (textured) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                RenderSystem.setShaderTexture(0, p.texId);
                b.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
                cr = cg = cb = 1f;
            } else {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                b.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            }
            int corners = p.pos.length / 3;
            for (int i = 0; i < corners; i++) {
                float lit = 1f + (p.shade[i] - 1f) * shade;
                float r = Math.min(1f, cr * lit + flash);
                float g = Math.min(1f, cg * lit + flash);
                float bl = Math.min(1f, cb * lit + flash);
                float vx = p.pos[i * 3];
                float vy = p.pos[i * 3 + 1];
                float vz = p.pos[i * 3 + 2];
                if (arms) {
                    float w = smooth(Math.abs(vx), sx * 0.75f, sx * 1.15f);
                    if (w > 0f) {
                        // Right arm (x > 0) hangs down by turning about z, then swings forward about x; the left arm mirrors it.
                        int arm = vx >= 0 ? 0 : 1;
                        float dir = vx >= 0 ? -1f : 1f;
                        float shoulder = vx >= 0 ? sx : -sx;
                        float px = vx - shoulder;
                        float py = vy - sy;
                        float rx = px * cd - py * dir * sd;
                        float ry = px * dir * sd + py * cd;
                        float fy = ry * cf[arm] + vz * sf[arm];
                        float fz = -ry * sf[arm] + vz * cf[arm];
                        vx += (rx + shoulder - vx) * w;
                        vy += (fy + sy - vy) * w;
                        vz += (fz - vz) * w;
                    }
                }
                b.vertex(mm, vx, vy, vz);
                if (textured) {
                    b.texture(p.uv[i * 2], p.uv[i * 2 + 1]);
                }
                b.color(r, g, bl, alpha).next();
            }
            tess.draw();
        }
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
