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
 * Every shader program of the mod in one place. The game looks programs up as shaders/core/<name> in the minecraft
 * namespace, so the files live in assets/minecraft/shaders/core/ and carry the neuro_ prefix to stay unique. Compiling a program is the slow part (it can freeze the game for a
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
        KNOWN.put("neuro_bg", VertexFormats.POSITION);
        KNOWN.put("neuro_glass", VertexFormats.POSITION);
        KNOWN.put("neuro_fog", VertexFormats.POSITION);
        KNOWN.put("neuro_particle", VertexFormats.POSITION_TEXTURE_COLOR);
        KNOWN.put("neuro_particle_add", VertexFormats.POSITION_TEXTURE_COLOR);
        KNOWN.put("neuro_fx", VertexFormats.POSITION_TEXTURE_COLOR);
        KNOWN.put("neuro_aurora", VertexFormats.POSITION_TEXTURE_COLOR);
    }

    private static java.lang.reflect.Field activeBlend;
    private static java.lang.reflect.Field programBlend;
    private static boolean reflected;

    private static float scale = 1f;

    private Shaders() { }

    /** A module that has its own Brightness setting sets it here while it draws, and 1 afterwards. */
    public static void setScale(float s) {
        scale = s;
    }

    /** The brightness the visuals have now: the global setting in Interface times the module's own. */
    public static float brightness() {
        return com.example.neurocanvas.module.Interface.brightness() * scale;
    }

    public static void applyBrightness(ShaderProgram p) {
        net.minecraft.client.gl.GlUniform u = p.getUniform("Brightness");
        if (u != null) {
            u.set(brightness());
        }
    }

    private static void reflect() {
        reflected = true;
        try {
            net.fabricmc.loader.api.MappingResolver r = net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver();
            String blend = r.mapClassName("named", "net.minecraft.client.gl.GlBlendState");
            String program = r.mapClassName("named", "net.minecraft.client.gl.ShaderProgram");
            activeBlend = Class.forName(blend).getDeclaredField(r.mapFieldName("named", "net.minecraft.client.gl.GlBlendState",
                    "activeBlendState", "Lnet/minecraft/client/gl/GlBlendState;"));
            programBlend = Class.forName(program).getDeclaredField(r.mapFieldName("named", "net.minecraft.client.gl.ShaderProgram",
                    "blendState", "Lnet/minecraft/client/gl/GlBlendState;"));
            activeBlend.setAccessible(true);
            programBlend.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            activeBlend = null;
            programBlend = null;
        }
    }

    /**
     * Call after drawing with one of our shaders. The game applies a shader's blend mode only when it differs from the
     * one it applied last, so a program with its own mode would leave later draws (ours and other mods') with the wrong
     * blending. This tells the game "the last applied mode was the normal alpha blend" and puts the GL state back.
     */
    public static void finish() {
        if (!reflected) {
            reflect();
        }
        try {
            if (activeBlend != null) {
                activeBlend.set(null, programBlend.get(net.minecraft.client.render.GameRenderer.getPositionColorProgram()));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The state reset is best effort.
        }
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
    }

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
            ShaderProgram p = new ShaderProgram(MinecraftClient.getInstance().getResourceManager(), id, KNOWN.get(id));
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
