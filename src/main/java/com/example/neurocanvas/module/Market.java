package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.BbInstance;
import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.client.MarketClient;
import com.example.neurocanvas.client.ModrinthClient;
import com.example.neurocanvas.client.NeuroCanvasClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** The catalog of cosmetics on the project's server: one button downloads an item and puts it on. The list is drawn by the menu. */
public final class Market extends Module {
    /** The tabs along the top of the market: everything, the six kinds of cosmetics, then packs from Modrinth. */
    public static final String[] TABS = {"All", "Capes", "Wings", "Hats", "Outfits", "Emotes", "Graffiti", "Configs", "Shaders", "Resource packs"};
    private static int kind;
    private final Setting source = add(Setting.choice("Source", 0, "Official", "Community"));
    private int loadedSource = -1;
    private final Setting refresh = add(Setting.button("Refresh"));
    private final Path wardrobe;

    /** How an item looks, for its card: a model that is turned around, or a flat picture. */
    public static final class Preview {
        public BbInstance model;
        public String type = "";
        /** The animation an emote plays (from its cosmetic.json), or null for the first one. */
        public String animation;
        public Identifier image;
        public int imageW = 1;
        public int imageH = 1;
        public float[] center = {0, 16, 0};
        public float radius = 16;
        public boolean failed;
        boolean loading = true;
    }

    private static final int MAX_PREVIEWS = 24;
    private final Map<String, Preview> previews = new LinkedHashMap<>();

    private volatile List<MarketClient.Entry> entries;
    private volatile String status = "";
    private final Set<String> busy = new HashSet<>();
    private boolean loading;
    private String pendingDelete = "";
    private String pendingReport = "";

    public boolean deletePending(MarketClient.Entry e) {
        return pendingDelete.equals(e.type() + "/" + e.name() + "/" + e.code());
    }

    public boolean reportPending(MarketClient.Entry e) {
        return pendingReport.equals(e.type() + "/" + e.name() + "/" + e.code());
    }

    /** Reports a shared item as inappropriate: the first press asks, the second sends. */
    public void requestReport(MarketClient.Entry e) {
        if (e.code() == null) {
            return;
        }
        String key = e.type() + "/" + e.name() + "/" + e.code();
        if (!pendingReport.equals(key)) {
            pendingReport = key;
            status = "Press the flag again to report " + e.name() + " (broken, offensive or copied)";
            return;
        }
        pendingReport = "";
        com.example.neurocanvas.client.CommunityClient.report(e.code()).whenComplete((v, error) ->
                MinecraftClient.getInstance().execute(() -> status = error != null ? "Could not report: " + MarketClient.reason(error) : "Thanks, " + e.name() + " was reported"));
    }

    /** Admin only: the first press asks, the second removes the item from the server. */
    public void requestDelete(MarketClient.Entry e) {
        if (!com.example.neurocanvas.client.AdminStore.has()) {
            return;
        }
        String key = e.type() + "/" + e.name() + "/" + e.code();
        if (!pendingDelete.equals(key)) {
            pendingDelete = key;
            status = "Press the red button again to delete " + e.name() + " from the server";
            return;
        }
        pendingDelete = "";
        status = "Deleting " + e.name() + "...";
        com.example.neurocanvas.client.CommunityClient.adminDelete(e, com.example.neurocanvas.client.AdminStore.key()).whenComplete((v, error) ->
                MinecraftClient.getInstance().execute(() -> {
                    if (error != null) {
                        status = "Could not delete " + e.name() + ": " + MarketClient.reason(error);
                        return;
                    }
                    status = "Deleted " + e.name();
                    entries = null;
                    closePreviews();
                    refresh();
                }));
    }

    public Market(Path wardrobe) {
        super("Market", Category.COSMETICS, "Cosmetics on the project's server: one button downloads an item and puts it on", true);
        aiControllable = false;
        alwaysOn = true;
        this.wardrobe = wardrobe;
    }

