package com.example.neurocanvas.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Every shader program of the mod in one place. Compiling a program is the slow part (it can freeze the game for a
 * moment the first time an effect appears), so {@link #compileAll} builds them all in advance, and keeps the reason
 * when one of them fails so it can be shown instead of silently doing nothing.
 */
public final class Shaders {
    private static final Map<String, VertexFormat> KNOWN = new LinkedHashMap<>();
    private static final Map<String, ShaderProgram> PROGRAMS = new HashMap<>();
    private static final Map<String, String> ERRORS = new HashMap<>();
    private static final Map<String, Long> MILLIS = new HashMap<>();

    static {
        KNOWN.put("neuro_blur", VertexFormats.POSITION);
        KNOWN.put("neuro_ui", VertexFormats.POSITION);
        KNOWN.put("neuro_fog", VertexFormats.POSITION);
        KNOWN.put("neuro_particle", VertexFormats.POSITION_TEXTURE_COLOR);
        KNOWN.put("neuro_particle_add", VertexFormats.POSITION_TEXTURE_COLOR);
    }

    private Shaders() { }

    /** The compiled program, or null if it cannot be built (the reason is in {@link #error}). Compiled once, then reused. */
    public static ShaderProgram get(String id) {
        ShaderProgram p = PROGRAMS.get(id);
        if (p != null) {
            return p;
        }
        if (ERRORS.containsKey(id) || !KNOWN.containsKey(id)) {
            return null;
        }
        return build(id);
    }

    private static ShaderProgram build(String id) {
        long t0 = System.nanoTime();
        try {
            ShaderProgram p = new ShaderProgram(MinecraftClient.getInstance().getResourceManager(), "neurocanvas:" + id, KNOWN.get(id));
            PROGRAMS.put(id, p);
            MILLIS.put(id, (System.nanoTime() - t0) / 1_000_000L);
            ERRORS.remove(id);
            return p;
        } catch (Throwable e) {
            String message = String.valueOf(e.getMessage()).strip();
            String first = message.lines().filter(l -> !l.isBlank()).findFirst().orElse(e.getClass().getSimpleName());
            ERRORS.put(id, first.length() > 220 ? first.substring(0, 220) : first);
            return null;
        }
    }

    public static String error(String id) {
        return ERRORS.get(id);
    }

    public static boolean allReady() {
        return PROGRAMS.size() == KNOWN.size();
    }

    /** Builds every program now. With {@code force} the old ones are thrown away and compiled again. */
    public static List<String> compileAll(boolean force) {
        if (force) {
            PROGRAMS.values().forEach(ShaderProgram::close);
            PROGRAMS.clear();
            ERRORS.clear();
        }
        List<String> report = new ArrayList<>();
        for (String id : KNOWN.keySet()) {
            boolean had = PROGRAMS.containsKey(id);
            ShaderProgram p = had ? PROGRAMS.get(id) : build(id);
            if (p != null) {
                report.add(id + ": ok" + (had ? " (already compiled)" : " (" + MILLIS.get(id) + " ms)"));
            } else {
                report.add(id + ": FAILED - " + ERRORS.get(id));
            }
        }
        return report;
    }
}
