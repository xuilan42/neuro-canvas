package com.example.neurocanvas.gui;

import com.example.neurocanvas.ai.AiConfig;
import com.example.neurocanvas.ai.AiPlan;
import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.module.NeuroAura;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** The NeuroAura tab: a chat with the AI, the list of AI effects, and the connection form. */
public final class AiPanel {
    private enum View { CHAT, CONNECTION }

    private record Btn(String label, int x, int y, int w, int h, Runnable action, boolean primary, boolean enabled) { }

    private record Row(int x, int y, int w, int h, Runnable left, Runnable right, boolean scrolled) { }

    /** A chat bubble after layout: wrapped text, the plan lines to show, and its size and position. */
    private record Item(NeuroAura.ChatMessage msg, List<String> lines, List<AiPlan.Line> plan, int more, int w, int h,
                        int y, boolean buttons, boolean statusLine) { }

    private static final String[] IDEAS = {
        "красный туман и вечернее небо",
        "сердечки при ударе и красное кольцо на цели",
        "минималистичный белый прицел",
        "северное сияние и ночь",
        "убери туман и сделай день",
        "кибер-стиль: неон, фиолетовый и голубой",
    };

    private final TextBox input = new TextBox("Write to NeuroAura (Enter to send)", 400, false);
    private final TextBox url = new TextBox("https://api.openai.com/v1", 200, false);
    private final TextBox key = new TextBox("API key", 300, true);
    private final TextBox model = new TextBox("gpt-4o-mini", 100, false);
    private final TextBox voice = new TextBox("whisper-1", 100, false);
    private final List<Btn> buttons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<TextBox> boxes = new ArrayList<>();
    private View view = View.CHAT;
    private String note = "";
    private double scroll;
    private boolean stick = true;
    private int viewTop;
    private int viewBottom;
    private int maxScroll;
    private int lastMessageCount;

    private NeuroAura aura() {
        return NeuroCanvasClient.modules().get(NeuroAura.class);
    }

    private TextBox focused() {
        for (TextBox b : boxes) {
            if (b.focused) {
                return b;
            }
        }
        return null;
    }

    private void unfocusAll() {
        input.focused = false;
        url.focused = false;
        key.focused = false;
        model.focused = false;
        voice.focused = false;
    }

    private void show(View next) {
        view = next;
        scroll = 0;
        stick = next == View.CHAT;
        unfocusAll();
        note = "";
    }

    // ---- input ----------------------------------------------------------------------------

    public boolean charTyped(char c) {
        TextBox f = focused();
        if (f == null) {
            return false;
        }
        f.type(c);
        return true;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        TextBox f = focused();
        if (f == null) {
            return false;
        }
        boolean ctrl = (modifiers & 2) != 0;
        if (keyCode == 256) {
            f.focused = false;
        } else if (keyCode == 257 || keyCode == 335) {
            if (f == input) {
                send();
            } else {
                f.focused = false;
            }
        } else if (keyCode == 259) {
            f.backspace(ctrl);
        } else if (keyCode == 86 && ctrl) {
            f.paste();
        }
        return true;
    }

