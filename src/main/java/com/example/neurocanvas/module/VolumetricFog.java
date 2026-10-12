package com.example.neurocanvas.module;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Fog drawn by our own shader after the world: it reads the depth buffer, works out how far and how high every pixel
 * is, and blends a fog whose density falls off with height. It does not use the game's fog at all, so it works with
 * Sodium. If the shader cannot be built, the module switches itself off and the normal fog stays.
 */
public final class VolumetricFog extends Module {
    private final Setting density = add(Setting.number("Density", 0, 1, 0.3));
    private final Setting style = add(Setting.choice("Style", 0, "Height fog", "Distance fog"));
    private final Setting falloff = add(Setting.number("Height falloff", 0, 1, 0.25));
    private final Setting level = add(Setting.number("Fog height (Y)", -64, 320, 63));
    private final Setting start = add(Setting.number("Start distance", 0, 100, 6));
    private final Setting opacity = add(Setting.number("Max opacity", 0.1, 1, 0.9));
    private final Setting skyBlend = add(Setting.number("Sky haze", 0, 1, 0.4));
    private final Setting vanillaColor = add(Setting.bool("Use sky fog color", true));
    private final Setting color = add(Setting.color("Color", 0.58));
    private final Setting sun = add(Setting.number("Sun glow", 0, 1, 0.5));
    private final Setting sunColor = add(Setting.color("Sun color", 0.1));
    private final Setting rain = add(Setting.number("Rain thickening", 0, 2, 0.8));
    private final Setting removeVanilla = add(Setting.bool("Replace vanilla fog", true));

    private ShaderProgram program;
    private Framebuffer depthCopy;
    private boolean failed;
    private boolean ranLastFrame;
    private String status = "off";
    private boolean reported;

    public VolumetricFog() {
        super("VolumetricFog", Category.WORLD, "Own fog shader: soft haze that is thicker low and fades with height (experimental)", false);
        sunColor.setArgb(0xFFFFC080);
        color.setArgb(0xFFB5C7DA);
    }

    public String status() {
        return status;
    }

    /** True while the shader is really drawing, so the game's own fog can be switched off. */
    public boolean replacesVanillaFog() {
        return enabled && !failed && ranLastFrame && removeVanilla.on();
    }

    private static boolean shaderPackActive() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            return (Boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static void set(ShaderProgram p, String name, float a, float b, float c) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(a, b, c);
        }
    }

    private static void set(ShaderProgram p, String name, float a, float b, float c, float d) {
        GlUniform u = p.getUniform(name);
        if (u != null) {
            u.set(a, b, c, d);
        }
    }

    private void fail(MinecraftClient mc, String why) {
        failed = true;
        ranLastFrame = false;
        status = "disabled: " + why;
        if (!reported && mc.player != null) {
            reported = true;
            mc.player.sendMessage(Text.literal("[VolumetricFog] " + status + ". The normal fog is used instead."), false);
        }
    }

    /** Draws the fog over the finished world. Called by the client before the other world effects. */
    public void pass(WorldRenderContext ctx) {
        ranLastFrame = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (!enabled || failed || world == null) {
            if (!enabled) {
                status = "off";
            }
            return;
        }
        if (ctx.camera().getSubmersionType() != CameraSubmersionType.NONE) {
            status = "paused (underwater or in lava)";
            return;
        }
        if (shaderPackActive()) {
            status = "paused (a shader pack is active)";
            return;
        }
        try {
            {
                program = com.example.neurocanvas.fx.Shaders.get("neuro_fog");
                if (program == null) {
                    fail(mc, "shader neuro_fog: " + com.example.neurocanvas.fx.Shaders.error("neuro_fog"));
                    return;
                }
            }
            Framebuffer main = mc.getFramebuffer();
            if (depthCopy == null) {
                depthCopy = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
            } else if (depthCopy.textureWidth != main.textureWidth || depthCopy.textureHeight != main.textureHeight) {
                depthCopy.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }
            // The depth buffer cannot be read while it is also being written, so a copy is read instead.
            depthCopy.copyDepthFrom(main);
            main.beginWrite(false);

            float td = ctx.tickDelta();
            float rainLevel = world.getRainGradient(td) * 0.7f + world.getThunderGradient(td) * 0.3f;
            double c = density.value * 0.03 * (1.0 + rainLevel * rain.value);
            double b = falloff.value * 0.15;

            float[] fogRgb;
            if (vanillaColor.on()) {
                float[] f = RenderSystem.getShaderFogColor();
                fogRgb = new float[] {f[0], f[1], f[2]};
            } else {
                int argb = color.argb();
                fogRgb = new float[] {((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
            }
            int sc = sunColor.argb();
            float sunVisible = world.getDimension().hasSkyLight() ? 1f : 0f;
            double angle = world.getSkyAngleRadians(td);
            Vec3d cam = ctx.camera().getPos();

            Matrix4f invProj = new Matrix4f(ctx.projectionMatrix()).invert();
            Matrix4f invView = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix()).invert();
            GlUniform u = program.getUniform("InvProj");
            if (u != null) {
                u.set(invProj.get(new float[16]));
            }
            u = program.getUniform("InvView");
            if (u != null) {
                u.set(invView.get(new float[16]));
            }
            set(program, "CamPos", (float) cam.x, (float) cam.y, (float) cam.z);
            set(program, "NcFogTint", fogRgb[0], fogRgb[1], fogRgb[2]);
            set(program, "SunDir", (float) -Math.sin(angle), (float) Math.cos(angle), 0f);
            set(program, "SunColor", ((sc >> 16) & 0xFF) / 255f, ((sc >> 8) & 0xFF) / 255f, (sc & 0xFF) / 255f);
            set(program, "Params0", (float) c, (float) b, level.f(), start.f());
            set(program, "Params1", opacity.f(), sun.f() * sunVisible, skyBlend.f(), style.index() == 1 ? 1f : 0f);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> program);
            RenderSystem.setShaderTexture(0, depthCopy.getDepthAttachment());
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            buf.vertex(-1.0, -1.0, 0.0).next();
            buf.vertex(1.0, -1.0, 0.0).next();
            buf.vertex(1.0, 1.0, 0.0).next();
            buf.vertex(-1.0, 1.0, 0.0).next();
            tess.draw();
            com.example.neurocanvas.fx.Shaders.finish();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            ranLastFrame = true;
            status = "running";
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger("neurocanvas").error("VolumetricFog failed", e);
            fail(mc, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).split("\n")[0]);
        }
    }

    /** Lets the player try again after a failure, for example after fixing a resource pack. */
    public void retry() {
        failed = false;
        reported = false;
        program = null;
        status = "retrying";
    }
}
