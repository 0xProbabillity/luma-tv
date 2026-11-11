package dev.madusha.shangos;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The Home and Apps pages: the greeting, the widget board (Home only) and the grid of app tiles.
 * Owns the tiles, which app is selected and which widget page Home is on, and builds them into the
 * launcher's content frame; the launcher decides when.
 */
final class AppPages {
    /** Apps that come first on a fresh install. */
    static final String[] PINNED = {
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
        "com.google.android.youtube.tvmusic"
    };

    /** What the pages need from the launcher. */
    interface Host {
        /** True on a pale wallpaper (dark text). */
        boolean light();

        /** True while the launcher is in front. */
        boolean started();

        Wallpaper wallpaper();

        /** The tab bar's tab {@code index}. */
        View tab(int index);

        /** The tab of the page being shown. */
        View pageTab();

        /** Opens {@code app}; {@code origin} (may be null) is the card its window grows out of. */
        void launch(App app, View origin);

        /** Hold OK on a tile: the menu to move, hide or inspect the app. */
        void menu(App app);
    }

    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final AppRepo repo;
    private final Hero hero;
    private final Feedback feedback;
    private final Host host;
    private final ArrayList<AppTile> tiles = new ArrayList<>();
    private ScrollView scroll;
    private FrameLayout heroView;
    private LinearLayout rows;
    private WidgetBoard board;
    private int homePage;
    private boolean home = true;
    private String selectedPackage = PINNED[0];

    /** The app whose tile has focus now, null when focus is elsewhere. */
    private String litPackage;

    /** The app whose tile is picked up to be moved, null when none is. */
    private String grabbed;

    private boolean moved, ignoreRelease;

