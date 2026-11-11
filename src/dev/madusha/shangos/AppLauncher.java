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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Opening an app, and the menu behind a long press on its tile. {@code opened}: an app was opened
 * from here (Home keeps it selected for the way back); {@code changed}: the order or visibility
 * changed (rebuild the page); {@code order}: the saved order of every app.
 */
final class AppLauncher {
    /** How many recently opened apps are remembered. */
    private static final int RECENT = 12;

    private final Activity activity;
    private final Prefs prefs;
    private final AppRepo repo;
    private final Overlays overlays;
    private final Feedback feedback;
    private final Consumer<App> opened, changed;
    private final Supplier<ArrayList<String>> order;

    AppLauncher(
            Activity activity,
            Prefs prefs,
            AppRepo repo,
            Overlays overlays,
            Feedback feedback,
            Consumer<App> opened,
            Consumer<App> changed,
            Supplier<ArrayList<String>> order) {
        this.activity = activity;
        this.prefs = prefs;
        this.repo = repo;
        this.overlays = overlays;
        this.feedback = feedback;
        this.opened = opened;
        this.changed = changed;
        this.order = order;
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
            opened.accept(app);
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
                () -> reorder(app, AppList.moveFirst(order.get(), hidden, app.pkg)));
        choices.put(
                hidden.contains(app.pkg) ? "Show on Home" : "Hide from Home",
                () -> reorder(app, AppList.toggleHidden(order.get(), hidden, app.pkg)));
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
        changed.accept(app);
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
