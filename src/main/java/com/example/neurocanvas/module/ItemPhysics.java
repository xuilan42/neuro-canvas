package com.example.neurocanvas.module;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.RotationAxis;

/** Dropped items stop spinning and bobbing, and flat items lie on the ground instead of standing up. */
public final class ItemPhysics extends Module {
    private final Setting lieFlat = add(Setting.bool("Lie flat", true));
    private final Setting noSpin = add(Setting.bool("No spin", true));
    private final Setting noBob = add(Setting.bool("No bobbing", true));

    public ItemPhysics() {
        super("ItemPhysics", Category.WORLD, "Dropped items lie still on the ground", false);
    }

    public boolean stopsSpin() {
        return enabled && noSpin.on();
    }

    public boolean stopsBobbing() {
        return enabled && noBob.on();
    }

    /** Called right after vanilla turned the item around its vertical axis. */
    public void layFlat(ItemEntity item, MatrixStack matrices) {
        if (!enabled || !lieFlat.on() || item.getStack().getItem() instanceof BlockItem) {
            return;
        }
        matrices.translate(0.0, noBob.on() ? -0.2 : -0.1, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
    }
}
