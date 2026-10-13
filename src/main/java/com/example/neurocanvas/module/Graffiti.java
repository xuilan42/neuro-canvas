package com.example.neurocanvas.module;

import com.example.neurocanvas.avatar.CosmeticPack;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Sprays a picture on the face of the block you look at. Set the module's Keybind to the spray key. */
public final class Graffiti extends CosmeticModule {
    private static final class Spray {
        Vec3d pos;
        Vec3d normal;
        float size;
        Identifier image;
        long created;
    }

    private final Setting lifetime = add(Setting.number("Lifetime (s)", 20, 900, 300));
    private final Setting reach = add(Setting.number("Reach (blocks)", 2, 12, 6));
    private final List<Spray> sprays = new ArrayList<>();
    private boolean keyWasDown;

    public Graffiti(Path root) {
        super("Graffiti", "A picture from neurocanvas/wardrobe/graffiti sprayed on a wall; the Keybind row is the spray key", CosmeticPack.Type.GRAFFITI, root, false);
        customKey = true;
    }

    @Override
    protected void tickCosmetic(MinecraftClient mc) {
        boolean down = key >= 0 && mc.currentScreen == null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
        if (down && !keyWasDown) {
            spray(mc);
        }
        keyWasDown = down;
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        super.tickAlways(mc);
        if (!enabled) {
            sprays.clear();
        }
    }

    /** Sprays the picture on the block face you are looking at. */
    public void spray(MinecraftClient mc) {
        if (!enabled || mc.player == null) {
            return;
        }
        if (main.image == null) {
            say("Pick a graffiti first");
            return;
        }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > reach.value * reach.value) {
            say("Too far from the wall");
            return;
        }
        Spray s = new Spray();
        s.normal = new Vec3d(hit.getSide().getOffsetX(), hit.getSide().getOffsetY(), hit.getSide().getOffsetZ());
        s.pos = hit.getPos().add(s.normal.multiply(0.012));
        s.size = (main.config.size == null ? 1.5f : main.config.size) * size.f();
        s.image = main.image;
        s.created = System.currentTimeMillis();
        sprays.removeIf(o -> o.pos.squaredDistanceTo(s.pos) < 0.25 && o.normal.equals(s.normal));
        sprays.add(s);
        while (sprays.size() > 24) {
            sprays.remove(0);
        }
    }

    @Override
    public void renderWorld(RenderEnv env) {
        long now = System.currentTimeMillis();
        sprays.removeIf(s -> now - s.created > lifetime.value * 1000);
        if (sprays.isEmpty()) {
            return;
        }
        Matrix4f m = env.m();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        Tessellator tess = Tessellator.getInstance();
        for (Spray s : sprays) {
            float fade = (float) Math.min(1.0, (lifetime.value * 1000 - (now - s.created)) / 8000.0);
            Vec3d n = s.normal;
            Vec3d u = Math.abs(n.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(-n.z, 0, n.x).normalize();
            Vec3d v = Math.abs(n.y) > 0.9 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
            double half = s.size / 2;
            RenderSystem.setShaderTexture(0, s.image);
            BufferBuilder b = tess.getBuffer();
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            double[][] c = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
            float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            for (int i = 0; i < 4; i++) {
                Vec3d q = s.pos.add(u.multiply(c[i][0] * half)).add(v.multiply(c[i][1] * half));
                b.vertex(m, (float) q.x, (float) q.y, (float) q.z).texture(uv[i][0], uv[i][1]).color(1f, 1f, 1f, fade).next();
            }
            tess.draw();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
