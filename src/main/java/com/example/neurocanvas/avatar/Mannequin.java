package com.example.neurocanvas.avatar;

import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;

/**
 * A plain Steve (6 boxes, the skin of the player who plays) on which cosmetics are shown in the menus: the Market cards and the
 * editor preview. It uses the same space as every cosmetic, so a cape, a hat, wings or an outfit sit on it exactly as in the game.
 */
public final class Mannequin {
    private static BbInstance instance;
    private static boolean failed;

    private Mannequin() { }

    private static BbInstance get() {
        if (instance == null && !failed) {
            try {
                instance = new BbInstance(MinecraftClient.getInstance(), BbModel.parse(ExampleAvatar.mannequinJson()));
            } catch (IOException | RuntimeException e) {
                failed = true;
            }
        }
        return instance;
    }

    /** Which kinds of cosmetic are shown on the Steve: the others (emotes, graffiti) are shown by themselves. */
    public static boolean wears(String type) {
        return type.equals("capes") || type.equals("wings") || type.equals("hats") || type.equals("outfits");
    }

    /** Draws the Steve and then the item on it, standing still. {@code base} puts model space (16 units per block) where you want it. */
    public static void drawWith(BbInstance item, String type, Matrix4f base, double seconds) {
        BbInstance steve = get();
        if (steve != null) {
            steve.resetPose();
            steve.draw(base, 1f, 0.6f);
        }
        if (item == null) {
            return;
        }
        item.resetPose();
        BbModel.Anim a = item.find("idle");
        if (a != null) {
            item.apply(a, BbInstance.time(a, 0L, (long) (seconds * 1_000_000_000L), 1.0));
        }
        switch (type) {
            case "capes" -> Attach.cape(item, base, 0.0, seconds, 1f, 0.6f);
            case "wings" -> Attach.wings(item, base, 1f, 0.6f);
            case "hats" -> Attach.hat(item, base, 0f, 0f, 0f, 1f, 0.6f);
            default -> Attach.outfit(item, base, 1f, 0.6f);
        }
    }
}