    public void scroll(double amount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - amount * 24));
        stick = scroll >= maxScroll - 1;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        for (TextBox b : boxes) {
            if (b.contains(mx, my)) {
                unfocusAll();
                b.focused = true;
                return true;
            }
        }
        for (Btn b : buttons) {
            if (b.enabled() && mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                b.action().run();
                return true;
            }
        }
        for (Row r : rows) {
            boolean inView = !r.scrolled() || (my >= viewTop && my < viewBottom);
            if (inView && mx >= r.x() && mx < r.x() + r.w() && my >= r.y() && my < r.y() + r.h()) {
                Runnable action = button == 1 ? r.right() : r.left();
                if (action != null) {
                    action.run();
                }
                return true;
            }
        }
        unfocusAll();
        return false;
    }

    private void send() {
        String text = input.text().trim();
        if (!text.isEmpty() && !aura().working()) {
            input.set("");
            stick = true;
            aura().send(text);
        }
    }

    private void toggleAutoApply() {
        AiConfig c = aura().config();
        c.autoApply = !c.autoApply;
        try {
            c.save();
        } catch (IOException ignored) {
            // The switch still works for this session if the file cannot be written.
        }
    }

    private void openConnection() {
        AiConfig c = aura().config();
        url.set(c.baseUrl);
        key.set(c.apiKey);
        model.set(c.model);
        voice.set(c.voiceModel);
        show(View.CONNECTION);
    }

    private void saveConnection() {
        AiConfig c = aura().config();
        c.baseUrl = url.text().trim();
        c.apiKey = key.text().trim();
        c.model = model.text().trim();
        c.voiceModel = voice.text().trim().isEmpty() ? "whisper-1" : voice.text().trim();
        try {
            c.save();
            note = c.ready() ? "Saved." : "Saved, but the key is still missing.";
        } catch (IOException e) {
            note = "Could not save: " + e.getMessage();
        }
    }

    // ---- left column: ideas ---------------------------------------------------------------

    public void renderHistory(DrawContext ctx, Theme th, TextRenderer tr, int x, int y, int w, int h, int mx, int my) {
        rows.clear();
        Ui.text(ctx, tr, "Ideas (click to use)", x + 4, y + 2, th.dim());
        int cy = y + 18;
        for (String idea : IDEAS) {
            List<String> lines = Ui.wrap(tr, idea, w - 16);
            int rh = lines.size() * 12 + 10;
            if (cy + rh > y + h) {
                break;
            }
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + rh;
            Ui.roundRect(ctx, x, cy, w, rh, 6, hover ? th.hover() : th.panel());
            int ty = cy + 5;
            for (String line : lines) {
                Ui.text(ctx, tr, line, x + 8, ty, th.text());
                ty += 12;
            }
            rows.add(new Row(x, cy, w, rh, () -> {
                show(View.CHAT);
                input.set(idea);
                unfocusAll();
                input.focused = true;
            }, null, false));
            cy += rh + 4;
        }
    }

    // ---- main panel -----------------------------------------------------------------------

    public void renderMain(DrawContext ctx, Theme th, TextRenderer tr, int x, int y, int w, int h, int mx, int my) {
        buttons.clear();
        boxes.clear();
        Ui.roundRect(ctx, x, y, w, h, 8, th.panel());
        switch (view) {
            case CONNECTION -> renderConnection(ctx, th, tr, x, y, w, h);
            default -> renderChat(ctx, th, tr, x, y, w, h, mx, my);
        }
        for (Btn b : buttons) {
            drawButton(ctx, th, tr, b, mx, my);
        }
    }

    private void drawButton(DrawContext ctx, Theme th, TextRenderer tr, Btn b, int mx, int my) {
        boolean hover = b.enabled() && mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h();
        int bg = !b.enabled() ? th.panel2() : b.primary() ? (hover ? th.accent2() : th.accent()) : (hover ? th.hover() : th.panel2());
        Ui.roundRect(ctx, b.x(), b.y(), b.w(), b.h(), 6, bg);
        int text = !b.enabled() ? th.dim() : b.primary() ? (th.bg() | 0xFF000000) : th.text();
        Ui.textCentered(ctx, tr, b.label(), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, text);
    }

    private void button(String label, int x, int y, int w, int h, Runnable action, boolean primary, boolean enabled) {
        buttons.add(new Btn(label, x, y, w, h, action, primary, enabled));
    }

    private void header(DrawContext ctx, Theme th, TextRenderer tr, int x, int y, int w, String title) {
        Ui.text(ctx, tr, title, x + 12, y + 11, th.text());
    }

    private void renderChat(DrawContext ctx, Theme th, TextRenderer tr, int x, int y, int w, int h, int mx, int my) {
        NeuroAura a = aura();
        AiConfig cfg = a.config();
        header(ctx, th, tr, x, y, w, "NeuroAura");
        int bx = x + w - 12;
        bx -= 86;
        button("Connection", bx, y + 8, 86, 20, this::openConnection, false, true);

        int sy = y + 34;
        boolean auto = cfg.autoApply;
        button(auto ? "Auto-apply: ON" : "Auto-apply: OFF", x + 12, sy, 112, 18, this::toggleAutoApply, auto, true);
        Ui.text(ctx, tr, cfg.ready() ? cfg.model : "Not configured: open Connection", x + 132, sy + 5,
                cfg.ready() ? th.dim() : 0xFFFFC040);
        button("Clear", x + w - 12 - 54, sy, 54, 18, () -> {
            a.clearChat();
            stick = true;
        }, false, !a.chat().isEmpty());

        int inputY = y + h - 34;
        boxes.add(input);
        boolean busy = a.working();
        int inputW = w - 24 - 70 - 84;
        input.draw(ctx, tr, th, x + 12, inputY, inputW, 24);
        boolean rec = a.recording();
        button("Img", x + 12 + inputW + 4, inputY, 38, 24, a::attachFromClipboardOrScreenshot, a.attached(), !rec);
        String micLabel = a.transcribing() ? "..." : rec ? ("\u25CF" + a.recordingSeconds() + "s") : "Mic";
        button(micLabel, x + 12 + inputW + 46, inputY, 38, 24, () -> a.toggleVoice(text -> {
            input.set(input.text().isEmpty() ? text : input.text() + " " + text);
            unfocusAll();
            input.focused = true;
        }), rec, !a.transcribing());
        button(busy ? "..." : "Send", x + w - 12 - 62, inputY, 62, 24, this::send, true, !busy && !input.text().isBlank());
        int chipH = 0;
        if (a.attached()) {
            chipH = 18;
            String chip = "Picture: " + trim(tr, a.attachedName(), w - 90);
            Ui.roundRect(ctx, x + 12, inputY - 20, Ui.width(tr, chip) + 30, 16, 6, th.panel2());
            Ui.text(ctx, tr, chip, x + 20, inputY - 16, th.text());
            int cx = x + 12 + Ui.width(tr, chip) + 14;
            boolean xh = mx >= cx && mx < cx + 14 && my >= inputY - 20 && my < inputY - 4;
            Ui.text(ctx, tr, "x", cx + 3, inputY - 16, xh ? 0xFFFF6B6B : th.dim());
            rows.add(new Row(cx, inputY - 20, 14, 16, a::clearAttachment, null, false));
        }

        viewTop = y + 60;
        viewBottom = inputY - 8 - chipH;
        int innerW = w - 24;
        List<Item> items = layout(a, tr, innerW);
        int contentH = items.isEmpty() ? 0 : items.get(items.size() - 1).y() + items.get(items.size() - 1).h() + 8;
        if (busy) {
            contentH += 30;
        }
        maxScroll = Math.max(0, contentH - (viewBottom - viewTop));
        if (a.chat().size() != lastMessageCount) {
            lastMessageCount = a.chat().size();
            if (stick) {
                scroll = maxScroll;
            }
        }
        if (stick || busy && scroll >= maxScroll - 40) {
            scroll = maxScroll;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(x, viewTop, x + w, viewBottom);
        if (items.isEmpty() && !busy) {
            int ty = viewTop + 6;
            for (String line : Ui.wrap(tr, "Say what you want and I will set it up: \"red fog and hearts when I hit\", "
                    + "\"make the crosshair white\", \"brighter\". You can also just ask me things.", innerW)) {
                Ui.text(ctx, tr, line, x + 12, ty, th.dim());
                ty += 12;
            }
        }
        for (Item it : items) {
            drawItem(ctx, th, tr, a, it, x, innerW, mx, my);
        }
        if (busy) {
            int dots = (int) (System.currentTimeMillis() / 350 % 4);
            int by = viewTop + contentH - 30 - (int) scroll + 4;
            Ui.roundRect(ctx, x + 12, by, 74, 22, 8, th.panel2());
            Ui.text(ctx, tr, "Thinking" + ".".repeat(dots), x + 20, by + 7, th.accent());
        }
        ctx.disableScissor();
    }

    private List<Item> layout(NeuroAura a, TextRenderer tr, int innerW) {
        List<Item> items = new ArrayList<>();
        int maxBubble = (int) (innerW * 0.84);
        int y = 4;
        for (NeuroAura.ChatMessage m : a.chat()) {
            List<String> lines = Ui.wrap(tr, m.text, maxBubble - 16);
            int textW = 0;
            for (String l : lines) {
                textW = Math.max(textW, Ui.width(tr, l));
            }
            List<AiPlan.Line> plan = new ArrayList<>();
            int more = 0;
            int planW = 0;
            if (m.plan != null) {
                List<AiPlan.Line> all = m.plan.describe();
                for (int i = 0; i < all.size(); i++) {
                    if (i < 9) {
                        plan.add(all.get(i));
                        planW = Math.max(planW, Ui.width(tr, all.get(i).text()));
                    } else {
                        more++;
                    }
                }
            }
            boolean buttons = m.plan != null && (m.status == NeuroAura.Status.PENDING || a.canUndo(m));
            boolean statusLine = m.plan != null && !buttons;
            int w = m.fromUser ? textW + 16 : Math.max(textW, planW) + 16;
            if (buttons) {
                w = Math.max(w, 190);
            }
            w = Math.max(40, Math.min(w, maxBubble));
            int hgt = 8 + lines.size() * 12 + 8;
            if (!plan.isEmpty()) {
                hgt += 6 + plan.size() * 12 + (more > 0 ? 12 : 0);
            }
            if (buttons) {
                hgt += 8 + 22;
            } else if (statusLine) {
                hgt += 6 + 12;
            }
            items.add(new Item(m, lines, plan, more, w, hgt, y, buttons, statusLine));
            y += hgt + 8;
        }
        return items;
    }

    private void drawItem(DrawContext ctx, Theme th, TextRenderer tr, NeuroAura a, Item it, int x, int innerW, int mx, int my) {
        NeuroAura.ChatMessage m = it.msg();
        int by = viewTop + it.y() - (int) scroll;
        if (by + it.h() < viewTop || by > viewBottom) {
            return;
        }
        int bx = m.fromUser ? x + 12 + innerW - it.w() : x + 12;
        int bg = m.fromUser ? Ui.mix(th.panel2() | 0xFF000000, th.accent(), 0.28f) : th.panel2();
        Ui.roundRect(ctx, bx, by, it.w(), it.h(), 8, bg);
        int textColor = m.error ? 0xFFFF6B6B : th.text();
        int ty = by + 8;
        for (String line : it.lines()) {
            Ui.text(ctx, tr, line, bx + 8, ty, textColor);
            ty += 12;
        }
        if (!it.plan().isEmpty()) {
            ty += 6;
            for (AiPlan.Line l : it.plan()) {
                int color = switch (l.kind()) {
                    case 0 -> th.text();
                    case 2 -> th.accent2();
                    case 3 -> 0xFFFFC040;
                    default -> th.dim();
                };
                Ui.text(ctx, tr, trim(tr, l.text(), it.w() - 16), bx + 8, ty, color);
                ty += 12;
            }
            if (it.more() > 0) {
                Ui.text(ctx, tr, "+" + it.more() + " more", bx + 8, ty, th.dim());
                ty += 12;
            }
        }
        if (it.buttons()) {
            ty += 8;
            if (m.status == NeuroAura.Status.PENDING) {
                inlineButton(ctx, th, tr, "Apply", bx + 8, ty, 80, true, () -> a.apply(m), mx, my);
                inlineButton(ctx, th, tr, "Dismiss", bx + 96, ty, 80, false, () -> a.dismiss(m), mx, my);
            } else {
                inlineButton(ctx, th, tr, "Undo", bx + 8, ty, 80, false, () -> a.undo(m), mx, my);
                Ui.text(ctx, tr, trim(tr, "saved as \"" + m.preset + "\"", it.w() - 108), bx + 96, ty + 7, th.dim());
            }
        } else if (it.statusLine()) {
            String status = switch (m.status) {
                case APPLIED -> "applied, saved as \"" + m.preset + "\"";
                case UNDONE -> "undone";
                case DISMISSED -> "dismissed";
                default -> "";
            };
            Ui.text(ctx, tr, trim(tr, status, it.w() - 16), bx + 8, ty + 6, th.dim());
        }
    }

    private void inlineButton(DrawContext ctx, Theme th, TextRenderer tr, String label, int x, int y, int w, boolean primary,
                              Runnable action, int mx, int my) {
        boolean hover = my >= viewTop && my < viewBottom && mx >= x && mx < x + w && my >= y && my < y + 22;
        int bg = primary ? (hover ? th.accent2() : th.accent()) : (hover ? th.hover() : th.panel());
        Ui.roundRect(ctx, x, y, w, 22, 6, bg);
        Ui.textCentered(ctx, tr, label, x + w / 2, y + 7, primary ? (th.bg() | 0xFF000000) : th.text());
        rows.add(new Row(x, y, w, 22, action, null, true));
    }

    // ---- connection form ------------------------------------------------------------------

    private void renderConnection(DrawContext ctx, Theme th, TextRenderer tr, int x, int y, int w, int h) {
        header(ctx, th, tr, x, y, w, "Connection");
        Ui.text(ctx, tr, "Any OpenAI-compatible server (OpenAI, OpenRouter, Groq, Ollama...)", x + 12, y + 26, th.dim());
        int bx = x + 12;
        int bw = w - 24;
        int cy = y + 48;
        Ui.text(ctx, tr, "API address", bx, cy, th.text());
        boxes.add(url);
        url.draw(ctx, tr, th, bx, cy + 12, bw, 22);
        cy += 44;
        Ui.text(ctx, tr, "API key (not needed for a server on your own computer)", bx, cy, th.text());
        boxes.add(key);
        key.draw(ctx, tr, th, bx, cy + 12, bw, 22);
        cy += 44;
        Ui.text(ctx, tr, "Model name (to attach pictures it must understand images)", bx, cy, th.text());
        boxes.add(model);
        model.draw(ctx, tr, th, bx, cy + 12, bw, 22);
        cy += 44;
        Ui.text(ctx, tr, "Speech to text model (the Mic button; needs /audio/transcriptions on the same server)", bx, cy, th.text());
        boxes.add(voice);
        voice.draw(ctx, tr, th, bx, cy + 12, bw, 22);
        cy += 44;
        button("Save", bx, cy, 80, 22, this::saveConnection, true, true);
        button("Back", bx + 88, cy, 80, 22, () -> show(View.CHAT), false, true);
        if (!note.isEmpty()) {
            Ui.text(ctx, tr, note, bx + 180, cy + 7, th.dim());
        }
        int ny = cy + 34;
        for (String line : Ui.wrap(tr, "The key is stored in neurocanvas/ai.json and is never included in presets or share codes.", bw)) {
            Ui.text(ctx, tr, line, bx, ny, th.dim());
            ny += 12;
        }
    }

    private static String trim(TextRenderer tr, String s, int maxW) {
        String out = s;
        while (out.length() > 1 && Ui.width(tr, out) > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out.length() < s.length() ? out + "..." : out;
    }
}
