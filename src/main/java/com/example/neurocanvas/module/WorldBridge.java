package com.example.neurocanvas.module;

import com.example.neurocanvas.client.AccountClient;
import com.example.neurocanvas.client.WorldTunnel;
import com.example.neurocanvas.mixin.PlayerManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.NetworkUtils;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;

/**
 * Plays a single-player world with other people. Switching it on opens the world to the local network, asks the project's server for an address
 * (something like abcd2345.neuro-connect.x-vare.com) and connects the two, so a friend only types that address in Multiplayer. It needs the mod
 * account (Misc > Account), runs for at most five hours, and takes at most the number of players set here (never more than 20). By default the
 * game does not check guests' accounts with Mojang ("Check Mojang accounts" turns the check back on), so anyone with the address can join under any
 * name: keep it to people you trust.
 */
public final class WorldBridge extends Module {
    private final Setting players = add(Setting.number("Max players", 1, 20, 8));
    private final Setting cheats = add(Setting.bool("Guests may use cheats", false));
    private final Setting verify = add(Setting.bool("Check Mojang accounts", false));
    private final Setting copy = add(Setting.button("Copy link"));

    private WorldTunnel tunnel;
    /** True while the check of accounts is switched off by us, so it can be put back. */
    private boolean unchecked;
    private boolean starting;
    private volatile String problem = "";
    private long warnedAt;

    public WorldBridge() {
        super("WorldBridge", Category.FRIENDS, "Opens your single-player world to friends through the project's server (address xxxx.neuro-connect.x-vare.com, up to 20 players, 5 hours)", false);
        aiControllable = false;
        risky = true;
    }

    private static void say(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[WorldBridge] " + text), false);
            }
        });
    }

    private void stop(String why) {
        WorldTunnel t = tunnel;
        tunnel = null;
        starting = false;
        enabled = false;
        if (t != null) {
            t.close();
        }
        restoreChecks();
        if (why != null) {
            say(why);
        }
    }

    /** Puts the game's own check of accounts back (the world is then closed to guests from the internet anyway). */
    private void restoreChecks() {
        if (unchecked) {
            unchecked = false;
            IntegratedServer server = MinecraftClient.getInstance().getServer();
            if (server != null) {
                server.setOnlineMode(true);
            }
        }
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        IntegratedServer server = mc.getServer();
        // The module never stays on across worlds or restarts: a world is opened only by switching it on now.
        if (mc.world == null || server == null) {
            if (tunnel != null || enabled) {
                stop(null);
            }
            return;
        }
        if (copy.on()) {
            copy.set(0);
            WorldTunnel t = tunnel;
            if (t != null && t.info() != null) {
                mc.keyboard.setClipboard(t.info().host());
                say("Address copied: " + t.info().host());
            } else {
                say("Not open yet.");
            }
        }
        if (!problem.isEmpty()) {
            String p = problem;
            problem = "";
            stop("Could not open the world: " + p);
            return;
        }
        if (!enabled) {
            if (tunnel != null) {
                stop("The world is no longer reachable from the internet (friends on your local network can still join until you leave the world).");
            }
            return;
        }
        if (tunnel != null) {
            WorldTunnel.Info info = tunnel.info();
            if (tunnel.stopped()) {
                stop(null);
            } else if (info != null && System.currentTimeMillis() >= info.expiresAt()) {
                stop("Five hours are up: the world is closed to the internet.");
            }
            return;
        }
        if (starting) {
            return;
        }
        if (!AccountClient.signedIn()) {
            stop("Sign in first (Misc > Account): the server needs to know who opens a world.");
            return;
        }
        start(mc, server);
    }

    private void start(MinecraftClient mc, IntegratedServer server) {
        starting = true;
        int max = (int) players.value;
        int lan;
        try {
            if (!server.isRemote()) {
                int port = NetworkUtils.findLocalPort();
                if (!server.openToLan(server.getDefaultGameMode(), cheats.on(), port)) {
                    stop("The game could not open the world to the local network.");
                    return;
                }
            }
            lan = server.getServerPort();
            // With the check off anybody can join under any name, with or without a bought game; the game does not ask Mojang who they are.
            if (!verify.on()) {
                server.setOnlineMode(false);
                unchecked = true;
            }
            // A world opened to the network takes eight players unless told otherwise.
            ((PlayerManagerAccessor) server.getPlayerManager()).neurocanvas$setMaxPlayers(Math.max(2, max + 1));
        } catch (RuntimeException e) {
            stop("Could not open the world: " + e.getMessage());
            return;
        }
        WorldTunnel t = new WorldTunnel(lan, WorldBridge::say);
        Thread th = new Thread(() -> {
            try {
                WorldTunnel.Info info = t.open(max);
                mc.execute(() -> {
                    if (!enabled || starting == false) {
                        t.close();
                        return;
                    }
                    tunnel = t;
                    starting = false;
                    t.onClosed(() -> { });
                    mc.keyboard.setClipboard(info.host());
                    Text link = Text.literal(info.host()).styled(s -> s.withUnderline(true).withColor(0x55FFFF)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, info.host())));
                    if (mc.player != null) {
                        mc.player.sendMessage(Text.literal("[WorldBridge] Your world is open. Address (copied, click to copy again): ").append(link)
                                .append(Text.literal("  Up to " + info.players() + " players, closes in 5 hours." + (verify.on() ? "" : " Guests are not checked against Mojang."))), false);
                    }
                });
            } catch (java.io.IOException | RuntimeException e) {
                problem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "neurocanvas-tunnel-open");
        th.setDaemon(true);
        th.start();
    }
}
