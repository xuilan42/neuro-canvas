package com.example.neurocanvas.module;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/** Moves, rotates and scales the items and hands you see in first person. */
public final class ViewModel extends Module {
    private final Setting hand = add(Setting.choice("Hand", 0, "Both", "Main", "Off"));
    private final Setting x = add(Setting.number("Position X", -1, 1, 0));
    private final Setting y = add(Setting.number("Position Y", -1, 1, 0));
    private final Setting z = add(Setting.number("Position Z", -1, 1, 0));
    private final Setting rotX = add(Setting.number("Rotation X", -180, 180, 0));
    private final Setting rotY = add(Setting.number("Rotation Y", -180, 180, 0));
    private final Setting rotZ = add(Setting.number("Rotation Z", -180, 180, 0));
    private final Setting scale = add(Setting.number("Scale", 0.3, 2, 1));
    private final Setting scaleX = add(Setting.number("Scale X", 0.3, 2, 1));
    private final Setting scaleY = add(Setting.number("Scale Y", 0.3, 2, 1));
    private final Setting scaleZ = add(Setting.number("Scale Z", 0.3, 2, 1));
    private final Setting noEquip = add(Setting.bool("No equip animation", false));

    public ViewModel() {
        super("ViewModel", Category.VISUALS, "Position, rotation and size of the item in your hand", false);
    }

    public boolean skipEquipAnimation() {
        return enabled && noEquip.on();
    }

    private boolean applies(Hand h) {
        if (!enabled) {
            return false;
        }
        int mode = hand.index();
        return !(mode == 1 && h != Hand.MAIN_HAND || mode == 2 && h != Hand.OFF_HAND);
    }

    /**
     * Step 1 of the hand's transform: the global offset. The swing animation comes next and works in the moved frame, then {@link #shape}
     * rotates and scales. @param side +1 for the right arm, -1 for the left (mirrors X and the Y/Z rotations)
     */
    public void offset(MatrixStack m, Hand h, float side) {
        if (applies(h)) {
            m.translate(x.f() * side, y.f(), z.f());
        }
    }

    /** Last step: rotation and size, around the point where vanilla puts the hand, so the item turns and grows in place. */
    public void shape(MatrixStack m, Hand h, float side) {
        if (!applies(h)) {
            return;
        }
        float px = 0.56f * side;
        float py = -0.52f;
        float pz = -0.72f;
        m.translate(px, py, pz);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX.f()));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY.f() * side));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ.f() * side));
        m.scale(scale.f() * scaleX.f(), scale.f() * scaleY.f(), scale.f() * scaleZ.f());
        m.translate(-px, -py, -pz);
    }
}
