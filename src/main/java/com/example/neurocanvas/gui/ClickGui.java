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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/**
 * The menu window: a header with the logo, the search and the close button, a vertical list of sections on the left, and on
 * the right the modules of the section as cards. A card shows the module's name, description and switch; a click on it
 * unfolds the settings in place. Friends and Market open as a full page with a back button, and NeuroAura and Configs keep
 * their own two-column pages. The layout is computed from the screen size on every frame and click.
 */
public final class ClickGui extends Screen {
    private int W = 720;
    private int H = 420;
    private static final int HEADER = 56;
    private static final int PAD = 14;
    private static final int SIDE_W = 144;
    private static final int LIST_W = 176;
    private static final int CARD_H = 26;
    private static final int GAP = 4;
    private static final int ROW_H = 26;
    private static final int CTRL_W = 200;
    private static final int PANEL_HEAD = 46;
    private static final int HEAD_H = 46;
    private static final int SEC_H = 46;
    private static final int CARD_GAP = 8;
    private static final int KEY_H = 28;
    private static final int CCW = 170;
    private static final int RSHIFT = 344;
    private static final int WIN_R = 12;
    private static final net.minecraft.util.Identifier LOGO = new net.minecraft.util.Identifier("neurocanvas", "textures/logo.png");

    private static final AiPanel AI = new AiPanel();

    // Remembered between openings, so the menu comes back to the section and the unfolded cards you were on.
    private static Category tab;
    private static Module fullView;
    private static final Set<String> expanded = new LinkedHashSet<>();
    private static boolean expandedLoaded;
    private static double listScroll;
    private static double settingsScroll;

    private Setting dragging;
    private int dragX0;
    private int dragW;
    private float open;
    private final TextBox searchBox = new TextBox("Search modules...", 40, false);
    private final TextBox nameBox = new TextBox("Preset name", 24, false).allow(c -> Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-', false);
    private final TextBox friendBox = new TextBox("Add a friend by account name", 17, false).allow(c -> c < 128 && (Character.isLetterOrDigit(c) || c == '_'), false);
    private final TextBox roomBox = new TextBox("Room name (3-40 letters, digits, _ or -; the same for both)", 40, false)
            .allow(c -> c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '-'), true);
    private final TextBox packBox = new TextBox("Search on Modrinth", 40, false);
    private final TextBox[] boxes = {searchBox, nameBox, friendBox, roomBox, packBox};
    private Module bindingFor;
    private long lastNano = System.nanoTime();
    private long openedAt = System.currentTimeMillis();
    private final Map<Module, Float> switchAnim = new HashMap<>();
    private final Map<Module, Float> hoverAnim = new HashMap<>();
    private final Map<Module, Float> openAnim = new HashMap<>();
    private Category animTab;
    private float listIn = 1f;
    private float selY = Float.NaN;

    private net.minecraft.client.gui.screen.Screen parent;

    public ClickGui() {
        super(Text.literal("NeuroCanvas"));
    }

    /** Opened over another screen (the title menu, a pause menu); closing returns to it. */
    public ClickGui(net.minecraft.client.gui.screen.Screen parent) {
        this();
        this.parent = parent;
    }

    @Override
    public void close() {
        if (client != null && parent != null) {
            client.setScreen(parent);
        } else {
            super.close();
        }
    }

