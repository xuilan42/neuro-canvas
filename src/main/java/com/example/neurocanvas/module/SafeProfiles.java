package com.example.neurocanvas.module;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What each project with rules does not allow, and how a server address is recognised. No game classes here, so it can be tested on its own.
 * Written from the rules the projects publish (FunTime's list of modifications, ReallyWorld's rules of 11.10.2026, HolyWorld's wiki); rules change.
 */
final class SafeProfiles {
    /** A project: its domains, the modules it does not allow, and whether it forbids hiding armor stands. */
    record Profile(String name, List<String> domains, Set<String> blocked, boolean noArmorStands) {
        boolean matches(String host) {
            for (String d : domains) {
                if (host.equals(d) || host.endsWith("." + d)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Set<String> COMMON = Set.of("ESP", "Arrow", "CustomHitbox", "FreeCam", "AutoRespawn");
    static final Profile FUNTIME = new Profile("FunTime", List.of("funtime.su"), union(COMMON, "ToggleSprint", "Cooldowns"), true);
    static final Profile REALLYWORLD = new Profile("ReallyWorld", List.of("reallyworld.me", "reallyworld.ru"), union(COMMON, "Predictions"), true);
    static final Profile HOLY_LITE = new Profile("HolyWorld Lite", List.of("holyworld.me", "holyworld.ru"), union(COMMON, "Predictions"), true);
    static final Profile HOLY_PRIME = new Profile("HolyWorld Prime", List.of("holyworld.me", "holyworld.ru"), COMMON, false);

    private SafeProfiles() { }

    private static Set<String> union(Set<String> base, String... more) {
        Set<String> out = new HashSet<>(base);
        out.addAll(List.of(more));
        return Set.copyOf(out);
    }

    static Profile profileOf(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (FUNTIME.matches(host)) {
            return FUNTIME;
        }
        if (REALLYWORLD.matches(host)) {
            return REALLYWORLD;
        }
        if (HOLY_LITE.matches(host)) {
            // The two modes share a domain; a name that says which one it is decides, and an unknown one gets the stricter rules.
            return host.startsWith("prime.") ? HOLY_PRIME : HOLY_LITE;
        }
        return null;
    }
}
