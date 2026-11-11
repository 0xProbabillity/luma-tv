package dev.madusha.shangos;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;

/**
 * A broken build must never hold a TV in a restart loop. Every crash is written down (a few
 * numbers in private preferences); if shangOS has crashed twice in the last five minutes, the next
 * time Android asks it to be the home screen it hands over to the TV's own home instead, and tries
 * again once the five minutes are up. Nothing leaves the TV.
 */
final class CrashGuard {
    private CrashGuard() {}

    /** Crashes inside this window count together. */
    static final long WINDOW_MS = 5 * 60_000L;

    /** This many crashes inside the window make shangOS stand aside. */
    static final int LIMIT = 2;

    private static boolean installed;

    /** True when {@code crashes} (comma-separated times) hold {@link #LIMIT} inside the window. */
    static boolean tripped(String crashes, long now) {
        return recent(crashes, now).length() > 0 && count(recent(crashes, now)) >= LIMIT;
    }

    /** The crashes still inside the window with {@code now} added; at most ten are kept. */
    static String record(String crashes, long now) {
        String kept = recent(crashes, now);
        String all = kept.isEmpty() ? String.valueOf(now) : kept + "," + now;
        String[] parts = all.split(",");
        if (parts.length <= 10) return all;
        StringBuilder out = new StringBuilder();
        for (int i = parts.length - 10; i < parts.length; i++) {
            if (out.length() > 0) out.append(',');
            out.append(parts[i]);
        }
        return out.toString();
    }

    private static String recent(String crashes, long now) {
        if (crashes == null || crashes.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : crashes.split(",")) {
            try {
                long t = Long.parseLong(part.trim());
                if (t > now - WINDOW_MS && t <= now) {
                    if (out.length() > 0) out.append(',');
                    out.append(t);
                }
            } catch (NumberFormatException ignored) {
                // a damaged entry is dropped
            }
        }
        return out.toString();
    }

    private static int count(String crashes) {
        return crashes.isEmpty() ? 0 : crashes.split(",").length;
    }

    /**
     * Call first in {@code onCreate}. Starts writing crashes down and, if shangOS crashed too often
     * just now, opens the TV's own home instead; true then means: finish and do nothing else.
     */
    static boolean begin(Activity activity) {
        Prefs prefs = Prefs.of(activity);
        if (!installed) {
            installed = true;
            final Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(
                    (thread, error) -> {
                        try {
                            prefs.setCrashes(
                                    record(prefs.crashes(), System.currentTimeMillis()));
                        } catch (RuntimeException ignored) {
                            // never hide the crash itself
                        }
                        if (previous != null) previous.uncaughtException(thread, error);
                    });
        }
        if (!tripped(prefs.crashes(), System.currentTimeMillis())) return false;
        ComponentName other = SystemHome.other(activity);
        if (other == null) return false; // shangOS is the only home: nothing to hand over to
        activity.startActivity(
                SystemHome.intent().setComponent(other).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }
}
