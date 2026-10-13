package com.example.neurocanvas.gui;

import com.example.neurocanvas.client.NeuroCanvasClient;
import com.example.neurocanvas.client.Updater;
import com.example.neurocanvas.module.Category;
import com.example.neurocanvas.module.Interface;
import com.example.neurocanvas.module.Module;
import com.example.neurocanvas.module.ModuleManager;
import com.example.neurocanvas.module.Setting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/**
 * Window with category tabs on top, the module list on the left and the settings of the
 * selected module on the right. Layout is computed from the screen size on every frame and click.
 */
public final class ClickGui extends Screen {
    private static final int W = 580;
    private static final int H = 340;
    private static final int HEADER = 40;
    private static final int PAD = 12;
    private static final int LIST_W = 176;
    private static final int CARD_H = 26;
    private static final int GAP = 4;
    private static final int ROW_H = 26;
    private static final int CTRL_W = 200;
    private static final int PANEL_HEAD = 46;
    private static final int RSHIFT = 344;

    private static final AiPanel AI = new AiPanel();

    // Remembered between openings, so the menu comes back to the tab and module you were on.
    private static Category tab;
    private static Module selected;
    private Setting dragging;
    private static double listScroll;
    private static double settingsScroll;
    private float open;
    private String query = "";
    private boolean searchFocus;
    private Module bindingFor;
    private long lastNano = System.nanoTime();
    private final Map<Module, Float> switchAnim = new HashMap<>();
    private final Map<Module, Float> hoverAnim = new HashMap<>();
    private Category animTab;
    private Module animSelected;
    private float listIn = 1f;
    private float panelIn = 1f;
    private float underlineX = Float.NaN;
    private float underlineW;

    public ClickGui() {
        super(Text.literal("NeuroCanvas"));
    }

