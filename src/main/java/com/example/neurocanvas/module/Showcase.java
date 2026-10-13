package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.Attach;
import com.example.neurocanvas.avatar.BbInstance;
import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.client.MarketClient;
import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.client.SharedStore;
import com.example.neurocanvas.fx.EffectDraw;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Shows which cosmetics people wear. What you wear (if it is shared or comes from the Market) is announced to the other users
 * on your server; what they announce is downloaded, checked and drawn on them. Capes, wings, hats and outfits are shown.
 */
public final class Showcase extends Module {
    /** One announced cosmetic. {@code ref} is a share code, or "m:" and the name of a Market item. */
    public record Item(String type, String ref, String name) { }

    private static final Pattern REF = Pattern.compile("[A-HJ-NP-Z2-9]{8}|m:[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,39}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,39}");
    private static final Set<String> SHOWN = Set.of("capes", "wings", "hats", "outfits");
    private static final int MAX_CACHE = 24;

    private static final class Loaded {
        BbInstance model;
        net.minecraft.util.Identifier image;
        String animation;
        float scale = 1f;
        boolean failed;
        long failedAt;
    }

    /** An emote another user is playing right now. */
    private static final class Emoting {
        Loaded loaded;
        long start;
    }

    /** A graffiti another user sprayed. */
    private static final class RemoteSpray {
        String owner;
        Loaded loaded;
        Vec3d pos;
        Vec3d normal;
        float size;
        long created;
    }

    private static final long SPRAY_LIFE_MS = 120_000;

    private final Setting announce = add(Setting.bool("Share what I wear", true));
    private final Setting showOthers = add(Setting.bool("Show what others wear", true));
    private final Setting shareAll = add(Setting.button("Share everything I wear"));

    private final Map<String, List<Item>> others = new HashMap<>();
    private final Map<String, Emoting> emoting = new HashMap<>();
    private final List<RemoteSpray> sprays = new ArrayList<>();
    private final Map<String, Loaded> cache = new LinkedHashMap<>();
    private final Set<String> officialNames = new HashSet<>();
    private boolean officialAsked;
    private String lastSent = "";
    private boolean wasConnected;
    private int tick;
    private long lastNano = System.nanoTime();

    public Showcase() {
        super("Showcase", Category.FRIENDS, "Show the cosmetics other users wear, and let them see yours (shared items only)", true);
        aiControllable = false;
        alwaysOn = true;
    }

    // ---- what I wear ------------------------------------------------------------------------

