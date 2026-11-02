package dev.madusha.shangos;

/**
 * Tile colour, tagline and description for apps Home knows by name; everything else gets a neutral
 * default.
 */
final class AppMeta {
    private AppMeta() {}

    static int color(String pkg) {
        if (pkg.contains("youtube.tv")) return 0xFFF6F5F5;
        if (pkg.contains("netflix")) return 0xFF141414;
        if (pkg.contains("soundcloud")) return 0xFFFF6900;
        if (pkg.contains("ted.android")) return 0xFFF5F5F5;
        if (pkg.contains("phlox")) return 0xFF304B6C;
        if (pkg.contains("stream.tv")) return 0xFF265EAF;
        if (pkg.contains("tvmusic")) return 0xFF26131B;
        if (pkg.contains("amazon")) return 0xFF142C3E;
        return 0xFF293341;
    }

    static String tag(String pkg) {
        if (pkg.contains("youtube.tv")) return "WATCH YOUR WAY";
        if (pkg.contains("netflix")) return "TIME FOR A GREAT STORY";
        if (pkg.contains("soundcloud") || pkg.contains("tvmusic"))
            return "THE SOUND OF YOUR EVENING";
        if (pkg.contains("ted.android")) return "IDEAS WORTH EXPLORING";
        if (pkg.contains("phlox")) return "A WINDOW TO YOUR WORLD";
        return "YOUR SPACE";
    }

    static String description(String pkg) {
        if (pkg.contains("youtube.tv"))
            return "Favorite creators. New discoveries. A bigger screen.";
        if (pkg.contains("netflix")) return "Great stories. Even better evenings.";
        if (pkg.contains("soundcloud")) return "Find your sound. Set the mood.";
        if (pkg.contains("tvmusic")) return "The artists you love. The mood you are in.";
        if (pkg.contains("ted.android")) return "A new perspective can change everything.";
        if (pkg.contains("phlox")) return "The web, from the comfort of your sofa.";
        if (pkg.contains("disney")) return "Stories for every age, all in one place.";
        if (pkg.contains("spotify")) return "Your music and podcasts, on the big speakers.";
        if (pkg.contains("amazon")) return "Movies and series for your next great evening.";
        return "Your apps. Always within reach.";
    }
}
