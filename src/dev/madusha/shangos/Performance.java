package dev.madusha.shangos;

import android.content.Context;

/** How much this TV may animate: the user's choice, or for Auto what the hardware check picks. */
final class Performance {
    private final Context context;
    private final Prefs prefs;
    private Hardware.Tier detected;

    Performance(Context context, Prefs prefs) {
        this.context = context;
        this.prefs = prefs;
    }

    Hardware.Tier tier() {
        switch (prefs.perf()) {
            case Prefs.PERF_QUALITY:
                return Hardware.Tier.QUALITY;
            case Prefs.PERF_LIGHT:
                return Hardware.Tier.LIGHT;
            default:
                if (detected == null) detected = Hardware.detect(context);
                return detected;
        }
    }

    /** "Auto (Light)": the choice and, for Auto, what the hardware check picked. */
    String label() {
        return Prefs.PERF_NAMES[prefs.perf()]
                + (prefs.perf() == Prefs.PERF_AUTO ? " (" + tier().label + ")" : "");
    }
}
