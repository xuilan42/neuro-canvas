package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private final Path file;
    /** lower case name -> name as typed */
    private final Map<String, String> friends = new LinkedHashMap<>();
    private final Map<String, Long> inMenuUntil = new HashMap<>();
    private boolean menuOpen;
    private int lastSent = -1;
    private long lastSentAt;

    public Friends(Path file) {
        super("Friends", Category.FRIENDS, "Who sees your pings and status, and your friends list", true);
        aiControllable = false;
        alwaysOn = true;
        this.file = file;
        load();
    }

    // ---- the list ---------------------------------------------------------------------------

    private void load() {
        try {
            if (Files.exists(file)) {
                String[] names = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), String[].class);
                if (names != null) {
                    for (String n : names) {
                        if (n != null && NAME.matcher(n).matches()) {
                            friends.put(n.toLowerCase(Locale.ROOT), n);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Start with an empty list.
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new Gson().toJson(friends.values()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // The list is kept in memory anyway.
        }
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public boolean isFriend(String name) {
        return name != null && friends.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public boolean add(String name) {
        if (!validName(name)) {
            return false;
        }
        friends.put(name.toLowerCase(Locale.ROOT), name);
        save();
        return true;
    }

    public void remove(String name) {
        friends.remove(name.toLowerCase(Locale.ROOT));
        save();
    }

    public void toggle(String name) {
        if (isFriend(name)) {
            remove(name);
        } else {
            add(name);
        }
    }

    public List<String> friendNames() {
        List<String> out = new ArrayList<>(friends.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
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

    /** Sends a message to the others on this server, addressed as the Share setting says. */
    public void broadcast(JsonObject message) {
        if (shareWith.index() == 2) {
            return;
        }
        if (shareWith.index() == 1) {
            if (friends.isEmpty()) {
                return;
            }
            JsonArray to = new JsonArray();
            int n = 0;
            for (String name : friends.values()) {
                if (n++ >= 64) {
                    break;
                }
                to.add(name);
            }
            message.add("to", to);
        }
        NeuroCanvasClient.modules().get(NameTags.class).send(message.toString());
    }

    // ---- "in menu" status -------------------------------------------------------------------

    public void setMenuOpen(boolean open) {
        menuOpen = open;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
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
