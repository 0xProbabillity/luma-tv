package dev.madusha.shangos;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Stage lighting: the app you are on tints the light behind the tiles, and the wallpaper dims a
 * touch while focus is on the apps. Two static gradient views cross-fade, so changing the colour
 * costs one gradient per focus and nothing per frame.
 */
final class StageLight extends FrameLayout {
    private static final long FADE_MS = 700, LINGER_MS = 160;

    private final View[] lights = new View[2];
    private final View dim;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int size;
    private int active;
    private int color; // the colour now lit, 0 when none

    /** Focus left the apps (or moved between two): fade out unless another app takes over. */
    private final Runnable off = () -> apply(0, false, 0, true);

    StageLight(Context context) {
        super(context);
        setClipChildren(false);
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        size = Math.round(width * .9f);
        for (int i = 0; i < 2; i++) {
            lights[i] = new View(context);
            lights[i].setAlpha(0);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            lp.leftMargin = (width - size) / 2;
            lp.topMargin = Math.round(height * .74f - size / 2f);
            addView(lights[i], lp);
        }
        dim = new View(context);
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(0);
        addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    /**
     * Lights the room with {@code accent} (0 for no colour) and dims the picture a little.
     * {@code pale} is a light wallpaper, which takes less of both.
     */
    void show(int accent, boolean pale, boolean animate) {
        handler.removeCallbacks(off);
        apply(accent, pale, pale ? .06f : .16f, animate);
    }

    /** Fades out after a moment, which a following {@link #show} cancels. */
    void hideSoon() {
        handler.removeCallbacks(off);
        handler.postDelayed(off, LINGER_MS);
    }

    /** Off at once (the setting is off, or the wallpaper was released). */
    void clear() {
        handler.removeCallbacks(off);
        for (View light : lights) {
            light.animate().cancel();
            light.setAlpha(0);
        }
        dim.animate().cancel();
        dim.setAlpha(0);
        color = 0;
    }

    private void apply(int accent, boolean pale, float dimTo, boolean animate) {
        fade(dim, dimTo, animate);
        if (accent == color) return;
        fade(lights[active], 0, animate);
        if (accent != 0) {
            active = 1 - active;
            lights[active].setBackground(gradient(accent));
            fade(lights[active], pale ? .30f : .46f, animate);
        }
        color = accent;
    }

    private GradientDrawable gradient(int accent) {
        int rgb = accent & 0x00FFFFFF;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(size / 2f);
        g.setColors(new int[] {0xB0000000 | rgb, rgb});
        return g;
    }

    private static void fade(View view, float to, boolean animate) {
        view.animate().cancel();
        if (animate)
            view.animate().alpha(to).setDuration(FADE_MS).setInterpolator(Motion.EASE).start();
        else view.setAlpha(to);
    }
}
