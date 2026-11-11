package dev.madusha.shangos;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

/** Opening an app, and the menu behind a long press on its tile: move, hide, App info. */
final class AppLauncher {
    /** How many recently opened apps are remembered. */
    private static final int RECENT = 12;

    /** What the launcher does around these actions. */
    interface Host {
        /** An app was opened from here: Home keeps it selected for the way back. */
        void opened(App app);

        /** The order or visibility changed: rebuild the page with {@code app} selected. */
        void changed(App app);

        /** The saved order of every app. */
        ArrayList<String> order();
    }

    private final Activity activity;
    private final Prefs prefs;
    private final AppRepo repo;
    private final Overlays overlays;
    private final Feedback feedback;
    private final Host host;

    AppLauncher(
            Activity activity,
            Prefs prefs,
            AppRepo repo,
            Overlays overlays,
            Feedback feedback,
            Host host) {
        this.activity = activity;
        this.prefs = prefs;
        this.repo = repo;
        this.overlays = overlays;
        this.feedback = feedback;
        this.host = host;
    }

    void launch(App app) {
        launch(app, null);
    }

    /**
     * Opens {@code app}. From a tile ({@code origin}) its window grows out of the card; from
     * anywhere else, or with motion off, it simply appears.
     */
    void launch(App app, View origin) {
        try {
            Intent intent =
                    activity.getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
            if (intent == null)
                intent = activity.getPackageManager().getLaunchIntentForPackage(app.pkg);
            if (intent == null) throw new ActivityNotFoundException();
            feedback.click();
            activity.startActivity(
                    intent,
                    origin != null && feedback.motion()
                            ? Motion.growFrom(origin)
                            : Motion.instant(activity));
            prefs.setRecent(
                    AppList.remember(prefs.recent(), app.pkg, System.currentTimeMillis(), RECENT));
            host.opened(app);
        } catch (RuntimeException error) {
            Toast.makeText(
                            activity,
                            "Could not open this app. It may be updating.",
                            Toast.LENGTH_SHORT)
                    .show();
            repo.markDirty();
            repo.refreshIfNeeded();
        }
    }

    /** The menu of an app: open it, put it first, hide it, its system page, remove it. */
    void menu(App app) {
        feedback.click();
        Set<String> hidden = prefs.hidden();
        LinkedHashMap<String, Runnable> choices = new LinkedHashMap<>();
        choices.put("Open", () -> launch(app));
        choices.put(
                "Move to first position",
                () -> reorder(app, AppList.moveFirst(host.order(), hidden, app.pkg)));
        choices.put(
                hidden.contains(app.pkg) ? "Show on Home" : "Hide from Home",
                () -> reorder(app, AppList.toggleHidden(host.order(), hidden, app.pkg)));
        choices.put(
                "App info",
                () ->
                        activity.startActivity(
                                new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + app.pkg))));
        if (removable(app.pkg)) choices.put("Uninstall", () -> uninstall(app));
        ArrayList<Runnable> actions = new ArrayList<>(choices.values());
        overlays.menu(
                app.label,
                choices.keySet().toArray(new String[0]),
                -1,
                index -> actions.get(index).run());
    }

    private void reorder(App app, AppList.Edit edit) {
        prefs.setOrderAndHidden(edit.orderCsv(), edit.hidden);
        host.changed(app);
    }

    /** Apps the user installed can be removed; the system's own cannot. */
    private boolean removable(String pkg) {
        try {
            ApplicationInfo info = activity.getPackageManager().getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Asks Android to remove the app: its own dialog asks the user to confirm. */
    private void uninstall(App app) {
        try {
            activity.startActivity(
                    new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)));
        } catch (RuntimeException e) {
            Toast.makeText(
                            activity,
                            "This TV can't start the uninstall. Try App info.",
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
