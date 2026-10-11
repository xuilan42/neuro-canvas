package com.example.neurocanvas.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** A single-line text field with paste support. Typing always appends at the end. */
public final class TextBox {
    private final String placeholder;
    private final int maxLength;
    private final boolean masked;
    private String text = "";
    public boolean focused;
    private int x;
    private int y;
    private int w;
    private int h;

    public TextBox(String placeholder, int maxLength, boolean masked) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.masked = masked;
    }

    public String text() {
        return text;
    }

    public void set(String value) {
        text = value == null ? "" : value;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void type(char c) {
        if (c >= 32 && text.length() < maxLength) {
            text += c;
        }
    }

    public void paste() {
        String clip = MinecraftClient.getInstance().keyboard.getClipboard();
        if (clip == null) {
            return;
        }
        String clean = clip.replace('\r', ' ').replace('\n', ' ').trim();
        text = (text + clean).substring(0, Math.min(maxLength, text.length() + clean.length()));
    }

    public void backspace(boolean whole) {
        if (whole) {
            text = "";
        } else if (!text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
        }
    }

    public void draw(DrawContext ctx, TextRenderer tr, Theme th, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        if (focused) {
            Ui.roundRect(ctx, x - 1, y - 1, w + 2, h + 2, 7, th.accent());
        }
        Ui.roundRect(ctx, x, y, w, h, 6, th.panel2());
        String shown = masked ? "*".repeat(text.length()) : text;
        boolean empty = shown.isEmpty();
        if (empty) {
            shown = placeholder;
        } else if (focused && System.currentTimeMillis() / 500 % 2 == 0) {
            shown += "|";
        }
        while (shown.length() > 1 && Ui.width(tr, shown) > w - 16) {
            shown = shown.substring(1);
        }
        Ui.text(ctx, tr, shown, x + 8, y + (h - 8) / 2, empty ? th.dim() : th.text());
    }
}
