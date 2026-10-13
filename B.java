import com.example.neurocanvas.avatar.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class B {
  public static void main(String[] a) throws Exception {
    for (CosmeticPack.Type t : CosmeticPack.Type.values()) {
      if (!CosmeticBuilder.hasEditor(t)) { System.out.println(t + ": no editor"); continue; }
      CosmeticBuilder.Template tpl = CosmeticBuilder.template(t, 32);
      BufferedImage img = new BufferedImage(tpl.width(), tpl.height(), BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, 0xFF000000 | (x * 7 << 16) | (y * 7 << 8));
      Map<String, byte[]> files = CosmeticBuilder.build(t, img);
      StringBuilder sb = new StringBuilder(t + " " + tpl.width() + "x" + tpl.height() + " files=" + files.keySet());
      if (files.containsKey("model.bbmodel")) {
        BbModel m = BbModel.parse(new String(files.get("model.bbmodel"), StandardCharsets.UTF_8));
        int faces = 0; for (BbModel.Bone b : m.bonesByUuid.values()) for (BbModel.Cube c : b.cubes) for (BbModel.Face f : c.faces) if (f != null && f.uv != null) faces++;
        sb.append(" cubes=").append(m.cubeCount).append(" bones=").append(m.bonesByUuid.size()).append(" texturedFaces=").append(faces).append(" anims=").append(m.animations.keySet()).append(" tex=").append(m.textures.get(0).length > 0);
        if (t == CosmeticPack.Type.OUTFIT) { BbModel.Cube c = m.bonesByUuid.values().iterator().next().cubes.get(0); sb.append(" frontUV=").append(Arrays.toString(c.faces[0].uv)); }
      }
      System.out.println(sb);
    }
  }
}
