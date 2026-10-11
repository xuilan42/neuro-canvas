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

    private int resetX() { return width / 2 - 60; }
    private int resetY() { return height - 40; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Theme th = Interface.theme();
        ctx.fill(0, 0, width, height, Ui.alpha(0x000000, 0.55f));
        Ui.textCentered(ctx, textRenderer, "Drag the elements. Esc to finish.", width / 2, 14, th.text());

        for (HudLayout.Element e : HudLayout.all()) {
            int x = e.x(width);
            int y = e.y(height);
            boolean hover = inside(mouseX, mouseY, x, y, e.w, e.h) || e == dragging;
            Ui.roundRect(ctx, x - 2, y - 2, e.w + 4, e.h + 4, 5, hover ? th.accent() : Ui.alpha(0xFFFFFF, 0.35f));
            Ui.roundRect(ctx, x - 1, y - 1, e.w + 2, e.h + 2, 4, 0xFF000000 | (th.panel() & 0xFFFFFF));
            Ui.textCentered(ctx, textRenderer, e.label, x + e.w / 2, y + Math.max(0, (Math.min(e.h, 14) - 8) / 2 + 1), th.text());
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
            int x = e.x(width);
            int y = e.y(height);
            if (inside(mx, my, x, y, e.w, e.h)) {
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
            dragging.setFromTopLeft((int) mx - grabX, (int) my - grabY, width, height);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
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