    @Override
    protected void init() {
        NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Friends.class).setMenuOpen(true);
        if (tab == null) {
            tab = categories().get(0);
            selected = modulesIn(tab).get(0);
        }
        if (tab == Category.CONFIG) {
            refreshPresets();
        }
        if (!Interface.animations()) {
            open = 1;
        }
    }

    private List<Category> categories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) {
            if (c == Category.CONFIG || !modulesIn(c).isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    private List<Module> modulesIn(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (m.category == c) {
                out.add(m);
            }
        }
        return out;
    }

    private int wx() { return (width - W) / 2; }
    private int wy() { return (height - H) / 2; }
    private int bodyTop() { return wy() + HEADER + 6; }
    private int bodyH() { return H - HEADER - 16; }
    private int listX() { return wx() + PAD; }
    private int panelX() { return wx() + PAD + LIST_W + 10; }
    private int panelW() { return W - 2 * PAD - LIST_W - 10; }
    private int cardY(int i) { return bodyTop() + i * (CARD_H + GAP) - (int) listScroll; }
    private int rowsTop() { return bodyTop() + PANEL_HEAD; }
    private int rowY(int i) { return rowsTop() + i * ROW_H - (int) settingsScroll; }
    private int ctrlX() { return panelX() + panelW() - PAD - CTRL_W; }

    private static final int SEARCH_W = 96;

    private int sbX() { return wx() + W - PAD - SEARCH_W; }
    private int sbY() { return wy() + 9; }

    private List<Module> visible() {
        if (query.isEmpty()) {
            return modulesIn(tab);
        }
        List<Module> out = new ArrayList<>();
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (m.name.toLowerCase().contains(query.toLowerCase())) {
                out.add(m);
            }
        }
        return out;
    }

    private static String keyName(int key) {
        return key < 0 ? "None" : InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
    }

    /** The rows above the settings: the key and its mode, which modules that are always on do not have. */
    private int bindRows() {
        return selected.alwaysOn ? 0 : 2;
    }

    private void renderBindRows(DrawContext ctx, Theme th) {
        int cx = ctrlX();
        int y = rowY(0);
        Ui.textMid(ctx, textRenderer, "Keybind", panelX() + PAD, y, ROW_H, th.text());
        Ui.surface(ctx, cx, y + 3, CTRL_W, ROW_H - 6, 6, th.panel2());
        boolean listening = bindingFor == selected;
        Ui.textCentered(ctx, textRenderer, listening ? "Press a key..." : keyName(selected.key),
                cx + CTRL_W / 2, y + (ROW_H - 8) / 2, listening ? th.accent() : th.text());
        y = rowY(1);
        Ui.textMid(ctx, textRenderer, "Bind mode", panelX() + PAD, y, ROW_H, th.text());
        Ui.surface(ctx, cx, y + 3, CTRL_W, ROW_H - 6, 6, th.panel2());
        Ui.textCentered(ctx, textRenderer, selected.hold ? "Hold" : "Toggle", cx + CTRL_W / 2, y + (ROW_H - 8) / 2, th.text());
    }

    private int tabWidth(Category c) {
        return c == tab ? Icons.SIZE + 5 + Ui.width(textRenderer, c.label) : Icons.SIZE;
    }

    private int tabX(int index, List<Category> cats) {
        int x = wx() + 112;
        for (int i = 0; i < index; i++) {
            x += tabWidth(cats.get(i)) + 18;
        }
        return x;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.setThemed(true);
        try {
            renderMenu(ctx, mouseX, mouseY);
        } finally {
            Ui.setThemed(false);
        }
    }

    private void renderMenu(DrawContext ctx, int mouseX, int mouseY) {
        Theme th = Interface.theme();
        ThemeStyle style = Interface.style();
        boolean fx = Interface.shaderEffects();
        boolean blurOn = fx && style.blur() > 0.01f;
        if (blurOn) {
            GuiFx.captureBlur(client, ctx, style.blur());
        }
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = now;
        boolean anim = Interface.animations();
        open = anim ? Math.min(1f, open + dt * 5f) : 1f;
        if (animTab != tab) {
            animTab = tab;
            listIn = 0f;
            panelIn = 0f;
        }
        if (animSelected != selected) {
            animSelected = selected;
            panelIn = 0f;
        }
        listIn = anim ? Math.min(1f, listIn + dt * 3.2f) : 1f;
        panelIn = anim ? Math.min(1f, panelIn + dt * 5.5f) : 1f;
        float ease = 1f - (float) Math.pow(1f - open, 3);

        drawBackground(ctx, th, ease);
        ctx.getMatrices().push();
        switch (Interface.openAnimation()) {
            case 1 -> scaleAbout(ctx, 0.9f + 0.1f * ease);
            case 2 -> {
                float back = 1f + 1.7f * (float) Math.pow(open - 1f, 3) + 0.7f * (float) Math.pow(open - 1f, 2);
                scaleAbout(ctx, 0.8f + 0.2f * Math.min(1.08f, back));
            }
            default -> ctx.getMatrices().translate(0, (1f - ease) * 18f, 0);
        }

        if (style.shadow()) {
            Ui.shadow(ctx, wx(), wy(), W, H, 10);
        }
        if (blurOn && GuiFx.blurAvailable()) {
            GuiFx.drawBlurred(ctx, wx(), wy(), W, H, 10, 1f);
        }
        Ui.surface(ctx, wx(), wy(), W, H, 10, th.bg());
        Ui.glow(ctx, wx(), wy(), W, H, 10, th.accent(), style.glow(), 14f);
        boolean effects = fx && open >= 1f && style.overlay() != ThemeStyle.Overlay.NONE;
        if (effects && !style.overlayOnTop()) {
            GuiFx.overlay(client, ctx, style.overlay().ordinal(), wx(), wy(), W, H, Ui.rad(10), th.accent(), th.accent2(), style.overlayStrength());
        }

        renderHeader(ctx, th, mouseX, mouseY);
        if (aiView()) {
            AI.renderHistory(ctx, th, textRenderer, listX(), bodyTop(), LIST_W, bodyH(), mouseX, mouseY);
            AI.renderMain(ctx, th, textRenderer, panelX(), bodyTop(), panelW(), bodyH(), mouseX, mouseY);
        } else if (configView()) {
            renderConfigs(ctx, th, mouseX, mouseY);
        } else {
            renderModuleList(ctx, th, mouseX, mouseY, dt, anim);
            renderSettings(ctx, th, mouseX, mouseY);
            if (panelIn < 1f) {
                float fade = 1f - (1f - (float) Math.pow(1f - panelIn, 3));
                Ui.roundRect(ctx, panelX(), bodyTop(), panelW(), bodyH(), 8, Ui.alpha(th.bg() & 0xFFFFFF, 0.9f * fade));
            }
            if (pickerFor != null) {
                drawPicker(ctx, th);
            }
        }

        if (effects && style.overlayOnTop()) {
            GuiFx.overlay(client, ctx, style.overlay().ordinal(), wx(), wy(), W, H, Ui.rad(10), th.accent(), th.accent2(), style.overlayStrength());
        }
        ctx.getMatrices().pop();
        drawUpdateBanner(ctx, th, mouseX, mouseY);
    }

    private void scaleAbout(DrawContext ctx, float k) {
        float cx = width / 2f;
        float cy = height / 2f;
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(k, k, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0);
    }

    private void drawBackground(DrawContext ctx, Theme th, float ease) {
        switch (Interface.backgroundStyle()) {
            case 1 -> ctx.fillGradient(0, 0, width, height, Ui.alpha(0x000000, 0.35f * ease),
                    Ui.alpha(th.accent() & 0xFFFFFF, 0.28f * ease));
            case 2 -> {
                ctx.fill(0, 0, width, height, Ui.alpha(0x000000, 0.55f * ease));
                for (int i = 0; i < 7; i++) {
                    int grow = 14 + i * 14;
                    Ui.roundRect(ctx, wx() - grow, wy() - grow, W + 2 * grow, H + 2 * grow, 14 + grow,
                            Ui.alpha(th.accent() & 0xFFFFFF, 0.035f * ease));
                }
            }
            default -> ctx.fill(0, 0, width, height, Ui.alpha(0x000000, 0.5f * ease));
        }
    }

    private int bannerW() {
        return Ui.width(textRenderer, bannerText()) + 28;
    }

    private String bannerText() {
        return "Update " + Updater.latest() + " available - click to open the download page";
    }

    private void drawUpdateBanner(DrawContext ctx, Theme th, int mx, int my) {
        if (Updater.latest() == null) {
            return;
        }
        int bw = bannerW();
        int bx = (width - bw) / 2;
        int by = wy() + H + 10;
        boolean hover = inside(mx, my, bx, by, bw, 20);
        Ui.roundRect(ctx, bx, by, bw, 20, 8, hover ? th.accent2() : th.accent());
        Ui.textCentered(ctx, textRenderer, bannerText(), width / 2, by + 6, th.bg() | 0xFF000000);
    }

    private void renderHeader(DrawContext ctx, Theme th, int mx, int my) {
        Ui.text(ctx, textRenderer, "Neuro", wx() + PAD + 2, wy() + 16, th.text());
        Ui.text(ctx, textRenderer, "Canvas", wx() + PAD + 2 + Ui.width(textRenderer, "Neuro") + 1, wy() + 16, th.accent());
        List<Category> cats = categories();
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            int x = tabX(i, cats);
            int w = tabWidth(c);
            boolean hover = inside(mx, my, x - 8, wy() + 6, w + 16, HEADER - 12);
            int color = c == tab ? th.text() : (hover ? th.text() : th.dim());
            Icons.draw(ctx, c, x, wy() + 15, c == tab ? th.accent() : color);
            if (c == tab) {
                Ui.text(ctx, textRenderer, c.label, x + Icons.SIZE + 5, wy() + 16, color);
            } else if (hover) {
                Ui.text(ctx, textRenderer, c.label, x + w / 2 - Ui.width(textRenderer, c.label) / 2, wy() + HEADER - 4, th.dim());
            }
            if (c == tab) {
                if (Float.isNaN(underlineX) || !Interface.animations()) {
                    underlineX = x - 2;
                    underlineW = w + 4;
                }
                underlineX += (x - 2 - underlineX) * 0.28f;
                underlineW += (w + 4 - underlineW) * 0.28f;
                Ui.roundRect(ctx, Math.round(underlineX), wy() + HEADER - 8, Math.round(underlineW), 2, 1, th.accent());
            }
        }
        if (searchFocus) {
            Ui.roundRect(ctx, sbX() - 1, sbY() - 1, SEARCH_W + 2, 24, 7, th.accent());
        }
        Ui.surface(ctx, sbX(), sbY(), SEARCH_W, 22, 6, th.panel2());
        String shown = query.isEmpty() ? "Search" : query + (searchFocus && System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        Ui.text(ctx, textRenderer, shown, sbX() + 8, sbY() + 7, query.isEmpty() ? th.dim() : th.text());
        ctx.fill(wx() + PAD, wy() + HEADER, wx() + W - PAD, wy() + HEADER + 1, th.panel2());
    }

    private void renderModuleList(DrawContext ctx, Theme th, int mx, int my, float dt, boolean anim) {
        List<Module> list = visible();
        int viewY = bodyTop();
        ctx.enableScissor(listX(), viewY, listX() + LIST_W, viewY + bodyH());
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int y = cardY(i);
            boolean hover = inside(mx, my, listX(), y, LIST_W, CARD_H) && my >= viewY && my < viewY + bodyH();
            boolean sel = m == selected;
            float h = hoverAnim.getOrDefault(m, 0f);
            float ht = hover ? 1f : 0f;
            h = anim ? h + Math.signum(ht - h) * Math.min(Math.abs(ht - h), dt * 9f) : ht;
            hoverAnim.put(m, h);

            float local = Math.max(0f, Math.min(1f, (listIn - i * 0.045f) / 0.55f));
            float eased = 1f - (float) Math.pow(1f - local, 3);
            ctx.getMatrices().push();
            ctx.getMatrices().translate((1f - eased) * -16f, 0, 0);
            int base = sel ? th.panel2() : Ui.mix(th.panel(), th.hover(), h);
            Ui.surface(ctx, listX(), y, LIST_W, CARD_H, 6, local < 1f ? Ui.mix(th.bg() | 0xFF000000, base | 0xFF000000, eased) : base);
            if (sel) {
                Ui.roundRect(ctx, listX(), y + 6, 3, CARD_H - 12, 1, th.accent());
            } else if (h > 0.02f) {
                int bar = Math.round((CARD_H - 12) * h);
                Ui.roundRect(ctx, listX(), y + (CARD_H - bar) / 2, 3, Math.max(1, bar), 1, Ui.alpha(th.accent() & 0xFFFFFF, h));
            }
            int textColor = m.enabled ? th.text() : Ui.mix(th.dim(), th.text(), h);
            Ui.textMid(ctx, textRenderer, m.name, listX() + 12 + Math.round(h * 2f), y, CARD_H, textColor);

            float target = m.enabled ? 1f : 0f;
            float a = switchAnim.getOrDefault(m, target);
            a = anim ? a + Math.signum(target - a) * Math.min(Math.abs(target - a), dt * 8f) : target;
            switchAnim.put(m, a);
            if (!m.alwaysOn) {
                drawSwitch(ctx, th, listX() + LIST_W - 38, y + (CARD_H - 14) / 2, a);
            }
            ctx.getMatrices().pop();
        }
        ctx.disableScissor();
    }

    private void drawSwitch(DrawContext ctx, Theme th, int x, int y, float a) {
        if (Interface.style().switchStyle() == ThemeStyle.SwitchStyle.BRACKET) {
            String text = a > 0.5f ? "[x]" : "[ ]";
            Ui.text(ctx, textRenderer, text, x + 28 - Ui.width(textRenderer, text), y + 3, a > 0.5f ? th.accent() : th.dim());
            return;
        }
        Ui.roundRect(ctx, x, y, 28, 14, 7, Ui.mix(th.hover(), th.accent(), a));
        Ui.roundRect(ctx, x + 2 + Math.round(a * 14), y + 2, 10, 10, 5, a > 0.5f ? th.bg() | 0xFF000000 : th.dim());
    }

    private void renderSettings(DrawContext ctx, Theme th, int mx, int my) {
        int px = panelX();
        int pw = panelW();
        Ui.surface(ctx, px, bodyTop(), pw, bodyH(), 8, th.panel());
        Ui.text(ctx, textRenderer, selected.name, px + PAD, bodyTop() + 9, th.text());
        Ui.text(ctx, textRenderer, selected.description, px + PAD, bodyTop() + 24, th.dim());
        if (friendsPanel()) {
            renderFriends(ctx, th, mx, my);
            return;
        }
        if (marketPanel()) {
            renderMarket(ctx, th, mx, my);
            return;
        }

        int viewY = rowsTop();
        int viewH = bodyTop() + bodyH() - viewY - 4;
        ctx.enableScissor(px, viewY, px + pw, viewY + viewH);
        if (!selected.alwaysOn) {
            renderBindRows(ctx, th);
        }
        List<Setting> settings = selected.settings;
        for (int i = 0; i < settings.size(); i++) {
            renderSetting(ctx, th, settings.get(i), rowY(i + bindRows()), mx, my);
        }
        ctx.disableScissor();
    }

    private void renderSetting(DrawContext ctx, Theme th, Setting s, int y, int mx, int my) {
        if (s.kind == Setting.Kind.BUTTON) {
            boolean hover = inside(mx, my, ctrlX(), y + 2, CTRL_W, ROW_H - 4);
            Ui.surface(ctx, ctrlX(), y + 2, CTRL_W, ROW_H - 4, 6, hover ? th.accent2() : th.accent());
            Ui.textCentered(ctx, textRenderer, s.name, ctrlX() + CTRL_W / 2, y + (ROW_H - 8) / 2, th.bg() | 0xFF000000);
            return;
        }
        Ui.textMid(ctx, textRenderer, s.name, panelX() + PAD, y, ROW_H, th.text());
        int cx = ctrlX();
        switch (s.kind) {
            case BOOL -> drawSwitch(ctx, th, cx + CTRL_W - 28, y + (ROW_H - 14) / 2, s.on() ? 1f : 0f);
            case CHOICE -> {
                Ui.surface(ctx, cx, y + 3, CTRL_W, ROW_H - 6, 6, th.panel2());
                Ui.textCentered(ctx, textRenderer, s.option(), cx + CTRL_W / 2, y + (ROW_H - 8) / 2, th.text());
                Ui.text(ctx, textRenderer, "<", cx + 8, y + (ROW_H - 8) / 2, th.dim());
                Ui.text(ctx, textRenderer, ">", cx + CTRL_W - 8 - Ui.width(textRenderer, ">"), y + (ROW_H - 8) / 2, th.dim());
            }
            case COLOR -> {
                int argb = s.argb();
                String hex = String.format("#%06X", argb & 0xFFFFFF);
                Ui.text(ctx, textRenderer, hex, cx + CTRL_W - 64 - Ui.width(textRenderer, hex), y + (ROW_H - 8) / 2, th.dim());
                Ui.roundRect(ctx, cx + CTRL_W - 56, y + 4, 56, ROW_H - 8, 6, th.text());
                Ui.roundRect(ctx, cx + CTRL_W - 55, y + 5, 54, ROW_H - 10, 5, argb | 0xFF000000);
                int a = argb >>> 24;
                if (a < 255) {
                    Ui.roundRect(ctx, cx + CTRL_W - 55, y + ROW_H - 9, 54, 4, 2, Ui.alpha(0x000000, 0.4f));
                    Ui.roundRect(ctx, cx + CTRL_W - 55, y + ROW_H - 9, Math.max(2, 54 * a / 255), 4, 2, th.text());
                }
            }
            case NUMBER -> {
                int tw = trackW();
                int ty = y + ROW_H / 2 - 3;
                float f = (float) ((s.value - s.min) / (s.max - s.min));
                Ui.roundRect(ctx, cx, ty, tw, 6, 3, th.panel2());
                Ui.gradientH(ctx, cx + 1, ty + 1, Math.max(0, Math.round(f * (tw - 2))), 4, th.accent2(), th.accent());
                Ui.roundRect(ctx, cx + Math.round(f * (tw - 8)), ty - 2, 8, 10, 4, th.text());
                String v = s.max - s.min >= 10 ? String.format("%.0f", s.value) : String.format("%.2f", s.value);
                Ui.text(ctx, textRenderer, v, cx + CTRL_W - Ui.width(textRenderer, v), y + (ROW_H - 8) / 2, th.dim());
            }
        }
    }

    // ---- Friends panel -----------------------------------------------------------------------

    private record FRow(int kind, int y, int h, String text, Setting setting, boolean flag, boolean extra) { }

    private static final int F_SETTING = 0, F_HEADER = 1, F_USER = 2, F_FRIEND = 3, F_INPUT = 4, F_NOTE = 5;
    private String friendInput = "";
    private boolean friendFocus;
    private int friendsContent;

    private boolean friendsPanel() {
        return selected instanceof com.example.neurocanvas.module.Friends && query.isEmpty();
    }

    private List<FRow> friendRows() {
        com.example.neurocanvas.module.Friends f = (com.example.neurocanvas.module.Friends) selected;
        com.example.neurocanvas.module.NameTags tags = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class);
        List<FRow> rows = new ArrayList<>();
        int top = rowsTop() - (int) settingsScroll;
        int y = top;
        for (Setting s : f.settings) {
            rows.add(new FRow(F_SETTING, y, ROW_H, s.name, s, false, false));
            y += ROW_H;
        }
        String me = client.player == null ? "" : client.player.getName().getString();
        List<String> online = new ArrayList<>();
        for (String n : tags.onlineNames()) {
            if (!n.equalsIgnoreCase(me)) {
                online.add(n);
            }
        }
        y += 6;
        rows.add(new FRow(F_HEADER, y, 20, "Online on this server (" + online.size() + ")", null, false, false));
        y += 20;
        if (!tags.connected()) {
            rows.add(new FRow(F_NOTE, y, 22, "Turn NameTags on to see who else runs the mod here", null, false, false));
            y += 22;
        } else if (online.isEmpty()) {
            rows.add(new FRow(F_NOTE, y, 22, "Nobody else is online here", null, false, false));
            y += 22;
        }
        for (String n : online) {
            rows.add(new FRow(F_USER, y, 24, n, null, f.isFriend(n), f.inMenu(n)));
            y += 24;
        }
        y += 6;
        List<String> mine = f.friendNames();
        rows.add(new FRow(F_HEADER, y, 20, "My friends (" + mine.size() + ")", null, false, false));
        y += 20;
        if (mine.isEmpty()) {
            rows.add(new FRow(F_NOTE, y, 22, "No friends yet: tap the star next to a name, or type one below", null, false, false));
            y += 22;
        }
        for (String n : mine) {
            boolean here = false;
            for (String o : online) {
                here |= o.equalsIgnoreCase(n);
            }
            rows.add(new FRow(F_FRIEND, y, 24, n, null, here, f.inMenu(n)));
            y += 24;
        }
        y += 4;
        rows.add(new FRow(F_INPUT, y, 28, "", null, false, false));
        y += 28;
        friendsContent = y - top;
        return rows;
    }

    private int friendRight() {
        return panelX() + panelW() - PAD;
    }

    private void renderFriends(DrawContext ctx, Theme th, int mx, int my) {
        int px = panelX() + PAD;
        int pw = panelW() - 2 * PAD;
        int viewY = rowsTop();
        int viewH = bodyTop() + bodyH() - viewY - 4;
        ctx.enableScissor(panelX(), viewY, panelX() + panelW(), viewY + viewH);
        for (FRow r : friendRows()) {
            switch (r.kind()) {
                case F_SETTING -> renderSetting(ctx, th, r.setting(), r.y(), mx, my);
                case F_HEADER -> {
                    Ui.text(ctx, textRenderer, r.text(), px, r.y() + 6, th.accent());
                    ctx.fill(px, r.y() + 17, px + pw, r.y() + 18, th.panel2());
                }
                case F_NOTE -> Ui.text(ctx, textRenderer, textRenderer.trimToWidth(r.text(), pw), px, r.y() + 7, th.dim());
                case F_USER, F_FRIEND -> {
                    boolean hover = inside(mx, my, px, r.y(), pw, 22) && my >= viewY && my < viewY + viewH;
                    Ui.surface(ctx, px, r.y(), pw, 22, 6, hover ? th.hover() : th.panel2());
                    int nameColor = r.kind() == F_FRIEND && !r.flag() ? th.dim() : th.text();
                    Ui.textMid(ctx, textRenderer, r.text(), px + 10, r.y(), 22, nameColor);
                    int after = px + 10 + Ui.width(textRenderer, r.text()) + 8;
                    if (r.kind() == F_FRIEND) {
                        ctx.fill(after, r.y() + 9, after + 5, r.y() + 14, r.flag() ? 0xFF5CE08A : th.hover());
                        after += 12;
                    }
                    if (r.extra()) {
                        Ui.text(ctx, textRenderer, "in menu", after, r.y() + 7, th.accent());
                    }
                    int bx = px + pw - 26;
                    if (r.kind() == F_USER) {
                        java.util.List<com.example.neurocanvas.module.Showcase.Item> worn = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Showcase.class).itemsOf(r.text());
                        if (!worn.isEmpty()) {
                            StringBuilder what = new StringBuilder();
                            for (com.example.neurocanvas.module.Showcase.Item w : worn) {
                                what.append(what.length() == 0 ? "" : ", ").append(w.name());
                            }
                            int getX = px + pw - 70;
                            boolean gh = inside(mx, my, getX, r.y() + 2, 36, 18);
                            Ui.roundRect(ctx, getX, r.y() + 2, 36, 18, 5, gh ? th.accent2() : th.accent());
                            Ui.textCentered(ctx, textRenderer, "Get", getX + 18, r.y() + 7, th.bg() | 0xFF000000);
                            int room = getX - after - 8;
                            if (room > 30) {
                                Ui.text(ctx, textRenderer, textRenderer.trimToWidth("wears " + what, room), after + (r.extra() ? 44 : 0), r.y() + 7, th.dim());
                            }
                        }
                        com.example.neurocanvas.gui.Icons.star(ctx, bx + 6, r.y() + 7, r.flag(), r.flag() ? 0xFFFFD23F : th.dim());
                    } else {
                        boolean xHover = inside(mx, my, bx, r.y(), 26, 22);
                        Ui.textCentered(ctx, textRenderer, "x", bx + 13, r.y() + 7, xHover ? 0xFFFF6B6B : th.dim());
                    }
                }
                case F_INPUT -> {
                    int addW = 60;
                    int boxW = pw - addW - 8;
                    if (friendFocus) {
                        Ui.roundRect(ctx, px - 1, r.y() - 1, boxW + 2, 26, 7, th.accent());
                    }
                    Ui.roundRect(ctx, px, r.y(), boxW, 24, 6, th.panel2());
                    String shown = friendInput.isEmpty() ? "Add a friend by name" : friendInput + (friendFocus && System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
                    Ui.text(ctx, textRenderer, shown, px + 8, r.y() + 8, friendInput.isEmpty() ? th.dim() : th.text());
                    boolean hover = inside(mx, my, px + boxW + 8, r.y(), addW, 24);
                    Ui.roundRect(ctx, px + boxW + 8, r.y(), addW, 24, 6, hover ? th.accent2() : th.accent());
                    Ui.textCentered(ctx, textRenderer, "Add", px + boxW + 8 + addW / 2, r.y() + 8, th.bg() | 0xFF000000);
                }
                default -> { }
            }
        }
        ctx.disableScissor();
    }

    private boolean friendsClicked(double mx, double my, int button) {
        com.example.neurocanvas.module.Friends f = (com.example.neurocanvas.module.Friends) selected;
        int px = panelX() + PAD;
        int pw = panelW() - 2 * PAD;
        int viewTop = rowsTop();
        int viewBottom = bodyTop() + bodyH() - 4;
        if (my < viewTop || my >= viewBottom) {
            return false;
        }
        for (FRow r : friendRows()) {
            if (my < r.y() || my >= r.y() + r.h()) {
                continue;
            }
            switch (r.kind()) {
                case F_SETTING -> {
                    Setting s = r.setting();
                    if (inside(mx, my, ctrlX() - 4, r.y(), CTRL_W + 8, ROW_H)) {
                        switch (s.kind) {
                            case BOOL -> s.set(s.on() ? 0 : 1);
                            case CHOICE -> s.cycle(button == 1 ? -1 : 1);
                            default -> { }
                        }
                        Interface.click(1.1f);
                    }
                    return true;
                }
                case F_USER -> {
                    com.example.neurocanvas.module.Showcase showcase = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Showcase.class);
                    if (mx >= px + pw - 30) {
                        f.toggle(r.text());
                        Interface.click(r.flag() ? 0.8f : 1.3f);
                    } else if (mx >= px + pw - 70 && mx < px + pw - 34 && !showcase.itemsOf(r.text()).isEmpty()) {
                        showcase.getAll(r.text());
                        Interface.click(1.3f);
                    }
                    return true;
                }
                case F_FRIEND -> {
                    if (mx >= px + pw - 30) {
                        f.remove(r.text());
                        Interface.click(0.8f);
                    }
                    return true;
                }
                case F_INPUT -> {
                    int addW = 60;
                    int boxW = pw - addW - 8;
                    if (mx >= px + boxW + 8 && mx < px + boxW + 8 + addW) {
                        addFriendFromInput(f);
                    } else {
                        friendFocus = true;
                    }
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        return false;
    }

    private void addFriendFromInput(com.example.neurocanvas.module.Friends f) {
        if (f.add(friendInput.trim())) {
            friendInput = "";
            Interface.click(1.3f);
        }
    }

    // ---- Market panel -------------------------------------------------------------------------

    private record MRow(int kind, int y, int h, String text, Setting setting, com.example.neurocanvas.client.MarketClient.Entry entry, int col) { }

    private static final int M_SETTING = 0, M_NOTE = 1, M_CARD = 2;
    private static final int PREVIEW_H = 104;
    private int marketContent;

    private boolean marketPanel() {
        return selected instanceof com.example.neurocanvas.module.Market && query.isEmpty();
    }

    private List<MRow> marketRows() {
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) selected;
        m.ensureLoaded();
        List<MRow> rows = new ArrayList<>();
        int top = rowsTop() - (int) settingsScroll;
        int y = top;
        for (Setting s : m.settings) {
            rows.add(new MRow(M_SETTING, y, ROW_H, s.name, s, null, 0));
            y += ROW_H;
        }
        y += 4;
        if (!m.status().isEmpty()) {
            rows.add(new MRow(M_NOTE, y, 22, m.status(), null, null, 0));
            y += 22;
        }
        if (m.entries() != null) {
            com.example.neurocanvas.avatar.CosmeticPack.Type only = m.filter();
            int n = 0;
            for (com.example.neurocanvas.client.MarketClient.Entry e : m.entries()) {
                if (only != null && !only.folder.equals(e.type())) {
                    continue;
                }
                rows.add(new MRow(M_CARD, y + (n / 2) * (MARKET_CARD_H + 8), MARKET_CARD_H, e.name(), null, e, n % 2));
                n++;
            }
            y += ((n + 1) / 2) * (MARKET_CARD_H + 8);
        }
        marketContent = y - top;
        return rows;
    }

    private static final int MARKET_CARD_H = 142;

    private int marketCardW() {
        return (panelW() - 2 * PAD - 8) / 2;
    }

    private static String kindLabel(String folder) {
        for (com.example.neurocanvas.avatar.CosmeticPack.Type t : com.example.neurocanvas.avatar.CosmeticPack.Type.values()) {
            if (t.folder.equals(folder)) {
                return t.label;
            }
        }
        return folder;
    }

    private int marketButtonW() {
        return 58;
    }

    private void renderMarket(DrawContext ctx, Theme th, int mx, int my) {
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) selected;
        int px = panelX() + PAD;
        int pw = panelW() - 2 * PAD;
        int viewY = rowsTop();
        int viewH = bodyTop() + bodyH() - viewY - 4;
        int cw = marketCardW();
        ctx.enableScissor(panelX(), viewY, panelX() + panelW(), viewY + viewH);
        for (MRow r : marketRows()) {
            if (r.kind() == M_SETTING) {
                renderSetting(ctx, th, r.setting(), r.y(), mx, my);
                continue;
            }
            if (r.kind() == M_NOTE) {
                Ui.text(ctx, textRenderer, textRenderer.trimToWidth(r.text(), pw), px, r.y() + 7, th.dim());
                continue;
            }
            int cx = px + r.col() * (cw + 8);
            int cy = r.y();
            if (cy + MARKET_CARD_H < viewY || cy > viewY + viewH) {
                continue;
            }
            com.example.neurocanvas.client.MarketClient.Entry e = r.entry();
            boolean hover = inside(mx, my, cx, cy, cw, MARKET_CARD_H) && my >= viewY && my < viewY + viewH;
            Ui.surface(ctx, cx, cy, cw, MARKET_CARD_H, 8, hover ? th.hover() : th.panel2());

            // the top part: how the item looks
            int pvx = cx + 4, pvy = cy + 4, pvw = cw - 8, pvh = PREVIEW_H - 4;
            Ui.roundRect(ctx, pvx, pvy, pvw, pvh, 6, Ui.mix(th.bg() | 0xFF000000, th.panel() | 0xFF000000, 0.5f));
            drawPreview(ctx, th, m.preview(e), pvx, pvy, pvw, pvh);
            String kind = kindLabel(e.type());
            Ui.roundRect(ctx, pvx + 4, pvy + 4, Ui.width(textRenderer, kind) + 8, 12, 5, Ui.alpha(th.bg() & 0xFFFFFF, 0.8f));
            Ui.text(ctx, textRenderer, kind, pvx + 8, pvy + 6, th.accent());
            if (com.example.neurocanvas.client.AdminStore.has()) {
                boolean ask = m.deletePending(e);
                Ui.roundRect(ctx, pvx + pvw - 20, pvy + 4, 16, 14, 5, ask ? 0xFFE53935 : 0xB0602020);
                Ui.textCentered(ctx, textRenderer, ask ? "!" : "x", pvx + pvw - 12, pvy + 7, 0xFFFFFFFF);
            }

            // the bottom part: name and the button
            int by = cy + PREVIEW_H + 6;
            int bw = marketButtonW();
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(e.name(), cw - bw - 24), cx + 8, by + 3, th.text());
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(e.author(), cw - bw - 24), cx + 8, by + 16, th.dim());
            boolean busy = m.busy(e);
            boolean have = m.installed(e);
            String label = busy ? "..." : have ? "Wear" : "Get";
            int bx = cx + cw - bw - 8;
            boolean bh = inside(mx, my, bx, by + 2, bw, 24);
            Ui.roundRect(ctx, bx, by + 2, bw, 24, 6, busy ? th.hover() : (bh ? th.accent2() : th.accent()));
            Ui.textCentered(ctx, textRenderer, label, bx + bw / 2, by + 10, th.bg() | 0xFF000000);
        }
        ctx.disableScissor();
    }

    /** The picture of a card: a model turning around, a graffiti picture, or a note while it loads. */
    private void drawPreview(DrawContext ctx, Theme th, com.example.neurocanvas.module.Market.Preview p, int x, int y, int w, int h) {
        if (p.image != null) {
            int side = Math.min(w, h) - 12;
            int iw = p.imageW >= p.imageH ? side : side * p.imageW / p.imageH;
            int ih = p.imageW >= p.imageH ? side * p.imageH / p.imageW : side;
            ctx.drawTexture(p.image, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, 0f, 0f, p.imageW, p.imageH, p.imageW, p.imageH);
            return;
        }
        if (p.model != null) {
            ctx.enableScissor(x, y, x + w, y + h);
            boolean onSteve = com.example.neurocanvas.avatar.Mannequin.wears(p.type);
            float radius = onSteve ? 21f : p.radius;
            float[] center = onSteve ? new float[] {0, 16, 0} : p.center;
            float s = Math.min(w, h) * 0.45f / radius;
            long ms = System.currentTimeMillis();
            float angle = (ms % 9000) / 9000f * (float) (Math.PI * 2);
            org.joml.Matrix4f m = new org.joml.Matrix4f(ctx.getMatrices().peek().getPositionMatrix());
            m.translate(x + w / 2f, y + h / 2f, 100f).scale(s, -s, s).rotateX(0.2f).rotateY(angle)
                    .translate(-center[0], -center[1], -center[2]);
            if (onSteve) {
                com.example.neurocanvas.avatar.Mannequin.drawWith(p.model, p.type, m, ms / 1000.0);
            } else {
                p.model.resetPose();
                // An emote plays its animation over and over, with a short pause between the repeats.
                com.example.neurocanvas.avatar.BbModel.Anim a = p.animation != null ? p.model.find(p.animation) : null;
                if (a == null && !p.model.model.animations.isEmpty()) {
                    a = p.model.model.animations.values().iterator().next();
                }
                if (a != null) {
                    double t = (ms / 1000.0) % (a.length + 0.7);
                    p.model.apply(a, Math.min(t, a.length));
                }
                p.model.draw(m, 1f, 0.7f);
            }
            ctx.disableScissor();
            return;
        }
        Ui.textCentered(ctx, textRenderer, p.failed ? "no preview" : "...", x + w / 2, y + h / 2 - 4, th.dim());
    }

    private boolean marketClicked(double mx, double my, int button) {
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) selected;
        int px = panelX() + PAD;
        int cw = marketCardW();
        if (my < rowsTop() || my >= bodyTop() + bodyH() - 4) {
            return false;
        }
        for (MRow r : marketRows()) {
            if (r.kind() == M_SETTING) {
                if (my >= r.y() && my < r.y() + r.h() && inside(mx, my, ctrlX() - 4, r.y(), CTRL_W + 8, ROW_H)) {
                    Setting s = r.setting();
                    switch (s.kind) {
                        case BUTTON -> s.value = 1;
                        case CHOICE -> s.cycle(button == 1 ? -1 : 1);
                        default -> { }
                    }
                    Interface.click(1.1f);
                    return true;
                }
            } else if (r.kind() == M_CARD) {
                int cx = px + r.col() * (cw + 8);
                int by = r.y() + PREVIEW_H + 6;
                if (com.example.neurocanvas.client.AdminStore.has() && inside(mx, my, cx + 4 + cw - 8 - 20, r.y() + 4 + 4, 16, 14)) {
                    m.requestDelete(r.entry());
                    Interface.click(0.8f);
                    return true;
                }
                if (inside(mx, my, cx + cw - marketButtonW() - 8, by + 2, marketButtonW(), 24)) {
                    m.get(r.entry());
                    Interface.click(1.3f);
                    return true;
                }
            }
        }
        return false;
    }

    // ---- colour picker popup -------------------------------------------------------------

    private static final int POP_H = 142;
    private static final int SV_H = 76;

    private Setting pickerFor;
    private int pickerRow;
    private float pickH;
    private float pickS;
    private float pickV;
    private float pickA;
    private int pickDrag;

    private void openPicker(Setting s, int row) {
        if (pickerFor == s) {
            pickerFor = null;
            return;
        }
        pickerFor = s;
        pickerRow = row;
        int argb = s.argb();
        float[] hsv = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        pickH = hsv[0];
        pickS = hsv[1];
        pickV = hsv[2];
        pickA = (argb >>> 24) / 255f;
    }

    private int popX() { return ctrlX() - 8; }
    private int popW() { return CTRL_W + 16; }

    private int popY() {
        int below = rowY(pickerRow) + ROW_H + 2;
        return below + POP_H > wy() + H - 6 ? rowY(pickerRow) - POP_H - 2 : below;
    }

    private int svY() { return popY() + 8; }
    private int hueY() { return svY() + SV_H + 8; }
    private int alphaY() { return hueY() + 18; }

    private void applyPicker(double mx, double my) {
        int x = ctrlX();
        switch (pickDrag) {
            case 1 -> {
                pickS = (float) clamp((mx - x) / CTRL_W, 0, 1);
                pickV = 1f - (float) clamp((my - svY()) / SV_H, 0, 1);
            }
            case 2 -> pickH = (float) clamp((mx - x) / CTRL_W, 0, 1);
            case 3 -> pickA = (float) clamp((mx - x) / CTRL_W, 0, 1);
            default -> { }
        }
        int rgb = java.awt.Color.HSBtoRGB(pickH, pickS, pickV) & 0xFFFFFF;
        pickerFor.setArgb((Math.round(pickA * 255) << 24) | rgb);
    }

    private void drawPicker(DrawContext ctx, Theme th) {
        int px = popX();
        int py = popY();
        int x = ctrlX();
        Ui.shadow(ctx, px, py, popW(), POP_H, 8);
        Ui.surface(ctx, px, py, popW(), POP_H, 8, th.panel2());

        int sy = svY();
        for (int i = 0; i < CTRL_W; i++) {
            int top = 0xFF000000 | (java.awt.Color.HSBtoRGB(pickH, i / (float) (CTRL_W - 1), 1f) & 0xFFFFFF);
            ctx.fillGradient(x + i, sy, x + i + 1, sy + SV_H, top, 0xFF000000);
        }
        int mx = x + Math.round(pickS * (CTRL_W - 1));
        int my = sy + Math.round((1f - pickV) * (SV_H - 1));
        int rgb = java.awt.Color.HSBtoRGB(pickH, pickS, pickV) & 0xFFFFFF;
        Ui.roundRect(ctx, mx - 4, my - 4, 8, 8, 4, 0xFFFFFFFF);
        Ui.roundRect(ctx, mx - 3, my - 3, 6, 6, 3, 0xFF000000 | rgb);

        int hy = hueY();
        Ui.rainbowH(ctx, x, hy, CTRL_W, 10);
        Ui.roundRect(ctx, x + Math.round(pickH * (CTRL_W - 6)), hy - 1, 6, 12, 3, 0xFFFFFFFF);

        int ay = alphaY();
        for (int i = 0; i < CTRL_W; i += 5) {
            for (int j = 0; j < 10; j += 5) {
                ctx.fill(x + i, ay + j, Math.min(x + i + 5, x + CTRL_W), ay + j + 5, ((i / 5 + j / 5) % 2 == 0) ? 0xFF8A8A8A : 0xFF5A5A5A);
            }
        }
        Ui.gradientH(ctx, x, ay, CTRL_W, 10, rgb, 0xFF000000 | rgb);
        Ui.roundRect(ctx, x + Math.round(pickA * (CTRL_W - 6)), ay - 1, 6, 12, 3, 0xFFFFFFFF);

        String info = String.format("#%06X   alpha %d%%", rgb, Math.round(pickA * 100));
        Ui.text(ctx, textRenderer, info, x, ay + 16, th.dim());
    }

    // ---- Configs tab ---------------------------------------------------------------------

    private record Btn(String label, int x, int y, int w, int h, Runnable action, boolean primary) { }

    private final List<String> presets = new ArrayList<>();
    private String selectedPreset = "";
    private String presetName = "";
    private boolean nameFocus;
    private String status = "";

    private boolean aiView() {
        return tab == Category.AI && query.isEmpty();
    }

    private boolean configView() {
        return tab == Category.CONFIG && query.isEmpty();
    }

    private void refreshPresets() {
        presets.clear();
        presets.addAll(NeuroCanvasClient.modules().presets());
    }

    private int nameBoxX() { return panelX() + PAD + 60; }
    private int nameBoxY() { return rowsTop() + 2; }
    private int nameBoxW() { return panelW() - 2 * PAD - 60; }

    private List<Btn> configButtons() {
        List<Btn> out = new ArrayList<>();
        int bx = panelX() + PAD;
        int full = panelW() - 2 * PAD;
        int third = (full - 16) / 3;
        int half = (full - 8) / 2;
        int y1 = rowsTop() + 40;
        int y2 = y1 + 32;
        int y3 = y2 + 32;
        out.add(new Btn("Save", bx, y1, third, 24, this::savePreset, true));
        out.add(new Btn("Load", bx + third + 8, y1, third, 24, this::loadPreset, false));
        out.add(new Btn("Delete", bx + 2 * (third + 8), y1, third, 24, this::deletePreset, false));
        out.add(new Btn("Copy share code", bx, y2, half, 24, this::copyCode, false));
        out.add(new Btn("Import from clipboard", bx + half + 8, y2, half, 24, this::importCode, false));
        out.add(new Btn("Edit HUD layout", bx, y3, full, 24, () -> client.setScreen(new HudEditor(this)), false));
        int y4 = y3 + 32;
        out.add(new Btn("Upload: get short code", bx, y4, half, 24, this::cloudUpload, true));
        out.add(new Btn("Download: code from clipboard", bx + half + 8, y4, half, 24, this::cloudDownload, false));
        return out;
    }

    private void savePreset() {
        String name = ModuleManager.cleanName(presetName.isBlank() ? selectedPreset : presetName);
        if (name.isEmpty()) {
            status = "Type a name first";
            return;
        }
        try {
            NeuroCanvasClient.modules().savePreset(name);
            selectedPreset = name;
            presetName = name;
            refreshPresets();
            status = "Saved \"" + name + "\"";
        } catch (IOException e) {
            status = "Could not save: " + e.getMessage();
        }
    }

    private void loadPreset() {
        if (selectedPreset.isEmpty()) {
            status = "Select a preset on the left";
            return;
        }
        try {
            NeuroCanvasClient.modules().loadPreset(selectedPreset);
            status = "Loaded \"" + selectedPreset + "\"";
        } catch (IOException e) {
            status = "Could not load: " + e.getMessage();
        }
    }

    private void deletePreset() {
        if (selectedPreset.isEmpty()) {
            status = "Select a preset on the left";
            return;
        }
        try {
            NeuroCanvasClient.modules().deletePreset(selectedPreset);
            status = "Deleted \"" + selectedPreset + "\"";
            selectedPreset = "";
            refreshPresets();
        } catch (IOException e) {
            status = "Could not delete: " + e.getMessage();
        }
    }

    private void cloudUpload() {
        status = "Uploading...";
        com.example.neurocanvas.client.CloudConfigs.upload(NeuroCanvasClient.modules().exportCode()).whenComplete((code, error) ->
                client.execute(() -> {
                    if (error != null) {
                        status = "Upload failed: " + com.example.neurocanvas.client.CloudConfigs.reason(error);
                    } else {
                        client.keyboard.setClipboard(code);
                        status = "Short code " + code + " copied. Send it to a friend.";
                    }
                }));
    }

    private void cloudDownload() {
        String code = client.keyboard.getClipboard().trim();
        if (!com.example.neurocanvas.client.CloudConfigs.looksLikeCode(code)) {
            status = "Copy a short code first (8 letters and digits)";
            return;
        }
        status = "Downloading...";
        com.example.neurocanvas.client.CloudConfigs.download(code).whenComplete((data, error) ->
                client.execute(() -> {
                    if (error != null) {
                        status = "Download failed: " + com.example.neurocanvas.client.CloudConfigs.reason(error);
                        return;
                    }
                    try {
                        NeuroCanvasClient.modules().importCode(data);
                        status = "Loaded the config " + code.toUpperCase();
                    } catch (IOException e) {
                        status = "Import failed: " + e.getMessage();
                    }
                }));
    }

    private void copyCode() {
        client.keyboard.setClipboard(NeuroCanvasClient.modules().exportCode());
        status = "Share code copied to clipboard";
    }

    private void importCode() {
        try {
            NeuroCanvasClient.modules().importCode(client.keyboard.getClipboard());
            status = "Imported settings from clipboard";
        } catch (IOException e) {
            status = "Import failed: " + e.getMessage();
        }
    }

    private void renderConfigs(DrawContext ctx, Theme th, int mx, int my) {
        int viewY = bodyTop();
        ctx.enableScissor(listX(), viewY, listX() + LIST_W, viewY + bodyH());
        if (presets.isEmpty()) {
            Ui.text(ctx, textRenderer, "No presets yet", listX() + 4, viewY + 8, th.dim());
        }
        for (int i = 0; i < presets.size(); i++) {
            String name = presets.get(i);
            int y = cardY(i);
            boolean hover = inside(mx, my, listX(), y, LIST_W, CARD_H) && my >= viewY && my < viewY + bodyH();
            boolean sel = name.equals(selectedPreset);
            Ui.surface(ctx, listX(), y, LIST_W, CARD_H, 6, sel ? th.panel2() : (hover ? th.hover() : th.panel()));
            if (sel) {
                Ui.roundRect(ctx, listX(), y + 6, 3, CARD_H - 12, 1, th.accent());
            }
            Ui.textMid(ctx, textRenderer, textRenderer.trimToWidth(name, LIST_W - 20), listX() + 12, y, CARD_H, th.text());
        }
        ctx.disableScissor();

        Ui.surface(ctx, panelX(), bodyTop(), panelW(), bodyH(), 8, th.panel());
        Ui.text(ctx, textRenderer, "Configs", panelX() + PAD, bodyTop() + 9, th.text());
        Ui.text(ctx, textRenderer, "Save, load and share all your settings", panelX() + PAD, bodyTop() + 24, th.dim());

        Ui.textMid(ctx, textRenderer, "Name", panelX() + PAD, nameBoxY() - 2, 26, th.text());
        if (nameFocus) {
            Ui.roundRect(ctx, nameBoxX() - 1, nameBoxY() - 1, nameBoxW() + 2, 24, 7, th.accent());
        }
        Ui.roundRect(ctx, nameBoxX(), nameBoxY(), nameBoxW(), 22, 6, th.panel2());
        String shown = presetName.isEmpty() ? "Preset name" : presetName + (nameFocus && System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        Ui.text(ctx, textRenderer, shown, nameBoxX() + 8, nameBoxY() + 7, presetName.isEmpty() ? th.dim() : th.text());

        for (Btn b : configButtons()) {
            boolean hover = inside(mx, my, b.x(), b.y(), b.w(), b.h());
            int bg = b.primary() ? (hover ? th.accent2() : th.accent()) : (hover ? th.hover() : th.panel2());
            Ui.roundRect(ctx, b.x(), b.y(), b.w(), b.h(), 6, bg);
            Ui.textCentered(ctx, textRenderer, b.label(), b.x() + b.w() / 2, b.y() + 8,
                    b.primary() ? (th.bg() | 0xFF000000) : th.text());
        }
        if (!status.isEmpty()) {
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(status, panelW() - 2 * PAD), panelX() + PAD, rowsTop() + 40 + 32 * 4 + 6, th.dim());
        }
    }

    private boolean configClicked(double mx, double my, int button) {
        if (inside(mx, my, nameBoxX(), nameBoxY(), nameBoxW(), 22)) {
            nameFocus = true;
            return true;
        }
        for (Btn b : configButtons()) {
            if (inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
                b.action().run();
                return true;
            }
        }
        if (inside(mx, my, listX(), bodyTop(), LIST_W, bodyH())) {
            for (int i = 0; i < presets.size(); i++) {
                if (inside(mx, my, listX(), cardY(i), LIST_W, CARD_H)) {
                    selectedPreset = presets.get(i);
                    presetName = selectedPreset;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int trackW() {
        return CTRL_W - 40;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (Updater.latest() != null && inside(mx, my, (width - bannerW()) / 2, wy() + H + 10, bannerW(), 20)) {
            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(Updater.url()));
            return true;
        }
        if (pickerFor != null) {
            if (inside(mx, my, popX(), popY(), popW(), POP_H)) {
                if (inside(mx, my, ctrlX(), svY(), CTRL_W, SV_H)) {
                    pickDrag = 1;
                } else if (inside(mx, my, ctrlX(), hueY() - 2, CTRL_W, 14)) {
                    pickDrag = 2;
                } else if (inside(mx, my, ctrlX(), alphaY() - 2, CTRL_W, 14)) {
                    pickDrag = 3;
                }
                if (pickDrag != 0) {
                    applyPicker(mx, my);
                }
                return true;
            }
            pickerFor = null;
        }
        searchFocus = inside(mx, my, sbX(), sbY(), SEARCH_W, 22);
        if (searchFocus) {
            return true;
        }
        nameFocus = false;
        friendFocus = false;
        bindingFor = null;
        List<Category> cats = categories();
        for (int i = 0; i < cats.size(); i++) {
            int x = tabX(i, cats);
            if (inside(mx, my, x - 8, wy() + 6, tabWidth(cats.get(i)) + 16, HEADER - 12)) {
                query = "";
                pickerFor = null;
                if (tab != cats.get(i)) {
                    Interface.click(1.0f);
                    tab = cats.get(i);
                    if (tab == Category.CONFIG) {
                        refreshPresets();
                    } else {
                        selected = modulesIn(tab).get(0);
                    }
                    listScroll = 0;
                    settingsScroll = 0;
                }
                return true;
            }
        }

        if (aiView()) {
            return AI.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
        }
        if (configView()) {
            return configClicked(mx, my, button);
        }
        List<Module> list = visible();
        if (inside(mx, my, listX(), bodyTop(), LIST_W, bodyH())) {
            for (int i = 0; i < list.size(); i++) {
                if (inside(mx, my, listX(), cardY(i), LIST_W, CARD_H)) {
                    Module m = list.get(i);
                    boolean onSwitch = mx >= listX() + LIST_W - 44;
                    if (!m.alwaysOn && (onSwitch || button == 1)) {
                        m.enabled = !m.enabled;
                        Interface.click(m.enabled ? 1.3f : 0.8f);
                    }
                    if (m != selected) {
                        selected = m;
                        settingsScroll = 0;
                    }
                    return true;
                }
            }
        }

        if (friendsPanel() && mx >= panelX() && friendsClicked(mx, my, button)) {
            return true;
        }
        if (marketPanel() && mx >= panelX() && marketClicked(mx, my, button)) {
            return true;
        }
        int viewTop = rowsTop();
        int viewBottom = bodyTop() + bodyH() - 4;
        if (!friendsPanel() && !marketPanel() && my >= viewTop && my < viewBottom) {
            if (!selected.alwaysOn && inside(mx, my, ctrlX() - 4, rowY(0), CTRL_W + 8, ROW_H)) {
                bindingFor = selected;
                return true;
            }
            if (!selected.alwaysOn && inside(mx, my, ctrlX() - 4, rowY(1), CTRL_W + 8, ROW_H)) {
                selected.hold = !selected.hold;
                return true;
            }
            for (int i = 0; i < selected.settings.size(); i++) {
                Setting s = selected.settings.get(i);
                int y = rowY(i + bindRows());
                if (!inside(mx, my, ctrlX() - 4, y, CTRL_W + 8, ROW_H)) {
                    continue;
                }
                switch (s.kind) {
                    case BOOL -> s.set(s.on() ? 0 : 1);
                    case BUTTON -> {
                        s.value = 1;
                        Interface.click(1.1f);
                    }
                    case CHOICE -> s.cycle(button == 1 ? -1 : 1);
                    case COLOR -> openPicker(s, i + bindRows());
                    default -> {
                        dragging = s;
                        applyDrag(mx);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pickDrag != 0) {
            applyPicker(mx, my);
            return true;
        }
        if (dragging != null) {
            applyDrag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        pickDrag = 0;
        return super.mouseReleased(mx, my, button);
    }

    private void applyDrag(double mx) {
        double f = Math.max(0, Math.min(1, (mx - ctrlX() - 4) / (trackW() - 8)));
        dragging.set(dragging.min + f * (dragging.max - dragging.min));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        pickerFor = null;
        if (aiView()) {
            AI.scroll(amount);
            return true;
        }
        if (mx < panelX()) {
            int content = (configView() ? presets.size() : visible().size()) * (CARD_H + GAP);
            listScroll = clamp(listScroll - amount * 18, 0, Math.max(0, content - bodyH()));
        } else {
            int content = friendsPanel() ? friendsContent : marketPanel() ? marketContent : (selected.settings.size() + bindRows()) * ROW_H;
            int view = bodyTop() + bodyH() - rowsTop() - 4;
            settingsScroll = clamp(settingsScroll - amount * 18, 0, Math.max(0, content - view));
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (aiView() && AI.charTyped(chr)) {
            return true;
        }
        if (friendFocus) {
            if (friendInput.length() < 16 && (Character.isLetterOrDigit(chr) && chr < 128 || chr == '_')) {
                friendInput += chr;
            }
            return true;
        }
        if (nameFocus) {
            if (presetName.length() < 24 && (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-')) {
                presetName += chr;
            }
            return true;
        }
        if (searchFocus && chr >= 32 && query.length() < 24) {
            query += chr;
            listScroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingFor != null) {
            if (keyCode == 256) {
                bindingFor = null;
            } else {
                bindingFor.key = keyCode == 259 || keyCode == 261 ? -1 : keyCode;
                bindingFor = null;
            }
            return true;
        }
        if (friendFocus) {
            if (keyCode == 259 && !friendInput.isEmpty()) {
                friendInput = friendInput.substring(0, friendInput.length() - 1);
            } else if (keyCode == 256) {
                friendFocus = false;
            } else if (keyCode == 257 || keyCode == 335) {
                addFriendFromInput((com.example.neurocanvas.module.Friends) NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Friends.class));
            }
            return true;
        }
        if (nameFocus) {
            if (keyCode == 259 && !presetName.isEmpty()) {
                presetName = presetName.substring(0, presetName.length() - 1);
            } else if (keyCode == 256) {
                nameFocus = false;
            } else if (keyCode == 257 || keyCode == 335) {
                nameFocus = false;
                savePreset();
            }
            return true;
        }
        if (aiView() && AI.keyPressed(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == 70 && (modifiers & 2) != 0) {
            searchFocus = true;
            return true;
        }
        if (searchFocus) {
            if (keyCode == 259 && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
            } else if (keyCode == 256) {
                query = "";
                searchFocus = false;
            } else if (keyCode == 257 || keyCode == 335) {
                searchFocus = false;
            }
            return true;
        }
        if (keyCode == RSHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Friends.class).setMenuOpen(false);
        NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Market.class).closePreviews();
        try {
            NeuroCanvasClient.modules().save();
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger("neurocanvas").error("Cannot save module config", e);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
