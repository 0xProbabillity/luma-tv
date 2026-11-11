package dev.madusha.shangos;

/**
 * "Opened 2 hours ago": the true line under the focused app, from the times shangOS itself
 * recorded. Pure, so unit tested.
 */
final class Recency {
    private Recency() {}

    private static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR;

    /**
     * When {@code pkg} was last opened from shangOS, or 0 if never. {@code recent} is the saved
     * list: comma-separated {@code package:epochMillis} entries, newest first.
     */
    static long lastOpened(String recent, String pkg) {
        if (recent == null) return 0;
        for (String entry : recent.split(",")) {
            int colon = entry.lastIndexOf(':');
            if (colon <= 0 || !entry.substring(0, colon).equals(pkg)) continue;
            try {
                return Math.max(0, Long.parseLong(entry.substring(colon + 1)));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    static String describe(long now, long then) {
        if (then <= 0) return "Not opened from shangOS yet";
        long age = now - then;
        if (age < 0 || age < MINUTE) return "Opened just now";
        if (age < HOUR) return "Opened " + plural(age / MINUTE, "minute") + " ago";
        if (age < DAY) return "Opened " + plural(age / HOUR, "hour") + " ago";
        if (age < 2 * DAY) return "Opened yesterday";
        if (age < 30 * DAY) return "Opened " + plural(age / DAY, "day") + " ago";
        return "Opened over a month ago";
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }
}