    @Override
    protected void init() {
        fit();
        NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Friends.class).setMenuOpen(true);
        NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Friends.class).refresh();
        if (tab == null) {
            tab = categories().get(0);
        }
        loadExpanded();
        if (tab == Category.CONFIG) {
            refreshPresets();
        }
        if (!Interface.animations()) {
            open = 1;
        }
    }

    private void fit() {
        W = Math.max(480, Math.min(720, width - 24));
        H = Math.max(300, Math.min(420, height - 24));
    }

    private static java.nio.file.Path expandedFile() {
        return NeuroCanvasClient.modules().menuFile();
    }

    /** The unfolded cards are remembered between openings and game sessions; with no file every card starts folded. */
    private static void loadExpanded() {
        if (expandedLoaded) {
            return;
        }
        expandedLoaded = true;
        try {
            java.nio.file.Path f = expandedFile();
            if (java.nio.file.Files.exists(f)) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
                if (o.has("open")) {
                    o.getAsJsonArray("open").forEach(e -> expanded.add(e.getAsString()));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A missing or broken file means everything starts folded.
        }
    }

    private static void saveExpanded() {
        try {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            expanded.forEach(a::add);
            o.add("open", a);
            java.nio.file.Files.writeString(expandedFile(), o.toString());
        } catch (IOException ignored) {
            // The state is a convenience; losing it is harmless.
        }
    }

    private static boolean special(Module m) {
        return m instanceof com.example.neurocanvas.module.Friends || m instanceof com.example.neurocanvas.module.Market;
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

    private List<Module> visible() {
        if (searchBox.isEmpty()) {
            return modulesIn(tab);
        }
        List<Module> out = new ArrayList<>();
        for (Module m : NeuroCanvasClient.modules().all()) {
            if (m.name.toLowerCase().contains(searchBox.text().toLowerCase())) {
                out.add(m);
            }
        }
        return out;
    }

    private static String keyName(int key) {
        return key < 0 ? "None" : InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
    }

    private static String subtitle(Category c) {
        return switch (c) {
            case VISUALS -> "Enhance your visual experience";
            case COSMETICS -> "Dress up your character";
            case COMBAT -> "See and understand every fight";
            case WORLD -> "Shape the world around you";
            case HUD -> "Information on your screen";
            case FRIENDS -> "Play together with friends";
            case MISC -> "The menu itself and extras";
            case AI -> "Describe a look and let the AI set it up";
            case CONFIG -> "Save, load and share all your settings";
        };
    }

    private static String version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("neurocanvas")
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    // ---- geometry ------------------------------------------------------------------------------

    private int wx() { return (width - W) / 2; }
    private int wy() { return (height - H) / 2; }
    private int bodyTop() { return wy() + HEADER; }
    private int bodyH() { return H - HEADER - PAD; }
    private int mainX() { return wx() + SIDE_W + 4; }
    private int mainW() { return W - SIDE_W - 4 - PAD; }

    private boolean split() { return aiView() || configView(); }
    private boolean fullPage() { return fullView != null && searchBox.isEmpty() && !split(); }

    private int listX() { return mainX(); }
    private int panelX() { return split() ? mainX() + LIST_W + 10 : mainX(); }
    private int panelW() { return split() ? mainW() - LIST_W - 10 : mainW(); }
    private int cardY(int i) { return bodyTop() + i * (CARD_H + GAP) - (int) listScroll; }
    private int rowsTop() { return bodyTop() + PANEL_HEAD; }
    private int rowY(int i) { return rowsTop() + i * ROW_H - (int) settingsScroll; }
    private int ctrlX() { return panelX() + panelW() - PAD - CTRL_W; }
    private int trackW() { return CTRL_W - 40; }

    private int sbX() { return wx() + SIDE_W + 20; }
    private int sbY() { return wy() + 16; }
    private int sbW() { return Math.max(120, Math.min(250, W - SIDE_W - 130)); }
    private static final int SB_H = 24;
    private int closeX() { return wx() + W - PAD - 22; }
    private int closeY() { return wy() + 16; }

    private int sideX() { return wx() + 10; }
    private int sideW() { return SIDE_W - 14; }
    private int userY() { return wy() + H - PAD - 42; }
    private static boolean needsAccount() { return !com.example.neurocanvas.client.AccountClient.signedIn(); }
    private int bannerY() { return userY() - 46; }
    private int sideItemH(int n) {
        int room = userY() - 8 - bodyTop() - (needsAccount() ? 46 : 0);
        return Math.max(20, Math.min(30, room / Math.max(1, n) - 2));
    }
    private int sideItemY(int i, int ih) { return bodyTop() + 2 + i * (ih + 2); }

    private int vy0() { return bodyTop() + SEC_H; }
    private int vy1() { return bodyTop() + bodyH() - 6; }
    private int cx0() { return mainX() + 10; }
    private int cx1() { return mainX() + mainW() - 14; }

    private int backX() { return panelX() + PAD; }
    private int backY() { return bodyTop() + 12; }

    // ---- card layout -----------------------------------------------------------------------------

    private record Item(int kind, Module m, Setting s, int y, int h) { }

    private static final int I_HEAD = 0, I_ROW = 1, I_KEY = 2, I_MODE = 3;
    private int cardsContent;

    private static int rowH(Setting s) {
        return s.kind == Setting.Kind.NUMBER ? 36 : 28;
    }

    private static float ease(float a) {
        return 1f - (float) Math.pow(1f - a, 3);
    }

    private int expandedExtra(Module m) {
        int h = 6 + 8;
        if (!m.alwaysOn) {
            h += 2 * KEY_H;
        }
        for (Setting s : m.settings) {
            h += rowH(s);
        }
        return h;
    }

    private float openOf(Module m) {
        return special(m) ? 0f : openAnim.getOrDefault(m, expanded.contains(m.name) ? 1f : 0f);
    }

    private List<Item> layout() {
        List<Item> out = new ArrayList<>();
        int top = vy0() - (int) listScroll;
        int y = top + 2;
        for (Module m : visible()) {
            float a = openOf(m);
            int extra = Math.round(expandedExtra(m) * ease(a));
            out.add(new Item(I_HEAD, m, null, y, HEAD_H + extra));
            if (a > 0.001f) {
                int ry = y + HEAD_H + 6;
                if (!m.alwaysOn) {
                    out.add(new Item(I_KEY, m, null, ry, KEY_H));
                    ry += KEY_H;
                    out.add(new Item(I_MODE, m, null, ry, KEY_H));
                    ry += KEY_H;
                }
                for (Setting s : m.settings) {
                    out.add(new Item(I_ROW, m, s, ry, rowH(s)));
                    ry += rowH(s);
                }
            }
            y += HEAD_H + extra + CARD_GAP;
        }
        cardsContent = y - top;
        return out;
    }

    private int toggleX() { return cx1() - 12 - 32; }

    // ---- rendering -------------------------------------------------------------------------------

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
        fit();
        Theme th = Interface.theme();
        ThemeStyle style = Interface.style();
        boolean fx = Interface.shaderEffects();
        boolean glassStyle = style.overlay() == ThemeStyle.Overlay.GLASS;
        boolean blurOn = fx && (style.blur() > 0.01f || glassStyle);
        if (blurOn) {
            GuiFx.captureBlur(client, ctx, Math.max(style.blur(), glassStyle ? 0.9f : 0f));
        }
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano) / 1_000_000_000f, 0.1f);
        lastNano = now;
        boolean anim = Interface.animations();
        open = anim ? Math.min(1f, open + dt * 5f) : 1f;
        if (animTab != tab) {
            animTab = tab;
            listIn = 0f;
        }
        listIn = anim ? Math.min(1f, listIn + dt * 3.2f) : 1f;
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
            Ui.shadow(ctx, wx(), wy(), W, H, 12);
        }
        boolean glass = glassStyle && blurOn && GuiFx.drawGlass(client, ctx, wx(), wy(), W, H, Ui.rad(WIN_R),
                th.bg(), th.accent(), th.accent2(), Interface.glassOpacity());
        if (!glass) {
            if (blurOn && GuiFx.blurAvailable()) {
                GuiFx.drawBlurred(ctx, wx(), wy(), W, H, WIN_R, 1f);
            }
            Ui.surface(ctx, wx(), wy(), W, H, WIN_R, th.bg());
        }
        Ui.glow(ctx, wx(), wy(), W, H, WIN_R, th.accent(), style.glow(), 16f);
        boolean effects = fx && open >= 1f && style.overlay() != ThemeStyle.Overlay.NONE && !glassStyle;
        if (effects && !style.overlayOnTop()) {
            GuiFx.overlay(client, ctx, style.overlay().ordinal(), wx(), wy(), W, H, Ui.rad(WIN_R), th.accent(), th.accent2(), style.overlayStrength());
        }

        renderHeader(ctx, th, mouseX, mouseY);
        renderSidebar(ctx, th, mouseX, mouseY, anim);
        if (aiView()) {
            AI.renderHistory(ctx, th, textRenderer, listX(), bodyTop(), LIST_W, bodyH(), mouseX, mouseY);
            AI.renderMain(ctx, th, textRenderer, panelX(), bodyTop(), panelW(), bodyH(), mouseX, mouseY);
        } else if (configView()) {
            renderConfigs(ctx, th, mouseX, mouseY);
        } else if (fullPage()) {
            renderFullPage(ctx, th, mouseX, mouseY);
        } else {
            renderCards(ctx, th, mouseX, mouseY, dt, anim);
            if (pickerFor != null) {
                drawPicker(ctx, th);
            }
        }

        if (effects && style.overlayOnTop()) {
            GuiFx.overlay(client, ctx, style.overlay().ordinal(), wx(), wy(), W, H, Ui.rad(WIN_R), th.accent(), th.accent2(), style.overlayStrength());
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
            default -> ctx.fill(0, 0, width, height, Ui.alpha(0x000000, 0.35f * ease));
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

    /** Text at a fractional size; the menu font has no small or large cut, so it is scaled. */
    private void textScaled(DrawContext ctx, String s, float x, float y, float k, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(k, k, 1f);
        Ui.text(ctx, textRenderer, s, 0, 0, color);
        ctx.getMatrices().pop();
    }

    private void iconScaled(DrawContext ctx, Category c, int x, int y, int k, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(k, k, 1f);
        Icons.draw(ctx, c, 0, 0, color);
        ctx.getMatrices().pop();
    }

    /** The text cut with ".." so that it fits the width in the menu font. */
    private String clip(String s, int maxW) {
        if (Ui.width(textRenderer, s) <= maxW) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && Ui.width(textRenderer, t + "..") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "..";
    }

    private void renderHeader(DrawContext ctx, Theme th, int mx, int my) {
        int lx = wx() + PAD + 2;
        int ly = wy() + 14;
        ctx.drawTexture(LOGO, lx - 4, ly - 4, 28, 28, 0f, 0f, 112, 112, 112, 112);
        textScaled(ctx, "NEURO", lx + 32, ly + 1, 1.15f, th.text());
        textScaled(ctx, "CANVAS", lx + 32 + Ui.width(textRenderer, "NEURO") * 1.15f + 4, ly + 1, 1.15f, th.accent());
        textScaled(ctx, "BOOST YOUR GAMEPLAY", lx + 32, ly + 15, 0.62f, th.dim());

        int sx = sbX();
        int sy = sbY();
        int sw = sbW();
        if (searchBox.focused) {
            Ui.roundRect(ctx, sx - 1, sy - 1, sw + 2, SB_H + 2, 12, Ui.alpha(th.accent() & 0xFFFFFF, 0.9f));
        }
        Ui.surface(ctx, sx, sy, sw, SB_H, 12, Ui.mix(th.panel2(), th.bg() | 0xFF000000, 0.15f));
        int dim = th.dim();
        Ui.ring(ctx, sx + 12, sy + SB_H / 2f - 1, 3.6f, 1.2f, dim);
        for (int i = 0; i < 3; i++) {
            ctx.fill(sx + 15 + i, sy + SB_H / 2 + 2 + i, sx + 16 + i, sy + SB_H / 2 + 3 + i, dim);
        }
        searchBox.drawText(ctx, textRenderer, th, sx + 18, sy, sw - 26, SB_H);

        int cx = closeX();
        int cy = closeY();
        boolean hover = inside(mx, my, cx - 2, cy - 2, 26, 26);
        if (hover) {
            Ui.roundRect(ctx, cx - 2, cy - 2, 26, 26, 8, Ui.alpha(0xFF5C6C, 0.28f));
        }
        int color = hover ? 0xFFFFFFFF : th.dim();
        for (int i = 0; i < 8; i++) {
            ctx.fill(cx + 7 + i, cy + 7 + i, cx + 8 + i, cy + 8 + i, color);
            ctx.fill(cx + 14 - i, cy + 7 + i, cx + 15 - i, cy + 8 + i, color);
        }
    }

    private void renderSidebar(DrawContext ctx, Theme th, int mx, int my, boolean anim) {
        List<Category> cats = categories();
        int ih = sideItemH(cats.size());
        int x = sideX();
        int w = sideW();
        int idx = searchBox.isEmpty() ? cats.indexOf(tab) : -1;
        if (idx >= 0) {
            float target = sideItemY(idx, ih);
            if (Float.isNaN(selY) || !anim) {
                selY = target;
            }
            selY += (target - selY) * 0.3f;
            int y = Math.round(selY);
            Ui.roundRect(ctx, x, y, w, ih, 9, Ui.alpha(th.accent() & 0xFFFFFF, 0.30f));
            Ui.roundRect(ctx, x + 1, y + 1, w - 2, ih / 2, 8, Ui.alpha(0xFFFFFF, 0.05f));
            Ui.glow(ctx, x, y, w, ih, 9, th.accent(), 0.35f, 8f);
            Ui.gradientH(ctx, x + 10, y + ih - 2, w - 20, 2, th.accent(), th.accent2());
        }
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            int y = sideItemY(i, ih);
            boolean hover = inside(mx, my, x, y, w, ih);
            boolean active = i == idx;
            int color = active || hover ? th.text() : th.dim();
            if (hover && !active) {
                Ui.roundRect(ctx, x, y, w, ih, 9, Ui.alpha(0xFFFFFF, 0.05f));
            }
            Icons.draw(ctx, c, x + 12, y + (ih - Icons.SIZE) / 2, active ? th.accent() : color);
            Ui.text(ctx, textRenderer, c.label, x + 12 + Icons.SIZE + 9, y + (ih - 8) / 2, color);
        }

        int uy = userY();
        Ui.surface(ctx, x, uy, w, 42, 10, th.panel());
        String name = client.getSession() == null ? "Player" : client.getSession().getUsername();
        int fx = x + 10;
        int fy = uy + 9;
        int fs = 24;
        if (client.player != null) {
            int skin = client.getTextureManager().getTexture(client.player.getSkinTexture()).getGlId();
            Ui.texturedRoundUv(ctx, fx, fy, fs, fs, 8, skin, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f);
            Ui.texturedRoundUv(ctx, fx, fy, fs, fs, 8, skin, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f);
        } else {
            Ui.roundRect(ctx, fx, fy, fs, fs, 8, th.panel2());
            Ui.textCentered(ctx, textRenderer, name.substring(0, 1).toUpperCase(), fx + fs / 2, fy + 8, th.text());
        }
        // The frame goes over the picture, so the square corners of the face are cut by it.
        Ui.outline(ctx, fx, fy, fs, fs, 8, -1.6f, 0f, Ui.alpha(th.accent() & 0xFFFFFF, 0.9f), Ui.alpha(th.accent() & 0xFFFFFF, 0.9f));
        Ui.roundRect(ctx, fx + fs - 5, fy + fs - 5, 9, 9, 4, th.bg() | 0xFF000000);
        Ui.roundRect(ctx, fx + fs - 4, fy + fs - 4, 7, 7, 3, 0xFF5CE08A);
        Ui.text(ctx, textRenderer, clip(name, w - 52), x + 44, uy + 11, th.text());
        if (com.example.neurocanvas.client.AccountClient.signedIn()) {
            String who = "@" + com.example.neurocanvas.client.AccountClient.name();
            Ui.text(ctx, textRenderer, clip(who, w - 52), x + 44, uy + 23, com.example.neurocanvas.client.AccountClient.verified() ? th.accent() : th.dim());
        } else {
            Ui.text(ctx, textRenderer, version(), x + 44, uy + 23, th.dim());
        }
        if (needsAccount()) {
            drawAccountBanner(ctx, th, x, bannerY(), w, mx, my);
        }
    }

    private void drawAccountBanner(DrawContext ctx, Theme th, int x, int y, int w, int mx, int my) {
        boolean linking = com.example.neurocanvas.client.AccountClient.state() == com.example.neurocanvas.client.AccountClient.State.LINKING;
        boolean hover = inside(mx, my, x, y, w, 40);
        Ui.roundRect(ctx, x, y, w, 40, 10, Ui.alpha(th.accent() & 0xFFFFFF, hover ? 0.42f : 0.28f));
        Ui.glow(ctx, x, y, w, 40, 10, th.accent(), 0.25f, 6f);
        String title = linking ? "Waiting for the browser..." : "Sign in / Register";
        String sub = linking ? "Code " + com.example.neurocanvas.client.AccountClient.linkCode() + "  (click: cancel)"
                : (com.example.neurocanvas.client.AccountClient.note().isEmpty() ? "Online, friends, sharing" : com.example.neurocanvas.client.AccountClient.note());
        Ui.text(ctx, textRenderer, clip(title, w - 16), x + 8, y + 9, th.text());
        Ui.text(ctx, textRenderer, clip(sub, w - 16), x + 8, y + 23, th.dim());
    }

    private void drawSection(DrawContext ctx, Theme th, int count) {
        int x = mainX() + 16;
        int y = bodyTop() + 10;
        if (searchBox.isEmpty()) {
            iconScaled(ctx, tab, x, y + 2, 2, th.accent());
            textScaled(ctx, tab.label, x + 28, y, 1.25f, th.text());
            Ui.text(ctx, textRenderer, subtitle(tab), x + 28, y + 17, th.dim());
        } else {
            textScaled(ctx, "Search results", x, y, 1.25f, th.text());
            Ui.text(ctx, textRenderer, "Modules matching \"" + searchBox.text() + "\"", x, y + 17, th.dim());
        }
        String n = count + (count == 1 ? " module" : " modules");
        Ui.text(ctx, textRenderer, n, mainX() + mainW() - 18 - Ui.width(textRenderer, n), y + 4, th.dim());
    }

    private void renderCards(DrawContext ctx, Theme th, int mx, int my, float dt, boolean anim) {
        Ui.surface(ctx, mainX(), bodyTop(), mainW(), bodyH(), 12, th.panel());
        List<Module> vis = visible();
        drawSection(ctx, th, vis.size());
        int viewH = vy1() - vy0();
        listScroll = clamp(listScroll, 0, Math.max(0, cardsContent - viewH));
        for (Module m : vis) {
            float target = expanded.contains(m.name) && !special(m) ? 1f : 0f;
            float a = openAnim.getOrDefault(m, target);
            openAnim.put(m, anim ? a + Math.signum(target - a) * Math.min(Math.abs(target - a), dt * 6f) : target);
            float on = m.enabled ? 1f : 0f;
            float s = switchAnim.getOrDefault(m, on);
            switchAnim.put(m, anim ? s + Math.signum(on - s) * Math.min(Math.abs(on - s), dt * 8f) : on);
        }
        List<Item> items = layout();
        ctx.enableScissor(mainX(), vy0(), mainX() + mainW(), vy1());
        int index = -1;
        int cardBottom = 0;
        float shift = 0f;
        for (Item it : items) {
            if (it.kind() == I_HEAD) {
                index++;
                float local = Math.max(0f, Math.min(1f, (listIn - index * 0.04f) / 0.55f));
                shift = (1f - ease(local)) * -14f;
                cardBottom = it.y() + it.h();
            }
            if (it.y() > vy1() || it.y() + it.h() < vy0()) {
                continue;
            }
            ctx.getMatrices().push();
            ctx.getMatrices().translate(shift, 0, 0);
            if (it.kind() == I_HEAD) {
                drawCardHead(ctx, th, it, mx, my, dt, anim);
            } else if (it.y() + it.h() > cardBottom) {
                if (it.y() < cardBottom) {
                    ctx.enableScissor(cx0(), Math.max(vy0(), it.y()), cx1(), Math.min(vy1(), cardBottom));
                    drawRow(ctx, th, it, mx, my);
                    ctx.disableScissor();
                }
            } else {
                drawRow(ctx, th, it, mx, my);
            }
            ctx.getMatrices().pop();
        }
        ctx.disableScissor();
        if (cardsContent > viewH) {
            int trackH = viewH;
            int barH = Math.max(18, trackH * viewH / cardsContent);
            int barY = vy0() + (int) ((trackH - barH) * (listScroll / Math.max(1.0, cardsContent - viewH)));
            Ui.roundRect(ctx, mainX() + mainW() - 8, barY, 3, barH, 1, Ui.alpha(th.accent() & 0xFFFFFF, 0.55f));
        }
    }

    private void drawCardHead(DrawContext ctx, Theme th, Item it, int mx, int my, float dt, boolean anim) {
        Module m = it.m();
        int x = cx0();
        int w = cx1() - cx0();
        int y = it.y();
        boolean hover = inside(mx, my, x, y, w, HEAD_H) && my >= vy0() && my < vy1();
        float ht = hover ? 1f : 0f;
        float h = hoverAnim.getOrDefault(m, 0f);
        h = anim ? h + Math.signum(ht - h) * Math.min(Math.abs(ht - h), dt * 9f) : ht;
        hoverAnim.put(m, h);
        boolean on = m.enabled && !m.alwaysOn;
        Ui.surface(ctx, x, y, w, it.h(), 10, Ui.mix(th.panel2(), th.hover(), h * 0.55f));
        if (on) {
            Ui.roundRect(ctx, x, y + 11, 3, HEAD_H - 22, 1, th.accent());
        }

        int bx = x + 14;
        int by = y + (HEAD_H - 28) / 2;
        Ui.roundRect(ctx, bx, by, 28, 28, 8, Ui.alpha(th.accent() & 0xFFFFFF, on ? 0.28f : 0.14f));
        Ui.textCentered(ctx, textRenderer, m.name.substring(0, 1).toUpperCase(), bx + 14, by + 10, on ? th.accent() : th.dim());

        int right = x + w - 12;
        boolean blocked = m.risky && Interface.safeMode();
        int controlsW = 0;
        if (blocked) {
            controlsW = Ui.width(textRenderer, "safe mode");
            Ui.text(ctx, textRenderer, "safe mode", right - controlsW, y + (HEAD_H - 8) / 2, th.dim());
        } else if (!m.alwaysOn) {
            controlsW = 32;
            drawSwitch(ctx, th, right - 32, y + (HEAD_H - 16) / 2, switchAnim.getOrDefault(m, on ? 1f : 0f));
        }
        int chevX = right - controlsW - 22;
        int chevY = y + HEAD_H / 2 - 3;
        int chev = h > 0.1f ? th.text() : th.dim();
        if (special(m)) {
            Ui.triangle(ctx, chevX + 2, chevY - 1, chevX + 2, chevY + 7, chevX + 7, chevY + 3, chev);
        } else if (openOf(m) > 0.5f) {
            Ui.triangle(ctx, chevX, chevY, chevX + 8, chevY, chevX + 4, chevY + 5, chev);
        } else {
            Ui.triangle(ctx, chevX + 2, chevY - 1, chevX + 2, chevY + 7, chevX + 7, chevY + 3, chev);
        }

        int tx = bx + 28 + 12;
        int tw = chevX - 10 - tx;
        Ui.text(ctx, textRenderer, clip(m.name, tw), tx, y + 10, th.text());
        Ui.text(ctx, textRenderer, clip(m.description, tw), tx, y + 24, th.dim());
    }

    private void drawSwitch(DrawContext ctx, Theme th, int x, int y, float a) {
        if (Interface.style().switchStyle() == ThemeStyle.SwitchStyle.BRACKET) {
            String text = a > 0.5f ? "[x]" : "[ ]";
            Ui.text(ctx, textRenderer, text, x + 32 - Ui.width(textRenderer, text), y + 4, a > 0.5f ? th.accent() : th.dim());
            return;
        }
        if (a > 0.02f) {
            Ui.glow(ctx, x, y, 32, 16, 8, th.accent(), a * 0.6f, 7f);
        }
        Ui.roundRect(ctx, x, y, 32, 16, 8, Ui.mix(th.hover(), th.accent(), a));
        Ui.roundRect(ctx, x + 2 + Math.round(a * 16), y + 2, 12, 12, 6, a > 0.5f ? 0xFFFFFFFF : th.dim());
    }

    private void drawRow(DrawContext ctx, Theme th, Item it, int mx, int my) {
        int x0 = cx0() + 14;
        int x1 = cx1() - 14;
        int y = it.y();
        if (it.kind() == I_KEY || it.kind() == I_MODE) {
            boolean key = it.kind() == I_KEY;
            Ui.text(ctx, textRenderer, key ? "Keybind" : "Bind mode", x0, y + (KEY_H - 8) / 2, th.text());
            boolean listening = key && bindingFor == it.m();
            Ui.roundRect(ctx, x1 - CCW, y + 4, CCW, KEY_H - 8, 7, listening ? Ui.alpha(th.accent() & 0xFFFFFF, 0.35f) : th.hover());
            String v = key ? (listening ? "Press a key..." : keyName(it.m().key)) : (it.m().hold ? "Hold" : "Toggle");
            Ui.textCentered(ctx, textRenderer, v, x1 - CCW / 2, y + (KEY_H - 8) / 2, listening ? th.accent() : th.text());
            return;
        }
        drawSetting(ctx, th, it.s(), x0, x1, y, mx, my);
    }

    private void drawSetting(DrawContext ctx, Theme th, Setting s, int x0, int x1, int y, int mx, int my) {
        int h = rowH(s);
        switch (s.kind) {
            case BUTTON -> {
                boolean hover = inside(mx, my, x0, y + 3, x1 - x0, h - 6);
                Ui.roundRect(ctx, x0, y + 3, x1 - x0, h - 6, 8, hover ? Ui.alpha(th.accent() & 0xFFFFFF, 0.55f) : Ui.alpha(th.accent() & 0xFFFFFF, 0.32f));
                Ui.textCentered(ctx, textRenderer, clip(s.name, x1 - x0 - 12), (x0 + x1) / 2, y + (h - 8) / 2, th.text());
            }
            case BOOL -> {
                Ui.text(ctx, textRenderer, clip(s.name, x1 - x0 - 44), x0, y + (h - 8) / 2, th.text());
                drawSwitch(ctx, th, x1 - 32, y + (h - 16) / 2, s.on() ? 1f : 0f);
            }
            case CHOICE -> {
                Ui.text(ctx, textRenderer, clip(s.name, x1 - x0 - CCW - 10), x0, y + (h - 8) / 2, th.text());
                Ui.roundRect(ctx, x1 - CCW, y + 4, CCW, h - 8, 7, th.hover());
                Ui.textCentered(ctx, textRenderer, clip(s.option(), CCW - 36), x1 - CCW / 2, y + (h - 8) / 2, th.text());
                Ui.text(ctx, textRenderer, "<", x1 - CCW + 9, y + (h - 8) / 2, th.dim());
                Ui.text(ctx, textRenderer, ">", x1 - 9 - Ui.width(textRenderer, ">"), y + (h - 8) / 2, th.dim());
            }
            case COLOR -> {
                int argb = s.argb();
                Ui.text(ctx, textRenderer, clip(s.name, x1 - x0 - 130), x0, y + (h - 8) / 2, th.text());
                String hex = String.format("#%06X", argb & 0xFFFFFF);
                Ui.text(ctx, textRenderer, hex, x1 - 58 - Ui.width(textRenderer, hex), y + (h - 8) / 2, th.dim());
                Ui.roundRect(ctx, x1 - 50, y + 5, 50, h - 10, 6, th.text());
                Ui.roundRect(ctx, x1 - 49, y + 6, 48, h - 12, 5, argb | 0xFF000000);
                int a = argb >>> 24;
                if (a < 255) {
                    Ui.roundRect(ctx, x1 - 47, y + h - 11, 44, 3, 1, Ui.alpha(0x000000, 0.45f));
                    Ui.roundRect(ctx, x1 - 47, y + h - 11, Math.max(2, 44 * a / 255), 3, 1, 0xFFFFFFFF);
                }
            }
            case NUMBER -> {
                Ui.text(ctx, textRenderer, clip(s.name, x1 - x0), x0, y + 3, th.text());
                int tw = x1 - 44 - x0;
                int ty = y + 22;
                float f = (float) ((s.value - s.min) / (s.max - s.min));
                Ui.roundRect(ctx, x0, ty, tw, 4, 2, th.hover());
                int fill = Math.max(0, Math.round(f * tw));
                if (fill > 0) {
                    Ui.roundRect(ctx, x0, ty, Math.max(4, fill), 4, 2, th.accent());
                    Ui.glow(ctx, x0, ty, Math.max(4, fill), 4, 2, th.accent(), 0.25f, 4f);
                }
                int kx = x0 + Math.round(f * (tw - 10));
                Ui.roundRect(ctx, kx - 1, ty - 4, 12, 12, 6, Ui.alpha(th.accent() & 0xFFFFFF, 0.3f));
                Ui.roundRect(ctx, kx, ty - 3, 10, 10, 5, 0xFFFFFFFF);
                String v = s.max - s.min >= 10 ? String.format("%.0f", s.value) : String.format("%.2f", s.value);
                Ui.text(ctx, textRenderer, v, x1 - Ui.width(textRenderer, v), ty - 2, th.dim());
            }
        }
    }

    /** The old two-column row, still used by the Friends and Market pages. */
    private void renderSetting(DrawContext ctx, Theme th, Setting s, int y, int mx, int my) {
        if (s.kind == Setting.Kind.BUTTON) {
            boolean hover = inside(mx, my, ctrlX(), y + 2, CTRL_W, ROW_H - 4);
            Ui.surface(ctx, ctrlX(), y + 2, CTRL_W, ROW_H - 4, 7, hover ? Ui.alpha(th.accent() & 0xFFFFFF, 0.55f) : Ui.alpha(th.accent() & 0xFFFFFF, 0.32f));
            Ui.textCentered(ctx, textRenderer, s.name, ctrlX() + CTRL_W / 2, y + (ROW_H - 8) / 2, th.text());
            return;
        }
        Ui.textMid(ctx, textRenderer, s.name, panelX() + PAD, y, ROW_H, th.text());
        int cx = ctrlX();
        switch (s.kind) {
            case BOOL -> drawSwitch(ctx, th, cx + CTRL_W - 32, y + (ROW_H - 16) / 2, s.on() ? 1f : 0f);
            case CHOICE -> {
                Ui.roundRect(ctx, cx, y + 3, CTRL_W, ROW_H - 6, 7, th.hover());
                Ui.textCentered(ctx, textRenderer, s.option(), cx + CTRL_W / 2, y + (ROW_H - 8) / 2, th.text());
                Ui.text(ctx, textRenderer, "<", cx + 8, y + (ROW_H - 8) / 2, th.dim());
                Ui.text(ctx, textRenderer, ">", cx + CTRL_W - 8 - Ui.width(textRenderer, ">"), y + (ROW_H - 8) / 2, th.dim());
            }
            default -> { }
        }
    }

    private void renderFullPage(DrawContext ctx, Theme th, int mx, int my) {
        int px = panelX();
        int pw = panelW();
        Ui.surface(ctx, px, bodyTop(), pw, bodyH(), 12, th.panel());
        boolean hb = inside(mx, my, backX(), backY(), 58, 20);
        Ui.roundRect(ctx, backX(), backY(), 58, 20, 8, hb ? th.hover() : th.panel2());
        Ui.triangle(ctx, backX() + 13, backY() + 10, backX() + 18, backY() + 6, backX() + 18, backY() + 14, th.dim());
        Ui.text(ctx, textRenderer, "Back", backX() + 24, backY() + 6, th.text());
        int tx = backX() + 70;
        Ui.text(ctx, textRenderer, fullView.name, tx, bodyTop() + 10, th.text());
        Ui.text(ctx, textRenderer, clip(fullView.description, pw - 70 - 2 * PAD), tx, bodyTop() + 24, th.dim());
        if (friendsPanel()) {
            renderFriends(ctx, th, mx, my);
        } else {
            renderMarket(ctx, th, mx, my);
        }
    }

    // ---- Friends panel -----------------------------------------------------------------------

    private record FRow(int kind, int y, int h, String text, Setting setting, boolean flag, boolean extra) { }

    private static final int F_SETTING = 0, F_HEADER = 1, F_USER = 2, F_FRIEND = 3, F_INPUT = 4, F_NOTE = 5, F_ROOM = 6, F_REQUEST = 7, F_OUT = 8;
    private int friendsContent;

    private boolean friendsPanel() {
        return fullView instanceof com.example.neurocanvas.module.Friends && searchBox.isEmpty();
    }

    private List<FRow> friendRows() {
        com.example.neurocanvas.module.Friends f = (com.example.neurocanvas.module.Friends) fullView;
        com.example.neurocanvas.module.NameTags tags = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class);
        List<FRow> rows = new ArrayList<>();
        int top = rowsTop() - (int) settingsScroll;
        int y = top;
        for (Setting s : f.settings) {
            rows.add(new FRow(F_SETTING, y, ROW_H, s.name, s, false, false));
            y += ROW_H;
        }
        y += 6;
        if (!f.signedIn()) {
            rows.add(new FRow(F_NOTE, y, 22, "Friends are accounts: press Sign in / Register on the left to play with friends", null, false, false));
            y += 28;
        }
        String room = tags.room();
        rows.add(new FRow(F_HEADER, y, 20, room.isEmpty() ? "Meet in a room (for friends on other addresses)" : "Room: " + room, null, false, false));
        y += 20;
        rows.add(new FRow(F_ROOM, y, 28, "", null, false, false));
        y += 28;
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
        if (f.signedIn()) {
            List<String> req = f.incomingRequests();
            if (!req.isEmpty()) {
                y += 6;
                rows.add(new FRow(F_HEADER, y, 20, "Friend requests (" + req.size() + ")", null, false, false));
                y += 20;
                for (String n : req) {
                    rows.add(new FRow(F_REQUEST, y, 24, n, null, false, false));
                    y += 24;
                }
            }
            y += 6;
            List<String> mine = f.friendNames();
            rows.add(new FRow(F_HEADER, y, 20, "My friends (" + mine.size() + ")", null, false, false));
            y += 20;
            if (mine.isEmpty()) {
                rows.add(new FRow(F_NOTE, y, 22, "No friends yet: tap the star next to a player, or type an account name below", null, false, false));
                y += 22;
            }
            for (String n : mine) {
                String nick = tags.nickOf(n);
                rows.add(new FRow(F_FRIEND, y, 24, n, null, nick != null, nick != null && f.inMenu(nick)));
                y += 24;
            }
            List<String> out = f.outgoingRequests();
            if (!out.isEmpty()) {
                y += 6;
                rows.add(new FRow(F_HEADER, y, 20, "Waiting for an answer (" + out.size() + ")", null, false, false));
                y += 20;
                for (String n : out) {
                    rows.add(new FRow(F_OUT, y, 24, n, null, false, false));
                    y += 24;
                }
            }
            if (!f.note().isEmpty()) {
                rows.add(new FRow(F_NOTE, y, 22, f.note(), null, false, false));
                y += 22;
            }
            y += 4;
            rows.add(new FRow(F_INPUT, y, 28, "", null, false, false));
            y += 28;
        }
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
                    com.example.neurocanvas.module.NameTags rowTags = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class);
                    String rowAccount = r.kind() == F_USER ? rowTags.accountOf(r.text()) : null;
                    int nameColor = r.kind() == F_FRIEND && !r.flag() ? th.dim() : th.text();
                    String shownName = r.kind() == F_FRIEND ? "@" + r.text() : r.text();
                    Ui.textMid(ctx, textRenderer, shownName, px + 10, r.y(), 22, nameColor);
                    int after = px + 10 + Ui.width(textRenderer, shownName) + 8;
                    if (r.kind() == F_FRIEND) {
                        ctx.fill(after, r.y() + 9, after + 5, r.y() + 14, r.flag() ? 0xFF5CE08A : th.hover());
                        after += 12;
                        if (r.flag()) {
                            String as = "as " + rowTags.nickOf(r.text());
                            Ui.text(ctx, textRenderer, as, after, r.y() + 7, th.dim());
                            after += Ui.width(textRenderer, as) + 8;
                        }
                    } else {
                        String tag = rowAccount != null ? "@" + rowAccount : "no account";
                        Ui.text(ctx, textRenderer, tag, after, r.y() + 7, rowAccount != null ? th.accent() : th.dim());
                        after += Ui.width(textRenderer, tag) + 8;
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
                        if (rowAccount != null) {
                            com.example.neurocanvas.gui.Icons.star(ctx, bx + 6, r.y() + 7, r.flag(), r.flag() ? 0xFFFFD23F : th.dim());
                        }
                    } else {
                        boolean xHover = inside(mx, my, bx, r.y(), 26, 22);
                        Ui.textCentered(ctx, textRenderer, "x", bx + 13, r.y() + 7, xHover ? 0xFFFF6B6B : th.dim());
                    }
                }
                case F_REQUEST, F_OUT -> {
                    boolean hover = inside(mx, my, px, r.y(), pw, 22) && my >= viewY && my < viewY + viewH;
                    Ui.surface(ctx, px, r.y(), pw, 22, 6, hover ? th.hover() : th.panel2());
                    Ui.textMid(ctx, textRenderer, "@" + r.text(), px + 10, r.y(), 22, th.text());
                    int bx = px + pw - 26;
                    boolean xHover = inside(mx, my, bx, r.y(), 26, 22);
                    Ui.textCentered(ctx, textRenderer, "x", bx + 13, r.y() + 7, xHover ? 0xFFFF6B6B : th.dim());
                    if (r.kind() == F_REQUEST) {
                        boolean ah = inside(mx, my, px + pw - 88, r.y() + 2, 56, 18);
                        Ui.roundRect(ctx, px + pw - 88, r.y() + 2, 56, 18, 5, ah ? th.accent2() : th.accent());
                        Ui.textCentered(ctx, textRenderer, "Accept", px + pw - 60, r.y() + 7, th.bg() | 0xFF000000);
                    } else {
                        Ui.text(ctx, textRenderer, "waiting", px + pw - 26 - 8 - Ui.width(textRenderer, "waiting"), r.y() + 7, th.dim());
                    }
                }
                case F_ROOM -> {
                    int btnW = 60;
                    int boxW = pw - btnW - 8;
                    com.example.neurocanvas.module.NameTags roomTags = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class);
                    roomBox.draw(ctx, textRenderer, th, px, r.y(), boxW, 24);
                    boolean leave = roomBox.isEmpty() && !roomTags.room().isEmpty();
                    boolean rh = inside(mx, my, px + boxW + 8, r.y(), btnW, 24);
                    Ui.roundRect(ctx, px + boxW + 8, r.y(), btnW, 24, 6, rh ? th.accent2() : th.accent());
                    Ui.textCentered(ctx, textRenderer, leave ? "Leave" : "Join", px + boxW + 8 + btnW / 2, r.y() + 8, th.bg() | 0xFF000000);
                }
                case F_INPUT -> {
                    int addW = 60;
                    int boxW = pw - addW - 8;
                    friendBox.draw(ctx, textRenderer, th, px, r.y(), boxW, 24);
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
        com.example.neurocanvas.module.Friends f = (com.example.neurocanvas.module.Friends) fullView;
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
                    String clickedAccount = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class).accountOf(r.text());
                    if (mx >= px + pw - 30) {
                        if (clickedAccount != null && f.signedIn()) {
                            if (f.isFriendAccount(clickedAccount)) {
                                f.removeAccount(clickedAccount);
                            } else {
                                f.addAccount(clickedAccount);
                            }
                            Interface.click(r.flag() ? 0.8f : 1.3f);
                        }
                    } else if (mx >= px + pw - 70 && mx < px + pw - 34 && !showcase.itemsOf(r.text()).isEmpty()) {
                        showcase.getAll(r.text());
                        Interface.click(1.3f);
                    }
                    return true;
                }
                case F_FRIEND -> {
                    if (mx >= px + pw - 30) {
                        f.removeAccount(r.text());
                        Interface.click(0.8f);
                    }
                    return true;
                }
                case F_REQUEST -> {
                    if (mx >= px + pw - 30) {
                        f.removeAccount(r.text());
                        Interface.click(0.8f);
                    } else if (mx >= px + pw - 88) {
                        f.addAccount(r.text());
                        Interface.click(1.3f);
                    }
                    return true;
                }
                case F_OUT -> {
                    if (mx >= px + pw - 30) {
                        f.removeAccount(r.text());
                        Interface.click(0.8f);
                    }
                    return true;
                }
                case F_ROOM -> {
                    int btnW = 60;
                    int boxW = pw - btnW - 8;
                    if (mx >= px + boxW + 8 && mx < px + boxW + 8 + btnW) {
                        com.example.neurocanvas.module.NameTags roomTags = NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class);
                        if (roomBox.isEmpty()) {
                            roomTags.setRoom("");
                        } else if (roomTags.setRoom(roomBox.text())) {
                            roomBox.set("");
                        }
                        Interface.click(1.3f);
                    } else {
                        clickBox(roomBox, mx);
                    }
                    return true;
                }
                case F_INPUT -> {
                    int addW = 60;
                    int boxW = pw - addW - 8;
                    if (mx >= px + boxW + 8 && mx < px + boxW + 8 + addW) {
                        addFriendFromInput(f);
                    } else {
                        clickBox(friendBox, mx);
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
        if (f.signedIn() && !friendBox.isEmpty()) {
            f.addAccount(friendBox.text());
            friendBox.set("");
            Interface.click(1.3f);
        }
    }

    // ---- Market panel -------------------------------------------------------------------------

    private record MRow(int kind, int y, int h, String text, Setting setting, com.example.neurocanvas.client.MarketClient.Entry entry, int col,
                        com.example.neurocanvas.client.ModrinthClient.Hit hit) {
        MRow(int kind, int y, int h, String text, Setting setting, com.example.neurocanvas.client.MarketClient.Entry entry, int col) {
            this(kind, y, h, text, setting, entry, col, null);
        }
    }

    private static final int M_SETTING = 0, M_NOTE = 1, M_CARD = 2, M_TABS = 3, M_SEARCH = 4, M_PACK = 5, M_MORE = 6;
    private static final int TAB_H = 26;
    private static final int PACK_H = 66;
    private static final int PREVIEW_H = 104;
    private int marketContent;

    private boolean marketPanel() {
        return fullView instanceof com.example.neurocanvas.module.Market && searchBox.isEmpty();
    }

    /** Where each tab of the market sits inside a row of width pw: {x, line, width}. They wrap onto the next line when they do not fit. */
    private int[][] marketTabRects(int pw) {
        String[] tabs = com.example.neurocanvas.module.Market.TABS;
        int[][] out = new int[tabs.length][];
        int x = 0;
        int line = 0;
        for (int i = 0; i < tabs.length; i++) {
            int w = Ui.width(textRenderer, tabs[i]) + 24;
            if (x > 0 && x + w > pw) {
                line++;
                x = 0;
            }
            out[i] = new int[] {x, line, w};
            x += w + 6;
        }
        return out;
    }

    private int marketTabsHeight(int pw) {
        int[][] r = marketTabRects(pw);
        return (r[r.length - 1][1] + 1) * TAB_H + 6;
    }

    private List<MRow> marketRows() {
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) fullView;
        m.ensureLoaded();
        List<MRow> rows = new ArrayList<>();
        int top = rowsTop() - (int) settingsScroll;
        int y = top;
        int pw = panelW() - 2 * PAD;
        int th = marketTabsHeight(pw);
        rows.add(new MRow(M_TABS, y, th, "", null, null, 0));
        y += th;
        com.example.neurocanvas.client.ModrinthClient.Kind pack = m.packKind();
        if (pack != null) {
            rows.add(new MRow(M_SEARCH, y, 30, "", null, null, 0));
            y += 36;
            if (pack == com.example.neurocanvas.client.ModrinthClient.Kind.SHADER && !com.example.neurocanvas.module.Market.irisPresent()) {
                rows.add(new MRow(M_NOTE, y, 22, "Shaders need the Iris mod (not installed): packs are saved, but nothing will use them", null, null, 0));
                y += 22;
            }
            if (!m.status().isEmpty()) {
                rows.add(new MRow(M_NOTE, y, 22, m.status(), null, null, 0));
                y += 22;
            }
            if (m.hits() != null) {
                for (com.example.neurocanvas.client.ModrinthClient.Hit h : m.hits()) {
                    rows.add(new MRow(M_PACK, y, PACK_H, h.title(), null, null, 0, h));
                    y += PACK_H + 8;
                }
                if (m.packMore() && !m.packLoading()) {
                    rows.add(new MRow(M_MORE, y, 26, "Load more", null, null, 0));
                    y += 34;
                }
            }
            marketContent = y - top;
            return rows;
        }
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

    private static String compact(long n) {
        return n >= 1_000_000 ? String.format("%.1fM", n / 1_000_000.0) : n >= 1000 ? (n / 1000) + "K" : Long.toString(n);
    }

    private void renderPackRow(DrawContext ctx, Theme th, MRow r, int px, int pw, int mx, int my, int viewY, int viewH) {
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) fullView;
        int y = r.y();
        if (y + r.h() < viewY || y > viewY + viewH) {
            return;
        }
        boolean hover = inside(mx, my, px, y, pw, r.h()) && my >= viewY && my < viewY + viewH;
        Ui.surface(ctx, px, y, pw, r.h(), 8, hover ? th.hover() : th.panel2());
        com.example.neurocanvas.client.ModrinthClient.Hit h = r.hit();
        int bw = 74;
        int textW = pw - bw - 34;
        net.minecraft.util.Identifier icon = m.icon(h);
        if (icon != null) {
            Ui.texturedRoundUv(ctx, px + 10, y + 12, 42, 42, 10, client.getTextureManager().getTexture(icon).getGlId(), 0f, 0f, 1f, 1f);
        } else {
            // The picture is still on its way: a soft block in the colour Modrinth gives the project.
            int base = h.color() != 0 ? (h.color() & 0xFFFFFF) : (th.accent() & 0xFFFFFF);
            Ui.roundRect(ctx, px + 10, y + 12, 42, 42, 10, Ui.alpha(base, 0.35f));
        }
        int tx = px + 62;
        Ui.text(ctx, textRenderer, clip(h.title(), textW - 50), tx, y + 9, th.text());
        Ui.text(ctx, textRenderer, clip("by " + h.author() + "  -  " + compact(h.downloads()) + " downloads", textW - 50), tx, y + 23, th.accent());
        java.util.List<String> lines = Ui.wrap(textRenderer, h.description(), textW - 50);
        if (!lines.isEmpty()) {
            Ui.text(ctx, textRenderer, clip(lines.get(0) + (lines.size() > 1 ? " ..." : ""), textW - 50), tx, y + 38, th.dim());
        }
        boolean busy = m.busy(h);
        boolean on = m.packActive(h);
        boolean have = m.packInstalled(h);
        String label = busy ? "..." : on ? "On" : have ? "Use" : "Get";
        int bx = px + pw - bw - 10;
        boolean bh = inside(mx, my, bx, y + (r.h() - 26) / 2, bw, 26);
        int fill = busy ? th.hover() : on ? Ui.alpha(0x4ADE80, 0.30f) : (bh ? th.accent2() : th.accent());
        Ui.roundRect(ctx, bx, y + (r.h() - 26) / 2, bw, 26, 8, fill);
        Ui.textCentered(ctx, textRenderer, label, bx + bw / 2, y + (r.h() - 8) / 2, on ? 0xFF4ADE80 : (busy ? th.dim() : (th.bg() | 0xFF000000)));
    }

    private void renderMarketTabs(DrawContext ctx, Theme th, MRow r, int px, int pw, int mx, int my) {
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) fullView;
        String[] tabs = com.example.neurocanvas.module.Market.TABS;
        int[][] rects = marketTabRects(pw);
        for (int i = 0; i < tabs.length; i++) {
            int tx = px + rects[i][0];
            int ty = r.y() + rects[i][1] * TAB_H;
            boolean on = i == m.kind();
            boolean hover = inside(mx, my, tx, ty, rects[i][2], TAB_H - 4);
            if (on) {
                Ui.glow(ctx, tx, ty, rects[i][2], TAB_H - 4, 9, th.accent(), 0.25f, 6f);
            }
            Ui.roundRect(ctx, tx, ty, rects[i][2], TAB_H - 4, 9, on ? Ui.alpha(th.accent() & 0xFFFFFF, 0.38f) : (hover ? th.hover() : th.panel2()));
            Ui.textCentered(ctx, textRenderer, tabs[i], tx + rects[i][2] / 2, ty + (TAB_H - 4 - 8) / 2, on || hover ? th.text() : th.dim());
        }
    }

    private void renderPackSearch(DrawContext ctx, Theme th, MRow r, int px, int pw, int mx, int my) {
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) fullView;
        int btnW = 76;
        int boxW = pw - btnW - 8;
        if (packBox.focused) {
            Ui.roundRect(ctx, px - 1, r.y() - 1, boxW + 2, 32, 9, th.accent());
        }
        Ui.roundRect(ctx, px, r.y(), boxW, 30, 8, th.panel2());
        packBox.drawText(ctx, textRenderer, th, px + 2, r.y(), boxW - 4, 30);
        boolean hover = inside(mx, my, px + boxW + 8, r.y(), btnW, 30);
        Ui.roundRect(ctx, px + boxW + 8, r.y(), btnW, 30, 8, hover ? th.accent2() : th.accent());
        Ui.textCentered(ctx, textRenderer, m.packLoading() ? "..." : "Search", px + boxW + 8 + btnW / 2, r.y() + 11, th.bg() | 0xFF000000);
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
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) fullView;
        int px = panelX() + PAD;
        int pw = panelW() - 2 * PAD;
        int viewY = rowsTop();
        int viewH = bodyTop() + bodyH() - viewY - 4;
        int cw = marketCardW();
        ctx.enableScissor(panelX(), viewY, panelX() + panelW(), viewY + viewH);
        for (MRow r : marketRows()) {
            if (r.kind() == M_TABS) {
                renderMarketTabs(ctx, th, r, px, pw, mx, my);
                continue;
            }
            if (r.kind() == M_SEARCH) {
                renderPackSearch(ctx, th, r, px, pw, mx, my);
                continue;
            }
            if (r.kind() == M_PACK) {
                renderPackRow(ctx, th, r, px, pw, mx, my, viewY, viewH);
                continue;
            }
            if (r.kind() == M_MORE) {
                boolean mh = inside(mx, my, px, r.y(), pw, 26);
                Ui.roundRect(ctx, px, r.y(), pw, 26, 8, mh ? th.hover() : th.panel2());
                Ui.textCentered(ctx, textRenderer, "Load more", px + pw / 2, r.y() + 9, th.text());
                continue;
            }
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
            if (e.code() != null) {
                boolean ask = m.reportPending(e);
                int fx = pvx + pvw - (com.example.neurocanvas.client.AdminStore.has() ? 38 : 20);
                Ui.roundRect(ctx, fx, pvy + 4, 16, 14, 5, ask ? 0xFFE59A35 : 0xB0404050);
                Ui.textCentered(ctx, textRenderer, "\u2691", fx + 8, pvy + 7, 0xFFFFFFFF);
            }
            if (com.example.neurocanvas.client.AdminStore.has() && e.reports() > 0) {
                Ui.text(ctx, textRenderer, e.reports() + " report" + (e.reports() == 1 ? "" : "s"), pvx + 8, pvy + pvh - 12, 0xFFFF6B6B);
            }
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
            boolean emote = p.type.equals("emotes");
            onSteve |= emote;
            float radius = onSteve ? 21f : p.radius;
            float[] center = onSteve ? new float[] {0, 16, 0} : p.center;
            float s = Math.min(w, h) * 0.45f / radius;
            long ms = System.currentTimeMillis();
            float angle = (ms % 9000) / 9000f * (float) (Math.PI * 2);
            org.joml.Matrix4f m = new org.joml.Matrix4f(ctx.getMatrices().peek().getPositionMatrix());
            m.translate(x + w / 2f, y + h / 2f, 100f).scale(s, -s, s).rotateX(0.2f).rotateY(angle)
                    .translate(-center[0], -center[1], -center[2]);
            if (emote) {
                com.example.neurocanvas.avatar.BbModel.Anim a = p.animation != null ? p.model.find(p.animation) : null;
                if (a == null && !p.model.model.animations.isEmpty()) {
                    a = p.model.model.animations.values().iterator().next();
                }
                com.example.neurocanvas.avatar.Mannequin.drawEmote(p.model, a, m, ms / 1000.0);
            } else if (onSteve) {
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
        com.example.neurocanvas.module.Market m = (com.example.neurocanvas.module.Market) fullView;
        int px = panelX() + PAD;
        int cw = marketCardW();
        if (my < rowsTop() || my >= bodyTop() + bodyH() - 4) {
            return false;
        }
        int pw = panelW() - 2 * PAD;
        for (MRow r : marketRows()) {
            if (r.kind() == M_TABS) {
                int[][] rects = marketTabRects(pw);
                for (int i = 0; i < rects.length; i++) {
                    if (inside(mx, my, px + rects[i][0], r.y() + rects[i][1] * TAB_H, rects[i][2], TAB_H - 4)) {
                        if (i != m.kind()) {
                            m.setKind(i);
                            packBox.set("");
                            packBox.focused = false;
                            settingsScroll = 0;
                            Interface.click(1.0f);
                        }
                        return true;
                    }
                }
            } else if (r.kind() == M_SEARCH) {
                int btnW = 76;
                int boxW = pw - btnW - 8;
                if (inside(mx, my, px + boxW + 8, r.y(), btnW, 30)) {
                    m.searchPacks(packBox.text());
                    packBox.focused = false;
                    Interface.click(1.2f);
                    return true;
                }
                if (inside(mx, my, px, r.y(), boxW, 30)) {
                    clickBox(packBox, mx);
                    return true;
                }
            } else if (r.kind() == M_PACK) {
                int bw = 74;
                if (inside(mx, my, px + pw - bw - 10, r.y() + (r.h() - 26) / 2, bw, 26)) {
                    if (!m.packActive(r.hit())) {
                        m.getPack(r.hit());
                    }
                    Interface.click(1.3f);
                    return true;
                }
            } else if (r.kind() == M_MORE) {
                if (inside(mx, my, px, r.y(), pw, 26)) {
                    m.morePacks();
                    Interface.click(1.1f);
                    return true;
                }
            } else if (r.kind() == M_SETTING) {
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
                if (r.entry().code() != null && inside(mx, my, cx + 4 + cw - 8 - (com.example.neurocanvas.client.AdminStore.has() ? 38 : 20), r.y() + 8, 16, 14)) {
                    m.requestReport(r.entry());
                    Interface.click(0.9f);
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

    private static final int POP_W = 216;
    private static final int POP_H = 164;
    private static final int PICK_W = 200;
    private static final int SV_H = 76;
    private static final int[] SWATCHES = {0xA855F7, 0x3B82F6, 0x22D3EE, 0xEC4899, 0x10B981, 0xFACC15, 0xFFFFFF};

    private Setting pickerFor;
    private int pickerY;
    private int pickerRowH;
    private float pickH;
    private float pickS;
    private float pickV;
    private float pickA;
    private int pickDrag;

    private void openPicker(Setting s, int rowY, int rowH) {
        if (pickerFor == s) {
            pickerFor = null;
            return;
        }
        pickerFor = s;
        pickerY = rowY;
        pickerRowH = rowH;
        int argb = s.argb();
        float[] hsv = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        pickH = hsv[0];
        pickS = hsv[1];
        pickV = hsv[2];
        pickA = (argb >>> 24) / 255f;
    }

    private int popX() { return Math.max(mainX() + 4, cx1() - POP_W); }

    private int popY() {
        int below = pickerY + pickerRowH + 2;
        return below + POP_H > wy() + H - 6 ? Math.max(wy() + 4, pickerY - POP_H - 2) : below;
    }

    private int pickX() { return popX() + 8; }
    private int svY() { return popY() + 8; }
    private int hueY() { return svY() + SV_H + 8; }
    private int alphaY() { return hueY() + 18; }
    private int swatchY() { return alphaY() + 32; }

    private void applyPicker(double mx, double my) {
        int x = pickX();
        switch (pickDrag) {
            case 1 -> {
                pickS = (float) clamp((mx - x) / PICK_W, 0, 1);
                pickV = 1f - (float) clamp((my - svY()) / SV_H, 0, 1);
            }
            case 2 -> pickH = (float) clamp((mx - x) / PICK_W, 0, 1);
            case 3 -> pickA = (float) clamp((mx - x) / PICK_W, 0, 1);
            default -> { }
        }
        int rgb = java.awt.Color.HSBtoRGB(pickH, pickS, pickV) & 0xFFFFFF;
        pickerFor.setArgb((Math.round(pickA * 255) << 24) | rgb);
    }

    private void pickSwatch(int rgb) {
        float[] hsv = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        pickH = hsv[0];
        pickS = hsv[1];
        pickV = hsv[2];
        pickerFor.setArgb((Math.round(pickA * 255) << 24) | (rgb & 0xFFFFFF));
    }

    private void drawPicker(DrawContext ctx, Theme th) {
        int px = popX();
        int py = popY();
        int x = pickX();
        Ui.shadow(ctx, px, py, POP_W, POP_H, 10);
        Ui.roundRect(ctx, px, py, POP_W, POP_H, 10, Ui.mix(th.bg() | 0xFF000000, th.panel2() | 0xFF000000, 0.35f));
        Ui.surface(ctx, px, py, POP_W, POP_H, 10, th.panel2());

        int sy = svY();
        for (int i = 0; i < PICK_W; i++) {
            int top = 0xFF000000 | (java.awt.Color.HSBtoRGB(pickH, i / (float) (PICK_W - 1), 1f) & 0xFFFFFF);
            ctx.fillGradient(x + i, sy, x + i + 1, sy + SV_H, top, 0xFF000000);
        }
        int mx = x + Math.round(pickS * (PICK_W - 1));
        int my = sy + Math.round((1f - pickV) * (SV_H - 1));
        int rgb = java.awt.Color.HSBtoRGB(pickH, pickS, pickV) & 0xFFFFFF;
        Ui.roundRect(ctx, mx - 4, my - 4, 8, 8, 4, 0xFFFFFFFF);
        Ui.roundRect(ctx, mx - 3, my - 3, 6, 6, 3, 0xFF000000 | rgb);

        int hy = hueY();
        Ui.rainbowH(ctx, x, hy, PICK_W, 10);
        Ui.roundRect(ctx, x + Math.round(pickH * (PICK_W - 6)), hy - 1, 6, 12, 3, 0xFFFFFFFF);

        int ay = alphaY();
        for (int i = 0; i < PICK_W; i += 5) {
            for (int j = 0; j < 10; j += 5) {
                ctx.fill(x + i, ay + j, Math.min(x + i + 5, x + PICK_W), ay + j + 5, ((i / 5 + j / 5) % 2 == 0) ? 0xFF8A8A8A : 0xFF5A5A5A);
            }
        }
        Ui.gradientH(ctx, x, ay, PICK_W, 10, rgb, 0xFF000000 | rgb);
        Ui.roundRect(ctx, x + Math.round(pickA * (PICK_W - 6)), ay - 1, 6, 12, 3, 0xFFFFFFFF);

        String info = String.format("#%06X   alpha %d%%", rgb, Math.round(pickA * 100));
        Ui.text(ctx, textRenderer, info, x, ay + 16, th.dim());
        int sw = swatchY();
        for (int i = 0; i < SWATCHES.length; i++) {
            Ui.roundRect(ctx, x + i * 28, sw, 24, 14, 5, 0xFFFFFFFF);
            Ui.roundRect(ctx, x + i * 28 + 1, sw + 1, 22, 12, 4, 0xFF000000 | SWATCHES[i]);
        }
    }

    // ---- Configs tab ---------------------------------------------------------------------

    private record Btn(String label, int x, int y, int w, int h, Runnable action, boolean primary) { }

    private final List<String> presets = new ArrayList<>();
    private String selectedPreset = "";
    private String status = "";

    private boolean aiView() {
        return tab == Category.AI && searchBox.isEmpty();
    }

    private boolean configView() {
        return tab == Category.CONFIG && searchBox.isEmpty();
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
        String name = ModuleManager.cleanName(nameBox.text().isBlank() ? selectedPreset : nameBox.text());
        if (name.isEmpty()) {
            status = "Type a name first";
            return;
        }
        try {
            NeuroCanvasClient.modules().savePreset(name);
            selectedPreset = name;
            nameBox.set(name);
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
        nameBox.draw(ctx, textRenderer, th, nameBoxX(), nameBoxY(), nameBoxW(), 22);

        for (Btn b : configButtons()) {
            boolean hover = inside(mx, my, b.x(), b.y(), b.w(), b.h());
            int bg = b.primary() ? (hover ? th.accent2() : th.accent()) : (hover ? th.hover() : th.panel2());
            Ui.roundRect(ctx, b.x(), b.y(), b.w(), b.h(), 6, bg);
            Ui.textCentered(ctx, textRenderer, clip(b.label(), b.w() - 10), b.x() + b.w() / 2, b.y() + 8,
                    b.primary() ? (th.bg() | 0xFF000000) : th.text());
        }
        if (!status.isEmpty()) {
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(status, panelW() - 2 * PAD), panelX() + PAD, rowsTop() + 40 + 32 * 4 + 6, th.dim());
        }
    }

    private boolean configClicked(double mx, double my, int button) {
        if (inside(mx, my, nameBoxX(), nameBoxY(), nameBoxW(), 22)) {
            clickBox(nameBox, mx);
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
                    nameBox.set(selectedPreset);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }


    private void switchTab(Category c) {
        searchBox.set("");
        pickerFor = null;
        searchBox.focused = false;
        if (tab != c) {
            Interface.click(1.0f);
            tab = c;
            fullView = null;
            if (tab == Category.CONFIG) {
                refreshPresets();
            }
            listScroll = 0;
            settingsScroll = 0;
        } else if (fullView != null) {
            fullView = null;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (Updater.latest() != null && inside(mx, my, (width - bannerW()) / 2, wy() + H + 10, bannerW(), 20)) {
            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(Updater.url()));
            return true;
        }
        if (pickerFor != null) {
            if (inside(mx, my, popX(), popY(), POP_W, POP_H)) {
                if (inside(mx, my, pickX(), svY(), PICK_W, SV_H)) {
                    pickDrag = 1;
                } else if (inside(mx, my, pickX(), hueY() - 2, PICK_W, 14)) {
                    pickDrag = 2;
                } else if (inside(mx, my, pickX(), alphaY() - 2, PICK_W, 14)) {
                    pickDrag = 3;
                } else if (my >= swatchY() && my < swatchY() + 14) {
                    int i = (int) ((mx - pickX()) / 28);
                    if (i >= 0 && i < SWATCHES.length && (mx - pickX()) % 28 < 24) {
                        pickSwatch(SWATCHES[i]);
                        Interface.click(1.2f);
                    }
                }
                if (pickDrag != 0) {
                    applyPicker(mx, my);
                }
                return true;
            }
            pickerFor = null;
        }
        if (inside(mx, my, closeX() - 2, closeY() - 2, 26, 26)) {
            Interface.click(0.8f);
            close();
            return true;
        }
        for (TextBox b : boxes) {
            b.focused = false;
        }
        AI.unfocus();
        bindingFor = null;
        if (inside(mx, my, sbX(), sbY(), sbW(), SB_H)) {
            clickBox(searchBox, mx);
            return true;
        }
        if (needsAccount() && inside(mx, my, sideX(), bannerY(), sideW(), 40)) {
            if (com.example.neurocanvas.client.AccountClient.state() == com.example.neurocanvas.client.AccountClient.State.LINKING) {
                com.example.neurocanvas.client.AccountClient.cancelLink();
            } else {
                com.example.neurocanvas.client.AccountClient.startLink();
            }
            Interface.click(1.2f);
            return true;
        }
        List<Category> cats = categories();
        int ih = sideItemH(cats.size());
        for (int i = 0; i < cats.size(); i++) {
            if (inside(mx, my, sideX(), sideItemY(i, ih), sideW(), ih)) {
                switchTab(cats.get(i));
                return true;
            }
        }

        if (aiView()) {
            return AI.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
        }
        if (configView()) {
            return configClicked(mx, my, button);
        }
        if (fullPage()) {
            if (inside(mx, my, backX(), backY(), 58, 20)) {
                fullView = null;
                settingsScroll = 0;
                Interface.click(0.9f);
                return true;
            }
            if (friendsPanel() && friendsClicked(mx, my, button)) {
                return true;
            }
            if (marketPanel() && marketClicked(mx, my, button)) {
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (my >= vy0() && my < vy1() && mx >= cx0() && mx < cx1() + 6) {
            List<Item> items = layout();
            for (Item it : items) {
                if (it.kind() == I_HEAD || my < it.y() || my >= it.y() + it.h() || openOf(it.m()) < 0.999f) {
                    continue;
                }
                return rowClicked(it, mx, my, button);
            }
            for (Item it : items) {
                if (it.kind() == I_HEAD && my >= it.y() && my < it.y() + HEAD_H) {
                    headClicked(it.m(), mx, button);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void headClicked(Module m, double mx, int button) {
        boolean blocked = m.risky && Interface.safeMode();
        boolean onSwitch = mx >= toggleX() - 6;
        if (blocked && (onSwitch || button == 1)) {
            Interface.click(0.6f);
            return;
        }
        if (!m.alwaysOn && (onSwitch || button == 1)) {
            m.enabled = !m.enabled;
            Interface.click(m.enabled ? 1.3f : 0.8f);
            return;
        }
        if (button != 0) {
            return;
        }
        Interface.click(1.0f);
        if (special(m)) {
            fullView = m;
            settingsScroll = 0;
        } else {
            if (!expanded.remove(m.name)) {
                expanded.add(m.name);
            }
            saveExpanded();
        }
    }

    private boolean rowClicked(Item it, double mx, double my, int button) {
        int x0 = cx0() + 14;
        int x1 = cx1() - 14;
        if (it.kind() == I_KEY) {
            if (mx >= x1 - CCW - 4) {
                bindingFor = it.m();
            }
            return true;
        }
        if (it.kind() == I_MODE) {
            if (mx >= x1 - CCW - 4) {
                it.m().hold = !it.m().hold;
                Interface.click(1.1f);
            }
            return true;
        }
        Setting s = it.s();
        switch (s.kind) {
            case BOOL -> {
                s.set(s.on() ? 0 : 1);
                Interface.click(s.on() ? 1.3f : 0.8f);
            }
            case BUTTON -> {
                s.value = 1;
                Interface.click(1.1f);
            }
            case CHOICE -> {
                if (mx >= x1 - CCW - 4) {
                    s.cycle(button == 1 ? -1 : 1);
                    Interface.click(1.1f);
                }
            }
            case COLOR -> {
                if (mx >= x1 - 140) {
                    openPicker(s, it.y(), it.h());
                }
            }
            default -> {
                if (mx <= x1 - 40) {
                    dragging = s;
                    dragX0 = x0;
                    dragW = x1 - 44 - x0;
                    applyDrag(mx);
                }
            }
        }
        return true;
    }

    @Override
    public void filesDragged(java.util.List<java.nio.file.Path> paths) {
        // A picture dropped on the NeuroAura tab is attached to the next message.
        if (aiView() && !paths.isEmpty()) {
            NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NeuroAura.class).attach(paths.get(0));
            return;
        }
        super.filesDragged(paths);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (TextBox b : boxes) {
            if (b.dragging()) {
                b.dragTo(textRenderer, mx);
                return true;
            }
        }
        if (aiView() && AI.mouseDragged(textRenderer, mx)) {
            return true;
        }
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
        for (TextBox b : boxes) {
            b.release();
        }
        AI.mouseReleased();
        dragging = null;
        pickDrag = 0;
        return super.mouseReleased(mx, my, button);
    }

    private void applyDrag(double mx) {
        double f = Math.max(0, Math.min(1, (mx - dragX0 - 5) / Math.max(1, dragW - 10)));
        dragging.set(dragging.min + f * (dragging.max - dragging.min));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        pickerFor = null;
        if (aiView()) {
            AI.scroll(amount);
            return true;
        }
        if (split()) {
            if (mx < panelX()) {
                int content = configView() ? presets.size() * (CARD_H + GAP) : 0;
                listScroll = clamp(listScroll - amount * 18, 0, Math.max(0, content - bodyH()));
            }
            return true;
        }
        if (fullPage()) {
            int content = friendsPanel() ? friendsContent : marketContent;
            int view = bodyTop() + bodyH() - rowsTop() - 4;
            settingsScroll = clamp(settingsScroll - amount * 18, 0, Math.max(0, content - view));
            return true;
        }
        layout();
        listScroll = clamp(listScroll - amount * 24, 0, Math.max(0, cardsContent - (vy1() - vy0())));
        return true;
    }

    private boolean mod(int modifiers, int bit) {
        return (modifiers & bit) != 0;
    }

    /** Puts the caret in a field with a click (shift extends the selection) and takes the focus away from the others. */
    private void clickBox(TextBox box, double mx) {
        long window = client.getWindow().getHandle();
        boolean shift = InputUtil.isKeyPressed(window, 340) || InputUtil.isKeyPressed(window, 344);
        for (TextBox b : boxes) {
            b.focused = false;
        }
        box.click(textRenderer, mx, shift);
    }

    private TextBox focusedBox() {
        for (TextBox b : boxes) {
            if (b.focused) {
                return b;
            }
        }
        return null;
    }

    private boolean aiFieldFocused() {
        return aiView() && AI.hasFocus();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (aiView() && AI.charTyped(chr)) {
            return true;
        }
        TextBox f = focusedBox();
        // The key that opened the menu may still deliver its character; the first moments are ignored.
        if (f == null && System.currentTimeMillis() - openedAt < 150) {
            return true;
        }
        if (f == null && bindingFor == null && pickerFor == null && chr >= 32 && chr != 127) {
            // Nothing is being edited: typing goes to the search, so you can just start writing.
            f = searchBox;
            f.focused = true;
        }
        if (f != null) {
            f.type(chr);
            if (f == searchBox) {
                listScroll = 0;
            }
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
        if (aiView() && AI.keyPressed(keyCode, modifiers)) {
            return true;
        }
        boolean enter = keyCode == 257 || keyCode == 335;
        TextBox f = focusedBox();
        if (f != null) {
            if (keyCode == 256) {
                if (f == searchBox) {
                    searchBox.set("");
                }
                f.focused = false;
            } else if (enter) {
                f.focused = false;
                if (f == packBox && fullView instanceof com.example.neurocanvas.module.Market m) {
                    m.searchPacks(packBox.text());
                } else if (f == roomBox) {
                    NeuroCanvasClient.modules().get(com.example.neurocanvas.module.NameTags.class).setRoom(roomBox.text());
                    roomBox.set("");
                } else if (f == friendBox) {
                    addFriendFromInput((com.example.neurocanvas.module.Friends) NeuroCanvasClient.modules().get(com.example.neurocanvas.module.Friends.class));
                    f.focused = true;
                } else if (f == nameBox) {
                    savePreset();
                }
            } else if (f.key(keyCode, modifiers) && f == searchBox) {
                listScroll = 0;
            }
            return true;
        }
        if (keyCode == 70 && mod(modifiers, 2)) {
            searchBox.focused = true;
            searchBox.selectAll();
            return true;
        }
        if (keyCode == 86 && mod(modifiers, 2) && pickerFor == null) {
            searchBox.focused = true;
            searchBox.paste();
            listScroll = 0;
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

