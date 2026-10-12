package com.example.neurocanvas.gui;

import net.minecraft.client.gui.DrawContext;

/** The pictures of the ping wheel: 9 x 9 pixel icons drawn from bitmaps, scaled up with whole pixels. */
public final class PingIcons {
    public static final int SIZE = 9;
    /** The eight pings of the outer ring. */
    public static final int PINGS = 8;

    public static final String[] NAMES = {"Attention", "Question", "Go here", "Danger", "Look", "Loot", "Enemy", "Help",
        "Clear my pings", "Countdown", "Wave", "Dance"};
    public static final int[] COLORS = {0xFFD23F, 0x4DD2FF, 0x5CE08A, 0xFF5C5C, 0xE8E8F5, 0xB06BFF, 0xFF8A3D, 0xFF7AB6,
        0xC8C8D8, 0xFFD23F, 0x5CE08A, 0xB06BFF};

    private static final String[][] BITMAPS = {
        {"...###...", "...###...", "...###...", "...###...", "...###...", ".........", "...###...", "...###...", "........."},
        {"..#####..", ".##...##.", ".##...##.", ".....##..", "....##...", "...##....", ".........", "...##....", "........."},
        {"...###...", "...###...", "...###...", "#########", ".#######.", "..#####..", "...###...", "....#....", "........."},
        {"##.....##", "###...###", ".###.###.", "..#####..", "...###...", "..#####..", ".###.###.", "###...###", "##.....##"},
        {".........", "..#####..", ".#.....#.", "#...#...#", "#..###..#", "#...#...#", ".#.....#.", "..#####..", "........."},
        {"..#####..", ".#######.", "#########", ".#######.", "..#####..", "...###...", "....#....", ".........", "........."},
        {".#######.", "#########", "##.###.##", "##.###.##", "#########", ".###.###.", "..#####..", "..#.#.#..", "........."},
        {"...###...", "...###...", "...###...", "#########", "#########", "#########", "...###...", "...###...", "...###..."},
        {"#########", "#.......#", "#.......#", "#.#####.#", "#.#####.#", "#.......#", "#.......#", "#.......#", "#########"},
        {"..#####..", ".#..#..#.", "#...#...#", "#...#...#", "#...###.#", "#.......#", ".#.....#.", "..#####..", "........."},
        {"..#.#.#..", "..#.#.#.#", "..#####.#", ".#######.", ".#######.", "..######.", "...####..", "....##...", "........."},
        {"...#####.", "...#...#.", "...#...#.", "...#...#.", "...#...#.", ".###.###.", "####.###.", ".##...#..", "........."}};

    private PingIcons() { }

    public static void draw(DrawContext ctx, int icon, int x, int y, int scale, int argb) {
        String[] rows = BITMAPS[icon];
        for (int j = 0; j < rows.length; j++) {
            String row = rows[j];
            int i = 0;
            while (i < row.length()) {
                if (row.charAt(i) != '#') {
                    i++;
                    continue;
                }
                int start = i;
                while (i < row.length() && row.charAt(i) == '#') {
                    i++;
                }
                ctx.fill(x + start * scale, y + j * scale, x + i * scale, y + (j + 1) * scale, argb);
            }
        }
    }
}
