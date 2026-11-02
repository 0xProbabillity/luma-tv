package dev.madusha.shangos;

import android.app.ActivityManager;
import android.content.Context;

import java.util.Locale;

/**
 * Decides how much visual effort this TV can afford. The classification is a pure function so its
 * thresholds are explicit and tested; {@link #detect} only gathers the facts. The user can always
 * override it in Settings.
 *
 * <p>The rules come from measurements on a 1.4 GB, 32-bit, 1280x720 stick: its moving wallpaper
 * cost 4% of a core while idle and slowed the focus animations, so devices like it start with the
 * still wallpaper.
 */
final class Hardware {
    private Hardware() {}

    /** How much the launcher animates on its own. */
    enum Tier {
        /** Still wallpaper: nothing moves unless the user moves it. */
        LIGHT("Light"),
        /** Slow, occasional glides of light; pauses after 10 minutes without a key press. */
        BALANCED("Balanced"),
        /** The same with longer, more frequent glides; pauses after 30 minutes. */
        QUALITY("Quality");

        final String label;

        Tier(String label) {
            this.label = label;
        }

        /** How long without a key press before ambient motion stops; 0 means it never moves. */
        long idleLimitMillis() {
            return this == QUALITY ? 30 * 60_000L : this == BALANCED ? 10 * 60_000L : 0;
        }

        boolean animates() {
            return this != LIGHT;
        }
    }

    private static final long GB = 1L << 30;

    /**
     * @param totalRamBytes memory the system reports as total
     * @param cores logical CPU cores
     * @param is64Bit whether this app runs as a 64-bit process (a 64-bit CPU running 32-bit
     *     userspace counts as 32-bit)
     * @param lowRamFlag the system's own "low RAM device" flag
     */
    static Tier classify(long totalRamBytes, int cores, boolean is64Bit, boolean lowRamFlag) {
        if (lowRamFlag) return Tier.LIGHT;
        if (totalRamBytes < 3 * GB / 2) return Tier.LIGHT; // under 1.5 GB: nothing to spare
        if (totalRamBytes < 2 * GB && !is64Bit)
            return Tier.LIGHT; // 32-bit under 2 GB: the 1.4 GB stick
        if (totalRamBytes >= 3 * GB && is64Bit && cores >= 4) return Tier.QUALITY;
        return Tier.BALANCED;
    }

    static Tier detect(Context context) {
        return classify(totalRam(context), cores(), android.os.Process.is64Bit(), lowRam(context));
    }

    private static long totalRam(Context context) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info.totalMem;
    }

    private static int cores() {
        return Runtime.getRuntime().availableProcessors();
    }

    private static boolean lowRam(Context context) {
        return ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
                .isLowRamDevice();
    }

    /**
     * "1.4 GB memory, 32-bit, 4 cores": what the choice was based on, shown in Settings and the
     * setup guide.
     */
    static String describe(Context context) {
        return String.format(
                Locale.US,
                "%.1f GB memory, %s, %d cores",
                totalRam(context) / (double) GB,
                android.os.Process.is64Bit() ? "64-bit" : "32-bit",
                cores());
    }
}
