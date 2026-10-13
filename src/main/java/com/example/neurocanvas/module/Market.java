package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.BbInstance;
import com.example.neurocanvas.avatar.BbModel;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.client.MarketClient;
import com.example.neurocanvas.client.NeuroCanvasClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final Setting show;
    private final Setting refresh = add(Setting.button("Refresh"));
    private final Path wardrobe;

    /** How an item looks, for its card: a model that is turned around, or a flat picture. */
    public static final class Preview {
        public BbInstance model;
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

    public Market(Path wardrobe) {
        super("Market", Category.COSMETICS, "Cosmetics on the project's server: one button downloads an item and puts it on", true);
        aiControllable = false;
        alwaysOn = true;
        this.wardrobe = wardrobe;
        String[] kinds = new String[CosmeticPack.Type.values().length + 1];
        kinds[0] = "All";
        for (int i = 0; i < CosmeticPack.Type.values().length; i++) {
            kinds[i + 1] = CosmeticPack.Type.values()[i].label;
        }
        show = add(Setting.choice("Show", 0, kinds));
        settings.add(0, settings.remove(settings.size() - 1));
    }

    /** The preview of an item. It is loaded on first use; until then (or when it failed) the card shows a placeholder. */
    public Preview preview(MarketClient.Entry e) {
        String key = e.type() + "/" + e.name();
        Preview p = previews.get(key);
        if (p != null) {
            return p;
        }
        Preview created = new Preview();
        previews.put(key, created);
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
    }

    public String status() {
        return status;
    }

    public List<MarketClient.Entry> entries() {
        return entries;
    }

    /** The kind selected in "Show", or null for all. */
    public CosmeticPack.Type filter() {
        int i = show.index();
        return i == 0 ? null : CosmeticPack.Type.values()[i - 1];
    }

    public boolean installed(MarketClient.Entry e) {
        return Files.isDirectory(wardrobe.resolve(e.type()).resolve(e.name()));
    }

    public boolean busy(MarketClient.Entry e) {
        synchronized (busy) {
            return busy.contains(e.type() + "/" + e.name());
        }
    }

    /** Loads the catalog once; the menu calls this when the panel opens. */
    public void ensureLoaded() {
        if (entries == null && !loading) {
            refresh();
        }
    }

    public void refresh() {
        loading = true;
        status = "Loading the catalog...";
        MarketClient.list().whenComplete((list, error) -> MinecraftClient.getInstance().execute(() -> {
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
        String key = e.type() + "/" + e.name();
        synchronized (busy) {
            if (!busy.add(key)) {
                return;
            }
        }
        boolean have = installed(e);
        status = (have ? "Putting on " : "Downloading ") + e.name() + "...";
        (have ? java.util.concurrent.CompletableFuture.<Void>completedFuture(null) : MarketClient.install(e, wardrobe)).whenComplete((v, error) ->
                MinecraftClient.getInstance().execute(() -> {
                    synchronized (busy) {
                        busy.remove(key);
                    }
                    if (error != null) {
                        status = "Could not get " + e.name() + ": " + MarketClient.reason(error);
                        return;
                    }
                    for (Module m : NeuroCanvasClient.modules().all()) {
                        if (m instanceof CosmeticModule c && c.type().folder.equals(e.type())) {
                            c.rescan();
                            status = c.wear(e.name()) ? e.name() + " is on (" + c.name + ")" : e.name() + " was downloaded but could not be found";
                        }
                    }
                }));
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (refresh.on()) {
            refresh.set(0);
            refresh();
        }
    }
}
