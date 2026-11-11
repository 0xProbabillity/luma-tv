package dev.madusha.shangos;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

/** Settings → Screen saver: whether shangOS Ambient is chosen, and how to choose it. */
final class ScreenSaverSettings {
    private final Activity activity;
    private final Overlays overlays;
    private final PrivacyScreen privacy;

    ScreenSaverSettings(Activity activity, Overlays overlays, PrivacyScreen privacy) {
        this.activity = activity;
        this.overlays = overlays;
        this.privacy = privacy;
    }

    /** What the row shows. */
    String label() {
        return active() ? "shangOS Ambient" : "Not set";
    }

    /**
     * True when shangOS Ambient is the TV's chosen screen saver (readable by any app; nothing is
     * requested).
     */
    private boolean active() {
        try {
            String chosen =
                    Settings.Secure.getString(
                            activity.getContentResolver(), "screensaver_components");
            return chosen != null && chosen.contains(activity.getPackageName() + "/");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Many TVs hide the screen saver choice: offer the system screen, and the computer route that
     * always works.
     */
    void menu() {
        overlays.menu(
                "Screen saver",
                new String[] {
                    "Open the TV's screen saver settings", "How to choose shangOS Ambient"
                },
                -1,
                choice -> {
                    if (choice == 0) {
                        privacy.openSystem(new Intent(Settings.ACTION_DREAM_SETTINGS));
                    } else {
                        overlays.notice(
                                "shangOS Ambient",
                                "A clock over your wallpaper that moves every minute. Many TVs only"
                                        + " let you choose a screen saver from a computer: run"
                                        + " ./shangos.sh ambient, or see "
                                        + SetupGuide.SETUP_URL.replace("https://", "")
                                        + " (Screen saver).");
                    }
                });
    }
}
