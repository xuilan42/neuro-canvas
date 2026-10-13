package com.example.neurocanvas.module;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.fx.EffectDraw;
import com.example.neurocanvas.fx.Lines;
import com.example.neurocanvas.gui.PingIcons;
import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

/**
 * Hold a key, pick a mark on the wheel and release: a ping appears on the spot you were looking at, with an icon, the
 * distance and the name. Pings are shown to other Neuro Canvas users on the same server (while NameTags is on).
 */
public final class PingWheel extends Module {
    /** What the cursor pointed at when the wheel opened. */
    public record Target(Vec3d pos, int entity) { }

    private static final class Active {
        int type;
        double x;
        double y;
        double z;
        int entity;
        String owner;
        boolean mine;
        long created;
        float sx;
        float sy;
        boolean edge;
        boolean hidden;
        double dist;
    }

    private final Setting lifetime = add(Setting.number("Lifetime (s)", 3, 40, 12));
    private final Setting maxDistance = add(Setting.number("Show up to (blocks)", 50, 1000, 400));
    private final Setting iconSize = add(Setting.number("Icon size", 0.6, 2.5, 1));
    private final Setting share = add(Setting.bool("Share with other users", true));
    private final Setting showOthers = add(Setting.bool("Show other users' pings", true));
    private final Setting sound = add(Setting.bool("Sound", true));
    private final Setting worldMarker = add(Setting.bool("Marker in the world", true));

    private final List<Active> pings = new ArrayList<>();
    private long countStart;
    private String countBy = "";
    private int lastCountStep = -1;

    public PingWheel() {
        super("PingWheel", Category.FRIENDS, "Hold its key (V by default, change it in the Keybind row): a wheel of pings for you and other users", true);
        aiControllable = false;
        customKey = true;
        key = 86;
    }

    // ---- picking the spot -------------------------------------------------------------------

    /** The block or entity under the crosshair, up to 256 blocks away; null when there is nothing there. */
    public static Target capture(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        double reach = 256.0;
        Vec3d from = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d to = from.add(look.multiply(reach));
        HitResult block = mc.player.raycast(reach, 1f, false);
        double blockDist = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : block.getPos().squaredDistanceTo(from);
        Box box = mc.player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
        EntityHitResult entity = ProjectileUtil.raycast(mc.player, from, to, box, e -> !e.isSpectator() && e.canHit(), reach * reach);
        if (entity != null && entity.getPos().squaredDistanceTo(from) < blockDist) {
            Entity e = entity.getEntity();
            return new Target(e.getPos().add(0, e.getHeight() + 0.2, 0), e.getId());
        }
        if (block.getType() == HitResult.Type.MISS) {
            return null;
        }
        return new Target(block.getPos().add(0, 0.6, 0), -1);
    }

    // ---- actions ----------------------------------------------------------------------------

