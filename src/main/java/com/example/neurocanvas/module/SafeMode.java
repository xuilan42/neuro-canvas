package com.example.neurocanvas.module;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Safe mode that follows the server. On a project that has rules against some of the mod's features (the anarchy servers FunTime, ReallyWorld and
 * HolyWorld) it switches itself on when you join, switches those features off and keeps them off; in a single-player world and on every other server
 * it stays out of the way. The only setting is in Interface: <i>Auto</i> (this) or <i>Always</i> (every module marked risky is blocked everywhere).
 * Leaving such a server puts back what it switched off.
 *
 * <p>The lists below are written from the rules those projects publish (FunTime's list of modifications, ReallyWorld's rules of 11.10.2026, HolyWorld's
 * wiki); rules change, so they are a best effort and not a guarantee that a server will accept the mod.
 */
public final class SafeMode {
    private static SafeProfiles.Profile active;
    private static boolean announced;
    /** Modules this class switched off; they are put back when the rules stop applying. */
    private static final Set<Module> TURNED_OFF = new HashSet<>();

    private SafeMode() { }

    /** The profile of the server the player is on, or null (single player, an unknown server, the menu). */
    static SafeProfiles.Profile profileOf(String address) {
        return SafeProfiles.profileOf(address);
    }

    public static boolean forced() {
        return Interface.safeMode();
    }

    public static String profileName() {
        return active == null ? null : active.name();
    }

    /** True when this module may not be used now: its server forbids it, or Safe mode is set to Always and the module is marked risky. */
    public static boolean blocks(Module m) {
        if (forced() && m.risky) {
            return true;
        }
        return active != null && active.blocked().contains(m.name);
    }

    /** NoRender's "Armor stands" option: some projects ban hiding armor stands. */
    public static boolean armorStandsBlocked() {
        return active != null && active.noArmorStands();
    }

    /** Called every tick: follows the server, switches blocked modules off, puts them back when the rules stop applying. */
    public static void tick(MinecraftClient mc, Iterable<Module> modules) {
        ServerInfo server = mc.getCurrentServerEntry();
        SafeProfiles.Profile now = mc.world == null || mc.isInSingleplayer() || server == null ? null : profileOf(server.address);
        if (now != active) {
            active = now;
            announced = false;
        }
        for (Module m : modules) {
            if (blocks(m)) {
                if (m.enabled) {
                    m.enabled = false;
                    TURNED_OFF.add(m);
                }
            } else if (TURNED_OFF.remove(m)) {
                m.enabled = true;
            }
        }
        if (active != null && !announced && mc.player != null) {
            announced = true;
            StringBuilder list = new StringBuilder();
            for (Module m : TURNED_OFF) {
                if (active.blocked().contains(m.name)) {
                    list.append(list.length() == 0 ? "" : ", ").append(m.name);
                }
            }
            mc.player.sendMessage(Text.literal("[Neuro Canvas] Safe mode for " + active.name() + (list.length() == 0 ? "" : ": switched off " + list)
                    + ". Not allowed there: " + String.join(", ", new java.util.TreeSet<>(active.blocked()))
                    + (active.noArmorStands() ? ", hiding armor stands" : "") + "."), false);
        }
    }
}
