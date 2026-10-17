package com.example.neurocanvas.gui;

import java.util.function.IntPredicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A single-line text field: a caret, a selection (shift and arrows, the mouse, double click for a word, Ctrl+A), Ctrl+C, X and V,
 * Home, End, Delete, and Ctrl with the arrows or Backspace for whole words. The menu feeds it characters, key presses and mouse events.
 */
public final class TextBox {
    private static final int LEFT = 263, RIGHT = 262, HOME = 268, END = 269, BACKSPACE = 259, DELETE = 261;

    private final String placeholder;
    private final int maxLength;
    private final boolean masked;
    private IntPredicate allowed = c -> c >= 32;
    private boolean lower;
    private String text = "";
    private int caret;
    private int anchor;
    private float scrollX;
    private boolean dragging;
    private long lastClick;
    private int clickCount;
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

    /** Only characters accepted by the filter are typed or pasted; {@code lowercase} folds letters to lower case. */
    public TextBox allow(IntPredicate filter, boolean lowercase) {
        this.allowed = filter;
        this.lower = lowercase;
        return this;
    }

    public String text() {
        return text;
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public void set(String value) {
        text = value == null ? "" : value;
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        caret = text.length();
        anchor = caret;
        scrollX = 0;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public String placeholder() {
        return placeholder;
    }

    // ---- editing --------------------------------------------------------------------------------

    private boolean hasSelection() {
        return caret != anchor;
    }

    private int selStart() {
        return Math.min(caret, anchor);
    }

    private int selEnd() {
        return Math.max(caret, anchor);
    }

    public String selected() {
        return hasSelection() ? text.substring(selStart(), selEnd()) : "";
    }

    private void deleteSelection() {
        if (hasSelection()) {
            text = text.substring(0, selStart()) + text.substring(selEnd());
            caret = selStart();
            anchor = caret;
        }
    }

    private String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') {
                c = ' ';
            }
            if (allowed.test(c)) {
                b.append(lower ? Character.toLowerCase(c) : c);
            }
        }
        return b.toString();
    }

    private void insert(String s) {
        deleteSelection();
        String c = clean(s);
        int room = maxLength - text.length();
        if (c.length() > room) {
            c = c.substring(0, Math.max(0, room));
        }
        text = text.substring(0, caret) + c + text.substring(caret);
        caret += c.length();
        anchor = caret;
    }

    public void type(char c) {
        insert(String.valueOf(c));
    }

    public void paste() {
        String clip = MinecraftClient.getInstance().keyboard.getClipboard();
        if (clip != null) {
            insert(clip.trim());
        }
    }

    public void copy() {
        if (hasSelection() && !masked) {
            MinecraftClient.getInstance().keyboard.setClipboard(selected());
        }
    }

    public void cut() {
        if (hasSelection() && !masked) {
            copy();
            deleteSelection();
        }
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    private int wordLeft(int from) {
        int i = from;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return i;
    }

    private int wordRight(int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        return i;
    }

    /** Editing keys. Returns true when the key was one of ours; Enter and Escape are left to the caller. */
    public boolean key(int keyCode, int modifiers) {
        boolean ctrl = (modifiers & 2) != 0;
        boolean shift = (modifiers & 1) != 0;
        switch (keyCode) {
            case 65 -> {
                if (!ctrl) {
                    return false;
                }
                selectAll();
            }
            case 67 -> {
                if (!ctrl) {
                    return false;
                }
                copy();
            }
            case 88 -> {
                if (!ctrl) {
                    return false;
                }
                cut();
            }
            case 86 -> {
                if (!ctrl) {
                    return false;
                }
                paste();
            }
            case BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (caret > 0) {
                    int from = ctrl ? wordLeft(caret) : caret - 1;
                    text = text.substring(0, from) + text.substring(caret);
                    caret = from;
                    anchor = caret;
                }
            }
            case DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (caret < text.length()) {
                    int to = ctrl ? wordRight(caret) : caret + 1;
                    text = text.substring(0, caret) + text.substring(to);
                }
            }
            case LEFT -> move(hasSelection() && !shift ? selStart() : ctrl ? wordLeft(caret) : Math.max(0, caret - 1), shift);
            case RIGHT -> move(hasSelection() && !shift ? selEnd() : ctrl ? wordRight(caret) : Math.min(text.length(), caret + 1), shift);
            case HOME -> move(0, shift);
            case END -> move(text.length(), shift);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void move(int to, boolean extend) {
        caret = to;
        if (!extend) {
            anchor = caret;
        }
    }

    // ---- mouse ----------------------------------------------------------------------------------

    private String shown() {
        return masked ? "*".repeat(text.length()) : text;
    }

    /** The character boundary nearest to a screen x. */
    private int indexAt(TextRenderer tr, double mx) {
        String s = shown();
        double rel = mx - (x + 8) + scrollX;
        int prev = 0;
        for (int i = 1; i <= s.length(); i++) {
            int cur = Ui.width(tr, s.substring(0, i));
            if (rel < (prev + cur) / 2.0) {
                return i - 1;
            }
            prev = cur;
        }
        return s.length();
    }

    /** A click inside the field: puts the caret there, shift extends the selection, a double click selects a word, a triple one all. */
    public void click(TextRenderer tr, double mx, boolean shift) {
        focused = true;
        long now = System.currentTimeMillis();
        clickCount = now - lastClick < 400 ? clickCount + 1 : 1;
        lastClick = now;
        int at = indexAt(tr, mx);
        if (clickCount >= 3) {
            selectAll();
        } else if (clickCount == 2) {
            int s = at;
            while (s > 0 && text.charAt(s - 1) != ' ') {
                s--;
            }
            int e = at;
            while (e < text.length() && text.charAt(e) != ' ') {
                e++;
            }
            anchor = s;
            caret = e;
        } else {
            caret = at;
            if (!shift) {
                anchor = at;
            }
            dragging = true;
        }
    }

    public void dragTo(TextRenderer tr, double mx) {
        if (dragging) {
            caret = indexAt(tr, mx);
        }
    }

    public void release() {
        dragging = false;
    }

    public boolean dragging() {
        return dragging;
    }

    // ---- drawing --------------------------------------------------------------------------------

    /** The text, selection and caret inside a box that the caller has already drawn. */
    public void drawText(DrawContext ctx, TextRenderer tr, Theme th, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        String s = shown();
        int ty = y + (h - 8) / 2;
        if (s.isEmpty() && !focused) {
            Ui.text(ctx, tr, Ui.fit(tr, placeholder, w - 16), x + 8, ty, th.dim());
            return;
        }
        int inner = w - 16;
        int caretPx = Ui.width(tr, s.substring(0, Math.min(caret, s.length())));
        if (caretPx - scrollX > inner - 2) {
            scrollX = caretPx - inner + 2;
        }
        if (caretPx - scrollX < 0) {
            scrollX = caretPx;
        }
        if (Ui.width(tr, s) <= inner) {
            scrollX = 0;
        }
        ctx.enableScissor(x + 8, y, x + w - 8, y + h);
        if (hasSelection()) {
            int a = Ui.width(tr, s.substring(0, selStart()));
            int b = Ui.width(tr, s.substring(0, Math.min(selEnd(), s.length())));
            Ui.roundRect(ctx, Math.round(x + 8 + a - scrollX), ty - 2, Math.max(2, b - a), 12, 3, Ui.alpha(th.accent() & 0xFFFFFF, 0.45f));
        }
        Ui.text(ctx, tr, s, Math.round(x + 8 - scrollX), ty, th.text());
        if (focused && System.currentTimeMillis() / 500 % 2 == 0) {
            int cx = Math.round(x + 8 + caretPx - scrollX);
            ctx.fill(cx, ty - 2, cx + 1, ty + 10, th.text());
        }
        ctx.disableScissor();
    }

    /** A complete field with its own background, as the AI panel uses it. */
    public void draw(DrawContext ctx, TextRenderer tr, Theme th, int x, int y, int w, int h) {
        if (focused) {
            Ui.roundRect(ctx, x - 1, y - 1, w + 2, h + 2, 7, th.accent());
        }
        Ui.roundRect(ctx, x, y, w, h, 6, th.panel2());
        drawText(ctx, tr, th, x, y, w, h);
    }
}
