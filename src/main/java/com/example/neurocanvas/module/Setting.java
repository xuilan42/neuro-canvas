package com.example.neurocanvas.module;

import com.google.gson.JsonElement;

/** One adjustable value of a module. Every kind is stored as a double so persistence stays trivial. */
public final class Setting {
    public enum Kind { NUMBER, BOOL, COLOR, CHOICE }

    public final String name;
    public final Kind kind;
    public final double min;
    public final double max;
    public final String[] options;
    public double value;

    private Setting(String name, Kind kind, double min, double max, double value, String[] options) {
        this.name = name;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.value = value;
        this.options = options;
    }

    public static Setting number(String name, double min, double max, double def) {
        return new Setting(name, Kind.NUMBER, min, max, def, null);
    }

    public static Setting bool(String name, boolean def) {
        return new Setting(name, Kind.BOOL, 0, 1, def ? 1 : 0, null);
    }

    /** A colour with alpha, stored as packed ARGB. {@code hue} only picks the default. */
    public static Setting color(String name, double hue) {
        int argb = 0xFF000000 | (java.awt.Color.HSBtoRGB((float) hue, 0.85f, 1f) & 0xFFFFFF);
        return new Setting(name, Kind.COLOR, 0, 4294967295.0, argb & 0xFFFFFFFFL, null);
    }

    /** Packed ARGB. Values saved by earlier versions (a bare hue in [0, 1]) are converted on read. */
    public int argb() {
        if (value <= 1.0) {
            return 0xFF000000 | (java.awt.Color.HSBtoRGB((float) value, 0.85f, 1f) & 0xFFFFFF);
        }
        return (int) (long) value;
    }

    public void setArgb(int argb) {
        value = argb & 0xFFFFFFFFL;
    }

    public static Setting choice(String name, int def, String... options) {
        return new Setting(name, Kind.CHOICE, 0, options.length - 1, def, options);
    }

    public void set(double v) {
        value = Math.max(min, Math.min(max, v));
    }

    public boolean on() {
        return value >= 0.5;
    }

    public float f() {
        return (float) value;
    }

    public int index() {
        return (int) Math.round(value);
    }

    public String option() {
        return options[index()];
    }

    public void cycle(int dir) {
        int n = options.length;
        value = ((index() + dir) % n + n) % n;
    }

    private static int toArgb(double v) {
        if (v <= 1.0) {
            return 0xFF000000 | (java.awt.Color.HSBtoRGB((float) v, 0.85f, 1f) & 0xFFFFFF);
        }
        return (int) (long) v;
    }

    /** Human-readable form of a value of this setting. */
    public String format(double v) {
        switch (kind) {
            case BOOL:
                return v >= 0.5 ? "ON" : "OFF";
            case CHOICE:
                return options[Math.max(0, Math.min(options.length - 1, (int) Math.round(v)))];
            case COLOR: {
                int argb = toArgb(v);
                String hex = String.format("#%06X", argb & 0xFFFFFF);
                int a = argb >>> 24;
                return a < 255 ? hex + " a" + (a * 100 / 255) + "%" : hex;
            }
            default:
                return max - min >= 10 ? String.format("%.0f", v) : String.format("%.2f", v);
        }
    }

    /** Reads a value written by a person or a model; returns null when it does not fit this setting. */
    public Double parse(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        try {
            switch (kind) {
                case NUMBER: {
                    double v = e.getAsDouble();
                    return Double.isFinite(v) ? v : null;
                }
                case BOOL: {
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
                        return e.getAsBoolean() ? 1.0 : 0.0;
                    }
                    String s = e.getAsString().trim().toLowerCase();
                    if (s.equals("true") || s.equals("on") || s.equals("1")) return 1.0;
                    if (s.equals("false") || s.equals("off") || s.equals("0")) return 0.0;
                    return null;
                }
                case COLOR: {
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                        double d = e.getAsDouble();
                        return d <= 1.0 ? (double) (toArgb(d) & 0xFFFFFFFFL) : d;
                    }
                    String s = e.getAsString().trim();
                    if (s.startsWith("#")) {
                        s = s.substring(1);
                    }
                    if (s.length() == 6) {
                        return (double) (0xFF000000L | Long.parseLong(s, 16));
                    }
                    if (s.length() == 8) {
                        return (double) Long.parseLong(s, 16);
                    }
                    return null;
                }
                case CHOICE: {
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                        int i = (int) Math.round(e.getAsDouble());
                        return i >= 0 && i < options.length ? (double) i : null;
                    }
                    String s = e.getAsString().trim();
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].equalsIgnoreCase(s)) {
                            return (double) i;
                        }
                    }
                    return null;
                }
                default:
                    return null;
            }
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
