package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server says an entity died with entity status 3, before it is removed: the signal kill detection relies on. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Shadow
    private ClientWorld world;

    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void neurocanvas$death(EntityStatusS2CPacket packet, CallbackInfo ci) {
        try {
            if (packet.getStatus() == 3 && this.world != null && net.minecraft.client.MinecraftClient.getInstance().isOnThread()) {
                Entity e = packet.getEntity(this.world);
                if (e instanceof LivingEntity living) {
                    NeuroCanvasClient.state().onDeathStatus(living);
                }
            }
        } catch (RuntimeException ignored) {
            // Kill effects are cosmetic; never break packet handling.
        }
    }
}
