package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.EnchantGlint;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every enchantment glint layer of the game (armour, held items, items in menus, entities) asks for its program here; EnchantGlint may answer with its own. */
@Mixin(GameRenderer.class)
public abstract class GlintProgramMixin {
    @Inject(method = {"getRenderTypeGlintProgram", "getRenderTypeGlintTranslucentProgram", "getRenderTypeGlintDirectProgram",
            "getRenderTypeArmorGlintProgram", "getRenderTypeArmorEntityGlintProgram", "getRenderTypeEntityGlintProgram",
            "getRenderTypeEntityGlintDirectProgram"}, at = @At("RETURN"), cancellable = true, require = 0)
    private static void neurocanvas$glint(CallbackInfoReturnable<ShaderProgram> cir) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        ShaderProgram p = NeuroCanvasClient.modules().get(EnchantGlint.class).program();
        if (p != null) {
            cir.setReturnValue(p);
        }
    }
}