    AppPages(
            Activity activity,
            Ui ui,
            Prefs prefs,
            AppRepo repo,
            Hero hero,
            Feedback feedback,
            Host host) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.repo = repo;
        this.hero = hero;
        this.feedback = feedback;
        this.host = host;
    }

    // ---- which apps, in what order ----

    /** The saved order of every app, installed or not. */
    ArrayList<String> order() {
        ArrayList<String> installed = new ArrayList<>();
        for (App app : repo.apps()) installed.add(app.pkg);
        return AppList.order(prefs.order(), PINNED, installed);
    }

    /** The apps to show: all of them (Apps tab) or those not hidden (Home). */
    ArrayList<App> visibleApps(boolean all) {
        HashSet<String> installed = new HashSet<>();
        for (App app : repo.apps()) installed.add(app.pkg);
        ArrayList<App> result = new ArrayList<>();
        ArrayList<String> shown = AppList.visible(order(), prefs.hidden(), installed, all);
        if (all)
            shown =
                    AppList.sorted(
                            prefs.appsSort(),
                            shown,
                            pkg -> repo.app(pkg) == null ? pkg : repo.app(pkg).label,
                            pkg -> Recency.lastOpened(prefs.recent(), pkg));
        for (String pkg : shown) {
            App app = repo.app(pkg); // the index can be replaced between the two reads
            if (app != null) result.add(app);
        }
        return result;
    }

    // ---- selection ----

    /** The app whose tile gets focus when a page is shown. */
    void select(String pkg) {
        selectedPackage = pkg;
    }

    /** Home starts on the first pinned app again. */
    void selectDefault() {
        selectedPackage = PINNED[0];
    }

    // ---- building ----

    /** A page is about to be rebuilt: stop the tiles and remember which widget page Home is on. */
    void clear() {
        grabbed = null;
        litPackage = null;
        host.wallpaper().stageOff();
        for (AppTile tile : tiles) tile.movement.stop();
        if (board != null) homePage = board.index;
        board = null;
        tiles.clear();
    }

    /** Adds the Home ({@code onHome}) or Apps page to {@code content}. */
    void build(FrameLayout content, boolean onHome) {
        home = onHome;
        // Without widgets, the greeting moves down to sit just above the favourites and the
        // wallpaper stays open above it.
        ArrayList<ArrayList<String>> layout =
                home
                        ? WidgetBoard.pages(prefs, !InputsWidget.list(activity).isEmpty())
                        : new ArrayList<>();
        FrameLayout heroView = hero.build(home);
        if (hero.backdrop() != null) content.addView(hero.backdrop(), ui.pos(960, 300, 0, 0));
        content.addView(
                heroView, ui.pos(864, 88, 48, home ? (!layout.isEmpty() ? 96 : 272) : 100));
        this.heroView = heroView;
        if (home && !layout.isEmpty()) {
            board = new WidgetBoard(activity, boardHost, layout, homePage);
            homePage = board.index;
            content.addView(board, ui.pos(WidgetBoard.W, WidgetBoard.H, 0, 0));
            board.setActive(host.started());
        }
        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        rows.setPadding(ui.px(48), ui.px(home ? 16 : 10), ui.px(48), ui.px(30));
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, ui.pos(960, home ? 150 : 330, 0, home ? 388 : 200));
        fillTiles();
    }

    /** (Re)creates the tiles, or the message in their place, in the rows. */
    private void fillTiles() {
        boolean light = host.light();
        rows.removeAllViews();
        tiles.clear();
        ArrayList<App> list = visibleApps(!home);
        if (!repo.loaded()) {
            skeleton(light);
        } else if (list.isEmpty()) {
            rows.addView(
                    ui.text(
                            "Your home is empty. Find your apps in the Apps tab.",
                            15,
                            Theme.ink(light, 0xBB),
                            "sans-serif"));
        } else {
            int count = home ? Math.min(AppTile.COLUMNS, list.size()) : list.size();
            for (int start = 0; start < count; start += AppTile.COLUMNS) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setGravity(Gravity.TOP);
                for (int i = start; i < Math.min(start + AppTile.COLUMNS, count); i++) {
                    AppTile tile = new AppTile(activity, ui, repo, tileHost, list.get(i), i);
                    LinearLayout.LayoutParams tp =
                            new LinearLayout.LayoutParams(ui.px(132), ui.px(108));
                    if (i > start) tp.leftMargin = ui.px(14.4f);
                    row.addView(tile, tp);
                    tiles.add(tile);
                }
                rows.addView(row, new LinearLayout.LayoutParams(-1, ui.px(112)));
            }
        }
    }

    /** Grey tiles that shimmer while the list of apps is read. */
    private void skeleton(boolean light) {
        boolean moving = feedback.motion();
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setContentDescription("Finding your apps");
        for (int i = 0; i < AppTile.COLUMNS; i++) {
            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.addView(
                    new Shimmer(activity, light, ui.px(16), moving),
                    new LinearLayout.LayoutParams(ui.px(132), ui.px(76)));
            LinearLayout.LayoutParams name = new LinearLayout.LayoutParams(ui.px(70), ui.px(8));
            name.topMargin = ui.px(16);
            name.leftMargin = ui.px(31);
            cell.addView(new Shimmer(activity, light, ui.px(4), moving), name);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(ui.px(132), ui.px(108));
            if (i > 0) lp.leftMargin = ui.px(14.4f);
            row.addView(cell, lp);
        }
        rows.addView(row, new LinearLayout.LayoutParams(-1, ui.px(112)));
    }

    /**
     * The first time the apps appear after the launcher starts: the greeting, the widgets and then
     * the tiles rise into place one after another. False when there are no tiles to show yet.
     */
    boolean rise() {
        if (tiles.isEmpty() || heroView == null) return false;
        ArrayList<View> views = new ArrayList<>();
        views.add(heroView);
        views.addAll(tiles);
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            v.setAlpha(0);
            v.setTranslationY(ui.px(14));
            v.animate()
                    .alpha(1)
                    .translationY(0)
                    .setStartDelay(i * 40L)
                    .setDuration(380)
                    .setInterpolator(Motion.EASE)
                    .start();
        }
        if (board != null) {
            board.setAlpha(0);
            board.animate().alpha(1).setStartDelay(80).setDuration(420).start();
        }
        return true;
    }

    /** The views that slide in with the page: the messages and the tiles. */
    List<View> entering() {
        ArrayList<View> views = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++)
            if (rows.getChildAt(i) instanceof TextView) views.add(rows.getChildAt(i));
        views.addAll(tiles);
        return views;
    }

    // ---- focus ----

    /** Focuses the selected app's tile, else the first one. */
    void focusFirst() {
        for (AppTile tile : tiles)
            if (tile.app.pkg.equals(selectedPackage)) {
                tile.requestFocus();
                return;
            }
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    /** The room takes the colour of the focused app (its icon's, once that has been read). */
    private void stageFor(App app) {
        int accent =
                repo.accent(
                        app,
                        () -> {
                            if (app.pkg.equals(litPackage)) stageFor(app);
                        });
        host.wallpaper().stage(accent == Tint.NEUTRAL ? 0 : accent);
    }

    /** The hero shows the selected app again: it may have been opened since it was last shown. */
    void refreshHero() {
        for (AppTile tile : tiles)
            if (tile.app.pkg.equals(selectedPackage)) {
                hero.select(tile.app);
                return;
            }
    }

    /** Home's first widget, or null when there are none. */
    View firstWidget() {
        return board != null ? board.first() : null;
    }

    /** Back: Home's widgets return to their first page. True if that was needed. */
    boolean rewindWidgets() {
        if (board == null || board.index <= 0) return false;
        board.showPage(0);
        return true;
    }

    /** Back: the grid scrolls to its top and the first tile is focused. True if that was needed. */
    boolean rewindGrid() {
        if (scroll == null || scroll.getScrollY() <= 0) return false;
        scroll.smoothScrollTo(0, 0);
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
        return true;
    }

    private AppTile nearestTile(float x) {
        AppTile best = null;
        float d = Float.MAX_VALUE;
        int[] l = new int[2];
        for (AppTile t : tiles) {
            if (t.index >= AppTile.COLUMNS) break;
            t.getLocationOnScreen(l);
            float e = Math.abs(l[0] + t.getWidth() / 2f - x);
            if (e < d) {
                d = e;
                best = t;
            }
        }
        if (best != null) feedback.moved(0, 1);
        return best;
    }

    // ---- picking a tile up to move it ----

    private AppTile tile(String pkg) {
        for (AppTile t : tiles) if (t.app.pkg.equals(pkg)) return t;
        return null;
    }

    private void grab(App app) {
        AppTile t = tile(app.pkg);
        if (t == null) return;
        if (!home && !AppList.MANUAL.equals(prefs.appsSort())) {
            host.menu(app); // a sorted Apps tab cannot be rearranged by hand
            return;
        }
        grabbed = app.pkg;
        moved = false;
        ignoreRelease = true; // the release of this very press must not put it down
        t.setGrabbed(true);
        feedback.click();
    }

    /** Moves the picked-up tile {@code step} places (a row is COLUMNS), if there is room. */
    private void move(int step) {
        AppTile t = tile(grabbed);
        if (t == null) return;
        int to = tiles.indexOf(t) + step;
        if (to < 0 || to >= tiles.size()) return;
        AppList.Edit edit =
                AppList.moveOver(
                        order(), prefs.hidden(), grabbed, tiles.get(to).app.pkg, step > 0);
        prefs.setOrderAndHidden(edit.orderCsv(), edit.hidden);
        moved = true;
        boolean sideways = Math.abs(step) == 1;
        feedback.moved(sideways ? step : 0, sideways ? 0 : step / AppTile.COLUMNS);
        for (AppTile old : tiles) old.movement.stop();
        fillTiles();
        AppTile next = tile(grabbed);
        if (next != null) {
            next.requestFocus();
            next.setGrabbed(true);
        }
        feedback.step(to);
    }

    /** OK: puts the tile down; put down where it was picked up, the app's menu opens instead. */
    private void release() {
        if (ignoreRelease) {
            ignoreRelease = false;
            return;
        }
        finishGrab(true);
    }

    /** Back puts the tile down where it is. True if one was picked up. */
    boolean cancelGrab() {
        if (grabbed == null) return false;
        finishGrab(false);
        return true;
    }

    private void finishGrab(boolean menuIfUnmoved) {
        AppTile t = tile(grabbed);
        grabbed = null;
        if (t == null) return;
        t.setGrabbed(false);
        if (!moved && menuIfUnmoved) host.menu(t.app);
        else feedback.click();
    }

    // ---- lifecycle ----

    /** The launcher is in front again: the widgets update. */
    void resume() {
        if (board != null) {
            board.setActive(true);
            board.refresh();
        }
    }

    void pause() {
        grabbed = null;
        if (board != null) board.setActive(false);
        for (AppTile tile : tiles) tile.movement.snap();
    }

    /** A permission was granted or refused: the widgets look again. */
    void refreshWidgets() {
        if (board != null) board.refresh();
    }

    /** Home shows its first widget page next time (Home pressed on Home). */
    void firstWidgetPage() {
        homePage = 0;
    }

    /** Forget the widgets, so the next build starts on their first page. */
    void resetWidgets() {
        board = null;
        homePage = 0;
    }

    // ---- what the tiles and the widgets ask of the launcher ----

    private final AppTile.Host tileHost =
            new AppTile.Host() {
                public boolean motion() {
                    return feedback.motion();
                }

                public boolean light() {
                    return host.light();
                }

                public int directionX() {
                    return feedback.directionX();
                }

                public int directionY() {
                    return feedback.directionY();
                }

                public int count() {
                    return tiles.size();
                }

                public void selected(App app) {
                    selectedPackage = app.pkg;
                    litPackage = app.pkg;
                    hero.select(app);
                    stageFor(app);
                }

                public void blurred() {
                    litPackage = null;
                    host.wallpaper().stageOff();
                }

                public void follow(int index) {
                    host.wallpaper().follow(index);
                }

                public void scrollToTop() {
                    scroll.post(() -> scroll.smoothScrollTo(0, 0));
                }

                public void launch(App app, View origin) {
                    host.launch(app, origin);
                }

                public void hold(App app) {
                    grab(app);
                }

                public boolean grabbed(App app) {
                    return app.pkg.equals(grabbed);
                }

                public void nudge(int step) {
                    move(step);
                }

                public void drop() {
                    release();
                }

                public void above(float x) {
                    View above = !home || board == null ? null : board.nearest(x);
                    if (above != null) above.requestFocus();
                    else host.pageTab().requestFocus();
                }

                public void moveTo(int next, int directionX, int directionY) {
                    feedback.moved(directionX, directionY);
                    feedback.step(next);
                    tiles.get(next).requestFocus();
                }
            };

    /**
     * What the Home widgets need from the launcher: sizes, colours, sounds, the hero line and app
     * details.
     */
    private final WidgetBoard.Host boardHost =
            new WidgetBoard.Host() {
                public float unit() {
                    return ui.unit;
                }

                public Frost frost() {
                    return ui.frost;
                }

                public boolean light() {
                    return host.light();
                }

                public boolean motion() {
                    return feedback.motion();
                }

                public void step(int index) {
                    feedback.step(index);
                }

                public void open() {
                    feedback.click();
                }

                public void hero(String key, String title, String detail) {
                    hero.widget(key, title, detail);
                }

                public View up() {
                    return host.tab(0);
                }

                public View down(float x) {
                    return nearestTile(x);
                }

                public void pageChanged(int direction) {
                    if (board != null) homePage = board.index;
                }

                public App app(String pkg) {
                    return repo.app(pkg);
                }

                public View art(String pkg) {
                    App a = repo.app(pkg);
                    return a == null ? null : new AppArt(activity, ui, repo, a);
                }

                public void launch(String pkg) {
                    App a = repo.app(pkg);
                    if (a != null) host.launch(a, null);
                    else
                        Toast.makeText(activity, "This app is not installed.", Toast.LENGTH_SHORT)
                                .show();
                }

                public void start(Intent intent) {
                    try {
                        activity.startActivity(intent, Motion.instant(activity));
                    } catch (RuntimeException e) {
                        Toast.makeText(
                                        activity,
                                        "This can't be opened on this TV.",
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                }

                public Prefs prefs() {
                    return prefs;
                }

                public Activity activity() {
                    return activity;
                }
            };
}
