package com.example.neurocanvas.fx;

import com.example.neurocanvas.module.RenderEnv;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Kill effects that change the dead creature itself: its model is kept (the game's own is hidden) and drawn changing until it is gone.
 * Ice freezes it from the feet up and breaks it, Cubes wears it away into little squares, Hologram turns it into a glitching projection
 * and Implode spins it down into a point. The caller supplies particles and what happens at the end.
 */
public final class KillModels {
    public static final int ICE = 0, CUBES = 1, HOLOGRAM = 2, IMPLODE = 3;

    /** Makes a particle of the module's own look (size, alpha, colours) at a place. */
    public interface Maker {
        FxParticles.P make(double x, double y, double z);
    }

    /** What the module does when the model is gone: ice shatters into shards, the others finish with a pop. */
    public interface Finish {
        void done(int style, LivingEntity e, Vec3d pos);
    }

    private static final class Active {
        LivingEntity e;
        Vec3d pos;
        float yaw;
        float height;
        float width;
        long start;
        int style;
        float spawned;
    }

    private static final float[] SECONDS = {1.5f, 1.7f, 1.6f, 1.1f};
    private final List<Active> list = new ArrayList<>();
    private final Random random = new Random();

    public boolean busy() {
        return !list.isEmpty();
    }

    /** True when the shader that all the model looks use can be built (without it the module falls back to particles). */
    public static boolean possible() {
        return ModelLook.available();
    }

    public void start(LivingEntity e, int style) {
        Active a = new Active();
        a.e = e;
        a.pos = new Vec3d(e.getX(), e.getY(), e.getZ());
        a.yaw = e.getYaw();
        a.height = e.getHeight();
        a.width = e.getWidth();
        a.start = System.currentTimeMillis();
        a.style = style;
        list.add(a);
        ModelFx.hide(e, (long) (SECONDS[style] * 1000) + 500);
    }

    public void clear() {
        for (Active a : list) {
            ModelFx.show(a.e);
        }
        list.clear();
    }

    private double rnd() {
        return random.nextDouble();
    }

    public void render(RenderEnv env, float dt, EffectSpec colors, Maker maker, Finish finish) {
        long now = System.currentTimeMillis();
        for (int i = list.size() - 1; i >= 0; i--) {
            Active a = list.get(i);
            float sec = (now - a.start) / 1000f;
            float total = SECONDS[a.style];
            if (sec >= total || a.e == null) {
                ModelFx.show(a.e);
                list.remove(i);
                finish.done(a.style, a.e, a.pos);
                continue;
            }
            float p = sec / total;
            float ticks = (float) (env.t() * 20.0);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            ModelLook.State st = ModelLook.STATE;
            st.ticks = ticks;
            st.feetY = (float) (a.pos.y - env.cam().y);
            st.height = a.height;
            st.alpha = 1f;
            st.color1 = colors.color1 & 0xFFFFFF;
            st.color2 = colors.color2 & 0xFFFFFF;
            switch (a.style) {
                case ICE -> {
                    // Frost climbs for most of the time, then the body stands frozen for a beat before it breaks (see Finish).
                    st.mode = ModelLook.ICE;
                    st.progress = MathHelper.clamp(p / 0.7f, 0f, 1f);
                    st.color1 = 0x8FDFFF;
                    st.color2 = 0xFFFFFF;
                    if (p < 0.7f) {
                        for (st.mode = ModelLook.ICE; a.spawned < p * 40; a.spawned++) {
                            double y = a.pos.y + a.height * MathHelper.clamp(p / 0.7f, 0f, 1f) * rnd();
                            FxParticles.P q = maker.make(a.pos.x + (rnd() - 0.5) * a.width, y, a.pos.z + (rnd() - 0.5) * a.width);
                            q.vy = 0.3 + rnd() * 0.5;
                            q.size *= 0.5f;
                            q.max *= 0.5f;
                            q.c1 = 0xFFE8F8FF;
                            q.c2 = 0xFF8FDFFF;
                            q.shape = FxParticles.Shape.PIXEL;
                            q.grow = -0.7f;
                            spawn(q);
                        }
                    }
                    ModelFx.draw(a.e, env, a.pos, a.yaw, (e, t) -> ModelLook.layer(t));
                }
                case CUBES -> {
                    st.mode = ModelLook.DISSOLVE;
                    st.progress = p;
                    st.color1 = 0xFFB347;
                    st.color2 = 0xFFE9B0;
                    // The front of the dissolve climbs the body; squares fly off from it.
                    double front = a.pos.y + a.height * MathHelper.clamp(p * 1.5f - 0.1f, 0f, 1f);
                    while (a.spawned < p * 260) {
                        a.spawned++;
                        FxParticles.P q = maker.make(a.pos.x + (rnd() - 0.5) * a.width, front + (rnd() - 0.5) * 0.25, a.pos.z + (rnd() - 0.5) * a.width);
                        q.vx = (rnd() - 0.5) * 1.2;
                        q.vy = 0.4 + rnd() * 1.6;
                        q.vz = (rnd() - 0.5) * 1.2;
                        q.gravity = -0.2f;
                        q.drag = 0.6f;
                        q.size = 0.04f + 0.05f * (float) rnd();
                        q.max = 0.9f + (float) rnd() * 0.8f;
                        q.shape = FxParticles.Shape.SQUARE;
                        q.spin = (float) (rnd() - 0.5) * 8f;
                        q.grow = -0.8f;
                        q.c1 = rnd() < 0.5 ? 0xFFFFC76B : 0xFFFFFFFF;
                        q.c2 = 0xFFFF6A2A;
                        spawn(q);
                    }
                    ModelFx.draw(a.e, env, a.pos, a.yaw, (e, t) -> ModelLook.layer(t));
                }
                case HOLOGRAM -> {
                    // A projection that flickers and slips sideways, then fades into rising squares.
                    float flick = 0.75f + 0.25f * (float) Math.sin(ticks * 1.7f) * (rnd() < 0.15 ? 0.2f : 1f);
                    float fade = 1f - MathHelper.clamp((p - 0.7f) / 0.3f, 0f, 1f);
                    boolean glitch = rnd() < 0.18;
                    Vec3d at = glitch ? a.pos.add((rnd() - 0.5) * 0.35, 0, (rnd() - 0.5) * 0.35) : a.pos;
                    EffectSpec c = new EffectSpec();
                    c.color1 = colors.color1;
                    c.color2 = colors.color2;
                    c.alpha = 1.3f * flick * fade;
                    ModelFx.drawHologram(a.e, env, at, c, ticks);
                    if (p > 0.55f) {
                        while (a.spawned < (p - 0.55f) * 320) {
                            a.spawned++;
                            FxParticles.P q = maker.make(a.pos.x + (rnd() - 0.5) * a.width, a.pos.y + rnd() * a.height, a.pos.z + (rnd() - 0.5) * a.width);
                            q.vy = 0.5 + rnd() * 1.4;
                            q.gravity = -0.3f;
                            q.size *= 0.45f;
                            q.max *= 0.7f;
                            q.shape = FxParticles.Shape.PIXEL;
                            q.c1 = 0xFFB8F4FF;
                            q.c2 = 0xFF3D8BFF;
                            spawn(q);
                        }
                    }
                }
                default -> {
                    // Implode: the body turns faster and faster, shrinks to a point and turns violet as it is pulled in.
                    st.mode = ModelLook.TINT;
                    st.progress = p;
                    st.color1 = 0x6A2BD9;
                    st.color2 = 0xC9A0FF;
                    float scale = Math.max(0.01f, 1f - (float) Math.pow(p, 2.2));
                    float spin = a.yaw + 720f * p * p;
                    Vec3d centre = a.pos;
                    for (int k = 0; k < 3; k++) {
                        double th = rnd() * Math.PI * 2;
                        double r = 0.9 + rnd() * 0.8;
                        FxParticles.P q = maker.make(centre.x + Math.cos(th) * r, centre.y + a.height * 0.5 + (rnd() - 0.5) * a.height, centre.z + Math.sin(th) * r);
                        q.attract = 4f;
                        q.tx = centre.x;
                        q.ty = centre.y + a.height * 0.5;
                        q.tz = centre.z;
                        q.cx = centre.x;
                        q.cz = centre.z;
                        q.swirl = 4f;
                        q.max = 0.5f;
                        q.size *= 0.5f;
                        q.c1 = 0xFFC9A0FF;
                        q.c2 = 0xFF2A0A5A;
                        spawn(q);
                    }
                    ModelFx.draw(a.e, env, a.pos, spin, scale, (e, t) -> ModelLook.layer(t));
                }
            }
        }
        RenderSystem.disableDepthTest();
    }

    private FxParticles fx;

    public void attach(FxParticles particles) {
        this.fx = particles;
    }

    private void spawn(FxParticles.P q) {
        if (fx != null) {
            fx.spawn(q);
        }
    }
}
