package dev.madusha.shangos;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Bundle;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Every curve the app animates with, in one place. Interpolators are stateless and shared. */
final class Motion {
    private Motion() {}

    /** Focus on a tile, menu rows and switches. */
    static final Spring TILE = new Spring(25, .72);

    /** Focus on widgets and their controls: a touch softer. */
    static final Spring WIDGET = new Spring(24, .7);

    /** Fast start, long gentle end: pages and fades. */
    static final Interpolator EASE = new PathInterpolator(.22f, 1f, .36f, 1f);

    /** Slow ambient glides: starts and ends gently. */
    static final Interpolator SOFT = new PathInterpolator(.45f, 0f, .55f, 1f);

    /** Parallax and other slow drifts. */
    static final Interpolator DRIFT = new PathInterpolator(.2f, .72f, .25f, 1f);

    /**
     * Launch options with no window animation (replaces the deprecated overridePendingTransition).
     */
    static Bundle instant(Context context) {
        return ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle();
    }
}
