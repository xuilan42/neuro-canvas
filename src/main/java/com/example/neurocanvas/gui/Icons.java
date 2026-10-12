package com.example.neurocanvas.gui;

import com.example.neurocanvas.module.Category;
import net.minecraft.client.gui.DrawContext;

/** Small 9 x 9 pixel icons for the menu sections, drawn from bitmaps so no image files are needed. */
public final class Icons {
    public static final int SIZE = 9;

    private static final String[] VISUALS = {
        ".........",
        "..#####..",
        ".#.....#.",
        "#...#...#",
        "#..###..#",
        "#...#...#",
        ".#.....#.",
        "..#####..",
        "........."};
    private static final String[] COSMETICS = {
        "..#####..",
        ".#.....#.",
        "#########",
        ".#.....#.",
        "..#...#..",
        "...#.#...",
        "....#....",
        ".........",
        "........."};
    private static final String[] COMBAT = {
        "......###",
        ".....###.",
        "....###..",
        "#..###...",
        ".#####...",
        "..###....",
        ".#.#.....",
        "#...#....",
        "........."};
    private static final String[] WORLD = {
        "..#####..",
        ".#..#..#.",
        "#...#...#",
        "#########",
        "#...#...#",
        "#...#...#",
        "#########",
        ".#..#..#.",
        "..#####.."};
    private static final String[] HUD = {
        "#########",
        "#.......#",
        "#.#####.#",
        "#.......#",
        "#.##..#.#",
        "#.......#",
        "#.#.....#",
        "#.......#",
        "#########"};
    private static final String[] MISC = {
        "..#......",
        "#########",
        "..#......",
        ".......#.",
        "#########",
        ".......#.",
        "...#.....",
        "#########",
        "...#....."};
    private static final String[] AI = {
        "....#....",
        "....#....",
        "...###...",
        "..#####..",
        "#########",
        "..#####..",
        "...###...",
        "....#....",
        "....#...."};
    private static final String[] CONFIG = {
        ".........",
        "###......",
        "#########",
        "#.......#",
        "#.......#",
        "#.......#",
        "#.......#",
        "#########",
        "........."};

    private Icons() { }

    private static String[] of(Category c) {
        return switch (c) {
            case VISUALS -> VISUALS;
            case COSMETICS -> COSMETICS;
            case COMBAT -> COMBAT;
            case WORLD -> WORLD;
            case HUD -> HUD;
            case MISC -> MISC;
            case AI -> AI;
            case CONFIG -> CONFIG;
        };
    }

    public static void draw(DrawContext ctx, Category c, int x, int y, int color) {
        String[] rows = of(c);
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
                ctx.fill(x + start, y + j, x + i, y + j + 1, color);
            }
        }
    }
}
