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
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A paint editor for cosmetics. The canvas has the layout of the kind of cosmetic being made, at a resolution of your choice
 * (the model takes any), with a soft brush (size, hardness, opacity), eraser, fill, colour picker, mirror drawing, zoom and pan,
 * undo and redo, pictures dropped or imported on it, and, for capes, wings, hats and outfits, a turning Steve wearing the result.
 * The name goes in the box in the top corner and the button next to it saves.
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

    private enum Tool { BRUSH, ERASER, FILL, PICK }

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,29}");
    private static final int[] PALETTE = {0xFF000000, 0xFFFFFFFF, 0xFF7F7F7F, 0xFFB03030, 0xFFFF5C5C, 0xFFFF8A3D, 0xFFFFD23F, 0xFF5CE08A,
        0xFF2E8B57, 0xFF4DD2FF, 0xFF3D7BDC, 0xFF2B3A6B, 0xFFB06BFF, 0xFFFF7AB6, 0xFF8B5A2B, 0xFFE8B88C};
    private static final int[] RESOLUTIONS = {1, 2, 4, 8};
    private static final int MAX_SIDE = 1024;
    private static final int SV_W = 100, SV_H = 72, HUE_H = 10;
    private static final int COL_A = 10, COL_A_W = 124, COL_B = 142, COL_B_W = 104;
    private static final int PREVIEW_W = 150;

    private final Target target;
    private final Screen parent;
    private CosmeticBuilder.Template template;
    private int sizeIndex = 1;
    private int res = 1;
    private int cw;
    private int ch;
    private int[] pixels;
    private final Deque<int[]> undo = new ArrayDeque<>();
    private final Deque<int[]> redo = new ArrayDeque<>();

    private Tool tool = Tool.BRUSH;
    private float hue = 0.55f;
    private float sat = 0.8f;
    private float val = 0.95f;
    private float brushSize = 2f;
    private float hardness = 0.85f;
    private float opacity = 1f;
    private boolean mirror;
    private boolean grid = true;
    private boolean guide = true;

    private float zoom = 1f;
    private float panX;
    private float panY;
    private boolean viewFitted;

    private Identifier canvasId;
    private NativeImageBackedTexture canvasTex;
    private boolean canvasDirty = true;

    private boolean importStretch;
    private int importIndex;
    private com.example.neurocanvas.avatar.BbInstance previewItem;
    private boolean previewDirty = true;

    private String name = "";
    private boolean nameFocus;
    private String status = "";
    private String savedName = "";
    private int drag;
    private float lastX = Float.NaN;
    private float lastY = Float.NaN;
    private boolean painted;

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
        template = target.template(sizes == null ? 0 : sizes[sizeIndex % sizes.length]);
        cw = template.width() * res;
        ch = template.height() * res;
        pixels = new int[cw * ch];
        brushSize = Math.max(1f, Math.min(brushSize, Math.max(8, Math.min(cw, ch) / 2f)));
        undo.clear();
        redo.clear();
        canvasDirty = true;
        previewDirty = true;
        viewFitted = false;
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

    private boolean hasPreview() { return target.previewType() != null && width >= 640; }
    private int previewX() { return width - 12 - PREVIEW_W; }
    private int areaX() { return COL_B + COL_B_W + 10; }
    private int areaY() { return 40; }
    private int areaW() { return width - areaX() - 12 - (hasPreview() ? PREVIEW_W + 10 : 0); }
    private int areaH() { return height - areaY() - 30; }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float maxBrush() {
        return Math.max(8f, Math.min(cw, ch) / 2f);
    }

    private void fitView() {
        zoom = Math.max(0.1f, Math.min(areaW() / (float) cw, areaH() / (float) ch));
        if (zoom >= 1f) {
            zoom = (float) Math.floor(zoom);
        }
        panX = (areaW() - cw * zoom) / 2f;
        panY = (areaH() - ch * zoom) / 2f;
        viewFitted = true;
    }

    private float toCanvasX(double mx) { return (float) ((mx - areaX() - panX) / zoom); }
    private float toCanvasY(double my) { return (float) ((my - areaY() - panY) / zoom); }

    // ---- history ----------------------------------------------------------------------------

    private void snapshot() {
        undo.push(pixels.clone());
        while (undo.size() > 24) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void doUndo() {
        if (!undo.isEmpty()) {
            redo.push(pixels);
            pixels = undo.pop();
            canvasDirty = true;
            previewDirty = true;
        }
    }

    private void doRedo() {
        if (!redo.isEmpty()) {
            undo.push(pixels);
            pixels = redo.pop();
            canvasDirty = true;
            previewDirty = true;
        }
    }

    // ---- painting ---------------------------------------------------------------------------

    /** One round dab of the brush: its edge is soft by the hardness, and colours blend over what is already there. */
    private void dab(float cx, float cy, boolean erase) {
        float r = brushSize / 2f;
        float edge = Math.max(1f, r * (1f - hardness));
        int argb = color();
        float sr = (argb >> 16) & 0xFF, sg = (argb >> 8) & 0xFF, sb = argb & 0xFF;
        int x0 = Math.max(0, (int) Math.floor(cx - r - 1)), x1 = Math.min(cw - 1, (int) Math.ceil(cx + r + 1));
        int y0 = Math.max(0, (int) Math.floor(cy - r - 1)), y1 = Math.min(ch - 1, (int) Math.ceil(cy + r + 1));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float cover = Math.max(0f, Math.min(1f, (r - d) / edge)) * opacity;
                if (cover <= 0f) {
                    continue;
                }
                int i = y * cw + x;
                int bg = pixels[i];
                float ba = (bg >>> 24) / 255f;
                if (erase) {
                    int a = Math.round(ba * (1f - cover) * 255f);
                    pixels[i] = a == 0 ? 0 : (a << 24) | (bg & 0xFFFFFF);
                    continue;
                }
                float oa = cover + ba * (1f - cover);
                if (oa <= 0f) {
                    continue;
                }
                float br = (bg >> 16) & 0xFF, bgc = (bg >> 8) & 0xFF, bb = bg & 0xFF;
                int rr = Math.round((sr * cover + br * ba * (1f - cover)) / oa);
                int gg = Math.round((sg * cover + bgc * ba * (1f - cover)) / oa);
                int bl = Math.round((sb * cover + bb * ba * (1f - cover)) / oa);
                pixels[i] = (Math.round(oa * 255f) << 24) | (rr << 16) | (gg << 8) | bl;
            }
        }
    }

    private void dabMirrored(float cx, float cy, boolean erase) {
        dab(cx, cy, erase);
        if (mirror) {
            dab(cw - cx, cy, erase);
        }
    }

    /** Dabs along a stroke so it is smooth however fast the mouse moves. */
    private void stroke(float x0, float y0, float x1, float y1, boolean erase) {
        float dist = (float) Math.hypot(x1 - x0, y1 - y0);
        float step = Math.max(0.6f, brushSize * 0.18f);
        int n = Math.max(1, (int) Math.ceil(dist / step));
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            dabMirrored(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, erase);
        }
    }

    private static int channelDiff(int a, int b) {
        return Math.max(Math.max(Math.abs((a >>> 24) - (b >>> 24)), Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))),
                Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
    }

    private void fill(int x, int y, int argb) {
        int target = pixels[y * cw + x];
        if (channelDiff(target, argb) == 0) {
            return;
        }
        boolean[] seen = new boolean[cw * ch];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] {x, y});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            if (p[0] < 0 || p[1] < 0 || p[0] >= cw || p[1] >= ch) {
                continue;
            }
            int i = p[1] * cw + p[0];
            if (seen[i] || channelDiff(pixels[i], target) > 14) {
                continue;
            }
            seen[i] = true;
            pixels[i] = argb;
            stack.push(new int[] {p[0] + 1, p[1]});
            stack.push(new int[] {p[0] - 1, p[1]});
            stack.push(new int[] {p[0], p[1] + 1});
            stack.push(new int[] {p[0], p[1] - 1});
        }
    }

    private void paintAt(double mx, double my, boolean first) {
        float x = toCanvasX(mx), y = toCanvasY(my);
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        switch (tool) {
            case PICK -> {
                if (ix >= 0 && iy >= 0 && ix < cw && iy < ch && (pixels[iy * cw + ix] >>> 24) != 0) {
                    setColor(pixels[iy * cw + ix]);
                    tool = Tool.BRUSH;
                }
            }
            case FILL -> {
                if (first && ix >= 0 && iy >= 0 && ix < cw && iy < ch) {
                    snapshot();
                    fill(ix, iy, color());
                    canvasDirty = true;
                    painted = true;
                }
            }
            default -> {
                boolean erase = tool == Tool.ERASER;
                if (first) {
                    snapshot();
                    lastX = Float.NaN;
                }
                if (Float.isNaN(lastX)) {
                    dabMirrored(x, y, erase);
                } else {
                    stroke(lastX, lastY, x, y, erase);
                }
                lastX = x;
                lastY = y;
                canvasDirty = true;
                painted = true;
            }
        }
    }

    // ---- resizing ---------------------------------------------------------------------------

    private BufferedImage toImage() {
        BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, cw, ch, pixels, 0, cw);
        return img;
    }

    /** Changes the resolution or the layout size; what is painted is scaled to the new canvas. */
    private void rescale(int newRes, int newSizeIndex) {
        BufferedImage old = toImage();
        int oldCw = cw, oldCh = ch;
        res = newRes;
        sizeIndex = newSizeIndex;
        int[] sizes = target.sizes();
        CosmeticBuilder.Template t = target.template(sizes == null ? 0 : sizes[sizeIndex % sizes.length]);
        int nw = t.width() * res, nh = t.height() * res;
        if (nw > MAX_SIDE || nh > MAX_SIDE) {
            res = 1;
            nw = t.width();
            nh = t.height();
        }
        float factor = nw / (float) oldCw;
        brushSize = Math.max(1f, brushSize * factor);
        template = t;
        cw = nw;
        ch = nh;
        BufferedImage scaled = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                factor >= 1 ? java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(old, 0, 0, cw, ch, 0, 0, oldCw, oldCh, null);
        g.dispose();
        pixels = scaled.getRGB(0, 0, cw, ch, null, 0, cw);
        undo.clear();
        redo.clear();
        canvasDirty = true;
        previewDirty = true;
        viewFitted = false;
        status = "Canvas " + cw + " x " + ch;
    }

    // ---- importing a picture ------------------------------------------------------------------

    private static final java.util.Set<String> IMAGE_EXT = java.util.Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    private static boolean isImage(Path p) {
        String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return IMAGE_EXT.stream().anyMatch(n::endsWith);
    }

    private static Path importFolder() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("neurocanvas").resolve("import");
    }

    /** Puts a picture on the canvas, scaled to fit (or stretched) with the colours averaged, so a photo stays readable. */
    private void importImage(Path file) {
        try {
            BufferedImage src = javax.imageio.ImageIO.read(file.toFile());
            if (src == null) {
                status = "That file is not a picture I can read";
                return;
            }
            snapshot();
            Arrays.fill(pixels, 0);
            int sw = src.getWidth(), sh = src.getHeight();
            double scale = importStretch ? 0 : Math.min(cw / (double) sw, ch / (double) sh);
            int dw = importStretch ? cw : Math.max(1, (int) Math.round(sw * scale));
            int dh = importStretch ? ch : Math.max(1, (int) Math.round(sh * scale));
            int ox = (cw - dw) / 2, oy = (ch - dh) / 2;
            for (int y = 0; y < dh; y++) {
                int y0 = (int) Math.floor(y * sh / (double) dh);
                int y1 = Math.max(y0 + 1, (int) Math.floor((y + 1) * sh / (double) dh));
                for (int x = 0; x < dw; x++) {
                    int x0 = (int) Math.floor(x * sw / (double) dw);
                    int x1 = Math.max(x0 + 1, (int) Math.floor((x + 1) * sw / (double) dw));
                    double a = 0, r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int yy = y0; yy < Math.min(y1, sh); yy++) {
                        for (int xx = x0; xx < Math.min(x1, sw); xx++) {
                            int c = src.getRGB(xx, yy);
                            double al = (c >>> 24) / 255.0;
                            a += al;
                            r += ((c >> 16) & 0xFF) * al;
                            g += ((c >> 8) & 0xFF) * al;
                            b += (c & 0xFF) * al;
                            n++;
                        }
                    }
                    if (n == 0 || a <= 0) {
                        continue;
                    }
                    int alpha = (int) Math.round(a / n * 255);
                    if (alpha < 8) {
                        continue;
                    }
                    pixels[(oy + y) * cw + ox + x] = (alpha << 24) | ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8) | (int) Math.round(b / a);
                }
            }
            previewDirty = true;
            canvasDirty = true;
            status = "Imported " + file.getFileName() + " (" + sw + " x " + sh + ")";
        } catch (IOException | RuntimeException e) {
            status = "Could not read the picture: " + e.getMessage();
        }
    }

    /** The Import button: a file path on the clipboard first, then the pictures in neurocanvas/import one after another. */
    private void importNext() {
        try {
            String clip = client.keyboard.getClipboard().trim().replaceAll("^\"|\"$", "");
            if (!clip.isEmpty() && clip.length() < 400) {
                Path p = Path.of(clip);
                if (Files.isRegularFile(p) && isImage(p)) {
                    importImage(p);
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // The clipboard is not a path.
        }
        try {
            Files.createDirectories(importFolder());
            java.util.List<Path> files;
            try (java.util.stream.Stream<Path> s = Files.list(importFolder())) {
                files = s.filter(Files::isRegularFile).filter(CosmeticEditor::isImage).sorted().toList();
            }
            if (files.isEmpty()) {
                status = "Drop a picture on this window, copy its path, or put pictures into neurocanvas/import (opened)";
                net.minecraft.util.Util.getOperatingSystem().open(importFolder().toFile());
                return;
            }
            importImage(files.get(importIndex++ % files.size()));
        } catch (IOException e) {
            status = "Could not look in the import folder: " + e.getMessage();
        }
    }

    @Override
    public void filesDragged(java.util.List<Path> paths) {
        for (Path p : paths) {
            if (isImage(p)) {
                importImage(p);
                return;
            }
        }
        status = "Drop a PNG, JPG, GIF or BMP picture";
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
            target.save(n, toImage());
            savedName = n;
            status = "Saved \"" + n + "\" and put it on";
        } catch (IOException | RuntimeException e) {
            status = "Could not save: " + e.getMessage();
        }
    }

    // ---- canvas texture ---------------------------------------------------------------------

    /** The canvas is drawn from a texture, so a large picture costs no more to draw than a small one. */
    private void uploadCanvas() {
        if (canvasTex == null || canvasTex.getImage() == null || canvasTex.getImage().getWidth() != cw || canvasTex.getImage().getHeight() != ch) {
            if (canvasId != null) {
                client.getTextureManager().destroyTexture(canvasId);
            }
            canvasTex = new NativeImageBackedTexture(new NativeImage(cw, ch, true));
            canvasId = new Identifier("neurocanvas", "editor/" + java.util.UUID.randomUUID());
            client.getTextureManager().registerTexture(canvasId, canvasTex);
            canvasDirty = true;
        }
        if (!canvasDirty) {
            return;
        }
        NativeImage img = canvasTex.getImage();
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                int p = pixels[y * cw + x];
                // NativeImage stores ABGR.
                img.setColor(x, y, (p & 0xFF00FF00) | ((p & 0xFF) << 16) | ((p >> 16) & 0xFF));
            }
        }
        canvasTex.setFilter(false, false);
        canvasTex.upload();
        canvasDirty = false;
    }

    @Override
    public void removed() {
        if (previewItem != null) {
            previewItem.close(client);
            previewItem = null;
        }
        if (canvasId != null) {
            client.getTextureManager().destroyTexture(canvasId);
            canvasId = null;
            canvasTex = null;
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

    // ---- drawing ----------------------------------------------------------------------------

    private void button(DrawContext ctx, Theme th, int x, int y, int w, int h, String label, boolean on, boolean hover) {
        int bg = on ? th.accent() : (hover ? th.hover() : th.panel2());
        Ui.roundRect(ctx, x, y, w, h, 5, bg);
        Ui.textCentered(ctx, textRenderer, label, x + w / 2, y + (h - 8) / 2, on ? (th.bg() | 0xFF000000) : th.text());
    }

    private void slider(DrawContext ctx, Theme th, int x, int y, int w, String label, float value01, String text) {
        Ui.text(ctx, textRenderer, label, x, y, th.dim());
        Ui.text(ctx, textRenderer, text, x + w - Ui.width(textRenderer, text), y, th.text());
        Ui.roundRect(ctx, x, y + 11, w, 6, 3, th.panel2());
        int fw = Math.round(value01 * (w - 2));
        Ui.roundRect(ctx, x + 1, y + 12, Math.max(2, fw), 4, 2, th.accent());
        Ui.roundRect(ctx, x + Math.round(value01 * (w - 8)), y + 9, 8, 10, 4, th.text());
    }

    private int toolsY() { return 40; }
    private int slidersY() { return 98; }
    private int buttonsY() { return 178; }
    private int colorY() { return 40; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Theme th = Interface.theme();
        if (!viewFitted) {
            fitView();
        }
        ctx.fill(0, 0, width, height, Ui.alpha(th.bg() & 0xFFFFFF, 0.97f));
        Ui.text(ctx, textRenderer, "Editor: " + target.title(), 14, barY() + 6, th.accent());
        Ui.text(ctx, textRenderer, cw + " x " + ch, 14 + Ui.width(textRenderer, "Editor: " + target.title()) + 10, barY() + 6, th.dim());

        if (nameFocus) {
            Ui.roundRect(ctx, nameX() - 1, barY() - 1, nameW() + 2, 22, 6, th.accent());
        }
        Ui.roundRect(ctx, nameX(), barY(), nameW(), 20, 5, th.panel2());
        String shown = name.isEmpty() ? "Name of the " + target.noun() : name + (nameFocus && System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        Ui.text(ctx, textRenderer, shown, nameX() + 7, barY() + 6, name.isEmpty() ? th.dim() : th.text());
        boolean saveHover = inside(mx, my, saveX(), barY(), saveW(), 20);
        Ui.roundRect(ctx, saveX(), barY(), saveW(), 20, 5, saveHover ? th.accent2() : th.accent());
        Ui.textCentered(ctx, textRenderer, "Save", saveX() + saveW() / 2, barY() + 6, th.bg() | 0xFF000000);

        // column A: tools, brush, buttons
        String[] toolNames = {"Brush", "Eraser", "Fill", "Pick"};
        Tool[] tools = Tool.values();
        for (int i = 0; i < 4; i++) {
            int bx = COL_A + (i % 2) * 64, by = toolsY() + (i / 2) * 26;
            button(ctx, th, bx, by, 60, 22, toolNames[i], tool == tools[i], inside(mx, my, bx, by, 60, 22));
        }
        int sy = slidersY();
        float sizeV = (float) Math.sqrt((brushSize - 1f) / Math.max(1f, maxBrush() - 1f));
        slider(ctx, th, COL_A, sy, COL_A_W, "Size", Math.max(0f, Math.min(1f, sizeV)), String.valueOf(Math.round(brushSize)));
        slider(ctx, th, COL_A, sy + 26, COL_A_W, "Hardness", hardness, Math.round(hardness * 100) + "%");
        slider(ctx, th, COL_A, sy + 52, COL_A_W, "Opacity", opacity, Math.round(opacity * 100) + "%");
        int by0 = buttonsY();
        String[][] grid2 = {{"Mirror", ""}, {"Grid", ""}, {"Guide", ""}, {"Undo", ""}, {"Redo", ""}, {"Clear", ""}};
        boolean[] on = {mirror, grid, guide, false, false, false};
        for (int i = 0; i < 6; i++) {
            int bx = COL_A + (i % 2) * 64, by = by0 + (i / 2) * 22;
            button(ctx, th, bx, by, 60, 18, grid2[i][0], on[i], inside(mx, my, bx, by, 60, 18));
        }
        int y2 = by0 + 66;
        button(ctx, th, COL_A, y2, 60, 18, "x" + res, false, inside(mx, my, COL_A, y2, 60, 18));
        if (target.sizes() != null) {
            button(ctx, th, COL_A + 64, y2, 60, 18, template.width() + " px", false, inside(mx, my, COL_A + 64, y2, 60, 18));
        }
        button(ctx, th, COL_A, y2 + 22, 60, 18, "Import", false, inside(mx, my, COL_A, y2 + 22, 60, 18));
        button(ctx, th, COL_A + 64, y2 + 22, 60, 18, importStretch ? "Stretch" : "Fit", importStretch, inside(mx, my, COL_A + 64, y2 + 22, 60, 18));
        button(ctx, th, COL_A, y2 + 44, 38, 18, "-", false, inside(mx, my, COL_A, y2 + 44, 38, 18));
        button(ctx, th, COL_A + 42, y2 + 44, 38, 18, "+", false, inside(mx, my, COL_A + 42, y2 + 44, 38, 18));
        button(ctx, th, COL_A + 84, y2 + 44, 40, 18, "Fit view", false, inside(mx, my, COL_A + 84, y2 + 44, 40, 18));

        // column B: colour
        int cy = colorY();
        int cells = 20;
        int cwid = SV_W / cells, chei = SV_H / 12;
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < 12; j++) {
                int c = java.awt.Color.HSBtoRGB(hue, i / (float) (cells - 1), 1f - j / 11f) | 0xFF000000;
                ctx.fill(COL_B + i * cwid, cy + j * chei, COL_B + (i + 1) * cwid, cy + (j + 1) * chei, c);
            }
        }
        int hx = COL_B + Math.round(sat * (SV_W - cwid)), hy = cy + Math.round((1f - val) * (SV_H - chei));
        ctx.fill(hx - 1, hy - 1, hx + cwid + 1, hy + chei + 1, 0xFFFFFFFF);
        ctx.fill(hx, hy, hx + cwid, hy + chei, color());
        int hyBar = cy + SV_H + 6;
        for (int i = 0; i < SV_W; i++) {
            ctx.fill(COL_B + i, hyBar, COL_B + i + 1, hyBar + HUE_H, java.awt.Color.HSBtoRGB(i / (float) SV_W, 1f, 1f) | 0xFF000000);
        }
        int hm = COL_B + Math.round(hue * (SV_W - 1));
        ctx.fill(hm - 1, hyBar - 2, hm + 2, hyBar + HUE_H + 2, 0xFFFFFFFF);
        int py = hyBar + HUE_H + 8;
        for (int i = 0; i < PALETTE.length; i++) {
            int px = COL_B + (i % 8) * 13, pyy = py + (i / 8) * 13;
            ctx.fill(px, pyy, px + 11, pyy + 11, PALETTE[i]);
        }
        ctx.fill(COL_B, py + 30, COL_B + 36, py + 46, color());
        Ui.text(ctx, textRenderer, String.format("#%06X", color() & 0xFFFFFF), COL_B + 42, py + 34, th.dim());

        renderCanvas(ctx, th, mx, my);
        if (hasPreview()) {
            renderPreview(ctx, th);
        }

        Ui.text(ctx, textRenderer, textRenderer.trimToWidth(template.hint() + "  (wheel: zoom, right drag: move)", width - 24), 12, height - 24, th.dim());
        if (!status.isEmpty()) {
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(status, width - 24), 12, height - 12, th.accent());
        }
    }

    private void renderCanvas(DrawContext ctx, Theme th, int mx, int my) {
        uploadCanvas();
        int ax = areaX(), ay = areaY(), aw = areaW(), ah = areaH();
        Ui.roundRect(ctx, ax - 3, ay - 3, aw + 6, ah + 6, 4, th.panel());
        ctx.enableScissor(ax, ay, ax + aw, ay + ah);
        float ox = ax + panX, oy = ay + panY;
        // the checkerboard shows what is transparent
        int cell = 8;
        int x0 = Math.max(ax, (int) Math.floor(ox)), x1 = Math.min(ax + aw, (int) Math.ceil(ox + cw * zoom));
        int y0 = Math.max(ay, (int) Math.floor(oy)), y1 = Math.min(ay + ah, (int) Math.ceil(oy + ch * zoom));
        for (int y = y0; y < y1; y += cell) {
            for (int x = x0; x < x1; x += cell) {
                int c = (((x - x0) / cell + (y - y0) / cell) & 1) == 0 ? 0xFF3A3A44 : 0xFF2C2C34;
                ctx.fill(x, y, Math.min(x + cell, x1), Math.min(y + cell, y1), c);
            }
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ox, oy, 0);
        ctx.getMatrices().scale(zoom, zoom, 1f);
        ctx.drawTexture(canvasId, 0, 0, cw, ch, 0f, 0f, cw, ch, cw, ch);
        ctx.getMatrices().pop();
        if (grid && zoom >= 8f) {
            int gc = Ui.alpha(0xFFFFFF, 0.1f);
            for (int x = 0; x <= cw; x++) {
                int sx = Math.round(ox + x * zoom);
                if (sx >= ax && sx <= ax + aw) {
                    ctx.fill(sx, y0, sx + 1, y1, gc);
                }
            }
            for (int y = 0; y <= ch; y++) {
                int sy = Math.round(oy + y * zoom);
                if (sy >= ay && sy <= ay + ah) {
                    ctx.fill(x0, sy, x1, sy + 1, gc);
                }
            }
        }
        if (guide) {
            for (CosmeticBuilder.Zone z : template.zones()) {
                int zx = Math.round(ox + z.x() * res * zoom), zy = Math.round(oy + z.y() * res * zoom);
                int zw = Math.round(z.w() * res * zoom), zh = Math.round(z.h() * res * zoom);
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
        // the brush outline under the cursor
        if (inside(mx, my, ax, ay, aw, ah) && (tool == Tool.BRUSH || tool == Tool.ERASER)) {
            float rr = Math.max(1.5f, brushSize / 2f * zoom);
            int steps = Math.max(16, Math.min(96, (int) (rr * 2)));
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2 / steps;
                int px = (int) Math.round(mx + Math.cos(a) * rr), pyy = (int) Math.round(my + Math.sin(a) * rr);
                ctx.fill(px, pyy, px + 1, pyy + 1, 0xFFFFFFFF);
            }
        }
        ctx.disableScissor();
    }

    // ---- input ------------------------------------------------------------------------------

    private void colorDrag(double mx, double my) {
        int cy = colorY();
        if (drag == 2) {
            sat = (float) Math.max(0, Math.min(1, (mx - COL_B) / (SV_W - 1.0)));
            val = (float) Math.max(0, Math.min(1, 1 - (my - cy) / (SV_H - 1.0)));
        } else if (drag == 3) {
            hue = (float) Math.max(0, Math.min(1, (mx - COL_B) / (SV_W - 1.0)));
        }
    }

    private void sliderDrag(int id, double mx) {
        float v = (float) Math.max(0, Math.min(1, (mx - COL_A - 4) / (COL_A_W - 8.0)));
        if (id == 0) {
            brushSize = 1f + v * v * (maxBrush() - 1f);
        } else if (id == 1) {
            hardness = v;
        } else {
            opacity = Math.max(0.03f, v);
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
        boolean onCanvas = inside(mx, my, areaX(), areaY(), areaW(), areaH());
        if (onCanvas && (button == 1 || button == 2)) {
            drag = 5;
            return true;
        }
        if (button != 0) {
            return true;
        }
        Tool[] tools = Tool.values();
        for (int i = 0; i < 4; i++) {
            if (inside(mx, my, COL_A + (i % 2) * 64, toolsY() + (i / 2) * 26, 60, 22)) {
                tool = tools[i];
                return true;
            }
        }
        int sy = slidersY();
        for (int i = 0; i < 3; i++) {
            if (inside(mx, my, COL_A, sy + i * 26 + 6, COL_A_W, 16)) {
                drag = 10 + i;
                sliderDrag(i, mx);
                return true;
            }
        }
        int by0 = buttonsY();
        for (int i = 0; i < 6; i++) {
            if (inside(mx, my, COL_A + (i % 2) * 64, by0 + (i / 2) * 22, 60, 18)) {
                switch (i) {
                    case 0 -> mirror = !mirror;
                    case 1 -> grid = !grid;
                    case 2 -> guide = !guide;
                    case 3 -> doUndo();
                    case 4 -> doRedo();
                    default -> {
                        snapshot();
                        Arrays.fill(pixels, 0);
                        canvasDirty = true;
                        previewDirty = true;
                    }
                }
                return true;
            }
        }
        int y2 = by0 + 66;
        if (inside(mx, my, COL_A, y2, 60, 18)) {
            int next = RESOLUTIONS[(java.util.Arrays.binarySearch(RESOLUTIONS, res) + 1) % RESOLUTIONS.length];
            rescale(next, sizeIndex);
            return true;
        }
        if (target.sizes() != null && inside(mx, my, COL_A + 64, y2, 60, 18)) {
            rescale(res, (sizeIndex + 1) % target.sizes().length);
            return true;
        }
        if (inside(mx, my, COL_A, y2 + 22, 60, 18)) {
            importNext();
            return true;
        }
        if (inside(mx, my, COL_A + 64, y2 + 22, 60, 18)) {
            importStretch = !importStretch;
            return true;
        }
        if (inside(mx, my, COL_A, y2 + 44, 38, 18)) {
            zoomAround(areaX() + areaW() / 2.0, areaY() + areaH() / 2.0, 1f / 1.4f);
            return true;
        }
        if (inside(mx, my, COL_A + 42, y2 + 44, 38, 18)) {
            zoomAround(areaX() + areaW() / 2.0, areaY() + areaH() / 2.0, 1.4f);
            return true;
        }
        if (inside(mx, my, COL_A + 84, y2 + 44, 40, 18)) {
            fitView();
            return true;
        }
        int cy = colorY();
        int hyBar = cy + SV_H + 6;
        int py = hyBar + HUE_H + 8;
        if (inside(mx, my, COL_B, cy, SV_W, SV_H)) {
            drag = 2;
            colorDrag(mx, my);
            tool = tool == Tool.ERASER ? Tool.BRUSH : tool;
        } else if (inside(mx, my, COL_B, hyBar - 2, SV_W, HUE_H + 4)) {
            drag = 3;
            colorDrag(mx, my);
        } else if (inside(mx, my, COL_B, py, 8 * 13, 26)) {
            int i = (int) ((mx - COL_B) / 13) + 8 * (int) ((my - py) / 13);
            if (i >= 0 && i < PALETTE.length) {
                setColor(PALETTE[i]);
                tool = tool == Tool.ERASER ? Tool.BRUSH : tool;
            }
        } else if (onCanvas) {
            drag = 1;
            paintAt(mx, my, true);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == 1) {
            paintAt(mx, my, false);
        } else if (drag == 5) {
            panX += (float) dx;
            panY += (float) dy;
        } else if (drag >= 10) {
            sliderDrag(drag - 10, mx);
        } else if (drag != 0) {
            colorDrag(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag == 1 && painted) {
            previewDirty = true;
        }
        painted = false;
        drag = 0;
        lastX = Float.NaN;
        return true;
    }

    private void zoomAround(double mx, double my, float factor) {
        float cxBefore = toCanvasX(mx), cyBefore = toCanvasY(my);
        zoom = Math.max(0.1f, Math.min(64f, zoom * factor));
        panX = (float) (mx - areaX() - cxBefore * zoom);
        panY = (float) (my - areaY() - cyBefore * zoom);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (inside(mx, my, areaX(), areaY(), areaW(), areaH())) {
            zoomAround(mx, my, (float) Math.pow(1.18, amount));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
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
            if ((modifiers & 1) != 0) {
                doRedo();
            } else {
                doUndo();
            }
            return true;
        }
        if (keyCode == 89 && (modifiers & 2) != 0) {
            doRedo();
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
