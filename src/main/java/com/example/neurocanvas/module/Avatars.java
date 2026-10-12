package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.ExampleAvatar;
import com.example.neurocanvas.fx.EffectDraw;
import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Avatars made in Blockbench (.bbmodel) with bones and animations. They play by themselves: an animation named idle, walk,
 * sprint, sneak, jump or fall follows what you do, attack and hurt play on a swing or a hit, and avatar.json can bind keys
 * to animations. The avatar either follows you like a stand or replaces your own body.
 */
public final class Avatars extends Module {
    private static final Gson GSON = new Gson();

    private static final class Config {
        String model;
        Float scale;
        Float yaw;
        Map<String, String> keys;
    }

    /** One animation that is playing on top of the automatic ones. */
    private static final class Playing {
        BbModel.Anim anim;
        long start;
        String key;
    }

    private static final class Quad {
        int tex;
        final float[] p = new float[12];
        final float[] uv = new float[8];
        float lit;
    }

    private final Setting avatar;
    private final Setting mode = add(Setting.choice("Mode", 0, "On me", "Stand"));
    private final Setting size = add(Setting.number("Size", 0.3, 4, 1));
    private final Setting back = add(Setting.number("Stand distance behind", 0, 4, 1.2));
    private final Setting side = add(Setting.number("Stand side offset", -3, 3, 1.1));
    private final Setting height = add(Setting.number("Stand height", -1, 3, 0));
    private final Setting follow = add(Setting.number("Stand follow speed", 1, 20, 8));
    private final Setting animSpeed = add(Setting.number("Animation speed", 0.2, 3, 1));
    private final Setting hideBody = add(Setting.bool("Hide my own body (On me)", true));
    private final Setting firstPerson = add(Setting.bool("Show in first person", false));
    private final Setting lighting = add(Setting.bool("World lighting", true));
    private final Setting shading = add(Setting.number("Shading", 0, 1, 0.6));
    private final Setting yawFix = add(Setting.number("Rotation fix", -180, 180, 0));
    private final Setting openFolder = add(Setting.button("Open avatars folder"));
    private final Setting reloadList = add(Setting.button("Reload avatar list"));

    private final Path root;
    private final List<String> folders = new ArrayList<>();
    private BbModel model;
    private final Map<Integer, Identifier> textures = new HashMap<>();
    private final List<Identifier> registered = new ArrayList<>();
    private Config config = new Config();
    private String loadedName = "";
    private String info = "No avatar loaded yet";

    private final List<Playing> overlays = new ArrayList<>();
    private final Set<Integer> keysDown = new HashSet<>();
    private boolean wasSwinging;
    private int lastHurt;
    private Vec3d standPos;
    private float standYaw;
    private long lastNano = System.nanoTime();
    private Boolean cachedOnMe = false;

