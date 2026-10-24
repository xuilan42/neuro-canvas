package com.example.neurocanvas.gui;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.HudLayout;
import com.example.neurocanvas.module.Interface;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Drag HUD elements to where you want them. Esc returns to the menu and saves. */
public final class HudEditor extends Screen {
    private final Screen parent;
    private HudLayout.Element dragging;
    private int grabX;
    private int grabY;

    public HudEditor(Screen parent) {
        super(Text.literal("HUD editor"));
        this.parent = parent;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Elements are laid out on a screen 1/scale as big as the real one and drawn scaled up, so editor positions go through the scale. */
    private static int virtual(int size, HudLayout.Element e) {
        return Math.round(size / e.scale);
    }

    private int realX(HudLayout.Element e) {
        return Math.round(e.x(virtual(width, e)) * e.scale);
    }

    private int realY(HudLayout.Element e) {
        return Math.round(e.y(virtual(height, e)) * e.scale);
    }

    private int resetX() { return width / 2 - 60; }
    private int resetY() { return height - 40; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Theme th = Interface.theme();
        ctx.fill(0, 0, width, height, Ui.alpha(0x000000, 0.55f));
        Ui.textCentered(ctx, textRenderer, "Drag the elements. They stick to the left, centre or right and the top, middle or bottom. Wheel over an element changes its size. Esc to finish.", width / 2, 14, th.text());

        int guide = Ui.alpha(0xFFFFFF, 0.18f);
        ctx.fill(width / 2, 0, width / 2 + 1, height, guide);
        ctx.fill(0, height / 2, width, height / 2 + 1, guide);
        ctx.fill(width / 3, 0, width / 3 + 1, height, guide);
        ctx.fill(width * 2 / 3, 0, width * 2 / 3 + 1, height, guide);
        for (HudLayout.Element e : HudLayout.all()) {
            int x = realX(e);
            int y = realY(e);
            int ew = Math.round(e.w * e.scale);
            int eh = Math.round(e.h * e.scale);
            boolean hover = inside(mouseX, mouseY, x, y, ew, eh) || e == dragging;
            Ui.roundRect(ctx, x - 2, y - 2, ew + 4, eh + 4, 5, hover ? th.accent() : Ui.alpha(0xFFFFFF, 0.35f));
            Ui.roundRect(ctx, x - 1, y - 1, ew + 2, eh + 2, 4, 0xFF000000 | (th.panel() & 0xFFFFFF));
            Ui.textCentered(ctx, textRenderer, e.label, x + ew / 2, y + Math.max(0, (Math.min(eh, 14) - 8) / 2 + 1), th.text());
            if (hover && e.owner != null && e.owner.hasHud()) {
                Ui.textCentered(ctx, textRenderer, "size x" + String.format("%.2f", e.scale) + "  (wheel)", x + ew / 2, y + eh + 6, th.dim());
            }
            if (e == dragging) {
                String[] h = {"left", "centre", "right"};
                String[] v = {"top", "middle", "bottom"};
                Ui.textCentered(ctx, textRenderer, v[e.ay] + " " + h[e.ax], width / 2, 30, th.accent());
            }
        }

        boolean hoverReset = inside(mouseX, mouseY, resetX(), resetY(), 120, 22);
        Ui.roundRect(ctx, resetX(), resetY(), 120, 22, 6, hoverReset ? th.hover() : th.panel2());
        Ui.textCentered(ctx, textRenderer, "Reset positions", resetX() + 60, resetY() + 7, th.text());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inside(mx, my, resetX(), resetY(), 120, 22)) {
            HudLayout.reset();
            return true;
        }
        List<HudLayout.Element> reversed = new ArrayList<>(HudLayout.all());
        java.util.Collections.reverse(reversed);
        for (HudLayout.Element e : reversed) {
            int x = realX(e);
            int y = realY(e);
            if (inside(mx, my, x, y, Math.round(e.w * e.scale), Math.round(e.h * e.scale))) {
                dragging = e;
                grabX = (int) mx - x;
                grabY = (int) my - y;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            float k = dragging.scale;
            dragging.setFromTopLeft(Math.round(((int) mx - grabX) / k), Math.round(((int) my - grabY) / k), virtual(width, dragging), virtual(height, dragging));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        List<HudLayout.Element> reversed = new ArrayList<>(HudLayout.all());
        java.util.Collections.reverse(reversed);
        for (HudLayout.Element e : reversed) {
            if (e.owner != null && e.owner.hasHud()
                    && inside(mx, my, realX(e), realY(e), Math.round(e.w * e.scale), Math.round(e.h * e.scale))) {
                e.owner.adjustHudSize(amount * 0.05);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        try {
            NeuroCanvasClient.modules().save();
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger("neurocanvas").error("Cannot save HUD layout", e);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
