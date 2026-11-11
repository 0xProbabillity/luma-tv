package dev.madusha.shangos;

import android.os.Handler;
import android.widget.FrameLayout;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Follows sunrise and sunset where the TV is: Daybreak switches picture, and with it the colours
 * of the page. Also the debug build's day/night preview.
 */
final class DaylightSync {
    /** What the sync needs from the launcher. */
    interface Host {
        /** True while the launcher is in front: nothing changes while another app is open. */
        boolean started();

        boolean overlayOpen();

        /** True when the page is currently drawn for a pale wallpaper. */
        boolean light();

        boolean motion();

        FrameLayout content();

        void updateWallpaper();

        /** The page is rebuilt in the colours of the new picture. */
        void rebuild();
    }

    private final Daylight daylight;
    private final Prefs prefs;
    private final Handler handler;
    private final Host host;
    private final Runnable tick = this::sync;

    /**
     * QA (debug build): am start -n dev.madusha.shangos/.MainActivity --es shangos.daylight
     * day|night|auto previews either look (not saved).
     */
    private String override = "auto";

    DaylightSync(Daylight daylight, Prefs prefs, Handler handler, Host host) {
        this.daylight = daylight;
        this.prefs = prefs;
        this.handler = handler;
        this.host = host;
    }

    boolean isDay() {
        return override.equals("auto")
                ? daylight.isDay(System.currentTimeMillis())
                : override.equals("day");
    }

    /**
     * The picture shown for a choice: Daybreak has a light one between sunrise and sunset and a
     * dark one at night.
     */
    int variant(int style) {
        return Wallpaper.variant(style, isDay());
    }

    /** True when the saved wallpaper is pale at this moment. */
    boolean pale() {
        return Wallpaper.isPale(variant(Wallpaper.choice(prefs)));
    }

    /** Previews {@code look} ("day", "night" or "auto"); debug builds only. */
    void preview(String look) {
        override = look;
    }

    /** Checks again in {@code millis}: a sunset that passed while an app was open. */
    void checkSoon(long millis) {
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, millis);
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    /** Switches the colours if the light changed, and schedules the next check. */
    void sync() {
        handler.removeCallbacks(tick);
        if (!host.started()) return;
        long now = System.currentTimeMillis();
        handler.postDelayed(
                tick, Math.max(30000, Math.min(3600000, daylight.nextChange(now) - now + 2000)));
        if (host.overlayOpen())
            return; // the wallpaper picker previews freely; the theme follows once it closes
        host.updateWallpaper();
        if (pale() == host.light()) return;
        if (!host.motion()) {
            host.rebuild();
            return;
        }
        FrameLayout content = host.content();
        content.animate().cancel();
        // Not while another app starts: a new page then competes with it for GPU memory, which
        // ended in EGL_BAD_ALLOC on this stick once. Paused, the colours are left as they are;
        // onResume switches them.
        content.animate()
                .alpha(0)
                .setDuration(220)
                .setInterpolator(Motion.EASE)
                .withEndAction(
                        () -> {
                            if (!host.started()) {
                                content.setAlpha(1);
                                return;
                            }
                            host.rebuild();
                            content.setAlpha(0);
                            content.animate()
                                    .alpha(1)
                                    .setDuration(420)
                                    .setInterpolator(Motion.EASE)
                                    .start();
                        })
                .start();
    }

    /** One line for `dumpsys activity`: the zone, today's sun times and the current light. */
    void dump(PrintWriter writer, String prefix, boolean light) {
        long now = System.currentTimeMillis();
        long[] sun = daylight.times(now);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        writer.println(
                prefix
                        + "shangOS daylight: zone="
                        + TimeZone.getDefault().getID()
                        + " sunrise="
                        + f.format(new Date(sun[0]))
                        + " sunset="
                        + f.format(new Date(sun[1]))
                        + " day="
                        + daylight.isDay(now)
                        + " next="
                        + f.format(new Date(daylight.nextChange(now)))
                        + " light="
                        + light);
    }
}
