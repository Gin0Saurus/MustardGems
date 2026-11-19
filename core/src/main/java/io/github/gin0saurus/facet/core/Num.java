package io.github.gin0saurus.facet.core;

/** Lenient number reading and clamping for config values. */
public final class Num {
    private Num() {}

    public static double d(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return def;
        try { return Double.parseDouble(String.valueOf(o).trim()); } catch (NumberFormatException e) { return def; }
    }

    public static long i(Object o, long def) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return def;
        try { return Math.round(Double.parseDouble(String.valueOf(o).trim())); } catch (NumberFormatException e) { return def; }
    }

    public static double clamp(double v, double min, double max, String where, Issues issues) {
        if (Double.isNaN(v)) { if (issues != null) issues.warn(where + ": not a number, using " + min); return min; }
        if (v < min) { if (issues != null) issues.warn(where + ": " + v + " is below the minimum " + min + ", clamped"); return min; }
        if (v > max) { if (issues != null) issues.warn(where + ": " + v + " is above the maximum " + max + ", clamped"); return max; }
        return v;
    }
}
