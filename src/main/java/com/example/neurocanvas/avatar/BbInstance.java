package com.example.neurocanvas.avatar;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A Blockbench model ready to draw: its textures live in the game, and it can be posed by animations and drawn with any base matrix. */
public final class BbInstance {
    private static final class Quad {
        int tex;
        final float[] p = new float[12];
        final float[] uv = new float[8];
        float lit;
    }

    public final BbModel model;
    private final Map<Integer, Identifier> textures = new HashMap<>();
    private final List<Identifier> registered = new ArrayList<>();
    private final Map<Integer, String> refs = new HashMap<>();
    /** per uuid: position x y z, rotation x y z (degrees), scale x y z. */
    private final Map<String, float[]> pose = new HashMap<>();

    public BbInstance(MinecraftClient mc, BbModel model) throws IOException {
        this.model = model;
        for (int i = 0; i < model.textures.size(); i++) {
            byte[] data = model.textures.get(i);
            if (model.textureRefs.get(i) != null) {
                refs.put(i, model.textureRefs.get(i));
                continue;
            }
            if (data.length == 0) {
                continue;
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(NativeImage.read(new ByteArrayInputStream(data)));
            Identifier id = new Identifier("neurocanvas", "cosmetic/" + UUID.randomUUID());
            mc.getTextureManager().registerTexture(id, tex);
            tex.setFilter(false, false);
            registered.add(id);
            textures.put(i, id);
        }
    }

    public void close(MinecraftClient mc) {
        for (Identifier id : registered) {
            mc.getTextureManager().destroyTexture(id);
        }
        registered.clear();
        textures.clear();
    }

    public BbModel.Anim find(String name) {
        BbModel.Anim a = model.animations.get(name);
        if (a != null) {
            return a;
        }
        for (Map.Entry<String, BbModel.Anim> e : model.animations.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** The playing time of an animation that started at {@code startNano}: it wraps when it loops and stops at the end otherwise. */
    public static double time(BbModel.Anim a, long startNano, long nowNano, double speed) {
        double t = (nowNano - startNano) / 1_000_000_000.0 * speed;
        return a.loop == 0 ? t % a.length : Math.min(t, a.length);
    }

    public void resetPose() {
        pose.clear();
    }

    public void apply(BbModel.Anim a, double time) {
        for (Map.Entry<String, BbModel.Channels> e : a.animators.entrySet()) {
            BbModel.Channels c = e.getValue();
            double[] pos = BbModel.sample(c.position, time);
            double[] rot = BbModel.sample(c.rotation, time);
            double[] scl = BbModel.sample(c.scale, time);
            if (pos == null && rot == null && scl == null) {
                continue;
            }
            float[] s = pose.computeIfAbsent(e.getKey(), k -> new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1});
            if (pos != null) {
                s[0] -= (float) pos[0];
                s[1] += (float) pos[1];
                s[2] += (float) pos[2];
            }
            if (rot != null) {
                s[3] += (float) rot[0];
                s[4] += (float) rot[1];
                s[5] += (float) rot[2];
            }
            if (scl != null) {
                s[6] *= (float) scl[0];
                s[7] *= (float) scl[1];
                s[8] *= (float) scl[2];
            }
        }
    }

    private static Matrix4f transformed(Matrix4f parent, double[] origin, double[] rot, float[] s) {
        Matrix4f m = new Matrix4f(parent);
        float ox = (float) origin[0], oy = (float) origin[1], oz = (float) origin[2];
        if (s != null) {
            m.translate(s[0], s[1], s[2]);
        }
        m.translate(ox, oy, oz);
        float rx = (float) rot[0] + (s == null ? 0 : s[3]);
        float ry = (float) rot[1] + (s == null ? 0 : s[4]);
        float rz = (float) rot[2] + (s == null ? 0 : s[5]);
        m.rotateZ((float) Math.toRadians(rz)).rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx));
        if (s != null) {
            m.scale(s[6], s[7], s[8]);
        }
        m.translate(-ox, -oy, -oz);
        return m;
    }

