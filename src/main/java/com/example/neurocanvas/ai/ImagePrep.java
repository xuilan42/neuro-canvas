package com.example.neurocanvas.ai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.fabricmc.loader.api.FabricLoader;

/** Makes a picture small enough to send to a vision model: at most 1024 px on a side, as a JPEG data URL. */
public final class ImagePrep {
    private ImagePrep() { }

    public static String dataUrl(Path file) throws IOException {
        BufferedImage src = ImageIO.read(file.toFile());
        if (src == null) {
            throw new IOException("that file is not a picture I can read");
        }
        double scale = Math.min(1.0, 1024.0 / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage flat = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(flat, null, null), param);
        } finally {
            writer.dispose();
        }
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** The newest screenshot the game took (F2), or null when there is none. */
    public static Path latestScreenshot() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
