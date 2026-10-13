package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.gui.Ui;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import com.example.neurocanvas.fx.EffectDraw;

/**
 * Shows a "NeuroAura" plate above other players who also run the mod on the same server. Players are matched
 * through a small registry server (see registry-server/), grouped by server address, so it works on offline-mode
 * servers and one nick on two servers stays two separate entries. Off by default; sends only server address and nick.
 */
public final class NameTags extends Module {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8)).build();

    private final Setting label = add(Setting.choice("Text", 0, "NeuroAura", "Neuro", "NC"));
    private final Setting height = add(Setting.number("Height", 0, 1.5, 0.45));
    private final Setting scale = add(Setting.number("Scale", 0.5, 2, 1));
    private final Setting color = add(Setting.color("Color", 0.42));
    private final Setting background = add(Setting.bool("Background", true));

    private final String url = loadUrl();
    private volatile Set<String> online = Collections.emptySet();
    private volatile java.util.List<String> onlineDisplay = Collections.emptyList();
    private volatile String status = "off";
    private volatile int generation;
    private volatile WebSocket socket;
    private volatile String room = "";
    private static final java.util.regex.Pattern ROOM = java.util.regex.Pattern.compile("[a-z0-9_-]{3,40}");
    private String connectedTo = "";

    public NameTags() {
        super("NameTags", Category.FRIENDS, "Plate above other NeuroAura users on the same server", false);
        aiControllable = false;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
        try {
            java.nio.file.Path f = roomFile();
            if (java.nio.file.Files.exists(f)) {
                String r = java.nio.file.Files.readString(f).trim().toLowerCase(Locale.ROOT);
                room = ROOM.matcher(r).matches() ? r : "";
            }
        } catch (IOException | RuntimeException ignored) {
            // No room saved.
        }
    }

    private static java.nio.file.Path roomFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("neurocanvas").resolve("online-room.txt");
    }

    public String room() {
        return room;
    }

    /** Sets a shared room name (or clears it with an empty one). Returns false when the name is not valid. */
    public boolean setRoom(String name) {
        String r = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!r.isEmpty() && !ROOM.matcher(r).matches()) {
            return false;
        }
        room = r;
        try {
            if (r.isEmpty()) {
                java.nio.file.Files.deleteIfExists(roomFile());
            } else {
                java.nio.file.Files.createDirectories(roomFile().getParent());
                java.nio.file.Files.writeString(roomFile(), r);
            }
        } catch (IOException ignored) {
            // The room still works until the game is closed.
        }
        return true;
    }

    public String status() {
        return status;
    }

    /** Registry address baked in at build time; the system property is for developers testing a local registry. */
    private static String loadUrl() {
        String override = System.getProperty("neurocanvas.registry");
        if (override != null && !override.isBlank()) {
            return override.trim().replaceAll("/+$", "");
        }
        try (InputStream in = NameTags.class.getResourceAsStream("/neurocanvas-registry.properties")) {
            if (in != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                return p.getProperty("url", "").trim().replaceAll("/+$", "");
            }
        } catch (IOException ignored) {
            // No address in this build.
        }
        return "";
    }

    /** Address of the current multiplayer server, lower case without the default port; null in singleplayer. */
    public static String serverAddress(MinecraftClient mc) {
        return serverKey(mc);
    }

    static String serverKey(MinecraftClient mc) {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null || info.address == null || info.address.isBlank()) {
            return null;
        }
        String a = info.address.trim().toLowerCase(Locale.ROOT);
        return a.endsWith(":25565") ? a.substring(0, a.length() - 6) : a;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        String r = room;
        String server = !r.isEmpty() && mc.player != null ? "room:" + r : serverKey(mc);
        String name = mc.player == null ? null : mc.player.getName().getString();
        if (!enabled || server == null || url.isBlank()) {
            stop();
            status = !enabled ? "off" : url.isBlank() ? "this build has no registry address" : "singleplayer";
            return;
        }
        String wanted = url + "|" + server + "|" + name;
        if (!wanted.equals(connectedTo)) {
            start(wanted, server, name);
        }
    }

    /** Closes the registry connection; the registry then removes this player from the list at once. */
    public void stop() {
        generation++;
        connectedTo = "";
        online = Collections.emptySet();
        WebSocket w = socket;
        socket = null;
        if (w != null) {
            w.abort();
        }
    }

    private void start(String id, String server, String name) {
        stop();
        connectedTo = id;
        int gen = generation;
        String base = url;
        Thread t = new Thread(() -> listen(gen, base, server, name), "neurocanvas-nametags");
        t.setDaemon(true);
        t.start();
    }

    /** Holds one WebSocket open; the registry pushes the list of online players whenever it changes. */
    private void listen(int gen, String base, String server, String name) {
        // The address may be a comma separated list: the first one is used, the next one only while it is unreachable.
        String[] bases = base.split(",");
        URI[] uris = new URI[bases.length];
        for (int i = 0; i < bases.length; i++) {
            String b = bases[i].trim().replaceAll("/+$", "");
            String wsBase = b.startsWith("https://") ? "wss://" + b.substring(8)
                    : b.startsWith("http://") ? "ws://" + b.substring(7) : b;
            uris[i] = URI.create(wsBase + "/v1/ws?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8)
                    + "&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        }
        int which = 0;
        while (gen == generation) {
            CompletableFuture<Void> closed = new CompletableFuture<>();
            StringBuilder buffer = new StringBuilder();
            WebSocket.Listener listener = new WebSocket.Listener() {
                @Override
                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    buffer.append(data);
                    if (last) {
                        read(buffer.toString());
                        buffer.setLength(0);
                    }
                    ws.request(1);
                    return null;
                }

                @Override
                public void onOpen(WebSocket ws) {
                    ws.request(1);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                    closed.complete(null);
                    return null;
                }

                @Override
                public void onError(WebSocket ws, Throwable error) {
                    closed.complete(null);
                }
            };
            try {
                status = "connecting...";
                WebSocket ws = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).buildAsync(uris[which], listener).get();
                if (gen != generation) {
                    ws.abort();
                    return;
                }
                socket = ws;
                while (gen == generation && !closed.isDone()) {
                    try {
                        closed.get(30, TimeUnit.SECONDS);
                    } catch (TimeoutException keepAlive) {
                        ws.sendText("ping", true);
                    }
                }
                ws.abort();
            } catch (ExecutionException | RuntimeException e) {
                if (gen == generation) {
                    status = "registry unreachable, retrying";
                    which = (which + 1) % uris.length;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (gen != generation) {
                return;
            }
            online = Collections.emptySet();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Sends one message to the other users on this server through the registry; false when there is no connection. */
    public boolean send(String json) {
        WebSocket w = socket;
        if (w == null) {
            return false;
        }
        w.sendText(json, true);
        return true;
    }

    /** The users on this server who run the mod, as they spell their names. */
    public java.util.List<String> onlineNames() {
        return onlineDisplay;
    }

    public boolean connected() {
        return socket != null;
    }

    private void read(String json) {
        try {
            JsonObject message = GSON.fromJson(json, JsonObject.class);
            if (message.has("ping") || message.has("count")) {
                MinecraftClient.getInstance().execute(() -> NeuroCanvasClient.modules().get(PingWheel.class).receive(message));
                return;
            }
            if (message.has("status")) {
                MinecraftClient.getInstance().execute(() -> NeuroCanvasClient.modules().get(Friends.class).receiveStatus(message.getAsJsonObject("status")));
                return;
            }
            Set<String> names = new HashSet<>();
            message.getAsJsonArray("players")
                    .forEach(e -> names.add(e.getAsString().toLowerCase(Locale.ROOT)));
            online = names;
            java.util.List<String> display = new java.util.ArrayList<>();
            message.getAsJsonArray("players").forEach(e -> display.add(e.getAsString()));
            onlineDisplay = display;
            status = "connected, " + names.size() + " on this server";
        } catch (RuntimeException e) {
            status = "bad message from the registry";
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        Set<String> names = online;
        MinecraftClient mc = env.mc();
        if (names.isEmpty() || mc.world == null) {
            return;
        }
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !p.isInvisible() && names.contains(p.getName().getString().toLowerCase(Locale.ROOT))) {
                plate(env, p);
            }
        }
        restoreState();
    }

    /** Draws the plate above one player. Also used by FakePlayer so the look can be tested without a second user. */
    public void plate(RenderEnv env, PlayerEntity p) {
        MinecraftClient mc = env.mc();
        TextRenderer tr = mc.textRenderer;
        String shown = label.option();
        if (NeuroCanvasClient.modules().get(Friends.class).inMenu(p.getName().getString())) {
            shown += "  \u00b7 in menu";
        }
        Text text = Ui.text(shown);
        int bg = background.on() ? 0x66000000 : 0;
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        Vec3d pos = EffectDraw.lerpPos(p, env.tickDelta());
        MatrixStack ms = new MatrixStack();
        ms.peek().getPositionMatrix().set(env.m());
        ms.translate(pos.x, pos.y + p.getHeight() + 0.5 + height.value, pos.z);
        ms.multiply(mc.getEntityRenderDispatcher().getRotation());
        float s = 0.025f * scale.f();
        ms.scale(-s, -s, s);
        float w = tr.getWidth(text);
        tr.draw(text, -w / 2f, 0, 0xFF000000 | (color.argb() & 0xFFFFFF), false, ms.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, bg, 15728880);
        consumers.draw();
    }

    public void restoreState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
