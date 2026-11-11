package dev.madusha.shangos;

/**
 * The words for "where you were" in a show or film: "Continue: Stranger Things · S4 E3 · 18 min
 * left". Pure, so unit tested.
 */
final class Resume {
    private Resume() {}

    /** Android TV's {@code watch_next_type} values. */
    static final int CONTINUE = 0, NEXT = 1, NEW = 2, WATCHLIST = 3;

    /** {@code a · b}, or whichever of the two is not empty. */
    static String join(String a, String b) {
        return a == null || a.isEmpty()
                ? (b == null ? "" : b)
                : b == null || b.isEmpty() ? a : a + " · " + b;
    }

    /** "18 min left", "1 h 5 min left". */
    static String left(long millis) {
        long m = Math.max(1, millis / 60000);
        return m < 60
                ? m + " min left"
                : m / 60 + " h " + (m % 60 > 0 ? m % 60 + " min " : "") + "left";
    }

    /** "S4 E3", "Episode 3", or empty when the programme has no episode numbers. */
    static String episode(String season, String episode) {
        boolean s = season != null && !season.isEmpty(), e = episode != null && !episode.isEmpty();
        if (s && e) return "S" + season + " E" + episode;
        return e ? "Episode " + episode : "";
    }

    /** True when the app recorded how far you got. */
    static boolean started(long position, long duration) {
        return duration > 0 && position > 0;
    }

    /** How far you got, 0 to 1, or -1 when unknown. */
    static float progress(long position, long duration) {
        return started(position, duration)
                ? Math.max(.03f, Math.min(1, position / (float) duration))
                : -1;
    }

    /**
     * The hero line for a programme: what it is, which episode and how much is left. Empty when
     * the programme has no title (the caller then says when the app was last opened instead).
     */
    static String line(
            String title, String season, String episode, long position, long duration, int type) {
        if (title == null || title.isEmpty()) return "";
        boolean started = started(position, duration);
        String lead =
                started || type == CONTINUE
                        ? "Continue"
                        : type == NEXT ? "Next episode" : type == NEW ? "New" : "Watchlist";
        String body =
                join(
                        join(title, episode(season, episode)),
                        started ? left(duration - position) : "");
        return lead + ": " + body;
    }
}