    private List<CosmeticModule> wornModules() {
        List<CosmeticModule> out = new ArrayList<>();
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (m instanceof CosmeticModule c && SHOWN.contains(c.type().folder) && c.wornName() != null) {
                out.add(c);
            }
        }
        return out;
    }

    /** The reference other users can use to get this item, or null when it is only on your computer. */
    private String refOf(CosmeticModule c, String name) {
        SharedStore.Item shared = SharedStore.find(c.type().folder, name);
        if (shared != null) {
            return shared.code();
        }
        try {
            java.nio.file.Path source = c.folder().resolve(name).resolve("source.txt");
            if (Files.exists(source)) {
                String code = Files.readString(source).trim();
                if (REF.matcher(code).matches()) {
                    return code;
                }
            }
        } catch (IOException ignored) {
            // Not from the community.
        }
        return officialNames.contains(c.type().folder + "/" + name) ? "m:" + name : null;
    }

    private List<Item> mine() {
        List<Item> out = new ArrayList<>();
        for (CosmeticModule c : wornModules()) {
            String name = c.wornName();
            String ref = refOf(c, name);
            if (ref != null) {
                out.add(new Item(c.type().folder, ref, name));
            }
        }
        return out;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (shareAll.on()) {
            shareAll.set(0);
            for (CosmeticModule c : wornModules()) {
                if (refOf(c, c.wornName()) == null) {
                    c.shareSelected(mc);
                }
            }
        }
        if (mc.player == null) {
            return;
        }
        if (!officialAsked) {
            officialAsked = true;
            MarketClient.list(false).whenComplete((list, error) -> {
                if (error == null) {
                    mc.execute(() -> list.forEach(e -> officialNames.add(e.type() + "/" + e.name())));
                }
            });
        }
        if (++tick % 20 != 0) {
            return;
        }
        boolean connected = NeuroCanvasClient.modules().get(NameTags.class).connected();
        List<Item> now = announce.on() ? mine() : List.of();
        JsonArray items = new JsonArray();
        StringBuilder signature = new StringBuilder();
        for (Item i : now) {
            JsonObject o = new JsonObject();
            o.addProperty("type", i.type());
            o.addProperty("ref", i.ref());
            o.addProperty("name", i.name());
            items.add(o);
            signature.append(i.type()).append(i.ref()).append(';');
        }
        String sig = signature.toString();
        if (connected && (!sig.equals(lastSent) || !wasConnected)) {
            if (!sig.isEmpty() || !lastSent.isEmpty() || !wasConnected) {
                JsonObject message = new JsonObject();
                message.addProperty("t", "wear");
                message.add("items", items);
                NeuroCanvasClient.modules().get(Friends.class).broadcast(message);
            }
            lastSent = sig;
        }
        wasConnected = connected;
    }

    // ---- emotes and graffiti ---------------------------------------------------------------

    private JsonObject itemJson(CosmeticModule m, String name) {
        String ref = refOf(m, name);
        if (ref == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("ref", ref);
        o.addProperty("name", name);
        return o;
    }

    /** Tells the others that you play an emote (only when it is shared or from the Market, so they can get it). */
    public void announceEmote(CosmeticModule module, String name) {
        if (!enabled || !announce.on()) {
            return;
        }
        JsonObject item = itemJson(module, name);
        if (item != null) {
            JsonObject message = new JsonObject();
            message.addProperty("t", "emote");
            message.add("item", item);
            NeuroCanvasClient.modules().get(Friends.class).broadcast(message);
        }
    }

    public void announceSpray(CosmeticModule module, String name, Vec3d pos, Vec3d normal, float size) {
        if (!enabled || !announce.on()) {
            return;
        }
        JsonObject item = itemJson(module, name);
        if (item != null) {
            JsonObject message = new JsonObject();
            message.addProperty("t", "spray");
            message.add("item", item);
            message.addProperty("x", Math.round(pos.x * 1000) / 1000.0);
            message.addProperty("y", Math.round(pos.y * 1000) / 1000.0);
            message.addProperty("z", Math.round(pos.z * 1000) / 1000.0);
            message.addProperty("nx", (int) normal.x);
            message.addProperty("ny", (int) normal.y);
            message.addProperty("nz", (int) normal.z);
            message.addProperty("size", Math.max(0.2, Math.min(size, 6)));
            NeuroCanvasClient.modules().get(Friends.class).broadcast(message);
        }
    }

    private Item readItem(JsonObject o, String type) {
        String ref = o.get("ref").getAsString();
        String name = o.has("name") ? o.get("name").getAsString() : "";
        if (!REF.matcher(ref).matches() || !(name.isEmpty() || NAME.matcher(name).matches())) {
            return null;
        }
        return new Item(type, ref, name.isEmpty() ? "item" : name);
    }

    public void receiveEmote(JsonObject o) {
        if (!enabled || !showOthers.on()) {
            return;
        }
        try {
            String from = o.get("from").getAsString();
            Item item = readItem(o.getAsJsonObject("item"), "emotes");
            if (item == null || !NeuroCanvasClient.modules().get(Friends.class).acceptsFrom(from)) {
                return;
            }
            Emoting e = new Emoting();
            e.loaded = load(item);
            e.start = System.nanoTime();
            emoting.put(from.toLowerCase(Locale.ROOT), e);
        } catch (RuntimeException ignored) {
            // A malformed message is ignored.
        }
    }

    public void receiveSpray(JsonObject o) {
        if (!enabled || !showOthers.on()) {
            return;
        }
        try {
            String from = o.get("from").getAsString();
            Item item = readItem(o.getAsJsonObject("item"), "graffiti");
            if (item == null || !NeuroCanvasClient.modules().get(Friends.class).acceptsFrom(from)) {
                return;
            }
            RemoteSpray s = new RemoteSpray();
            s.owner = from.toLowerCase(Locale.ROOT);
            s.loaded = load(item);
            s.pos = new Vec3d(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
            s.normal = new Vec3d(o.get("nx").getAsInt(), o.get("ny").getAsInt(), o.get("nz").getAsInt());
            s.size = (float) Math.max(0.2, Math.min(o.get("size").getAsDouble(), 6));
            s.created = System.currentTimeMillis();
            long mine = sprays.stream().filter(x -> x.owner.equals(s.owner)).count();
            while (mine >= 6) {
                for (int i = 0; i < sprays.size(); i++) {
                    if (sprays.get(i).owner.equals(s.owner)) {
                        sprays.remove(i);
                        break;
                    }
                }
                mine--;
            }
            sprays.add(s);
            while (sprays.size() > 40) {
                sprays.remove(0);
            }
        } catch (RuntimeException ignored) {
            // A malformed message is ignored.
        }
    }

    /** True while another user's emote plays: their own body is not drawn then, the emote's model takes its place. */
    public boolean hidesBody(net.minecraft.entity.Entity entity) {
        if (!enabled || emoting.isEmpty() || !(entity instanceof PlayerEntity) || entity == MinecraftClient.getInstance().player) {
            return false;
        }
        Emoting e = emoting.get(entity.getName().getString().toLowerCase(Locale.ROOT));
        return e != null && e.loaded.model != null;
    }

    // ---- what others wear -------------------------------------------------------------------

    public void receive(JsonObject wear) {
        if (!enabled || !showOthers.on()) {
            return;
        }
        try {
            String from = wear.get("from").getAsString();
            if (!NeuroCanvasClient.modules().get(Friends.class).acceptsFrom(from)) {
                return;
            }
            List<Item> items = new ArrayList<>();
            for (JsonElement e : wear.getAsJsonArray("items")) {
                JsonObject o = e.getAsJsonObject();
                String type = o.get("type").getAsString();
                String ref = o.get("ref").getAsString();
                String name = o.has("name") ? o.get("name").getAsString() : "";
                if (SHOWN.contains(type) && REF.matcher(ref).matches() && (name.isEmpty() || NAME.matcher(name).matches())) {
                    items.add(new Item(type, ref, name.isEmpty() ? "item" : name));
                }
                if (items.size() >= 4) {
                    break;
                }
            }
            if (items.isEmpty()) {
                others.remove(from.toLowerCase(Locale.ROOT));
            } else {
                others.put(from.toLowerCase(Locale.ROOT), items);
            }
        } catch (RuntimeException ignored) {
            // A malformed message is ignored.
        }
    }

    public List<Item> itemsOf(String nick) {
        return others.getOrDefault(nick.toLowerCase(Locale.ROOT), List.of());
    }

    /** Downloads and puts on everything a player wears. */
    public void getAll(String nick) {
        Market market = NeuroCanvasClient.modules().get(Market.class);
        for (Item i : itemsOf(nick)) {
            boolean official = i.ref().startsWith("m:");
            market.get(new MarketClient.Entry(i.type(), official ? i.ref().substring(2) : i.name(), official ? "Neuro Canvas" : nick, 0, official ? null : i.ref()));
        }
    }

    private Loaded load(Item item) {
        String key = item.type() + "/" + item.ref();
        Loaded l = cache.get(key);
        if (l != null) {
            if (l.failed && System.currentTimeMillis() - l.failedAt > 60_000) {
                cache.remove(key);
            } else {
                return l;
            }
        }
        Loaded created = new Loaded();
        cache.put(key, created);
        if (cache.size() > MAX_CACHE) {
            String oldest = cache.keySet().iterator().next();
            Loaded gone = cache.remove(oldest);
            if (gone != null && gone.model != null) {
                gone.model.close(MinecraftClient.getInstance());
            }
            if (gone != null && gone.image != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(gone.image);
            }
        }
        boolean official = item.ref().startsWith("m:");
        MarketClient.Entry entry = new MarketClient.Entry(item.type(), official ? item.ref().substring(2) : "shared", "", 0, official ? null : item.ref());
        MarketClient.fetchAsync(entry).whenComplete((files, error) -> MinecraftClient.getInstance().execute(() -> {
            try {
                if (error != null) {
                    throw new IOException("download failed");
                }
                for (Map.Entry<String, byte[]> f : files.entrySet()) {
                    if (item.type().equals("graffiti") && f.getKey().toLowerCase().endsWith(".png") && created.image == null) {
                        net.minecraft.client.texture.NativeImageBackedTexture tex = new net.minecraft.client.texture.NativeImageBackedTexture(
                                net.minecraft.client.texture.NativeImage.read(new java.io.ByteArrayInputStream(f.getValue())));
                        net.minecraft.util.Identifier id = new net.minecraft.util.Identifier("neurocanvas", "remote/" + java.util.UUID.randomUUID());
                        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
                        tex.setFilter(true, false);
                        created.image = id;
                    } else if (f.getKey().toLowerCase().endsWith(".bbmodel")) {
                        created.model = new BbInstance(MinecraftClient.getInstance(), BbModel.parse(new String(f.getValue(), StandardCharsets.UTF_8)));
                    } else if (f.getKey().equals("cosmetic.json")) {
                        CosmeticPack.Config cfg = new Gson().fromJson(new String(f.getValue(), StandardCharsets.UTF_8), CosmeticPack.Config.class);
                        if (cfg != null && cfg.scale != null) {
                            created.scale = Math.max(0.2f, Math.min(cfg.scale, 4f));
                        }
                        if (cfg != null && cfg.animation != null) {
                            created.animation = cfg.animation;
                        }
                    }
                }
                if (created.model == null && created.image == null) {
                    throw new IOException("nothing to show");
                }
            } catch (IOException | RuntimeException e) {
                created.failed = true;
                created.failedAt = System.currentTimeMillis();
            }
        }));
        return created;
    }

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        if (!enabled || !showOthers.on() || others.isEmpty() || mc.world == null || mc.player == null) {
            return;
        }
        long now = System.nanoTime();
        float td = env.tickDelta();
        double t = env.t();
        long nowMs = System.currentTimeMillis();
        sprays.removeIf(x -> nowMs - x.created > SPRAY_LIFE_MS);
        if (!sprays.isEmpty()) {
            com.example.neurocanvas.avatar.SprayDraw.begin();
            for (RemoteSpray x : sprays) {
                if (x.loaded.image != null && x.pos.squaredDistanceTo(mc.player.getPos()) < 128 * 128) {
                    float fade = (float) Math.min(1.0, (SPRAY_LIFE_MS - (nowMs - x.created)) / 8000.0);
                    com.example.neurocanvas.avatar.SprayDraw.quad(env.m(), x.pos, x.normal, x.size, x.loaded.image, fade);
                }
            }
            com.example.neurocanvas.avatar.SprayDraw.end();
        }
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            Emoting em = p == mc.player ? null : emoting.get(p.getName().getString().toLowerCase(Locale.ROOT));
            if (em != null && em.loaded.model != null) {
                BbInstance model = em.loaded.model;
                BbModel.Anim a = em.loaded.animation != null ? model.find(em.loaded.animation) : null;
                if (a == null && !model.model.animations.isEmpty()) {
                    a = model.model.animations.values().iterator().next();
                }
                if (a == null || (now - em.start) / 1_000_000_000.0 > a.length) {
                    emoting.remove(p.getName().getString().toLowerCase(Locale.ROOT));
                } else {
                    model.resetPose();
                    model.apply(a, BbInstance.time(a, em.start, now, 1.0));
                    Vec3d at = EffectDraw.lerpPos(p, td);
                    float yaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
                    float lit = 0.35f + 0.65f * mc.world.getLightLevel(BlockPos.ofFloored(at.x, at.y + 1, at.z)) / 15f;
                    model.draw(Attach.base(env.m(), at, yaw, em.loaded.scale / 16f), lit, 0.5f);
                    continue;
                }
            }
            if (p == mc.player || p.isInvisible() || p.isSpectator()) {
                continue;
            }
            List<Item> items = others.get(p.getName().getString().toLowerCase(Locale.ROOT));
            if (items == null || p.squaredDistanceTo(mc.player) > 96 * 96) {
                continue;
            }
            Vec3d pos = EffectDraw.lerpPos(p, td);
            float bodyYaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
            float headYaw = MathHelper.lerpAngleDegrees(td, p.prevHeadYaw, p.headYaw);
            float pitch = MathHelper.lerp(td, p.prevPitch, p.getPitch());
            double speed = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ) * 20.0;
            float light = 0.35f + 0.65f * mc.world.getLightLevel(BlockPos.ofFloored(pos.x, pos.y + 1, pos.z)) / 15f;
            for (Item item : items) {
                Loaded l = load(item);
                if (l.model == null) {
                    continue;
                }
                Attach.pose(l.model, speed > 0.3, now, 1.0);
                var base = Attach.base(env.m(), pos, bodyYaw, l.scale / 16f);
                switch (item.type()) {
                    case "capes" -> Attach.cape(l.model, base, speed, t, light, 0.5f);
                    case "hats" -> Attach.hat(l.model, base, bodyYaw, headYaw, pitch, light, 0.5f);
                    case "wings" -> Attach.wings(l.model, base, light, 0.5f);
                    default -> Attach.outfit(l.model, base, light, 0.5f);
                }
            }
        }
    }
}