    public Avatars(Path root) {
        super("Avatar", Category.COSMETICS, "Your own animated Blockbench avatar: follows you as a stand or replaces your body", false);
        aiControllable = false;
        this.root = root;
        avatar = add(Setting.choice("Avatar", 0, "(none)"));
        settings.add(0, settings.remove(settings.size() - 1));
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve("README.txt"), "One folder per avatar:\n\n  avatars/<name>/model.bbmodel   (made in Blockbench, textures embedded)\n"
                    + "  avatars/<name>/avatar.json     (optional)\n\nAnimations named idle, walk, sprint, sneak, jump, fall, attack and hurt play by themselves.\n"
                    + "avatar.json: { \"model\": \"model.bbmodel\", \"scale\": 1.0, \"yaw\": 0, \"keys\": { \"G\": \"wave\" } }\n"
                    + "A key plays the animation with that name: once, or while held (hold), or as a toggle (loop).\n\n"
                    + "Press \"Reload avatar list\" in the Avatar module after adding a folder.\n");
            if (!Files.isDirectory(root.resolve("Example"))) {
                ExampleAvatar.write(root.resolve("Example"));
            }
        } catch (IOException ignored) {
            // The folder is optional; avatars can still be added by hand.
        }
        rescan();
        WorldState.addHitListener(e -> play("attack", null));
    }

    public String info() {
        return info;
    }

    /** Plays a gesture animation: emote_NAME or NAME, if the loaded avatar has one. */
    public void emote(String name) {
        if (model == null) {
            return;
        }
        play(find("emote_" + name) != null ? "emote_" + name : name, null);
    }

    /** True when the vanilla body of this player must not be drawn because the avatar replaces it. */
    public boolean hidesBody(Entity entity) {
        return enabled && model != null && hideBody.on() && mode.index() == 0 && entity == MinecraftClient.getInstance().player
                && (firstPerson.on() || !MinecraftClient.getInstance().options.getPerspective().isFirstPerson());
    }

    public void rescan() {
        String current = avatar.option();
        folders.clear();
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.list(root)) {
                s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted(String.CASE_INSENSITIVE_ORDER).forEach(folders::add);
            } catch (IOException ignored) {
                // No avatars found.
            }
        }
        List<String> options = new ArrayList<>(folders);
        if (options.isEmpty()) {
            options.add("(none)");
        }
        avatar.setOptions(options.toArray(new String[0]));
        avatar.value = Math.max(0, options.indexOf(current));
        loadedName = "";
    }

    // ---- loading ----------------------------------------------------------------------------

    private void load(MinecraftClient mc, String name) {
        unload(mc);
        loadedName = name;
        Path folder = root.resolve(name);
        try {
            Config cfg = new Config();
            Path json = folder.resolve("avatar.json");
            if (Files.exists(json)) {
                Config read = GSON.fromJson(Files.readString(json), Config.class);
                if (read != null) {
                    cfg = read;
                }
            }
            Path file = cfg.model != null ? folder.resolve(cfg.model) : null;
            if (file == null || !Files.exists(file)) {
                try (Stream<Path> s = Files.list(folder)) {
                    file = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel")).sorted().findFirst().orElse(null);
                }
            }
            if (file == null) {
                info = "\"" + name + "\": no .bbmodel file in the folder";
                return;
            }
            BbModel m = BbModel.parse(Files.readString(file, StandardCharsets.UTF_8));
            for (int i = 0; i < m.textures.size(); i++) {
                byte[] data = m.textures.get(i);
                if (data.length == 0) {
                    continue;
                }
                NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
                NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                Identifier id = new Identifier("neurocanvas", "avatar/" + UUID.randomUUID());
                mc.getTextureManager().registerTexture(id, tex);
                tex.setFilter(false, false);
                registered.add(id);
                textures.put(i, id);
            }
            model = m;
            config = cfg;
            info = name + ": " + m.cubeCount + " cubes, " + m.bonesByUuid.size() + " bones, " + m.animations.size() + " animations ("
                    + String.join(", ", m.animations.keySet()) + ")";
        } catch (IOException | RuntimeException e) {
            info = "Could not load \"" + name + "\": " + e.getMessage();
        }
    }

    private void unload(MinecraftClient mc) {
        for (Identifier id : registered) {
            mc.getTextureManager().destroyTexture(id);
        }
        registered.clear();
        textures.clear();
        overlays.clear();
        model = null;
    }

    // ---- behaviour --------------------------------------------------------------------------

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (openFolder.on()) {
            openFolder.set(0);
            net.minecraft.util.Util.getOperatingSystem().open(root.toFile());
        }
        if (reloadList.on()) {
            reloadList.set(0);
            rescan();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[Avatar] " + folders.size() + " avatar folder(s) found"), false);
            }
        }
        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null) {
            if (model != null && !enabled) {
                unload(mc);
                loadedName = "";
            }
            return;
        }
        String want = avatar.option();
        if (!want.equals(loadedName) && !want.equals("(none)")) {
            load(mc, want);
        }
        if (model == null) {
            return;
        }
        boolean swinging = p.handSwinging;
        if (swinging && !wasSwinging) {
            play("attack", null);
        }
        wasSwinging = swinging;
        if (p.hurtTime > lastHurt) {
            play("hurt", null);
        }
        lastHurt = p.hurtTime;
        pollKeys(mc);
    }

    private BbModel.Anim find(String name) {
        if (model == null) {
            return null;
        }
        BbModel.Anim a = model.animations.get(name);
        if (a != null) {
            return a;
        }
        for (Map.Entry<String, BbModel.Anim> e : model.animations.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Starts an animation on top of the automatic ones, once. */
    private void play(String name, String key) {
        BbModel.Anim a = find(name);
        if (a == null || !enabled) {
            return;
        }
        for (Playing p : overlays) {
            if (p.anim == a && (key == null || key.equals(p.key))) {
                p.start = System.nanoTime();
                return;
            }
        }
        Playing p = new Playing();
        p.anim = a;
        p.start = System.nanoTime();
        p.key = key;
        overlays.add(p);
    }

    private void pollKeys(MinecraftClient mc) {
        if (config.keys == null || mc.currentScreen != null) {
            return;
        }
        long window = mc.getWindow().getHandle();
        for (Map.Entry<String, String> e : config.keys.entrySet()) {
            int code;
            try {
                code = InputUtil.fromTranslationKey("key.keyboard." + e.getKey().toLowerCase(Locale.ROOT)).getCode();
            } catch (IllegalArgumentException ex) {
                continue;
            }
            boolean down = InputUtil.isKeyPressed(window, code);
            boolean was = keysDown.contains(code);
            if (down && !was) {
                keysDown.add(code);
                BbModel.Anim a = find(e.getValue());
                if (a != null && a.loop == 0) {
                    boolean removed = overlays.removeIf(p -> p.anim == a && e.getKey().equals(p.key));
                    if (!removed) {
                        play(e.getValue(), e.getKey());
                    }
                } else {
                    play(e.getValue(), e.getKey());
                }
            } else if (!down && was) {
                keysDown.remove(code);
                BbModel.Anim a = find(e.getValue());
                if (a != null && a.loop == 2) {
                    overlays.removeIf(p -> p.anim == a && e.getKey().equals(p.key));
                }
            }
        }
    }

    private String baseState(ClientPlayerEntity p) {
        boolean air = !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater();
        double speed = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ) * 20.0;
        if (p.isSneaking() && find("sneak") != null) {
            return "sneak";
        }
        if (air) {
            if (p.getVelocity().y > 0 && find("jump") != null) {
                return "jump";
            }
            if (find("fall") != null) {
                return "fall";
            }
        }
        if (speed > 0.3) {
            if (p.isSprinting() && find("sprint") != null) {
                return "sprint";
            }
            if (find("walk") != null) {
                return "walk";
            }
        }
        return "idle";
    }

    // ---- pose -------------------------------------------------------------------------------

    /** per uuid: position x y z, rotation x y z (degrees), scale x y z. */
    private final Map<String, float[]> pose = new HashMap<>();

    private float[] slot(String uuid) {
        return pose.computeIfAbsent(uuid, k -> new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1});
    }

    private void apply(BbModel.Anim a, double time) {
        for (Map.Entry<String, BbModel.Channels> e : a.animators.entrySet()) {
            BbModel.Channels c = e.getValue();
            double[] pos = BbModel.sample(c.position, time);
            double[] rot = BbModel.sample(c.rotation, time);
            double[] scl = BbModel.sample(c.scale, time);
            if (pos == null && rot == null && scl == null) {
                continue;
            }
            float[] s = slot(e.getKey());
            if (pos != null) {
                s[0] -= (float) pos[0];
                s[1] += (float) pos[1];
                s[2] += (float) pos[2];
            }
            if (rot != null) {
                s[3] += (float) rot[0];
                s[4] += (float) rot[1];
                s[5] += (float) rot[2];
            }
            if (scl != null) {
                s[6] *= (float) scl[0];
                s[7] *= (float) scl[1];
                s[8] *= (float) scl[2];
            }
        }
    }

    private double animTime(BbModel.Anim a, long start, long now) {
        double t = (now - start) / 1_000_000_000.0 * animSpeed.value;
        if (a.loop == 0) {
            return t % a.length;
        }
        return Math.min(t, a.length);
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientPlayerEntity p = mc.player;
        if (model == null || p == null || mc.world == null || (!firstPerson.on() && mc.options.getPerspective().isFirstPerson())) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = now;
        float td = env.tickDelta();

        pose.clear();
        BbModel.Anim base = find(baseState(p));
        if (base == null) {
            base = find("idle");
        }
        if (base != null) {
            apply(base, animTime(base, 0L, now));
        }
        overlays.removeIf(o -> o.anim.loop == 1 && (now - o.start) / 1_000_000_000.0 * animSpeed.value > o.anim.length);
        for (Playing o : overlays) {
            apply(o.anim, animTime(o.anim, o.start, now));
        }

        Vec3d player = EffectDraw.lerpPos(p, td);
        float bodyYaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
        Vec3d at;
        float yaw;
        if (mode.index() == 0) {
            at = player;
            yaw = bodyYaw;
        } else {
            float k = 1f - (float) Math.exp(-dt * follow.value);
            standYaw = MathHelper.lerpAngleDegrees(k, standYaw, bodyYaw);
            double fy = Math.toRadians(standYaw);
            Vec3d fwd = new Vec3d(-Math.sin(fy), 0, Math.cos(fy));
            Vec3d right = new Vec3d(-Math.cos(fy), 0, -Math.sin(fy));
            Vec3d target = player.add(right.multiply(side.value)).add(fwd.multiply(-back.value)).add(0, height.value, 0);
            standPos = standPos == null || standPos.squaredDistanceTo(target) > 256 ? target : standPos.add(target.subtract(standPos).multiply(k));
            at = standPos;
            yaw = standYaw;
        }

        float light = 1f;
        if (lighting.on()) {
            float level = mc.world.getLightLevel(BlockPos.ofFloored(at.x, at.y + 1, at.z)) / 15f;
            light = 0.35f + 0.65f * level;
        }

        float scale = (config.scale == null ? 1f : config.scale) * size.f() / 16f;
        Matrix4f mm = new Matrix4f(env.m());
        mm.translate((float) at.x, (float) at.y, (float) at.z);
        mm.rotateY((float) Math.toRadians(-yaw + 180f + yawFix.f() + (config.yaw == null ? 0f : config.yaw)));
        mm.scale(scale);

        List<Quad> quads = new ArrayList<>();
        Matrix4f identity = new Matrix4f();
        for (BbModel.Bone b : model.roots) {
            collect(b, identity, quads);
        }
        draw(mm, quads, light);
    }

    private static Matrix4f transformed(Matrix4f parent, double[] origin, double[] rot, float[] s) {
        Matrix4f m = new Matrix4f(parent);
        float ox = (float) origin[0], oy = (float) origin[1], oz = (float) origin[2];
        if (s != null) {
            m.translate(s[0], s[1], s[2]);
        }
        m.translate(ox, oy, oz);
        float rx = (float) rot[0] + (s == null ? 0 : s[3]);
        float ry = (float) rot[1] + (s == null ? 0 : s[4]);
        float rz = (float) rot[2] + (s == null ? 0 : s[5]);
        m.rotateZ((float) Math.toRadians(rz)).rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx));
        if (s != null) {
            m.scale(s[6], s[7], s[8]);
        }
        m.translate(-ox, -oy, -oz);
        return m;
    }

    private void collect(BbModel.Bone bone, Matrix4f parent, List<Quad> out) {
        Matrix4f m = transformed(parent, bone.origin, bone.rotation, pose.get(bone.uuid));
        for (BbModel.Cube c : bone.cubes) {
            Matrix4f cm = transformed(m, c.origin, c.rotation, pose.get(c.uuid));
            addCube(c, cm, out);
        }
        for (BbModel.Bone child : bone.children) {
            collect(child, m, out);
        }
    }

    private static void addCube(BbModel.Cube c, Matrix4f m, List<Quad> out) {
        double g = c.inflate;
        double x1 = Math.min(c.from[0], c.to[0]) - g, x2 = Math.max(c.from[0], c.to[0]) + g;
        double y1 = Math.min(c.from[1], c.to[1]) - g, y2 = Math.max(c.from[1], c.to[1]) + g;
        double z1 = Math.min(c.from[2], c.to[2]) - g, z2 = Math.max(c.from[2], c.to[2]) + g;
        // Corners as top-left, top-right, bottom-right, bottom-left, seen from outside the face.
        double[][][] corners = {
            {{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}},
            {{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}},
            {{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}},
            {{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}},
            {{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}},
            {{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}}};
        Vector3f v = new Vector3f();
        for (int f = 0; f < 6; f++) {
            BbModel.Face face = c.faces[f];
            if (face == null || face.uv == null) {
                continue;
            }
            Quad q = new Quad();
            q.tex = face.texture;
            float[] uv = face.uv;
            float[] us = {uv[0], uv[2], uv[2], uv[0]};
            float[] vs = {uv[1], uv[1], uv[3], uv[3]};
            for (int i = 0; i < 4; i++) {
                v.set((float) corners[f][i][0], (float) corners[f][i][1], (float) corners[f][i][2]);
                m.transformPosition(v);
                q.p[i * 3] = v.x;
                q.p[i * 3 + 1] = v.y;
                q.p[i * 3 + 2] = v.z;
                q.uv[i * 2] = us[i];
                q.uv[i * 2 + 1] = vs[i];
            }
            float ax = q.p[3] - q.p[0], ay = q.p[4] - q.p[1], az = q.p[5] - q.p[2];
            float bx = q.p[9] - q.p[0], by = q.p[10] - q.p[1], bz = q.p[11] - q.p[2];
            float nx = -(ay * bz - az * by), ny = -(az * bx - ax * bz), nz = -(ax * by - ay * bx);
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            q.lit = len < 1e-6f ? 1f : 0.75f + 0.25f * (ny / len);
            out.add(q);
        }
    }

    private void draw(Matrix4f mm, List<Quad> quads, float light) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        Set<Integer> done = new HashSet<>();
        for (Quad first : quads) {
            if (!done.add(first.tex)) {
                continue;
            }
            Identifier id = textures.get(first.tex);
            if (id != null) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                RenderSystem.setShaderTexture(0, id);
                b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            } else {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            }
            for (Quad q : quads) {
                if (q.tex != first.tex) {
                    continue;
                }
                float lit = (1f + (q.lit - 1f) * shading.f()) * light;
                for (int i = 0; i < 4; i++) {
                    b.vertex(mm, q.p[i * 3], q.p[i * 3 + 1], q.p[i * 3 + 2]);
                    if (id != null) {
                        b.texture(q.uv[i * 2], q.uv[i * 2 + 1]);
                    }
                    float c = id != null ? lit : 0.7f * lit;
                    b.color(c, c, c, 1f).next();
                }
            }
            tess.draw();
        }
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