    public void place(int type, Target target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target == null || mc.player == null) {
            return;
        }
        String me = mc.player.getName().getString();
        while (countMine() >= 3) {
            pings.remove(firstMine());
        }
        add(type, target.pos().x, target.pos().y, target.pos().z, target.entity(), me, true);
        if (share.on()) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "ping");
            o.addProperty("type", type);
            o.addProperty("x", Math.round(target.pos().x * 100) / 100.0);
            o.addProperty("y", Math.round(target.pos().y * 100) / 100.0);
            o.addProperty("z", Math.round(target.pos().z * 100) / 100.0);
            o.addProperty("entity", target.entity());
            NeuroCanvasClient.modules().get(Friends.class).broadcast(o);
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private int countMine() {
        int n = 0;
        for (Active a : pings) {
            if (a.mine) {
                n++;
            }
        }
        return n;
    }

    private Active firstMine() {
        for (Active a : pings) {
            if (a.mine) {
                return a;
            }
        }
        return null;
    }

    private void add(int type, double x, double y, double z, int entity, String owner, boolean mine) {
        Active a = new Active();
        a.type = type;
        a.x = x;
        a.y = y;
        a.z = z;
        a.entity = entity;
        a.owner = owner;
        a.mine = mine;
        a.created = now();
        pings.add(a);
        if (sound.on()) {
            float pitch = 0.8f + (type % 4) * 0.2f;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), pitch, 0.7f));
        }
    }

    public void clearMine() {
        pings.removeIf(a -> a.mine);
    }

    public void startCountdown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        begin(mc.player == null ? "" : mc.player.getName().getString());
        if (share.on()) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "count");
            NeuroCanvasClient.modules().get(Friends.class).broadcast(o);
        }
    }

    private void begin(String by) {
        countStart = now();
        countBy = by;
        lastCountStep = -1;
    }

    /** A gesture: plays the avatar animation with that name, if the avatar has one. */
    public void emote(String name) {
        if (!NeuroCanvasClient.modules().get(Wardrobe.class).emote(name)) {
            NeuroCanvasClient.modules().get(Avatars.class).emote(name);
        }
    }

    /** A message from another user, relayed by the registry. Runs on the game thread. */
    public void receive(JsonObject o) {
        if (!enabled || !showOthers.on()) {
            return;
        }
        try {
            if (o.has("ping")) {
                JsonObject p = o.getAsJsonObject("ping");
                int type = p.get("type").getAsInt();
                if (type < 0 || type >= PingIcons.PINGS) {
                    return;
                }
                String from = p.has("from") ? p.get("from").getAsString() : "?";
                if (!NeuroCanvasClient.modules().get(Friends.class).acceptsFrom(from)) {
                    return;
                }
                while (countOf(from) >= 3) {
                    pings.remove(firstOf(from));
                }
                add(type, p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble(),
                        p.has("entity") ? p.get("entity").getAsInt() : -1, from, false);
            } else if (o.has("count")) {
                String from = o.getAsJsonObject("count").get("from").getAsString();
                if (NeuroCanvasClient.modules().get(Friends.class).acceptsFrom(from)) {
                    begin(from);
                }
            }
        } catch (RuntimeException ignored) {
            // A malformed message is ignored.
        }
    }

    private Active firstOf(String owner) {
        for (Active a : pings) {
            if (!a.mine && a.owner.equals(owner)) {
                return a;
            }
        }
        return null;
    }

    private int countOf(String owner) {
        int n = 0;
        for (Active a : pings) {
            if (!a.mine && a.owner.equals(owner)) {
                n++;
            }
        }
        return n;
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    public void renderWorld(RenderEnv env) {
        MinecraftClient mc = env.mc();
        long t = now();
        pings.removeIf(a -> t - a.created > lifetime.value * 1000);
        if (pings.isEmpty() || mc.world == null || mc.player == null) {
            return;
        }
        Lines lines = worldMarker.on() ? Lines.begin(env, 2.4f) : null;
        double time = env.t();
        for (Active a : pings) {
            double x = a.x, y = a.y, z = a.z;
            if (a.entity >= 0) {
                Entity e = mc.world.getEntityById(a.entity);
                if (e != null) {
                    Vec3d p = EffectDraw.lerpPos(e, env.tickDelta());
                    x = p.x;
                    y = p.y + e.getHeight() + 0.2;
                    z = p.z;
                }
            }
            a.dist = mc.player.getPos().distanceTo(new Vec3d(x, y, z));
            a.hidden = a.dist > maxDistance.value;
            Vector4f v = new Vector4f((float) x, (float) y, (float) z, 1f);
            v.mul(env.m());
            v.mul(RenderSystem.getProjectionMatrix());
            float w = v.w;
            float nx = v.x / (Math.abs(w) < 1e-4f ? 1e-4f : w);
            float ny = v.y / (Math.abs(w) < 1e-4f ? 1e-4f : w);
            boolean behind = w <= 0.05f;
            if (behind) {
                nx = -nx;
                ny = -ny;
            }
            a.edge = behind || Math.abs(nx) > 0.92f || Math.abs(ny) > 0.88f;
            if (a.edge) {
                float k = Math.max(Math.abs(nx) / 0.92f, Math.abs(ny) / 0.88f);
                if (k < 1e-3f) {
                    nx = 0f;
                    ny = -0.88f;
                } else {
                    nx /= k;
                    ny /= k;
                }
            }
            a.sx = (nx * 0.5f + 0.5f) * mc.getWindow().getScaledWidth();
            a.sy = (0.5f - ny * 0.5f) * mc.getWindow().getScaledHeight();
            if (lines != null && !a.hidden) {
                int color = (0xFF << 24) | PingIcons.COLORS[a.type];
                double pulse = 0.5 + 0.12 * Math.sin(time * 4);
                double base = a.entity >= 0 ? y - 1.2 : y - 0.6;
                int n = 20;
                for (int i = 0; i < n; i++) {
                    double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                    lines.line(x + Math.cos(a0) * pulse, base, z + Math.sin(a0) * pulse, x + Math.cos(a1) * pulse, base, z + Math.sin(a1) * pulse, color);
                }
                lines.line(x, base, z, x, base + 1.6, z, color);
            }
        }
        if (lines != null) {
            lines.end();
        }
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        if (mc.options.hudHidden) {
            return;
        }
        Theme th = Interface.theme();
        long t = now();
        for (Active a : pings) {
            if (a.hidden) {
                continue;
            }
            float age = (t - a.created) / 1000f;
            float fade = Math.min(1f, Math.min(age / 0.2f, (float) (lifetime.value - age) / 1.5f));
            float pop = age < 0.25f ? 0.6f + 1.6f * age : 1f;
            int scale = Math.max(1, Math.round(2 * iconSize.f() * (a.edge ? 0.75f : 1f) * pop));
            int size = PingIcons.SIZE * scale;
            int x = Math.round(a.sx) - size / 2;
            int y = Math.round(a.sy) - size / 2;
            int color = PingIcons.COLORS[a.type];
            Ui.roundRect(ctx, x - 5, y - 5, size + 10, size + 10, 8, Ui.alpha(th.bg() & 0xFFFFFF, 0.75f * fade));
            PingIcons.draw(ctx, a.type, x, y, scale, Ui.alpha(color, fade));
            String text = Math.round(a.dist) + "m";
            if (!a.mine) {
                text = a.owner + "  " + text;
            }
            Ui.textCentered(ctx, mc.textRenderer, text, Math.round(a.sx), y + size + 8, Ui.alpha(th.text() & 0xFFFFFF, fade));
        }
        drawCountdown(ctx, mc, th, t);
    }

    private void drawCountdown(DrawContext ctx, MinecraftClient mc, Theme th, long t) {
        if (countStart == 0) {
            return;
        }
        float s = (t - countStart) / 1000f;
        if (s > 3.9f) {
            countStart = 0;
            return;
        }
        int step = s < 3f ? (int) s : 3;
        String text = step == 0 ? "3" : step == 1 ? "2" : step == 2 ? "1" : "GO";
        if (step != lastCountStep) {
            lastCountStep = step;
            if (sound.on()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), step == 3 ? 1.6f : 1.0f, 1f));
            }
        }
        float phase = s < 3f ? s - step : s - 3f;
        float scale = 3.5f - 1.2f * Math.min(1f, phase * 2f);
        float alpha = step == 3 ? Math.max(0f, 1f - phase / 0.9f) : 1f - 0.4f * phase;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() * 0.28f, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        Ui.textCentered(ctx, mc.textRenderer, text, 0, -4, Ui.alpha(step == 3 ? 0x5CE08A : th.accent() & 0xFFFFFF, alpha));
        ctx.getMatrices().pop();
        if (!countBy.isEmpty()) {
            Ui.textCentered(ctx, mc.textRenderer, countBy + " started a countdown", ctx.getScaledWindowWidth() / 2,
                    (int) (ctx.getScaledWindowHeight() * 0.28f) + 22, Ui.alpha(th.dim() & 0xFFFFFF, alpha));
        }
    }
}
