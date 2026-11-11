package dev.madusha.shangos;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Settings, Home: how the Apps tab is ordered, and the apps hidden from Home. */
final class AppsSettings {
    private final Prefs prefs;
    private final AppRepo repo;
    private final Overlays overlays;
    private final Supplier<ArrayList<String>> order;
    private final IntConsumer reload;

    /** {@code order}: the saved order of every app; {@code reload} shows Settings again. */
    AppsSettings(
            Prefs prefs,
            AppRepo repo,
            Overlays overlays,
            Supplier<ArrayList<String>> order,
            IntConsumer reload) {
        this.prefs = prefs;
        this.repo = repo;
        this.overlays = overlays;
        this.order = order;
        this.reload = reload;
    }

    /** The hidden apps that are installed, by name. */
    private ArrayList<App> hidden() {
        ArrayList<App> out = new ArrayList<>();
        Set<String> pkgs = prefs.hidden();
        for (String pkg : pkgs) {
            App app = repo.app(pkg);
            if (app != null) out.add(app);
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    // ---- Apps order ----

    private static final String[] SORTS = {"Manual", "A to Z", "Recently used"};
    private static final String[] SORT_KEYS = {AppList.MANUAL, AppList.AZ, AppList.RECENT};

    private int sortIndex() {
        String m = prefs.appsSort();
        return m.equals(AppList.AZ) ? 1 : m.equals(AppList.RECENT) ? 2 : 0;
    }

    String sortLabel() {
        return SORTS[sortIndex()];
    }

    String sortAbout() {
        return "The order of the Apps tab. Manual is your own order, shared with the Home row (hold"
                + " OK on a tile to move it). The others sort the Apps tab only.";
    }

    void sortMenu(int row) {
        overlays.menu(
                "Apps order",
                SORTS,
                sortIndex(),
                choice -> {
                    prefs.setAppsSort(SORT_KEYS[choice]);
                    reload.accept(row);
                });
    }

    // ---- Hidden apps ----

    String hiddenLabel() {
        int n = hidden().size();
        return n == 0 ? "None" : n == 1 ? "1 app" : n + " apps";
    }

    String hiddenAbout() {
        return "Apps you hid from Home stay in the Apps tab. Choose one here to show it on Home"
                + " again.";
    }

    /** Lists them; choosing one shows it on Home again and returns to Settings row {@code row}. */
    void showHidden(int row) {
        ArrayList<App> apps = hidden();
        if (apps.isEmpty()) {
            overlays.notice(
                    "Hidden apps",
                    "Nothing is hidden. To hide an app from Home, hold OK on its tile and choose"
                            + " Hide from Home.");
            return;
        }
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i).label;
        overlays.menu(
                "Show on Home again",
                names,
                -1,
                index -> {
                    AppList.Edit edit =
                            AppList.toggleHidden(order.get(), prefs.hidden(), apps.get(index).pkg);
                    prefs.setOrderAndHidden(edit.orderCsv(), edit.hidden);
                    reload.accept(row);
                });
    }
}
