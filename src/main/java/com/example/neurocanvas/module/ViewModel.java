package com.example.neurocanvas.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
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
    private final Setting editInChat = add(Setting.bool("Edit in chat", false));
    private final Setting resetHand = add(Setting.button("Reset hand"));
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

    // ---- editing the hand with the mouse while the chat is open --------------------------------------------------

    private boolean dragging;
    private boolean wasDown;
    private int lastX;
    private int lastY;

    private boolean editing() {
        return enabled && editInChat.on();
    }

    @Override
    public void tickAlways(MinecraftClient mc) {
        if (resetHand.on()) {
            resetHand.set(0);
            x.value = 0;
            y.value = 0;
            z.value = 0;
            scale.value = 1;
            scaleX.value = 1;
            scaleY.value = 1;
            scaleZ.value = 1;
            persist();
        }
    }

    private static void persist() {
        try {
            com.example.neurocanvas.client.NeuroCanvasClient.modules().save();
        } catch (java.io.IOException ignored) {
            // The values stay for this session.
        }
    }

    private static double clamp(double v, Setting s) {
        return Math.max(s.min, Math.min(s.max, v));
    }

    /**
     * Called for every chat screen that opens. While "Edit in chat" is on: dragging with the left button moves the hand (the world behind the chat
     * shows it live), the wheel brings it closer or sends it away, Shift + wheel changes its size. The strip with the text box is left alone.
     */
    public void hookChat(Screen screen) {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, td) -> chatFrame(s, ctx, mx, my));
        net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, horizontal, vertical) -> {
            if (!editing()) {
                return true;
            }
            if (net.minecraft.client.gui.screen.Screen.hasShiftDown()) {
                scale.value = clamp(scale.value + vertical * 0.04, scale);
            } else {
                z.value = clamp(z.value + vertical * 0.05, z);
            }
            persist();
            return false;
        });
    }

    private void chatFrame(Screen screen, DrawContext ctx, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!editing()) {
            dragging = false;
            wasDown = false;
            return;
        }
        boolean down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (down && !wasDown) {
            dragging = my < screen.height - 18;
            lastX = mx;
            lastY = my;
        }
        if (dragging && down) {
            float side = mc.player != null && mc.player.getMainArm() == Arm.LEFT ? -1f : 1f;
            x.value = clamp(x.value + (mx - lastX) / (double) screen.width * 2.0 * side, x);
            y.value = clamp(y.value - (my - lastY) / (double) screen.height * 1.6, y);
            lastX = mx;
            lastY = my;
        }
        if (!down && dragging) {
            dragging = false;
            persist();
        }
        wasDown = down;
        String hint = "Hand editor: drag = move   wheel = closer / farther   Shift + wheel = size";
        ctx.drawCenteredTextWithShadow(mc.textRenderer, hint, screen.width / 2, screen.height - 34, 0xFFFFFF);
    }
}
