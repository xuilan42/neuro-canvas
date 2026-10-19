package com.example.neurocanvas.mixin;

import com.example.neurocanvas.gui.GuiSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Toasts (advancements, recipes, system messages) get a themed panel; the game's own background pieces are skipped while the toast is drawn. */
@Mixin(targets = "net.minecraft.client.toast.ToastManager$Entry")
public abstract class ToastEntryMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/toast/Toast;draw(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/toast/ToastManager;J)Lnet/minecraft/client/toast/Toast$Visibility;"), require = 0)
    private Toast.Visibility neurocanvas$toast(Toast toast, DrawContext context, ToastManager manager, long startTime, Operation<Toast.Visibility> original) {
        boolean skinned = GuiSkin.toastStart(toast, context);
        try {
            return original.call(toast, context, manager, startTime);
        } finally {
            if (skinned) {
                GuiSkin.toastEnd();
            }
        }
    }
}
