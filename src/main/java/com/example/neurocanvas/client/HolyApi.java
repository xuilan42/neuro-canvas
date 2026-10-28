package com.example.neurocanvas.client;

import com.example.neurocanvas.module.Module;
import com.example.neurocanvas.module.SafeMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Talks to HolyWorld's In-Game API. When you join a HolyWorld server the mod sends the list of its features (the lower-case names of its modules)
 * and the server answers with the ones it blocks; those modules are switched off and hidden from the menu, as the API requires. The server's
 * answer is added to Safe mode's own list for HolyWorld, never replaces it, so an empty answer still leaves the rules in force. Nothing else is sent:
 * no account, no nick, no settings.
 */
public final class HolyApi {
    private static final Logger LOG = LoggerFactory.getLogger("neurocanvas");
    private static final Identifier CHANNEL = new Identifier("liteapi", "feature-control");
    /** The server allows one request in ten seconds per player. */
    private static final long RETRY_MS = 11_000;
    private static final int ATTEMPTS = 3;

    private static volatile Set<String> blocked = Set.of();
    private static boolean active;
    private static boolean answered;
    private static String pendingId = "";
    private static long sentAt;
    private static int attempts;

    private HolyApi() { }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, sender) -> {
            if (buf.readableBytes() > HolyApiProtocol.MAX_PACKET) {
                return;
            }
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            client.execute(() -> answer(client, new String(bytes, StandardCharsets.UTF_8)));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> begin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        active = false;
        answered = false;
        pendingId = "";
        attempts = 0;
        blocked = Set.of();
    }

    private static void begin(MinecraftClient client) {
        reset();
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || !SafeMode.isHolyWorld(server.address)) {
            return;
        }
        active = true;
        send();
    }

    /** The features: every module that has an on/off switch, by the lower-case name, plus the NoRender option that hides armor stands. */
    static List<String> features() {
        List<String> out = new ArrayList<>();
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (!m.alwaysOn) {
                out.add(id(m));
            }
        }
        out.add("armorstands");
        return out;
    }

    private static String id(Module m) {
        return m.name.toLowerCase(Locale.ROOT);
    }

    private static void send() {
        attempts++;
        pendingId = UUID.randomUUID().toString();
        sentAt = System.currentTimeMillis();
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBytes(HolyApiProtocol.request(pendingId, features()).getBytes(StandardCharsets.UTF_8));
            ClientPlayNetworking.send(CHANNEL, buf);
        } catch (RuntimeException e) {
            LOG.debug("HolyWorld API: cannot send: {}", e.toString());
        }
    }

    /** Called every tick: asks again (at most three times) when the server has not answered. */
    public static void tick(MinecraftClient mc) {
        if (active && !answered && attempts < ATTEMPTS && mc.player != null && System.currentTimeMillis() - sentAt > RETRY_MS) {
            send();
        }
    }

    private static void answer(MinecraftClient mc, String json) {
        HolyApiProtocol.Result r = HolyApiProtocol.parse(json, pendingId);
        if (r == null) {
            return;
        }
        if (!r.ok()) {
            LOG.debug("HolyWorld API: error {}", r.error());
            return;
        }
        answered = true;
        blocked = r.blocklist();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Neuro Canvas] HolyWorld API answered. "
                    + (blocked.isEmpty() ? "Nothing is blocked for this client." : "Blocked by the server: " + String.join(", ", new java.util.TreeSet<>(blocked)) + ".")), false);
        }
    }

    /** True when the server blocks this module: it is switched off and not shown anywhere. */
    public static boolean hidden(Module m) {
        Set<String> b = blocked;
        return !b.isEmpty() && b.contains(id(m));
    }

    public static boolean armorStandsHidden() {
        Set<String> b = blocked;
        return !b.isEmpty() && b.contains("armorstands");
    }
}
