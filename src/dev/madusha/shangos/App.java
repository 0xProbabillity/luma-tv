package dev.madusha.shangos;

import android.content.ComponentName;

/**
 * One installed app, as Home needs it. Immutable and small: pictures and the accent colour are
 * fetched on demand through {@link AppRepo}.
 */
final class App {
    final String pkg, label;
    final ComponentName component;

    App(String pkg, String label, ComponentName component) {
        this.pkg = pkg;
        this.label = label;
        this.component = component;
    }
}
