package dev.madusha.shangos;

/**
 * Colour of an app, taken from its own icon, so every tile can be tinted without a table of known
 * apps. Pure arithmetic on ARGB ints (no Android classes), so it is unit tested.
 */
final class Tint {
    private Tint() {}

    /**
     * Used until an icon has been read, and for icons with no usable colour (all white, all black,
     * empty).
     */
    static final int NEUTRAL = 0xFF2A3042;

    /**
     * The icon's accent: the average of its colourful pixels, each weighted by how saturated and
     * how opaque it is, so a red icon on a white square gives red, not pink. Near-white, near-black
     * and transparent pixels do not vote.
     */
    static int dominant(int[] argb) {
        double r = 0, g = 0, b = 0, total = 0;
        for (int pixel : argb) {
            int a = pixel >>> 24;
            if (a < 64) continue;
            int pr = (pixel >> 16) & 255, pg = (pixel >> 8) & 255, pb = pixel & 255;
            int max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
            if (max < 40 || min > 225) continue; // near black, near white
            double saturation = (max - min) / (double) max;
            if (saturation < .18) continue; // greys carry no colour
            double weight = saturation * saturation * (a / 255.0);
            r += pr * weight;
            g += pg * weight;
            b += pb * weight;
            total += weight;
        }
        if (total < 1e-6) return NEUTRAL;
        return 0xFF000000 | (clamp(r / total) << 16) | (clamp(g / total) << 8) | clamp(b / total);
    }

    /**
     * Lighter and darker versions of {@code color} for a tile's top and bottom, kept from becoming
     * harsh or muddy.
     */
    static int[] gradient(int color) {
        return new int[] {mix(color, 0xFFFFFFFF, .14f), mix(color, 0xFF000000, .38f)};
    }

    /** {@code amount} (0..1) of {@code to} mixed into {@code from}, opaque. */
    static int mix(int from, int to, float amount) {
        int r = Math.round(((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int g = Math.round(((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int b = Math.round((from & 255) * (1 - amount) + (to & 255) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** True if dark text is more readable on {@code color} than light text (relative luminance). */
    static boolean isLight(int color) {
        double r = ((color >> 16) & 255) / 255.0,
                g = ((color >> 8) & 255) / 255.0,
                b = (color & 255) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b > .6;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
