package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;

/**
 * Who sees what you share online (pings, countdowns, the "in menu" status) and whose messages you accept. The friends
 * list is kept in neurocanvas/online-friends.json. Messages go through the registry; with "Friends only" they are
 * addressed to your friends by name, so nobody else receives them at all.
 */
public final class Friends extends Module {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final long STATUS_RESEND_MS = 10_000;
    private static final long STATUS_LIFE_MS = 25_000;

    private final Setting shareWith = add(Setting.choice("Share with", 0, "Everyone", "Friends only", "Nobody"));
    private final Setting acceptFrom = add(Setting.choice("Accept pings from", 0, "Everyone", "Friends only"));
    private final Setting myStatus = add(Setting.bool("Show when I am in the menu", true));
    private final Setting othersStatus = add(Setting.bool("Show who is in the menu", true));

    private final Map<String, Long> inMenuUntil = new HashMap<>();
    private boolean menuOpen;
    private int lastSent = -1;
    private long lastSentAt;

    public Friends(Path file) {
        super("Friends", Category.FRIENDS, "Who sees your pings and status, and your friends list", true);
        aiControllable = false;
        alwaysOn = true;
    }

    // ---- the list: accounts, not nicks ------------------------------------------------------

    /** Friends are accounts (kept on the server, and a friend counts once both have added each other). A player here is a friend when their account is one. */
    private volatile List<String> friendNames = List.of();
    private volatile List<String> incoming = List.of();
    private volatile List<String> outgoing = List.of();
    private volatile java.util.Set<String> friendKeys = java.util.Set.of();
    private volatile String friendsNote = "";
    private long lastFetch;
    private boolean wasSignedIn;

    private void apply(com.google.gson.JsonObject o) {
        friendNames = names(o, "friends");
        incoming = names(o, "incoming");
        outgoing = names(o, "outgoing");
        java.util.Set<String> keys = new java.util.HashSet<>();
        friendNames.forEach(n -> keys.add(n.toLowerCase(Locale.ROOT)));
        friendKeys = keys;
    }

    private static List<String> names(com.google.gson.JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) {
            o.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    /** Asks the server for the lists again (cheap; the menu and a timer call it). */
    public void refresh() {
        if (!com.example.neurocanvas.client.AccountClient.signedIn()) {
            return;
        }
        lastFetch = System.currentTimeMillis();
        com.example.neurocanvas.client.AccountClient.call("GET", "/v1/acc/friends", null).whenComplete((o, e) -> {
            if (e == null) {
                apply(o);
            }
        });
    }

    private void change(java.util.concurrent.CompletableFuture<com.google.gson.JsonObject> call) {
        call.whenComplete((o, e) -> MinecraftClient.getInstance().execute(() -> {
            if (e != null) {
                friendsNote = com.example.neurocanvas.client.MarketClient.reason(e);
                return;
            }
            friendsNote = "";
            apply(o);
        }));
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public boolean signedIn() {
        return com.example.neurocanvas.client.AccountClient.signedIn();
    }

    public String note() {
        return friendsNote;
    }

    /** Whether the player with this in-game nick is a friend: what counts is the account they are signed in with. */
    public boolean isFriend(String nick) {
        if (nick == null || friendKeys.isEmpty()) {
            return false;
        }
        String acc = NeuroCanvasClient.modules().get(NameTags.class).accountOf(nick);
        return acc != null && friendKeys.contains(acc.toLowerCase(Locale.ROOT));
    }

    public boolean isFriendAccount(String account) {
        return account != null && friendKeys.contains(account.toLowerCase(Locale.ROOT));
    }

    /** Sends a friend request, or accepts one; the answer is the new state of the lists. */
    public void addAccount(String account) {
        String name = account == null ? "" : account.trim().replaceFirst("^@", "");
        if (!validName(name)) {
            friendsNote = "An account name is 3 to 16 letters, digits or _";
            return;
        }
        com.google.gson.JsonObject b = new com.google.gson.JsonObject();
        b.addProperty("name", name);
        change(com.example.neurocanvas.client.AccountClient.call("POST", "/v1/acc/friends", b));
    }

    /** Removes a friend, cancels a request or declines one. */
    public void removeAccount(String account) {
        change(com.example.neurocanvas.client.AccountClient.call("DELETE", "/v1/acc/friends?name=" + java.net.URLEncoder.encode(account, StandardCharsets.UTF_8), null));
    }

    public List<String> friendNames() {
        return friendNames;
    }

    public List<String> incomingRequests() {
        return incoming;
    }

    public List<String> outgoingRequests() {
        return outgoing;
    }

    // ---- sharing ----------------------------------------------------------------------------

    public boolean sharesWithNobody() {
        return shareWith.index() == 2;
    }

    public boolean friendsOnly() {
        return shareWith.index() == 1;
    }

    /** Whether a message from this user is accepted. */
    public boolean acceptsFrom(String from) {
        return acceptFrom.index() == 0 || isFriend(from);
    }

    /** Sends a message to the others on this server. With "Friends only" the server delivers it to your friends' accounts, whatever their nicks. */
    public void broadcast(JsonObject message) {
        if (shareWith.index() == 2) {
            return;
        }
        if (shareWith.index() == 1) {
            if (!com.example.neurocanvas.client.AccountClient.signedIn() || friendNames.isEmpty()) {
                return;
            }
            message.addProperty("friends", true);
        }
        NeuroCanvasClient.modules().get(NameTags.class).send(message.toString());
    }

    // ---- "in menu" status -------------------------------------------------------------------

    public void setMenuOpen(boolean open) {
        menuOpen = open;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        boolean in = com.example.neurocanvas.client.AccountClient.signedIn();
        if (in && (!wasSignedIn || System.currentTimeMillis() - lastFetch > 30_000)) {
            refresh();
        } else if (!in && wasSignedIn) {
            friendNames = List.of();
            incoming = List.of();
            outgoing = List.of();
            friendKeys = java.util.Set.of();
        }
        wasSignedIn = in;
        if (!enabled || !myStatus.on() || mc.player == null) {
            return;
        }
        int state = menuOpen ? 1 : 0;
        long now = System.currentTimeMillis();
        boolean changed = state != lastSent;
        if (changed || (menuOpen && now - lastSentAt > STATUS_RESEND_MS)) {
            if (changed && lastSent < 0 && state == 0) {
                lastSent = 0;
                return;
            }
            lastSent = state;
            lastSentAt = now;
            JsonObject o = new JsonObject();
            o.addProperty("t", "status");
            o.addProperty("state", state);
            broadcast(o);
        }
    }

    public void receiveStatus(JsonObject status) {
        if (!enabled || !othersStatus.on()) {
            return;
        }
        try {
            String from = status.get("from").getAsString();
            if (!acceptsFrom(from)) {
                return;
            }
            String key = from.toLowerCase(Locale.ROOT);
            if (status.get("state").getAsInt() == 1) {
                inMenuUntil.put(key, System.currentTimeMillis() + STATUS_LIFE_MS);
            } else {
                inMenuUntil.remove(key);
            }
        } catch (RuntimeException ignored) {
            // A malformed message is ignored.
        }
    }

    public boolean inMenu(String name) {
        Long until = inMenuUntil.get(name.toLowerCase(Locale.ROOT));
        return until != null && until > System.currentTimeMillis();
    }
}
