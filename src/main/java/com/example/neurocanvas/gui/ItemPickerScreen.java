package com.example.neurocanvas.gui;

import com.example.neurocanvas.module.Interface;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** A short list with a tick box in front of every line: pick what to act on, then confirm. Used for sharing and unsharing cosmetics. */
public final class ItemPickerScreen extends Screen {
    public record Row(String name, String note, boolean checked) { }

    private static final int ROW_H = 24;
    private final Screen parent;
    private final String heading;
    private final String hint;
    private final String confirm;
    private final List<Row> rows;
    private final boolean[] on;
    private final Consumer<List<String>> action;
    private double scroll;

    public ItemPickerScreen(Screen parent, String heading, String hint, String confirm, List<Row> rows, Consumer<List<String>> action) {
        super(Text.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.hint = hint;
        this.confirm = confirm;
        this.rows = rows;
        this.action = action;
        this.on = new boolean[rows.size()];
        for (int i = 0; i < on.length; i++) {
            on[i] = rows.get(i).checked();
        }
    }

    private int pw() { return Math.min(360, width - 40); }
    private int px() { return (width - pw()) / 2; }
    private int ph() { return Math.min(height - 40, 90 + rows.size() * ROW_H + 8); }
    private int py() { return (height - ph()) / 2; }
    private int listTop() { return py() + 52; }
    private int listH() { return ph() - 52 - 44; }
    private int btnY() { return py() + ph() - 34; }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int chosen() {
        int n = 0;
        for (boolean b : on) {
            n += b ? 1 : 0;
        }
        return n;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        Theme th = Interface.theme();
        Ui.surface(ctx, px(), py(), pw(), ph(), 10, th.panel());
        Ui.text(ctx, textRenderer, heading, px() + 14, py() + 12, th.text());
        Ui.text(ctx, textRenderer, textRenderer.trimToWidth(hint, pw() - 28), px() + 14, py() + 28, th.dim());
        double max = Math.max(0, rows.size() * ROW_H - listH());
        scroll = Math.max(0, Math.min(max, scroll));
        ctx.enableScissor(px(), listTop(), px() + pw(), listTop() + listH());
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop() + i * ROW_H - (int) scroll;
            if (y + ROW_H < listTop() || y > listTop() + listH()) {
                continue;
            }
            boolean hover = inside(mx, my, px() + 8, y, pw() - 16, ROW_H - 2) && my >= listTop() && my < listTop() + listH();
            Ui.roundRect(ctx, px() + 8, y, pw() - 16, ROW_H - 2, 6, hover ? th.hover() : th.panel2());
            int bx = px() + 16;
            int by = y + (ROW_H - 2 - 12) / 2;
            Ui.roundRect(ctx, bx, by, 12, 12, 3, on[i] ? th.accent() : Ui.alpha(0xFFFFFF, 0.12f));
            if (on[i]) {
                Ui.textCentered(ctx, textRenderer, "x", bx + 6, by + 2, th.bg() | 0xFF000000);
            }
            Row r = rows.get(i);
            Ui.textMid(ctx, textRenderer, textRenderer.trimToWidth(r.name(), pw() - 130), bx + 20, y, ROW_H - 2, th.text());
            if (!r.note().isEmpty()) {
                int nw = Ui.width(textRenderer, r.note());
                Ui.textMid(ctx, textRenderer, r.note(), px() + pw() - 16 - nw, y, ROW_H - 2, th.dim());
            }
        }
        if (rows.isEmpty()) {
            Ui.text(ctx, textRenderer, "Nothing to show", px() + 16, listTop() + 8, th.dim());
        }
        ctx.disableScissor();
        int bw = (pw() - 14 * 2 - 8) / 2;
        boolean cHover = inside(mx, my, px() + 14, btnY(), bw, 24);
        boolean okHover = inside(mx, my, px() + 14 + bw + 8, btnY(), bw, 24);
        Ui.roundRect(ctx, px() + 14, btnY(), bw, 24, 7, cHover ? th.hover() : th.panel2());
        Ui.textCentered(ctx, textRenderer, "Cancel", px() + 14 + bw / 2, btnY() + 8, th.text());
        int n = chosen();
        Ui.roundRect(ctx, px() + 14 + bw + 8, btnY(), bw, 24, 7, n == 0 ? th.panel2() : (okHover ? th.accent2() : th.accent()));
        Ui.textCentered(ctx, textRenderer, confirm + (n > 0 ? " (" + n + ")" : ""), px() + 14 + bw + 8 + bw / 2, btnY() + 8,
                n == 0 ? th.dim() : (th.bg() | 0xFF000000));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bw = (pw() - 14 * 2 - 8) / 2;
        if (inside((int) mx, (int) my, px() + 14, btnY(), bw, 24)) {
            close();
            return true;
        }
        if (inside((int) mx, (int) my, px() + 14 + bw + 8, btnY(), bw, 24)) {
            if (chosen() > 0) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < on.length; i++) {
                    if (on[i]) {
                        names.add(rows.get(i).name());
                    }
                }
                client.setScreen(parent);
                action.accept(names);
            }
            return true;
        }
        if (my >= listTop() && my < listTop() + listH()) {
            int i = (int) ((my - listTop() + scroll) / ROW_H);
            if (i >= 0 && i < rows.size() && inside((int) mx, (int) my, px() + 8, listTop() + i * ROW_H - (int) scroll, pw() - 16, ROW_H - 2)) {
                on[i] = !on[i];
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll -= amount * 20;
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
