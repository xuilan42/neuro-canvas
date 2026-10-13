package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.client.MarketClient;
import com.example.neurocanvas.client.NeuroCanvasClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;

/** The catalog of cosmetics on the project's server: one button downloads an item and puts it on. The list is drawn by the menu. */
public final class Market extends Module {
    private final Setting show;
    private final Setting refresh = add(Setting.button("Refresh"));
    private final Path wardrobe;

    private volatile List<MarketClient.Entry> entries;
    private volatile String status = "";
    private final Set<String> busy = new HashSet<>();
    private boolean loading;

    public Market(Path wardrobe) {
        super("Market", Category.COSMETICS, "Cosmetics on the project's server: one button downloads an item and puts it on", true);
        aiControllable = false;
        this.wardrobe = wardrobe;
        String[] kinds = new String[CosmeticPack.Type.values().length + 1];
        kinds[0] = "All";
        for (int i = 0; i < CosmeticPack.Type.values().length; i++) {
            kinds[i + 1] = CosmeticPack.Type.values()[i].label;
        }
        show = add(Setting.choice("Show", 0, kinds));
        settings.add(0, settings.remove(settings.size() - 1));
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
