package com.example.neurocanvas.gui;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.Interface;
import com.example.neurocanvas.module.PingWheel;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

/**
 * The ping wheel: an outer ring of eight pings and an inner ring of four actions. It opens while the key is held,
 * the cursor picks a segment, and letting go of the key does it. The spot is taken when the wheel opens.
 */
public final class PingWheelScreen extends Screen {
    private static final int OUTER_IN = 88;
    private static final int OUTER_OUT = 150;
    private static final int INNER_IN = 34;
    private static final int INNER_OUT = 80;

    private final PingWheel wheel;
    private final PingWheel.Target target;
    private final int keyCode;
    private int hover = -1;
    private float open;
    private long last = System.nanoTime();

    public PingWheelScreen(PingWheel wheel, PingWheel.Target target, int keyCode) {
        super(Text.literal("Ping wheel"));
        this.wheel = wheel;
        this.target = target;
        this.keyCode = keyCode;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        long window = client.getWindow().getHandle();
        if (!InputUtil.isKeyPressed(window, keyCode)) {
            choose();
        }
    }

    private void choose() {
        int pick = hover;
        close();
        if (pick < 0) {
            return;
        }
        if (pick < PingIcons.PINGS) {
            if (target != null) {
                wheel.place(pick, target);
            } else if (client.player != null) {
                client.player.sendMessage(Text.literal("[PingWheel] nothing to ping there: aim at a block or an entity"), true);
            }
            return;
        }
        switch (pick) {
            case 8 -> wheel.clearMine();
            case 9 -> wheel.startCountdown();
            case 10 -> wheel.emote("wave");
            default -> wheel.emote("dance");
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hover >= 0) {
            choose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void updateHover(int mx, int my) {
        double dx = mx - width / 2.0;
        double dy = my - height / 2.0;
        double r = Math.hypot(dx, dy);
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        if (angle < 0) {
            angle += Math.PI * 2;
        }
        if (r >= OUTER_IN - 8 && r <= OUTER_OUT + 30) {
            hover = (int) Math.floor((angle + Math.PI / 8) / (Math.PI / 4)) % PingIcons.PINGS;
        } else if (r >= INNER_IN && r < OUTER_IN - 8) {
            hover = PingIcons.PINGS + (int) Math.floor((angle + Math.PI / 4) / (Math.PI / 2)) % 4;
        } else {
            hover = -1;
        }
    }

    /** One annular sector, from angle a0 to a1 (radians, 0 = up, clockwise). */
    private static void sector(Matrix4f m, float cx, float cy, float r0, float r1, double a0, double a1, int argb) {
        float a = (argb >>> 24) / 255f, r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        int steps = 14;
        for (int i = 0; i <= steps; i++) {
            double ang = a0 + (a1 - a0) * i / steps - Math.PI / 2;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            buf.vertex(m, cx + c * r1, cy + s * r1, 0f).color(r, g, b, a).next();
            buf.vertex(m, cx + c * r0, cy + s * r0, 0f).color(r, g, b, a).next();
        }
        Tessellator.getInstance().draw();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - last) / 1_000_000_000f, 0.1f);
        last = now;
        open = Interface.animations() ? Math.min(1f, open + dt * 7f) : 1f;
        float ease = 1f - (float) Math.pow(1f - open, 3);
        updateHover(mouseX, mouseY);
        Theme th = Interface.theme();
        ctx.fill(0, 0, width, height, Ui.alpha(0x000000, 0.35f * ease));

        float cx = width / 2f;
        float cy = height / 2f;
        float k = 0.8f + 0.2f * ease;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        double gap = 0.025;
        for (int i = 0; i < PingIcons.PINGS; i++) {
            double a0 = -Math.PI / 8 + i * Math.PI / 4 + gap;
            double a1 = a0 + Math.PI / 4 - 2 * gap;
            boolean h = hover == i;
            int base = (0xFF << 24) | (th.panel() & 0xFFFFFF);
            int fill = h ? Ui.mix(base, 0xFF000000 | PingIcons.COLORS[i], 0.55f) : Ui.alpha(th.panel() & 0xFFFFFF, 0.9f * ease);
            sector(m, cx, cy, OUTER_IN * k, (OUTER_OUT + (h ? 8 : 0)) * k, a0, a1, fill);
        }
        for (int i = 0; i < 4; i++) {
            double a0 = -Math.PI / 4 + i * Math.PI / 2 + gap * 2;
            double a1 = a0 + Math.PI / 2 - 4 * gap;
            boolean h = hover == PingIcons.PINGS + i;
            int base = (0xFF << 24) | (th.panel2() & 0xFFFFFF);
            int fill = h ? Ui.mix(base, 0xFF000000 | PingIcons.COLORS[PingIcons.PINGS + i], 0.55f) : Ui.alpha(th.panel2() & 0xFFFFFF, 0.9f * ease);
            sector(m, cx, cy, INNER_IN * k, (INNER_OUT + (h ? 5 : 0)) * k, a0, a1, fill);
        }

        for (int i = 0; i < PingIcons.PINGS; i++) {
            double ang = i * Math.PI / 4 - Math.PI / 2;
            double rad = (OUTER_IN + OUTER_OUT) / 2.0 * k;
            int scale = hover == i ? 4 : 3;
            int ix = (int) (cx + Math.cos(ang) * rad) - PingIcons.SIZE * scale / 2;
            int iy = (int) (cy + Math.sin(ang) * rad) - PingIcons.SIZE * scale / 2;
            PingIcons.draw(ctx, i, ix, iy, scale, Ui.alpha(PingIcons.COLORS[i], ease));
        }
        for (int i = 0; i < 4; i++) {
            double ang = i * Math.PI / 2 - Math.PI / 2;
            double rad = (INNER_IN + INNER_OUT) / 2.0 * k;
            int scale = hover == PingIcons.PINGS + i ? 3 : 2;
            int ix = (int) (cx + Math.cos(ang) * rad) - PingIcons.SIZE * scale / 2;
            int iy = (int) (cy + Math.sin(ang) * rad) - PingIcons.SIZE * scale / 2;
            PingIcons.draw(ctx, PingIcons.PINGS + i, ix, iy, scale, Ui.alpha(PingIcons.COLORS[PingIcons.PINGS + i], ease));
        }

        String label = hover >= 0 ? PingIcons.NAMES[hover] : (target == null ? "Aim at something" : "Release on a mark");
        int labelColor = hover >= 0 ? PingIcons.COLORS[hover] : th.dim();
        Ui.textCentered(ctx, textRenderer, label, (int) cx, (int) (cy + OUTER_OUT + 22), Ui.alpha(labelColor & 0xFFFFFF, ease));
        if (hover >= 0 && hover < PingIcons.PINGS && target == null) {
            Ui.textCentered(ctx, textRenderer, "no block or entity under the crosshair", (int) cx, (int) (cy + OUTER_OUT + 36), th.dim());
        }
        if (NeuroCanvasClient.modules() != null && !NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class).connected()) {
            Ui.textCentered(ctx, textRenderer, "Others cannot see your pings: turn NameTags on", (int) cx, (int) (cy - OUTER_OUT - 22), th.dim());
        }
    }
}