    private void collect(BbModel.Bone bone, Matrix4f parent, List<Quad> out) {
        Matrix4f m = transformed(parent, bone.origin, bone.rotation, pose.get(bone.uuid));
        for (BbModel.Cube c : bone.cubes) {
            addCube(c, transformed(m, c.origin, c.rotation, pose.get(c.uuid)), out);
        }
        for (BbModel.Bone child : bone.children) {
            collect(child, m, out);
        }
    }

    private static void addCube(BbModel.Cube c, Matrix4f m, List<Quad> out) {
        double g = c.inflate;
        double x1 = Math.min(c.from[0], c.to[0]) - g, x2 = Math.max(c.from[0], c.to[0]) + g;
        double y1 = Math.min(c.from[1], c.to[1]) - g, y2 = Math.max(c.from[1], c.to[1]) + g;
        double z1 = Math.min(c.from[2], c.to[2]) - g, z2 = Math.max(c.from[2], c.to[2]) + g;
        double[][][] corners = {
            {{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}},
            {{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}},
            {{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}},
            {{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}},
            {{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}},
            {{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}}};
        Vector3f v = new Vector3f();
        for (int f = 0; f < 6; f++) {
            BbModel.Face face = c.faces[f];
            if (face == null || face.uv == null) {
                continue;
            }
            Quad q = new Quad();
            q.tex = face.texture;
            float[] uv = face.uv;
            float[] us = {uv[0], uv[2], uv[2], uv[0]};
            float[] vs = {uv[1], uv[1], uv[3], uv[3]};
            for (int i = 0; i < 4; i++) {
                v.set((float) corners[f][i][0], (float) corners[f][i][1], (float) corners[f][i][2]);
                m.transformPosition(v);
                q.p[i * 3] = v.x;
                q.p[i * 3 + 1] = v.y;
                q.p[i * 3 + 2] = v.z;
                q.uv[i * 2] = us[i];
                q.uv[i * 2 + 1] = vs[i];
            }
            float ax = q.p[3] - q.p[0], ay = q.p[4] - q.p[1], az = q.p[5] - q.p[2];
            float bx = q.p[9] - q.p[0], by = q.p[10] - q.p[1], bz = q.p[11] - q.p[2];
            float nx = -(ay * bz - az * by), ny = -(az * bx - ax * bz), nz = -(ax * by - ay * bx);
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            q.lit = len < 1e-6f ? 1f : 0.75f + 0.25f * (ny / len);
            out.add(q);
        }
    }

    /** The game's own skin: Steve, or the skin of the player who plays. */
    private static Identifier skin(String ref) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ref.equals("neurocanvas:steve")) {
            return new Identifier("neurocanvas", "textures/steve.png");
        }
        if (ref.equals("minecraft:player") && mc.player != null) {
            return mc.player.getSkinTexture();
        }
        return net.minecraft.client.util.DefaultSkinHelper.getTexture();
    }

    /** Draws the model with its current pose. {@code base} places it in the world (one model unit is whatever it scales to). */
    public void draw(Matrix4f base, float light, float shading) {
        List<Quad> quads = new ArrayList<>();
        Matrix4f identity = new Matrix4f();
        for (BbModel.Bone b : model.roots) {
            collect(b, identity, quads);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        Set<Integer> done = new HashSet<>();
        for (Quad first : quads) {
            if (!done.add(first.tex)) {
                continue;
            }
            Identifier id = refs.containsKey(first.tex) ? skin(refs.get(first.tex)) : textures.get(first.tex);
            if (id != null) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                RenderSystem.setShaderTexture(0, id);
                b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            } else {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            }
            for (Quad q : quads) {
                if (q.tex != first.tex) {
                    continue;
                }
                float lit = (1f + (q.lit - 1f) * shading) * light;
                for (int i = 0; i < 4; i++) {
                    b.vertex(base, q.p[i * 3], q.p[i * 3 + 1], q.p[i * 3 + 2]);
                    if (id != null) {
                        b.texture(q.uv[i * 2], q.uv[i * 2 + 1]);
                    }
                    float c = id != null ? lit : 0.7f * lit;
                    b.color(c, c, c, 1f).next();
                }
            }
            tess.draw();
        }
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }
}
