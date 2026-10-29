package com.madushas.shangos;

import android.content.ComponentName;

/** One installed app, as Home needs it. Immutable and small: pictures are fetched on demand through {@link AppRepo}. */
final class App {
    final String pkg, label, description, tag;
    final int color;
    final ComponentName component;
    App(String pkg, String label, ComponentName component) {
        this.pkg = pkg; this.label = label; this.component = component;
        this.color = AppMeta.color(pkg); this.description = AppMeta.description(pkg); this.tag = AppMeta.tag(pkg);
    }
}
