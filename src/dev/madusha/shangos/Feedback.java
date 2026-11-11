package dev.madusha.shangos;

import android.animation.ValueAnimator;

/**
 * What moving around feels like: whether things animate, the quiet navigation sounds, and which way
 * the d-pad last moved (the tilt of a focused tile and the pitch of its sound follow it).
 */
final class Feedback {
    private final Prefs prefs;
    private final SonicFeedback sonic;
    private int directionX = 1, directionY = 0;

    Feedback(Prefs prefs, SonicFeedback sonic) {
        this.prefs = prefs;
        this.sonic = sonic;
    }

    /** True when the user left motion on and the system has not turned animations off. */
    boolean motion() {
        return prefs.motion() && ValueAnimator.areAnimatorsEnabled();
    }

    /** The sound of choosing something. */
    void click() {
        if (prefs.sound()) sonic.open();
    }

    /** The sound of moving to tile or row {@code index}. */
    void step(int index) {
        if (prefs.sound())
            sonic.step(
                    (index % AppTile.COLUMNS) / (AppTile.COLUMNS - 1f), directionX + directionY);
    }

    int directionX() {
        return directionX;
    }

    int directionY() {
        return directionY;
    }

    /** The d-pad moved by {@code x} columns and {@code y} rows (each -1, 0 or 1). */
    void moved(int x, int y) {
        directionX = x;
        directionY = y;
    }
}
