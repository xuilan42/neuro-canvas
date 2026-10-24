package com.example.neurocanvas.module;

import com.example.neurocanvas.fx.Shaders;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;

/**
 * Your own enchantment shimmer on armour and items: the purple glint is drawn by our shader (galaxy, rainbow, glitch or a flow between two
 * colours). The game's own glint layers keep doing the drawing, so enchanted things shimmer exactly where they do in vanilla; only the programs
 * they use are swapped (see GlintProgramMixin).
 */
public final class EnchantGlint extends Module {
    private final Setting pattern = add(Setting.choice("Pattern", 0, "Galaxy", "Rainbow", "Glitch", "Flow"));
    private final Setting color1 = add(Setting.color("Color 1", 0.75));
    private final Setting color2 = add(Setting.color("Color 2", 0.52));
    private final Setting speed = add(Setting.number("Speed", 0.1, 4, 1));
    private final Setting scale = add(Setting.number("Scale", 0.3, 4, 1));
    private final Setting strength = add(Setting.number("Strength", 0.2, 3, 1.2));

    public EnchantGlint() {
        super("EnchantGlint", Category.COSMETICS, "Your own shimmer on enchanted armour and items: galaxy, rainbow, glitch or a colour flow", false);
    }

    /** The shader to use for the game's glint layers, set up for this frame; null leaves the game's own glint. */
    public ShaderProgram program() {
        if (!enabled || !Interface.shaderVisuals()) {
            return null;
        }
        ShaderProgram p = Shaders.get("neuro_glint");
        if (p == null) {
            return null;
        }
        set(p, "Pattern", pattern.index());
        set(p, "Speed", speed.f());
        set(p, "Scale", scale.f());
        set(p, "Strength", strength.f());
        rgb(p, "Color1", color1.argb());
        rgb(p, "Color2", color2.argb());
        return p;
    }

    private static void set(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }

    private static void rgb(ShaderProgram p, String name, int c) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
