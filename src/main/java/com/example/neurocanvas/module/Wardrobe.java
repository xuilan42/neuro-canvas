package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.BbInstance;
import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.avatar.ExamplePack;
import com.example.neurocanvas.fx.EffectDraw;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * The wardrobe: one slot each for a cape, wings, a hat, an outfit and a pet, two emote slots and graffiti. Items are folders in
 * neurocanvas/wardrobe (see {@link CosmeticPack}); here they are loaded, worn and animated. Only you see them for now.
 */
public final class Wardrobe extends Module {
    private static final String NONE = "None";

    /** One place where an item can be worn. */
    private static final class Slot {
        final CosmeticPack.Type type;
        final Setting setting;
        String loadedName = NONE;
        CosmeticPack.Config config = new CosmeticPack.Config();
        BbInstance model;
        Identifier image;

        Slot(CosmeticPack.Type type, Setting setting) {
            this.type = type;
            this.setting = setting;
        }
    }

    private static final class Spray {
        Vec3d pos;
        Vec3d normal;
        float size;
        Identifier image;
        float aspect;
        long created;
    }

    private final Setting size = add(Setting.number("Size", 0.4, 3, 1));
    private final Setting shading = add(Setting.number("Shading", 0, 1, 0.5));
    private final Setting firstPerson = add(Setting.bool("Show worn items in first person", false));
    private final Setting petDistance = add(Setting.number("Pet distance", 0.6, 4, 1.4));
    private final Setting sprayLife = add(Setting.number("Graffiti lifetime (s)", 20, 900, 300));
    private final Setting animSpeed = add(Setting.number("Animation speed", 0.2, 3, 1));
    private final Setting reload = add(Setting.button("Reload"));
    private final Setting openFolder = add(Setting.button("Open wardrobe folder"));

    private final Slot cape;
    private final Slot wings;
    private final Slot hat;
    private final Slot outfit;
    private final Slot pet;
    private final Slot emote1;
    private final Slot emote2;
    private final Slot graffiti;
    private final List<Slot> slots = new ArrayList<>();

    private final Path root;
    private Map<CosmeticPack.Type, List<CosmeticPack.Item>> items = new java.util.EnumMap<>(CosmeticPack.Type.class);
    private final List<Spray> sprays = new ArrayList<>();
    private Slot emoting;
    private long emoteStart;
    private Vec3d petPos;
    private float petYaw;
    private long lastNano = System.nanoTime();
    private boolean keyWasDown;

    public Wardrobe(Path root) {
        super("Wardrobe", Category.COSMETICS, "Cape, wings, hat, outfit, pet, emotes and graffiti from neurocanvas/wardrobe (its Keybind sprays the graffiti)", false);
        aiControllable = false;
        customKey = true;
        this.root = root;
        // The slot settings come first in the list, before the general ones.
        cape = slot(CosmeticPack.Type.CAPE, "Cape");
        wings = slot(CosmeticPack.Type.WINGS, "Wings");
        hat = slot(CosmeticPack.Type.HAT, "Hat");
        outfit = slot(CosmeticPack.Type.OUTFIT, "Outfit");
        pet = slot(CosmeticPack.Type.PET, "Pet");
        emote1 = slot(CosmeticPack.Type.EMOTE, "Emote 1 (Wave)");
        emote2 = slot(CosmeticPack.Type.EMOTE, "Emote 2 (Dance)");
        graffiti = slot(CosmeticPack.Type.GRAFFITI, "Graffiti");
        for (int i = 0; i < slots.size(); i++) {
            settings.add(i, settings.remove(settings.size() - slots.size() + i));
        }
        try {
            if (!Files.isDirectory(root)) {
                ExamplePack.write(root);
            }
        } catch (IOException ignored) {
            // The folder is optional; items can still be added by hand.
        }
        rescan();
    }

