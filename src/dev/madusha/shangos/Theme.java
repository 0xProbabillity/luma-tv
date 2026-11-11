package dev.madusha.shangos;

/**
 * Colour tokens. {@code light} is true on a pale wallpaper (Daybreak by day): text turns dark and
 * glass turns white.
 */
final class Theme {
    private Theme() {}

    /**
     * Text and glyph colour with the given alpha: near-black on a pale wallpaper, white elsewhere.
     */
    static int ink(boolean light, int alpha) {
        return (alpha << 24) | (light ? 0x1D1D1F : 0xFFFFFF);
    }

    /** Unfocused glass: frosted white on a pale wallpaper, a faint white veil on dark ones. */
    static int glass(boolean light) {
        return light ? 0x8CFFFFFF : 0x1FFFFFFF;
    }

    /**
     * A focused control: a dark pill with white text on the pale wallpaper, a white pill elsewhere.
     */
    static int focusFill(boolean light) {
        return light ? 0xFF1D1D1F : 0xFAFFFFFF;
    }

    static int focusInk(boolean light) {
        return light ? 0xFFFFFFFF : 0xFF1D1D1F;
    }

    static int restFill(boolean light) {
        return light ? 0x99FFFFFF : 0x1AFFFFFF;
    }

    /** Dialogs and menus are always dark glass, whatever the wallpaper. */
    static final int PANEL = 0xF51C1C1E, SCRIM = 0xA6000000;

    /** Focused row inside a dark panel. */
    static final int PANEL_FOCUS = 0xFAFFFFFF, PANEL_FOCUS_INK = 0xFF111827, PANEL_ROW = 0x14FFFFFF;

    /** Switch on / accent. */
    static final int ON = 0xFF34C759, ACCENT = 0xFF0A84FF;
}
