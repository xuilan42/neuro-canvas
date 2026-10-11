package com.example.neurocanvas.mixin;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.FreeCam;
import com.example.neurocanvas.module.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Unique
    private float neurocanvas$tickDelta;

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void neurocanvas$head(BlockView area, Entity focused, boolean thirdPerson, boolean inverted, float tickDelta,
                                  CallbackInfo ci) {
        neurocanvas$tickDelta = tickDelta;
    }

    /** FreeLook: swap the character's angles for the free angles, keeping the 180 degree flip of front view. */
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"),
            require = 0)
    private void neurocanvas$freeLook(Args args) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        FreeLook look = NeuroCanvasClient.modules().get(FreeLook.class);
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (!look.active() || p == null) {
            return;
        }
        float entityYaw = p.getYaw(neurocanvas$tickDelta);
        float entityPitch = p.getPitch(neurocanvas$tickDelta);
        float yaw = args.get(0);
        float pitch = args.get(1);
        args.set(0, look.yaw() + (yaw - entityYaw));
        args.set(1, Math.abs(pitch - entityPitch) < 0.01f ? look.pitch() : -look.pitch());
    }

    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void neurocanvas$freeCam(BlockView area, Entity focused, boolean thirdPerson, boolean inverted, float tickDelta,
                                     CallbackInfo ci) {
        if (NeuroCanvasClient.modules() == null) {
            return;
        }
        FreeCam cam = NeuroCanvasClient.modules().get(FreeCam.class);
        if (cam.active()) {
            cam.step(MinecraftClient.getInstance());
            setRotation(cam.yaw(), cam.pitch());
            setPos(cam.x(), cam.y(), cam.z());
        }
    }

    /** With a detached camera your own body must be drawn, like in third person. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true, require = 0)
    private void neurocanvas$body(CallbackInfoReturnable<Boolean> cir) {
        if (NeuroCanvasClient.modules() != null && NeuroCanvasClient.modules().get(FreeCam.class).active()) {
            cir.setReturnValue(true);
        }
    }
}
