package dev.madusha.shangos;

import android.os.Handler;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Calm mode: after a quiet moment the tab bar and clock fade back so the wallpaper has the room,
 * and return the moment a key is pressed. One poll a second while Home is in front.
 */
final class Calm {
    /** Quiet time before the chrome fades back. */
    private static final long IDLE_MS = 20_000;

    private final Handler handler;
    private final Chrome chrome;
    private final BooleanSupplier allowed;
    private final LongSupplier idle;
    private boolean running;

    private final Runnable tick =
            new Runnable() {
                public void run() {
                    if (!running) return;
                    chrome.setCalm(allowed.getAsBoolean() && idle.getAsLong() >= IDLE_MS);
                    handler.postDelayed(this, 1000);
                }
            };

    /**
     * {@code allowed}: the setting is on, a page with a wallpaper shows and no dialog is open.
     * {@code idle}: milliseconds since the last key press.
     */
    Calm(Handler handler, Chrome chrome, BooleanSupplier allowed, LongSupplier idle) {
        this.handler = handler;
        this.chrome = chrome;
        this.allowed = allowed;
        this.idle = idle;
    }

    void start() {
        handler.removeCallbacks(tick);
        running = true;
        handler.postDelayed(tick, 1000);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
        chrome.setCalm(false);
    }

    /** A key was pressed: the chrome returns at once. */
    void poke() {
        chrome.setCalm(false);
    }
}