    /** The preview of an item. It is loaded on first use; until then (or when it failed) the card shows a placeholder. */
    public Preview preview(MarketClient.Entry e) {
        String key = e.type() + "/" + e.name() + "/" + e.code();
        Preview p = previews.get(key);
        if (p != null) {
            return p;
        }
        Preview created = new Preview();
        created.type = e.type();
        previews.put(key, created);
        if (e.type().equals("configs")) {
            created.loading = false;
            created.failed = true;
            return created;
        }
        if (previews.size() > MAX_PREVIEWS) {
            String oldest = previews.keySet().iterator().next();
            close(previews.remove(oldest));
        }
        MarketClient.fetchAsync(e).whenComplete((files, error) -> MinecraftClient.getInstance().execute(() -> {
            created.loading = false;
            if (error != null) {
                created.failed = true;
                return;
            }
            try {
                build(created, e, files);
            } catch (IOException | RuntimeException ex) {
                created.failed = true;
            }
        }));
        return created;
    }

    private static void build(Preview p, MarketClient.Entry e, Map<String, byte[]> files) throws IOException {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e.type().equals(CosmeticPack.Type.GRAFFITI.folder)) {
            for (Map.Entry<String, byte[]> f : files.entrySet()) {
                if (f.getKey().toLowerCase().endsWith(".png")) {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(f.getValue()));
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                    Identifier id = new Identifier("neurocanvas", "market/" + UUID.randomUUID());
                    mc.getTextureManager().registerTexture(id, tex);
                    tex.setFilter(false, false);
                    p.image = id;
                    p.imageW = img.getWidth();
                    p.imageH = img.getHeight();
                    return;
                }
            }
            throw new IOException("no picture");
        }
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            if (f.getKey().equals("cosmetic.json")) {
                try {
                    CosmeticPack.Config cfg = new com.google.gson.Gson().fromJson(new String(f.getValue(), StandardCharsets.UTF_8), CosmeticPack.Config.class);
                    p.animation = cfg == null ? null : cfg.animation;
                } catch (RuntimeException ignored) {
                    // The first animation plays.
                }
            }
        }
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            if (f.getKey().toLowerCase().endsWith(".bbmodel")) {
                BbModel model = BbModel.parse(new String(f.getValue(), StandardCharsets.UTF_8));
                double[] lo = {1e9, 1e9, 1e9};
                double[] hi = {-1e9, -1e9, -1e9};
                for (BbModel.Bone b : model.bonesByUuid.values()) {
                    for (BbModel.Cube c : b.cubes) {
                        for (int i = 0; i < 3; i++) {
                            lo[i] = Math.min(lo[i], Math.min(c.from[i], c.to[i]));
                            hi[i] = Math.max(hi[i], Math.max(c.from[i], c.to[i]));
                        }
                    }
                }
                if (lo[0] > hi[0]) {
                    throw new IOException("empty model");
                }
                p.center = new float[] {(float) ((lo[0] + hi[0]) / 2), (float) ((lo[1] + hi[1]) / 2), (float) ((lo[2] + hi[2]) / 2)};
                p.radius = (float) Math.max(2.0, Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2])) / 2);
                p.model = new BbInstance(mc, model);
                return;
            }
        }
        throw new IOException("no model");
    }

    private static void close(Preview p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (p.model != null) {
            p.model.close(mc);
            p.model = null;
        }
        if (p.image != null) {
            mc.getTextureManager().destroyTexture(p.image);
            p.image = null;
        }
    }

    /** Frees the preview textures; the menu calls this when it closes. */
    public void closePreviews() {
        for (Preview p : previews.values()) {
            close(p);
        }
        previews.clear();
        closeIcons();
    }

    public String status() {
        return status;
    }

    public List<MarketClient.Entry> entries() {
        return entries;
    }

    public int kind() {
        return kind;
    }

    public void setKind(int k) {
        int next = Math.max(0, Math.min(k, TABS.length - 1));
        if (next != kind) {
            kind = next;
            status = "";
            hits = null;
            packQuery = "";
            closePreviews();
        }
    }

    /** The kind of cosmetic selected in the tabs, or null for all (and for the pack tabs). */
    public CosmeticPack.Type filter() {
        return kind >= 1 && kind <= CosmeticPack.Type.values().length ? CosmeticPack.Type.values()[kind - 1] : null;
    }

    /** The folder name of the kind the selected tab shows ("configs" for the config tab), or null for all. */
    public String filterFolder() {
        return kind == 7 ? "configs" : filter() == null ? null : filter().folder;
    }

    /** The kind of pack the selected tab lists, or null when it lists cosmetics. */
    public ModrinthClient.Kind packKind() {
        return kind == 8 ? ModrinthClient.Kind.SHADER : kind == 9 ? ModrinthClient.Kind.RESOURCEPACK : null;
    }

    // ---- packs from Modrinth ------------------------------------------------------------------------

    private volatile List<ModrinthClient.Hit> hits;
    private volatile boolean packLoading;
    private volatile boolean packMore;
    private String packQuery = "";
    private final Map<String, String> installedPacks = new LinkedHashMap<>();
    private boolean packsRead;

    public List<ModrinthClient.Hit> hits() {
        return hits;
    }

    public boolean packLoading() {
        return packLoading;
    }

    public boolean packMore() {
        return packMore;
    }

    public String packQuery() {
        return packQuery;
    }

    private Path packFile() {
        return wardrobe.resolveSibling("market-packs.json");
    }

    private void readPacks() {
        if (packsRead) {
            return;
        }
        packsRead = true;
        try {
            if (Files.exists(packFile())) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(packFile(), StandardCharsets.UTF_8)).getAsJsonObject();
                o.entrySet().forEach(e -> installedPacks.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (IOException | RuntimeException ignored) {
            // A broken record just means nothing shows as installed.
        }
    }

    private void writePacks() {
        try {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            installedPacks.forEach(o::addProperty);
            Files.createDirectories(packFile().getParent());
            Files.writeString(packFile(), o.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // The record is only a convenience.
        }
    }

    private static Path packFolder(ModrinthClient.Kind k) {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(k.folder);
    }

    public void searchPacks(String query) {
        ModrinthClient.Kind k = packKind();
        if (k == null || packLoading) {
            return;
        }
        packQuery = query.trim();
        loadPacks(k, 0);
    }

    public void morePacks() {
        ModrinthClient.Kind k = packKind();
        if (k != null && !packLoading && hits != null) {
            loadPacks(k, hits.size());
        }
    }

    private void loadPacks(ModrinthClient.Kind k, int offset) {
        packLoading = true;
        status = "Searching Modrinth...";
        ModrinthClient.search(k, packQuery, offset).whenComplete((list, error) -> MinecraftClient.getInstance().execute(() -> {
            packLoading = false;
            if (packKind() != k) {
                return;
            }
            if (error != null) {
                status = "Could not search Modrinth: " + ModrinthClient.reason(error);
                if (hits == null) {
                    hits = List.of();
                }
                return;
            }
            List<ModrinthClient.Hit> all = new ArrayList<>(offset == 0 || hits == null ? List.of() : hits);
            all.addAll(list);
            hits = all;
            packMore = list.size() >= 20;
            status = all.isEmpty() ? "Nothing found" : "";
        }));
    }

    public boolean packInstalled(ModrinthClient.Hit h) {
        readPacks();
        String f = installedPacks.get(h.id());
        ModrinthClient.Kind k = packKind();
        return f != null && k != null && Files.exists(packFolder(k).resolve(f));
    }

    public boolean packActive(ModrinthClient.Hit h) {
        ModrinthClient.Kind k = packKind();
        String f = installedPacks.get(h.id());
        if (k == null || f == null) {
            return false;
        }
        if (k == ModrinthClient.Kind.RESOURCEPACK) {
            return MinecraftClient.getInstance().getResourcePackManager().getEnabledNames().contains("file/" + f);
        }
        return f.equals(irisPack());
    }

    public static boolean irisPresent() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");
    }

    private static Path irisConfig() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("iris.properties");
    }

    private static String irisPack() {
        try {
            if (Files.exists(irisConfig())) {
                java.util.Properties p = new java.util.Properties();
                try (java.io.Reader r = Files.newBufferedReader(irisConfig(), StandardCharsets.UTF_8)) {
                    p.load(r);
                }
                return p.getProperty("shaderPack");
            }
        } catch (IOException ignored) {
            // No Iris settings yet.
        }
        return null;
    }

    /** Downloads the pack if needed and puts it on: a resource pack is switched on at once, a shader pack is chosen in Iris. */
    public void getPack(ModrinthClient.Hit h) {
        ModrinthClient.Kind k = packKind();
        if (k == null) {
            return;
        }
        String key = "pack/" + h.id();
        synchronized (busy) {
            if (!busy.add(key)) {
                return;
            }
        }
        readPacks();
        MinecraftClient mc = MinecraftClient.getInstance();
        String have = installedPacks.get(h.id());
        boolean present = have != null && Files.exists(packFolder(k).resolve(have));
        status = (present ? "Putting on " : "Downloading ") + h.title() + "...";
        java.util.concurrent.CompletableFuture<String> job = present ? java.util.concurrent.CompletableFuture.completedFuture(have)
                : ModrinthClient.install(k, h.id(), packFolder(k), pct -> mc.execute(() -> status = "Downloading " + h.title() + ": " + pct + "%"));
        job.whenComplete((file, error) -> mc.execute(() -> {
            synchronized (busy) {
                busy.remove(key);
            }
            if (error != null) {
                status = "Could not get " + h.title() + ": " + ModrinthClient.reason(error);
                return;
            }
            installedPacks.put(h.id(), file);
            writePacks();
            status = k == ModrinthClient.Kind.RESOURCEPACK ? useResourcePack(mc, h.title(), file) : useShader(h.title(), file);
        }));
    }

    private static String useResourcePack(MinecraftClient mc, String title, String file) {
        try {
            net.minecraft.resource.ResourcePackManager rpm = mc.getResourcePackManager();
            rpm.scanPacks();
            String id = "file/" + file;
            if (rpm.getProfile(id) == null) {
                return title + " was downloaded, but the game does not list it as a pack";
            }
            List<String> names = new ArrayList<>(rpm.getEnabledNames());
            if (!names.contains(id)) {
                names.add(id);
            }
            rpm.setEnabledProfiles(names);
            mc.options.resourcePacks.clear();
            mc.options.resourcePacks.addAll(rpm.getEnabledNames());
            mc.options.write();
            mc.reloadResources();
            return title + " is on";
        } catch (RuntimeException e) {
            return title + " was downloaded, switch it on in Options > Resource Packs";
        }
    }

    private static String useShader(String title, String file) {
        if (!irisPresent()) {
            return title + " is saved in shaderpacks. Shaders need the Iris mod, which is not installed";
        }
        try {
            java.util.Properties p = new java.util.Properties();
            if (Files.exists(irisConfig())) {
                try (java.io.Reader r = Files.newBufferedReader(irisConfig(), StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
            p.setProperty("shaderPack", file);
            p.setProperty("enableShaders", "true");
            try (java.io.Writer w = Files.newBufferedWriter(irisConfig(), StandardCharsets.UTF_8)) {
                p.store(w, "Iris Configuration");
            }
            return title + " is chosen in Iris: reload shaders in the Iris menu or restart the game";
        } catch (IOException e) {
            return title + " was downloaded, choose it in Options > Video Settings > Shader Packs";
        }
    }

    public boolean installed(MarketClient.Entry e) {
        return MarketClient.installedName(e, wardrobe) != null;
    }

    public boolean busy(MarketClient.Entry e) {
        synchronized (busy) {
            return busy.contains(e.type() + "/" + e.name() + "/" + e.code());
        }
    }

    // ---- icons of the packs -------------------------------------------------------------------

    private static final int ICON_SIZE = 64;
    private static final int MAX_ICONS = 80;
    private final Map<String, Identifier> icons = new LinkedHashMap<>();
    private final Set<String> iconRequested = new HashSet<>();

    /** The icon of a pack as a texture, or null while it loads (or when it has none). It is fetched in the background on first use. */
    public Identifier icon(ModrinthClient.Hit h) {
        Identifier id = icons.get(h.id());
        if (id != null || h.iconUrl().isEmpty() || !iconRequested.add(h.id())) {
            return id;
        }
        ModrinthClient.icon(h.iconUrl()).thenApplyAsync(bytes -> {
            try {
                return com.example.neurocanvas.client.IconDecoder.decode(bytes, ICON_SIZE);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((px, error) -> MinecraftClient.getInstance().execute(() -> {
            if (error != null || px == null || packKind() == null) {
                return;
            }
            NativeImage img = new NativeImage(ICON_SIZE, ICON_SIZE, false);
            for (int y = 0; y < ICON_SIZE; y++) {
                for (int x = 0; x < ICON_SIZE; x++) {
                    int argb = px[y * ICON_SIZE + x];
                    img.setColor(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
                }
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            tex.setFilter(true, false);
            Identifier tid = new Identifier("neurocanvas", "market/icon/" + UUID.randomUUID());
            MinecraftClient.getInstance().getTextureManager().registerTexture(tid, tex);
            icons.put(h.id(), tid);
            while (icons.size() > MAX_ICONS) {
                String oldest = icons.keySet().iterator().next();
                MinecraftClient.getInstance().getTextureManager().destroyTexture(icons.remove(oldest));
                iconRequested.remove(oldest);
            }
        }));
        return null;
    }

    private void closeIcons() {
        for (Identifier id : icons.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        }
        icons.clear();
        iconRequested.clear();
    }

    public boolean busy(ModrinthClient.Hit h) {
        synchronized (busy) {
            return busy.contains("pack/" + h.id());
        }
    }

    /** Loads the catalog once; the menu calls this when the panel opens. */
    public void ensureLoaded() {
        if (packKind() != null) {
            if (hits == null && !packLoading) {
                loadPacks(packKind(), 0);
            }
            return;
        }
        if (entries == null && !loading) {
            refresh();
        }
    }

    public void refresh() {
        loading = true;
        status = "Loading the catalog...";
        loadedSource = source.index();
        MarketClient.list(source.index() == 1).whenComplete((list, error) -> MinecraftClient.getInstance().execute(() -> {
            loading = false;
            if (error != null) {
                status = "Could not load the catalog: " + MarketClient.reason(error);
            } else {
                entries = list;
                status = list.isEmpty() ? "The catalog is empty" : "";
            }
        }));
    }

    /** Downloads an item and wears it: its module is switched on and the item is chosen in it. */
    public void get(MarketClient.Entry e) {
        String key = e.type() + "/" + e.name() + "/" + e.code();
        synchronized (busy) {
            if (!busy.add(key)) {
                return;
            }
        }
        if (e.type().equals("configs")) {
            getConfig(e, key);
            return;
        }
        String have = MarketClient.installedName(e, wardrobe);
        status = (have != null ? "Putting on " : "Downloading ") + e.name() + "...";
        (have != null ? java.util.concurrent.CompletableFuture.completedFuture(have) : MarketClient.install(e, wardrobe)).whenComplete((name, error) ->
                MinecraftClient.getInstance().execute(() -> {
                    synchronized (busy) {
                        busy.remove(key);
                    }
                    if (error != null) {
                        status = "Could not get " + e.name() + ": " + MarketClient.reason(error);
                        return;
                    }
                    status = e.name() + " was downloaded but could not be put on";
                    for (Module m : NeuroCanvasClient.modules().all()) {
                        if (m instanceof CosmeticModule c && c.type().folder.equals(e.type())) {
                            c.rescan();
                            status = c.wear(name) ? e.name() + " is on (" + c.name + ")" : status;
                        }
                    }
                }));
    }

    /** Applies someone's shared config. Your current settings are saved as a preset first, so nothing is lost. */
    private void getConfig(MarketClient.Entry e, String key) {
        status = "Downloading " + e.name() + "...";
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, byte[]> files = MarketClient.fetch(e);
                byte[] data = files.get("config.json");
                if (data == null) {
                    throw new IOException("the config has no data");
                }
                return com.google.gson.JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject().get("code").getAsString();
            } catch (IOException | InterruptedException | RuntimeException ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }).whenComplete((code, error) -> MinecraftClient.getInstance().execute(() -> {
            synchronized (busy) {
                busy.remove(key);
            }
            if (error != null) {
                status = "Could not get " + e.name() + ": " + MarketClient.reason(error);
                return;
            }
            try {
                String backup = ModuleManager.cleanName("Before " + e.name());
                NeuroCanvasClient.modules().savePreset(backup);
                NeuroCanvasClient.modules().importCode(code);
                status = e.name() + " is applied (your old settings: preset \"" + backup + "\")";
            } catch (IOException ex) {
                status = "Could not apply " + e.name() + ": " + ex.getMessage();
            }
        }));
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (kind == 7 && source.index() == 0) {
            source.set(1);
        }
        if (loadedSource >= 0 && loadedSource != source.index() && !loading) {
            entries = null;
            closePreviews();
            refresh();
        }
        if (refresh.on()) {
            refresh.set(0);
            refresh();
        }
    }
}
