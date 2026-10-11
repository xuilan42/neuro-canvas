package com.example.neurocanvas.module;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Tells teammates from enemies (BedWars, SkyWars, ...) so other modules can skip them. A player counts
 * as a teammate if any enabled check matches: same scoreboard team, same name colour, same tab-list
 * colour, same dyed armour colour, or you added them to the manual list.
 */
public final class Teams extends Module {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() { }.getType();
    private static Teams instance;

    private final Setting scoreboard = add(Setting.bool("Scoreboard team", true));
    private final Setting nameColor = add(Setting.bool("Name color", true));
    private final Setting tabColor = add(Setting.bool("Tab list color", true));
    private final Setting armorColor = add(Setting.bool("Armor color", true));
    private final Setting manual = add(Setting.bool("Manual list", true));

    private final Path file;
    private final Set<String> friends = new TreeSet<>();

    public Teams(Path file) {
        super("Teams", Category.COMBAT, "Teammates are skipped by Arrow and target modules (/teams add <name> for a manual list)", false);
        aiControllable = false;
        this.file = file;
        instance = this;
        load();
    }

    /** True when the Teams module is on and this entity should be treated as a friend. */
    public static boolean ignores(Entity entity) {
        return instance != null && instance.enabled && instance.isTeammate(entity);
    }

    public boolean isTeammate(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity me = mc.player;
        if (me == null || !(entity instanceof PlayerEntity other) || other == me) {
            return false;
        }
        if (manual.on() && friends.contains(other.getName().getString().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (scoreboard.on() && me.isTeammate(other)) {
            return true;
        }
        if (nameColor.on() && sameColor(nameColorOf(me), nameColorOf(other))) {
            return true;
        }
        if (tabColor.on() && sameColor(tabColorOf(mc, me), tabColorOf(mc, other))) {
            return true;
        }
        return armorColor.on() && sameColor(armorColorOf(me), armorColorOf(other));
    }

    private static boolean sameColor(Integer a, Integer b) {
        return a != null && a.equals(b);
    }

    /** Colour of the nameplate: the scoreboard team's colour, else the first coloured part of the display name. */
    private static Integer nameColorOf(PlayerEntity player) {
        Integer fromTeam = teamColor(player.getScoreboardTeam());
        return fromTeam != null ? fromTeam : colorOf(player.getDisplayName());
    }

    private static Integer tabColorOf(MinecraftClient mc, PlayerEntity player) {
        if (mc.getNetworkHandler() == null) {
            return null;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry == null) {
            return null;
        }
        Text name = entry.getDisplayName();
        if (name == null && entry.getScoreboardTeam() != null) {
            name = Team.decorateName(entry.getScoreboardTeam(), Text.literal(entry.getProfile().getName()));
        }
        Integer fromText = name == null ? null : colorOf(name);
        return fromText != null ? fromText : teamColor(entry.getScoreboardTeam());
    }

    private static Integer armorColorOf(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.getItem() instanceof DyeableArmorItem dyeable && dyeable.hasColor(stack)) {
                return dyeable.getColor(stack);
            }
        }
        return null;
    }

    private static Integer teamColor(AbstractTeam team) {
        if (team != null && team.getColor() != Formatting.RESET) {
            return team.getColor().getColorValue();
        }
        return null;
    }

    private static Integer colorOf(Text text) {
        Optional<Integer> found = text.visit((Style style, String s) ->
                !s.isBlank() && style.getColor() != null ? Optional.of(style.getColor().getRgb()) : Optional.empty(), Style.EMPTY);
        return found.orElse(null);
    }

    // ---- manual list ----------------------------------------------------------------------

    public boolean addFriend(String name) {
        boolean added = friends.add(name.toLowerCase(Locale.ROOT));
        save();
        return added;
    }

    public boolean removeFriend(String name) {
        boolean removed = friends.remove(name.toLowerCase(Locale.ROOT));
        save();
        return removed;
    }

    public void clearFriends() {
        friends.clear();
        save();
    }

    public Collection<String> friends() {
        return new ArrayList<>(friends);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                loaded.forEach(n -> friends.add(n.toLowerCase(Locale.ROOT)));
            }
        } catch (IOException | RuntimeException ignored) {
            // A broken list just starts empty; it is rewritten on the next change.
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(friends), LIST_TYPE, writer);
            }
        } catch (IOException ignored) {
            // Losing the manual list on a write error is acceptable; the in-memory list still works.
        }
    }
}
