package com.example.neurocanvas.gui;

import com.example.neurocanvas.avatar.CosmeticBuilder;
import com.example.neurocanvas.avatar.CosmeticPack;
import com.example.neurocanvas.module.CosmeticModule;
import com.example.neurocanvas.module.Interface;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * A small pixel editor for cosmetics. The canvas (its size and a guide of where the faces of the model are) depends on the kind
 * of cosmetic; the name goes in the box in the top corner and the button next to it saves the picture as a new item and puts it on.
 */
public final class CosmeticEditor extends Screen {
    /** What the editor paints: the kind of canvas and where the finished picture goes. */
    public interface Target {
        String title();

        /** Used in the name box: "Name of the cape". */
        String noun();

        CosmeticBuilder.Template template(int size);

        /** The canvas sizes to choose from, or null when the size is fixed. */
        int[] sizes();

        boolean exists(String name);

        void save(String name, BufferedImage image) throws IOException;

        /** The kind of cosmetic to show on a Steve next to the canvas ("capes", "hats"...), or null for no preview. */
        default String previewType() {
            return null;
        }

        /** The files of the model the picture makes, for the preview. */
        default Map<String, byte[]> previewFiles(BufferedImage image) throws IOException {
            return null;
        }
    }

    private enum Tool { PENCIL, ERASER, FILL, PICK }

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,29}");
    private static final int[] PALETTE = {0xFF000000, 0xFFFFFFFF, 0xFF7F7F7F, 0xFFB03030, 0xFFFF5C5C, 0xFFFF8A3D, 0xFFFFD23F, 0xFF5CE08A,
        0xFF2E8B57, 0xFF4DD2FF, 0xFF3D7BDC, 0xFF2B3A6B, 0xFFB06BFF, 0xFFFF7AB6, 0xFF8B5A2B, 0xFFE8B88C};
    private static final int SV_W = 100, SV_H = 72, HUE_H = 10, LEFT_W = 128;

    private final Target target;
    private final Screen parent;
    private int graffitiSizeIndex = 1;
    private CosmeticBuilder.Template template;
    private int[] pixels;
    private int cw;
    private int ch;
    private final Deque<int[]> history = new ArrayDeque<>();

    private Tool tool = Tool.PENCIL;
    private float hue = 0.55f;
    private float sat = 0.8f;
    private float val = 0.95f;
    private int brush = 1;
    private boolean mirror;
    private boolean grid = true;
    private boolean guide = true;
    private String name = "";
    private boolean nameFocus;
    private String status = "";
    private String savedName = "";
    private int drag;
    private static final int PREVIEW_W = 150;
    private com.example.neurocanvas.avatar.BbInstance previewItem;
    private boolean previewDirty = true;
    private int lastX = -1;
    private int lastY = -1;

    public CosmeticEditor(Target target, Screen parent) {
        super(Text.literal("Cosmetic editor"));
        this.target = target;
        this.parent = parent;
        resetCanvas();
    }

    /** The editor for one of the cosmetic modules: the finished picture becomes a new item of that module and is put on. */
    public static CosmeticEditor forCosmetic(CosmeticModule module, Screen parent) {
        CosmeticPack.Type type = module.type();
        return new CosmeticEditor(new Target() {
            @Override
            public String title() {
                return type.label;
            }

            @Override
            public String noun() {
                return type.label.toLowerCase();
            }

            @Override
            public CosmeticBuilder.Template template(int size) {
                return CosmeticBuilder.template(type, size);
            }

            @Override
            public int[] sizes() {
                return type == CosmeticPack.Type.GRAFFITI ? CosmeticBuilder.GRAFFITI_SIZES : null;
            }

            @Override
            public boolean exists(String name) {
                return Files.exists(module.folder().resolve(name));
            }

            @Override
            public String previewType() {
                return com.example.neurocanvas.avatar.Mannequin.wears(type.folder) ? type.folder : null;
            }

            @Override
            public Map<String, byte[]> previewFiles(BufferedImage image) throws IOException {
                return previewType() == null ? null : CosmeticBuilder.build(type, image);
            }

            @Override
            public void save(String name, BufferedImage image) throws IOException {
                Path folder = module.folder().resolve(name);
                Map<String, byte[]> files = CosmeticBuilder.build(type, image);
                Files.createDirectories(folder);
                for (Map.Entry<String, byte[]> f : files.entrySet()) {
                    Files.write(folder.resolve(f.getKey()), f.getValue());
                }
                module.rescan();
                module.wear(name);
            }
        }, parent);
    }

    private void resetCanvas() {
        int[] sizes = target.sizes();
        previewDirty = true;
        template = target.template(sizes == null ? 0 : sizes[graffitiSizeIndex % sizes.length]);
        cw = template.width();
        ch = template.height();
        pixels = new int[cw * ch];
        history.clear();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int color() {
        return java.awt.Color.HSBtoRGB(hue, sat, val) | 0xFF000000;
    }

    private void setColor(int argb) {
        float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        hue = hsb[0];
        sat = hsb[1];
        val = hsb[2];
    }

    // ---- layout -----------------------------------------------------------------------------

    private int saveW() { return 64; }
    private int saveX() { return width - 12 - saveW(); }
    private int nameW() { return 160; }
    private int nameX() { return saveX() - 8 - nameW(); }
    private int barY() { return 6; }

    private int areaX() { return LEFT_W + 20; }
    private int areaY() { return 40; }
    private boolean hasPreview() { return target.previewType() != null && width >= 560; }
    private int previewX() { return width - 12 - PREVIEW_W; }
    private int areaW() { return width - areaX() - 12 - (hasPreview() ? PREVIEW_W + 10 : 0); }
    private int areaH() { return height - areaY() - 28; }

    private int pix() {
        return Math.max(1, Math.min(areaW() / cw, areaH() / ch));
    }

    private int canvasX() { return areaX() + (areaW() - pix() * cw) / 2; }
    private int canvasY() { return areaY() + (areaH() - pix() * ch) / 2; }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int toolsY() { return 40; }
    private int optionsY() { return toolsY() + 62; }
    private int colorY() { return optionsY() + 110; }

    // ---- painting ---------------------------------------------------------------------------

    private void snapshot() {
        previewDirty = true;
        history.push(pixels.clone());
        while (history.size() > 40) {
            history.removeLast();
        }
    }

    private void undo() {
        if (!history.isEmpty()) {
            pixels = history.pop();
            previewDirty = true;
        }
    }

    private void stamp(int x, int y, int argb) {
        int r = brush - 1;
        for (int dy = -r / 2; dy <= r - r / 2; dy++) {
            for (int dx = -r / 2; dx <= r - r / 2; dx++) {
                put(x + dx, y + dy, argb);
                if (mirror) {
                    put(cw - 1 - (x + dx), y + dy, argb);
                }
            }
        }
    }

    private void put(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < cw && y < ch) {
            pixels[y * cw + x] = argb;
        }
    }

    private void line(int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            stamp(x0, y0, argb);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void fill(int x, int y, int argb) {
        int target = pixels[y * cw + x];
        if (target == argb) {
            return;
        }
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] {x, y});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            if (p[0] < 0 || p[1] < 0 || p[0] >= cw || p[1] >= ch || pixels[p[1] * cw + p[0]] != target) {
                continue;
            }
            pixels[p[1] * cw + p[0]] = argb;
            stack.push(new int[] {p[0] + 1, p[1]});
            stack.push(new int[] {p[0] - 1, p[1]});
            stack.push(new int[] {p[0], p[1] + 1});
            stack.push(new int[] {p[0], p[1] - 1});
        }
    }

    private void paintAt(double mx, double my, boolean first) {
        int x = (int) Math.floor((mx - canvasX()) / pix());
        int y = (int) Math.floor((my - canvasY()) / pix());
        if (x < 0 || y < 0 || x >= cw || y >= ch) {
            if (!first) {
                lastX = -1;
            }
            return;
        }
        switch (tool) {
            case PICK -> {
                int c = pixels[y * cw + x];
                if ((c >>> 24) != 0) {
                    setColor(c);
                }
                tool = Tool.PENCIL;
            }
            case FILL -> {
                if (first) {
                    snapshot();
                    fill(x, y, color());
                }
            }
            default -> {
                int argb = tool == Tool.ERASER ? 0 : color();
                if (first) {
                    snapshot();
                    lastX = -1;
                }
                if (lastX >= 0) {
                    line(lastX, lastY, x, y, argb);
                } else {
                    stamp(x, y, argb);
                }
                lastX = x;
                lastY = y;
            }
        }
    }

    // ---- saving -----------------------------------------------------------------------------

    private void save() {
        String n = name.trim();
        if (!NAME.matcher(n).matches()) {
            status = "Type a name first (letters, digits, space, _ - .)";
            return;
        }
        if (target.exists(n) && !n.equals(savedName)) {
            status = "\"" + n + "\" already exists: pick another name";
            return;
        }
        boolean empty = true;
        for (int p : pixels) {
            empty &= (p >>> 24) == 0;
        }
        if (empty) {
            status = "The canvas is empty";
            return;
        }
        try {
            BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    img.setRGB(x, y, pixels[y * cw + x]);
                }
            }
            target.save(n, img);
            savedName = n;
            status = "Saved \"" + n + "\" and put it on";
        } catch (IOException | RuntimeException e) {
            status = "Could not save: " + e.getMessage();
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    private void button(DrawContext ctx, Theme th, int x, int y, int w, int h, String label, boolean on, boolean hover) {
        int bg = on ? th.accent() : (hover ? th.hover() : th.panel2());
        Ui.roundRect(ctx, x, y, w, h, 5, bg);
        Ui.textCentered(ctx, textRenderer, label, x + w / 2, y + (h - 8) / 2, on ? (th.bg() | 0xFF000000) : th.text());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Theme th = Interface.theme();
        ctx.fill(0, 0, width, height, Ui.alpha(th.bg() & 0xFFFFFF, 0.97f));
        Ui.text(ctx, textRenderer, "Editor: " + target.title(), 14, barY() + 6, th.accent());
        Ui.text(ctx, textRenderer, template.width() + " x " + template.height(), 14 + Ui.width(textRenderer, "Editor: " + target.title()) + 10, barY() + 6, th.dim());

        // name and save, top right
        if (nameFocus) {
            Ui.roundRect(ctx, nameX() - 1, barY() - 1, nameW() + 2, 22, 6, th.accent());
        }
        Ui.roundRect(ctx, nameX(), barY(), nameW(), 20, 5, th.panel2());
        String shown = name.isEmpty() ? "Name of the " + target.noun() : name + (nameFocus && System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        Ui.text(ctx, textRenderer, shown, nameX() + 7, barY() + 6, name.isEmpty() ? th.dim() : th.text());
        boolean saveHover = inside(mx, my, saveX(), barY(), saveW(), 20);
        Ui.roundRect(ctx, saveX(), barY(), saveW(), 20, 5, saveHover ? th.accent2() : th.accent());
        Ui.textCentered(ctx, textRenderer, "Save", saveX() + saveW() / 2, barY() + 6, th.bg() | 0xFF000000);

        // tools
        int tx = 10;
        String[] toolNames = {"Pencil", "Eraser", "Fill", "Pick"};
        Tool[] tools = Tool.values();
        for (int i = 0; i < 4; i++) {
            int bx = tx + (i % 2) * 62, by = toolsY() + (i / 2) * 28;
            button(ctx, th, bx, by, 58, 24, toolNames[i], tool == tools[i], inside(mx, my, bx, by, 58, 24));
        }
        int oy = optionsY();
        Ui.text(ctx, textRenderer, "Brush", tx, oy + 4, th.dim());
        button(ctx, th, tx + 48, oy, 20, 16, "-", false, inside(mx, my, tx + 48, oy, 20, 16));
        Ui.textCentered(ctx, textRenderer, String.valueOf(brush), tx + 80, oy + 4, th.text());
        button(ctx, th, tx + 92, oy, 20, 16, "+", false, inside(mx, my, tx + 92, oy, 20, 16));
        button(ctx, th, tx, oy + 22, 58, 18, "Mirror", mirror, inside(mx, my, tx, oy + 22, 58, 18));
        button(ctx, th, tx + 62, oy + 22, 58, 18, "Grid", grid, inside(mx, my, tx + 62, oy + 22, 58, 18));
        button(ctx, th, tx, oy + 44, 58, 18, "Guide", guide, inside(mx, my, tx, oy + 44, 58, 18));
        button(ctx, th, tx + 62, oy + 44, 58, 18, "Undo", false, inside(mx, my, tx + 62, oy + 44, 58, 18));
        button(ctx, th, tx, oy + 66, 58, 18, "Clear", false, inside(mx, my, tx, oy + 66, 58, 18));
        if (target.sizes() != null) {
            button(ctx, th, tx + 62, oy + 66, 58, 18, cw + " px", false, inside(mx, my, tx + 62, oy + 66, 58, 18));
        }

        // colour: saturation/value square, hue strip, palette
        int cy = colorY();
        int cells = 20;
        int cwid = SV_W / cells, chei = SV_H / 12;
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < 12; j++) {
                int c = java.awt.Color.HSBtoRGB(hue, i / (float) (cells - 1), 1f - j / 11f) | 0xFF000000;
                ctx.fill(tx + i * cwid, cy + j * chei, tx + (i + 1) * cwid, cy + (j + 1) * chei, c);
            }
        }
        int hx = tx + Math.round(sat * (SV_W - cwid)), hy = cy + Math.round((1f - val) * (SV_H - chei));
        ctx.fill(hx - 1, hy - 1, hx + cwid + 1, hy + chei + 1, 0xFFFFFFFF);
        ctx.fill(hx, hy, hx + cwid, hy + chei, color());
        int hyBar = cy + SV_H + 6;
        for (int i = 0; i < SV_W; i++) {
            ctx.fill(tx + i, hyBar, tx + i + 1, hyBar + HUE_H, java.awt.Color.HSBtoRGB(i / (float) SV_W, 1f, 1f) | 0xFF000000);
        }
        int hm = tx + Math.round(hue * (SV_W - 1));
        ctx.fill(hm - 1, hyBar - 2, hm + 2, hyBar + HUE_H + 2, 0xFFFFFFFF);
        int py = hyBar + HUE_H + 8;
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = tx + (i % 8) * 14, sy = py + (i / 8) * 14;
            ctx.fill(sx, sy, sx + 12, sy + 12, PALETTE[i]);
        }
        ctx.fill(tx, py + 32, tx + 40, py + 48, color());
        Ui.text(ctx, textRenderer, String.format("#%06X", color() & 0xFFFFFF), tx + 46, py + 36, th.dim());

        renderCanvas(ctx, th, mx, my);
        if (hasPreview()) {
            renderPreview(ctx, th);
        }

        Ui.text(ctx, textRenderer, textRenderer.trimToWidth(template.hint(), width - 24), 12, height - 22, th.dim());
        if (!status.isEmpty()) {
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(status, width - 24), 12, height - 11, th.accent());
        }
    }

    /** Rebuilds the model from the canvas when something was painted. Done between strokes, never while the mouse is down. */
    private void refreshPreview() {
        if (!previewDirty || drag != 0) {
            return;
        }
        previewDirty = false;
        if (previewItem != null) {
            previewItem.close(client);
            previewItem = null;
        }
        boolean empty = true;
        for (int p : pixels) {
            empty &= (p >>> 24) == 0;
        }
        if (empty) {
            return;
        }
        try {
            BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    img.setRGB(x, y, pixels[y * cw + x]);
                }
            }
            Map<String, byte[]> files = target.previewFiles(img);
            byte[] model = files == null ? null : files.get("model.bbmodel");
            if (model != null) {
                previewItem = new com.example.neurocanvas.avatar.BbInstance(client,
                        com.example.neurocanvas.avatar.BbModel.parse(new String(model, java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (IOException | RuntimeException ignored) {
            // No preview this time.
        }
    }

    /** A turning Steve wearing what is being painted. */
    private void renderPreview(DrawContext ctx, Theme th) {
        refreshPreview();
        int x = previewX(), y = areaY(), w = PREVIEW_W, h = areaH();
        Ui.roundRect(ctx, x, y, w, h, 6, th.panel());
        Ui.text(ctx, textRenderer, "Preview", x + 8, y + 6, th.dim());
        ctx.enableScissor(x, y + 18, x + w, y + h);
        float s = Math.min(w, h - 18) * 0.42f / 21f;
        long ms = System.currentTimeMillis();
        float angle = (ms % 9000) / 9000f * (float) (Math.PI * 2);
        org.joml.Matrix4f m = new org.joml.Matrix4f(ctx.getMatrices().peek().getPositionMatrix());
        m.translate(x + w / 2f, y + 18 + (h - 18) / 2f, 100f).scale(s, -s, s).rotateX(0.2f).rotateY(angle).translate(0f, -16f, 0f);
        com.example.neurocanvas.avatar.Mannequin.drawWith(previewItem, target.previewType(), m, ms / 1000.0);
        ctx.disableScissor();
    }

    @Override
    public void removed() {
        if (previewItem != null) {
            previewItem.close(client);
            previewItem = null;
        }
    }

    private void renderCanvas(DrawContext ctx, Theme th, int mx, int my) {
        int p = pix();
        int ox = canvasX(), oy = canvasY();
        Ui.roundRect(ctx, ox - 4, oy - 4, cw * p + 8, ch * p + 8, 4, th.panel());
        int cell = Math.max(p, 4);
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                int c = pixels[y * cw + x];
                int bgc = ((x + y) & 1) == 0 ? 0xFF3A3A44 : 0xFF2C2C34;
                ctx.fill(ox + x * p, oy + y * p, ox + (x + 1) * p, oy + (y + 1) * p, bgc);
                if ((c >>> 24) != 0) {
                    ctx.fill(ox + x * p, oy + y * p, ox + (x + 1) * p, oy + (y + 1) * p, c);
                }
            }
        }
        if (grid && p >= 4) {
            int gc = Ui.alpha(0xFFFFFF, 0.1f);
            for (int x = 0; x <= cw; x++) {
                ctx.fill(ox + x * p, oy, ox + x * p + 1, oy + ch * p, gc);
            }
            for (int y = 0; y <= ch; y++) {
                ctx.fill(ox, oy + y * p, ox + cw * p, oy + y * p + 1, gc);
            }
        }
        if (guide) {
            for (CosmeticBuilder.Zone z : template.zones()) {
                int zx = ox + z.x() * p, zy = oy + z.y() * p, zw = z.w() * p, zh = z.h() * p;
                int c = 0xFF000000 | z.color();
                ctx.fill(zx, zy, zx + zw, zy + 1, c);
                ctx.fill(zx, zy + zh - 1, zx + zw, zy + zh, c);
                ctx.fill(zx, zy, zx + 1, zy + zh, c);
                ctx.fill(zx + zw - 1, zy, zx + zw, zy + zh, c);
                if (zw >= Ui.width(textRenderer, z.label()) + 4 && zh >= 10) {
                    Ui.text(ctx, textRenderer, z.label(), zx + 3, zy + 3, Ui.alpha(z.color(), 0.9f));
                }
            }
        }
        // the pixel under the cursor
        int hx = (int) Math.floor((mx - ox) / (double) p), hy = (int) Math.floor((my - oy) / (double) p);
        if (hx >= 0 && hy >= 0 && hx < cw && hy < ch) {
            int c = 0xFFFFFFFF;
            ctx.fill(ox + hx * p, oy + hy * p, ox + (hx + 1) * p, oy + hy * p + 1, c);
            ctx.fill(ox + hx * p, oy + (hy + 1) * p - 1, ox + (hx + 1) * p, oy + (hy + 1) * p, c);
            ctx.fill(ox + hx * p, oy + hy * p, ox + hx * p + 1, oy + (hy + 1) * p, c);
            ctx.fill(ox + (hx + 1) * p - 1, oy + hy * p, ox + (hx + 1) * p, oy + (hy + 1) * p, c);
        }
    }

    // ---- input ------------------------------------------------------------------------------

    private void pickColor(double mx, double my) {
        int tx = 10;
        int cy = colorY();
        if (drag == 2) {
            sat = (float) Math.max(0, Math.min(1, (mx - tx) / (SV_W - 1.0)));
            val = (float) Math.max(0, Math.min(1, 1 - (my - cy) / (SV_H - 1.0)));
        } else if (drag == 3) {
            hue = (float) Math.max(0, Math.min(1, (mx - tx) / (SV_W - 1.0)));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        nameFocus = inside(mx, my, nameX(), barY(), nameW(), 20);
        if (nameFocus) {
            return true;
        }
        if (inside(mx, my, saveX(), barY(), saveW(), 20)) {
            save();
            return true;
        }
        int tx = 10;
        Tool[] tools = Tool.values();
        for (int i = 0; i < 4; i++) {
            if (inside(mx, my, tx + (i % 2) * 62, toolsY() + (i / 2) * 28, 58, 24)) {
                tool = tools[i];
                return true;
            }
        }
        int oy = optionsY();
        if (inside(mx, my, tx + 48, oy, 20, 16)) {
            brush = Math.max(1, brush - 1);
        } else if (inside(mx, my, tx + 92, oy, 20, 16)) {
            brush = Math.min(6, brush + 1);
        } else if (inside(mx, my, tx, oy + 22, 58, 18)) {
            mirror = !mirror;
        } else if (inside(mx, my, tx + 62, oy + 22, 58, 18)) {
            grid = !grid;
        } else if (inside(mx, my, tx, oy + 44, 58, 18)) {
            guide = !guide;
        } else if (inside(mx, my, tx + 62, oy + 44, 58, 18)) {
            undo();
        } else if (inside(mx, my, tx, oy + 66, 58, 18)) {
            snapshot();
            Arrays.fill(pixels, 0);
        } else if (target.sizes() != null && inside(mx, my, tx + 62, oy + 66, 58, 18)) {
            graffitiSizeIndex = (graffitiSizeIndex + 1) % target.sizes().length;
            resetCanvas();
        } else {
            int cy = colorY();
            int hyBar = cy + SV_H + 6;
            int py = hyBar + HUE_H + 8;
            if (inside(mx, my, tx, cy, SV_W, SV_H)) {
                drag = 2;
                pickColor(mx, my);
                tool = tool == Tool.ERASER ? Tool.PENCIL : tool;
            } else if (inside(mx, my, tx, hyBar - 2, SV_W, HUE_H + 4)) {
                drag = 3;
                pickColor(mx, my);
            } else if (inside(mx, my, tx, py, 8 * 14, 28)) {
                int i = (int) ((mx - tx) / 14) + 8 * (int) ((my - py) / 14);
                if (i >= 0 && i < PALETTE.length) {
                    setColor(PALETTE[i]);
                    tool = tool == Tool.ERASER ? Tool.PENCIL : tool;
                }
            } else if (inside(mx, my, canvasX(), canvasY(), cw * pix(), ch * pix())) {
                drag = 1;
                paintAt(mx, my, true);
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == 1) {
            paintAt(mx, my, false);
        } else if (drag != 0) {
            pickColor(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag == 1) {
            previewDirty = true;
        }
        drag = 0;
        lastX = -1;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nameFocus && name.length() < 30 && (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-' || chr == '.')) {
            name += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameFocus) {
            if (keyCode == 259 && !name.isEmpty()) {
                name = name.substring(0, name.length() - 1);
            } else if (keyCode == 256) {
                nameFocus = false;
            } else if (keyCode == 257 || keyCode == 335) {
                nameFocus = false;
                save();
            }
            return true;
        }
        if (keyCode == 90 && (modifiers & 2) != 0) {
            undo();
            return true;
        }
        if (keyCode == 256) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
