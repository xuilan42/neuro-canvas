package com.example.neurocanvas.fx;

import com.example.neurocanvas.gui.Ui;
import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Text floating at a point in the world, always facing the camera and drawn through blocks. */
public final class Billboard {
    private Billboard() { }

    public static void draw(RenderEnv env, Vec3d pos, String label, int argb, float scale) {
        MinecraftClient mc = env.mc();
        TextRenderer tr = mc.textRenderer;
        Text text = Ui.text(label);
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack ms = new MatrixStack();
        ms.peek().getPositionMatrix().set(env.m());
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(mc.getEntityRenderDispatcher().getRotation());
        float s = 0.025f * scale;
        ms.scale(-s, -s, s);
        float w = tr.getWidth(text);
        tr.draw(text, -w / 2f, 0, 0xFF000000 | (argb & 0xFFFFFF), false, ms.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, 15728880);
        consumers.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
