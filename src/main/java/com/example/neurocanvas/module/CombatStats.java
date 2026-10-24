package com.example.neurocanvas.module;

import com.example.neurocanvas.gui.Theme;
import com.example.neurocanvas.gui.Ui;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Clicks per second, the current hit combo and the number of hits you landed. */
public final class CombatStats extends Module implements WorldState.CombatListener {
    private static final int ROW_H = 12;
    private static final HudLayout.Element ELEMENT = HudLayout.register("combatstats", "Combat stats", false, 0.006, 0.2);

    static {
        ELEMENT.w = 90;
        ELEMENT.h = ROW_H * 3 + 6;
    }

    private final Setting scale = add(Setting.number("Scale", 0.6, 2, 1));
    private final Setting background = add(Setting.number("Background", 0, 1, 0.7));
    private final Setting cps = add(Setting.bool("CPS", true));
    private final Setting combo = add(Setting.bool("Combo", true));
    private final Setting hits = add(Setting.bool("Hits", true));
    private final Setting comboTimeout = add(Setting.number("Combo timeout (s)", 1, 10, 4));

    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();
    private boolean leftDown;
    private boolean rightDown;
    private int comboCount;
    private int best;
    private int totalHits;
    private long lastHit;

    public CombatStats() {
        super("CombatStats", Category.HUD, "CPS, combo and hit counter", false);
        hudSize();
        WorldState.addCombatListener(this);
    }

    @Override
    public void onDamage(String name, float amount, boolean dealt) {
        if (!enabled) {
            return;
        }
        if (dealt) {
            comboCount++;
            totalHits++;
            best = Math.max(best, comboCount);
            lastHit = System.currentTimeMillis();
        } else {
            comboCount = 0;
        }
    }

    private void poll(MinecraftClient mc, long now) {
        if (mc.currentScreen == null) {
            long window = mc.getWindow().getHandle();
            boolean left = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean right = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (left && !leftDown) {
                leftClicks.add(now);
            }
            if (right && !rightDown) {
                rightClicks.add(now);
            }
            leftDown = left;
            rightDown = right;
        }
        while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) {
            leftClicks.pollFirst();
        }
        while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) {
            rightClicks.pollFirst();
        }
        if (comboCount > 0 && now - lastHit > comboTimeout.value * 1000) {
            comboCount = 0;
        }
    }

    @Override
    public void renderHud(DrawContext ctx, MinecraftClient mc) {
        poll(mc, System.currentTimeMillis());
        Theme th = Interface.theme();
        String[][] rows = new String[3][];
        int n = 0;
        if (cps.on()) {
            rows[n++] = new String[] {"CPS", leftClicks.size() + " | " + rightClicks.size()};
        }
        if (combo.on()) {
            rows[n++] = new String[] {"Combo", comboCount + "  (best " + best + ")"};
        }
        if (hits.on()) {
            rows[n++] = new String[] {"Hits", String.valueOf(totalHits)};
        }
        float s = scale.f();
        int labelW = 0;
        int valueW = 0;
        for (int i = 0; i < n; i++) {
            labelW = Math.max(labelW, Ui.width(mc.textRenderer, rows[i][0]));
            valueW = Math.max(valueW, Ui.width(mc.textRenderer, rows[i][1]));
        }
        int w = Math.max(70, labelW + valueW + 24);
        int h = Math.max(1, n) * ROW_H + 6;
        ELEMENT.w = Math.round(w * s);
        ELEMENT.h = Math.round(h * s);
        if (n == 0 || mc.options.hudHidden) {
            return;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ELEMENT.x(ctx.getScaledWindowWidth()), ELEMENT.y(ctx.getScaledWindowHeight()), 0);
        ctx.getMatrices().scale(s, s, 1f);
        Ui.roundRect(ctx, 0, 0, w, h, 6, ((int) (255 * background.f()) << 24) | (th.panel() & 0xFFFFFF));
        int y = 4;
        for (int i = 0; i < n; i++) {
            Ui.text(ctx, mc.textRenderer, rows[i][0], 7, y, th.dim());
            Ui.text(ctx, mc.textRenderer, rows[i][1], w - 7 - Ui.width(mc.textRenderer, rows[i][1]), y, th.text());
            y += ROW_H;
        }
        ctx.getMatrices().pop();
    }
}