    private Slot slot(CosmeticPack.Type type, String label) {
        Slot s = new Slot(type, add(Setting.choice(label, 0, NONE)));
        slots.add(s);
        return s;
    }

    // ---- items ------------------------------------------------------------------------------

    /** Looks for items again and refreshes every slot's list. */
    public void rescan() {
        items = CosmeticPack.scan(root);
        for (Slot s : slots) {
            String current = s.setting.option();
            List<String> options = new ArrayList<>();
            options.add(NONE);
            for (CosmeticPack.Item i : items.get(s.type)) {
                options.add(i.name());
            }
            s.setting.setOptions(options.toArray(new String[0]));
            s.setting.value = Math.max(0, options.indexOf(current));
            s.loadedName = "\u0000";
        }
    }

    private void load(MinecraftClient mc, Slot s) {
        unload(mc, s);
        String want = s.setting.option();
        s.loadedName = want;
        if (want.equals(NONE)) {
            return;
        }
        CosmeticPack.Item item = null;
        for (CosmeticPack.Item i : items.get(s.type)) {
            if (i.name().equals(want)) {
                item = i;
            }
        }
        if (item == null) {
            return;
        }
        try {
            Path file = item.file();
            if (file == null) {
                say(mc, s.type.label + " \"" + want + "\": no " + (s.type == CosmeticPack.Type.GRAFFITI ? ".png" : ".bbmodel") + " file in its folder");
                return;
            }
            s.config = item.config();
            if (s.type == CosmeticPack.Type.GRAFFITI) {
                try (InputStream in = Files.newInputStream(file)) {
                    NativeImage img = NativeImage.read(in);
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                    Identifier id = new Identifier("neurocanvas", "graffiti/" + UUID.randomUUID());
                    mc.getTextureManager().registerTexture(id, tex);
                    tex.setFilter(true, false);
                    s.image = id;
                }
            } else {
                s.model = new BbInstance(mc, BbModel.parse(Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException | RuntimeException e) {
            say(mc, "Could not load " + s.type.label.toLowerCase() + " \"" + want + "\": " + e.getMessage());
        }
    }

    private void unload(MinecraftClient mc, Slot s) {
        if (s.model != null) {
            s.model.close(mc);
            s.model = null;
        }
        if (s.image != null) {
            mc.getTextureManager().destroyTexture(s.image);
            s.image = null;
        }
        if (emoting == s) {
            emoting = null;
        }
    }

    private static void say(MinecraftClient mc, String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Wardrobe] " + text), false);
        }
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (openFolder.on()) {
            openFolder.set(0);
            net.minecraft.util.Util.getOperatingSystem().open(root.toFile());
        }
        if (reload.on()) {
            reload.set(0);
            rescan();
            say(mc, "found " + items.values().stream().mapToInt(List::size).sum() + " item(s)");
        }
        if (!enabled || mc.player == null) {
            if (!enabled) {
                for (Slot s : slots) {
                    if (s.model != null || s.image != null) {
                        unload(mc, s);
                        s.loadedName = "\u0000";
                    }
                }
                sprays.clear();
            }
            return;
        }
        for (Slot s : slots) {
            if (!s.setting.option().equals(s.loadedName)) {
                load(mc, s);
            }
        }
        boolean down = key >= 0 && mc.currentScreen == null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
        if (down && !keyWasDown) {
            spray(mc);
        }
        keyWasDown = down;
    }

    // ---- emotes -----------------------------------------------------------------------------

    /** Plays the emote of slot 1 (wave) or slot 2 (dance). False when that slot is empty. */
    public boolean emote(String name) {
        Slot s = name.equals("dance") ? emote2 : emote1;
        if (!enabled || s.model == null) {
            return false;
        }
        emoting = s;
        emoteStart = System.nanoTime();
        return true;
    }

    /** True while an emote plays on this player: the emote's model replaces the body. */
    public boolean hidesBody(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return enabled && emoting != null && entity == mc.player && (firstPerson.on() || !mc.options.getPerspective().isFirstPerson());
    }

    public boolean hidesCape(Entity entity) {
        return enabled && cape.model != null && entity == MinecraftClient.getInstance().player;
    }

    // ---- graffiti ---------------------------------------------------------------------------

    /** Sprays the picture on the block face you are looking at. */
    public void spray(MinecraftClient mc) {
        if (!enabled || graffiti.image == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            if (enabled && graffiti.image == null) {
                say(mc, "pick a graffiti in the Graffiti slot first");
            }
            return;
        }
        if (mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > 36) {
            say(mc, "too far from the wall");
            return;
        }
        Spray s = new Spray();
        Vec3d n = new Vec3d(hit.getSide().getOffsetX(), hit.getSide().getOffsetY(), hit.getSide().getOffsetZ());
        s.normal = n;
        s.pos = hit.getPos().add(n.multiply(0.012));
        s.size = graffiti.config.size == null ? 1.5f : graffiti.config.size;
        s.image = graffiti.image;
        s.created = System.currentTimeMillis();
        sprays.removeIf(o -> o.pos.squaredDistanceTo(s.pos) < 0.25 && o.normal.equals(s.normal));
        sprays.add(s);
        while (sprays.size() > 24) {
            sprays.remove(0);
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    private Matrix4f base(RenderEnv env, Vec3d at, float yaw, float itemScale) {
        float scale = size.f() * itemScale / 16f;
        Matrix4f m = new Matrix4f(env.m());
        m.translate((float) at.x, (float) at.y, (float) at.z);
        m.rotateY((float) Math.toRadians(-yaw + 180f));
        m.scale(scale);
        return m;
    }

    private float scaleOf(Slot s) {
        return s.config.scale == null ? 1f : s.config.scale;
    }

    /** Poses a model with its idle animation, or its walk animation while {@code moving}. */
    private void pose(Slot s, boolean moving, long now) {
        s.model.resetPose();
        BbModel.Anim a = moving ? s.model.find("walk") : null;
        if (a == null) {
            a = s.model.find("idle");
        }
        if (a != null) {
            s.model.apply(a, BbInstance.time(a, 0L, now, animSpeed.value));
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = now;
        float td = env.tickDelta();
        long nowMs = System.currentTimeMillis();
        sprays.removeIf(s -> nowMs - s.created > sprayLife.value * 1000);
        renderSprays(env, nowMs);

        Vec3d player = EffectDraw.lerpPos(p, td);
        float bodyYaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(td, p.prevHeadYaw, p.headYaw);
        float pitch = MathHelper.lerp(td, p.prevPitch, p.getPitch());
        double speed = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ) * 20.0;
        boolean moving = speed > 0.3;
        float light = 0.35f + 0.65f * mc.world.getLightLevel(BlockPos.ofFloored(player.x, player.y + 1, player.z)) / 15f;
        boolean wearing = firstPerson.on() || !mc.options.getPerspective().isFirstPerson();
        double t = env.t();

        if (emoting != null && emoting.model != null && wearing) {
            BbModel.Anim a = emoting.config.animation != null ? emoting.model.find(emoting.config.animation) : null;
            if (a == null && !emoting.model.model.animations.isEmpty()) {
                a = emoting.model.model.animations.values().iterator().next();
            }
            if (a == null || (now - emoteStart) / 1_000_000_000.0 * animSpeed.value > a.length) {
                emoting = null;
            } else {
                emoting.model.resetPose();
                emoting.model.apply(a, BbInstance.time(a, emoteStart, now, animSpeed.value));
                emoting.model.draw(base(env, player, bodyYaw, scaleOf(emoting)), light, shading.f());
            }
        }
        if (wearing) {
            if (outfit.model != null) {
                pose(outfit, moving, now);
                outfit.model.draw(base(env, player, bodyYaw, scaleOf(outfit)), light, shading.f());
            }
            if (cape.model != null) {
                pose(cape, moving, now);
                Matrix4f m = base(env, player, bodyYaw, scaleOf(cape));
                float swing = (float) (0.06 + Math.min(speed * 0.14, 0.9) + 0.03 * Math.sin(t * 2.2));
                m.translate(0f, 24f, 2f).rotateX(-swing).translate(0f, -24f, -2f);
                cape.model.draw(m, light, shading.f());
            }
            if (wings.model != null) {
                pose(wings, moving, now);
                wings.model.draw(base(env, player, bodyYaw, scaleOf(wings)), light, shading.f());
            }
            if (hat.model != null) {
                pose(hat, moving, now);
                Matrix4f m = base(env, player, bodyYaw, scaleOf(hat));
                m.translate(0f, 24f, 0f).rotateY((float) Math.toRadians(-MathHelper.wrapDegrees(headYaw - bodyYaw)))
                        .rotateX((float) Math.toRadians(-pitch)).translate(0f, -24f, 0f);
                hat.model.draw(m, light, shading.f());
            }
        }
        if (pet.model != null) {
            renderPet(env, p, player, bodyYaw, dt, light, now);
        }
    }

    private void renderPet(RenderEnv env, ClientPlayerEntity p, Vec3d player, float bodyYaw, float dt, float light, long now) {
        double fy = Math.toRadians(bodyYaw);
        Vec3d fwd = new Vec3d(-Math.sin(fy), 0, Math.cos(fy));
        Vec3d right = new Vec3d(-Math.cos(fy), 0, -Math.sin(fy));
        Vec3d target = player.add(right.multiply(petDistance.value * 0.8)).add(fwd.multiply(-petDistance.value * 0.7));
        if (petPos == null || petPos.squaredDistanceTo(target) > 144) {
            petPos = target;
        }
        Vec3d before = petPos;
        double k = 1.0 - Math.exp(-dt * 4.0);
        petPos = petPos.add(target.subtract(petPos).multiply(k));
        double moved = petPos.distanceTo(before) / Math.max(dt, 1e-3);
        Vec3d toPlayer = player.subtract(petPos);
        float want = (float) Math.toDegrees(Math.atan2(-toPlayer.x, toPlayer.z));
        petYaw = MathHelper.lerpAngleDegrees(0.2f, petYaw, moved > 0.4 ? (float) Math.toDegrees(Math.atan2(-(petPos.x - before.x), petPos.z - before.z)) : want);
        double ground = p.isOnGround() ? player.y : Math.min(player.y, petPos.y);
        Vec3d at = new Vec3d(petPos.x, ground, petPos.z);
        pose(pet, moved > 0.4, now);
        pet.model.draw(base(env, at, petYaw, scaleOf(pet)), light, shading.f());
    }

    private void renderSprays(RenderEnv env, long nowMs) {
        if (sprays.isEmpty()) {
            return;
        }
        Matrix4f m = env.m();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        Tessellator tess = Tessellator.getInstance();
        for (Spray s : sprays) {
            long age = nowMs - s.created;
            float fade = (float) Math.min(1.0, (sprayLife.value * 1000 - age) / 8000.0);
            Vec3d n = s.normal;
            Vec3d u = Math.abs(n.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(-n.z, 0, n.x).normalize();
            Vec3d v = Math.abs(n.y) > 0.9 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
            double hw = s.size / 2, hh = s.size / 2;
            RenderSystem.setShaderTexture(0, s.image);
            BufferBuilder b = tess.getBuffer();
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            double[][] c = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
            float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            for (int i = 0; i < 4; i++) {
                Vec3d q = s.pos.add(u.multiply(c[i][0] * hw)).add(v.multiply(c[i][1] * hh));
                b.vertex(m, (float) q.x, (float) q.y, (float) q.z).texture(uv[i][0], uv[i][1]).color(1f, 1f, 1f, fade).next();
            }
            tess.draw();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
