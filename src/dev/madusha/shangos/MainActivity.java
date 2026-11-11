package dev.madusha.shangos;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;

/** An offline, remote-first home screen. No network, analytics, or accessibility service. */
public final class MainActivity extends Activity {
    /**
     * How long after returning from another screen a Back press is ignored while a dialog is open.
     */
    private static final long BACK_GUARD_MS = 1000;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Animator> pageAnimations = new ArrayList<>();

    // What the launcher knows and does, each in its own class (see docs/dev/ARCHITECTURE.md).
    private Prefs prefs;
    private Ui ui;
    private Feedback feedback;
    private SonicFeedback sonic;
    private Performance performance;
    private DaylightSync daylightSync;
    private AppRepo repo;
    private Overlays overlays;

    // The views: a wallpaper, the tab bar and clock, and the page between them.
    private FrameLayout root, content;
    private Wallpaper wallpaper;
    private Chrome chrome;
    private Calm calm;

    // The pages and the screens opened from Settings.
    private Hero hero;
    private AppLauncher launcher;
    private AppPages pages;
    private SettingsScreen settings;
    private WallpaperPicker wallpaperPicker;
    private NameEditor nameEditor;
    private HomeScreen homeScreen;
    private PrivacyScreen privacy;
    private LicensesScreen licences;
    private AboutScreen about;
    private SetupGuide setup;
    private CityPicker cityPicker;

    // State shared with the collaborators through their hosts.
    private String page = "home";

    /** The wallpaper is pale (Daybreak by day): text and glyphs turn dark and glass turns white. */
    private boolean light;

    private boolean started, fullyDrawn, lowRam, pausedWithOverlay;

    /** An app was opened from here and Home has not come back to the front yet. */
    private boolean appOpen;

    /** The first page after the launcher starts rises in (AppPages.rise). */
    private boolean intro = true;
    private long backGuardUntil;
    private long lastInteraction = SystemClock.uptimeMillis();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (CrashGuard.begin(this)) { // crashed twice just now: the TV's own home takes over
            finish();
            return;
        }
        prefs = Prefs.of(this);
        ui = new Ui(this, getResources().getDisplayMetrics().widthPixels / 960f);
        overlays = new Overlays(overlaysHost, ui);
        WatchNext watchNext = new WatchNext(this, WidgetBoard.IO);
        hero = new Hero(this, ui, prefs, handler, watchNext, () -> feedback.motion(), () -> light);
        chrome = new Chrome(this, ui, handler, chromeHost);
        calm =
                new Calm(
                        handler,
                        chrome,
                        () -> prefs.calm() && !page.equals("settings") && !overlays.isOpen(),
                        () -> SystemClock.uptimeMillis() - lastInteraction);
        homeScreen = new HomeScreen(this, overlays);
        cityPicker = new CityPicker(this, ui, prefs, overlays, screenHost);
        nameEditor = new NameEditor(this, ui, prefs, overlays, screenHost, nameEditorHost);
        wallpaperPicker =
                new WallpaperPicker(this, ui, prefs, overlays, screenHost, wallpaperPickerHost);
        privacy =
                new PrivacyScreen(
                        this, ui, prefs, overlays, screenHost, () -> settings.privacyRow());
        licences = new LicensesScreen(this, ui, overlays, screenHost, () -> settings.aboutRow());
        about = new AboutScreen(this, ui, overlays, screenHost, privacy, licences);
        setup = new SetupGuide(this, ui, prefs, overlays, privacy, cityPicker, setupHost);
        WeatherSettings weather = new WeatherSettings(prefs, overlays, cityPicker, this::reload);
        SettingsPages settingsPages =
                new SettingsPages(
                        this,
                        ui,
                        prefs,
                        overlays,
                        screenHost,
                        settingsHost,
                        setup,
                        about,
                        wallpaperPicker,
                        nameEditor,
                        homeScreen,
                        weather,
                        new ScreenSaverSettings(this, overlays, privacy),
                        new AppsSettings(prefs, repo, overlays, () -> pages.order(), this::reload));
        settings =
                new SettingsScreen(
                        this,
                        ui,
                        prefs,
                        overlays,
                        screenHost,
                        settingsHost,
                        privacy,
                        weather,
                        settingsPages);
        getWindow().setDecorFitsSystemWindows(false); // edge to edge, under any system bar
        sonic = new SonicFeedback(this);
        feedback = new Feedback(prefs, sonic);
        performance = new Performance(this, prefs);
        daylightSync =
                new DaylightSync(this, new Daylight(this, prefs), prefs, handler, daylightHost);
        HashSet<String> stock = new HashSet<>(Arrays.asList(HomeScreen.STOCK_HOMES));
        stock.add(getPackageName());
        lowRam = ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).isLowRamDevice();
        repo =
                new AppRepo(
                        this,
                        stock,
                        lowRam,
                        new File(getCacheDir(), "apps.cache"),
                        () -> {
                            if (isDestroyed()) return;
                            repo.prefetch(pages.visibleApps(false), 6);
                            showPage(page, true);
                            afterFirstLoad();
                        });
        pages = new AppPages(this, ui, prefs, repo, hero, feedback, pagesHost);
        launcher =
                new AppLauncher(
                        this,
                        prefs,
                        repo,
                        overlays,
                        feedback,
                        app -> {
                            pages.select(app.pkg);
                            appOpen = true;
                        },
                        app -> {
                            pages.select(app.pkg);
                            showPage(page, true);
                        },
                        () -> pages.order());
        // Daybreak (pale by day, dark after sunset) is the default wallpaper, so the text colour
        // is decided before the first frame.
        light = daylightSync.pale();
        // The previous launch's apps are already known: warm their pictures before the first frame.
        if (repo.loaded()) repo.prefetch(pages.visibleApps(false), 6);
        createShell();
        hideSystemBars();
        if (Build.VERSION.SDK_INT >= 33)
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        repo.start();
        repo.refreshIfNeeded();
        // With a saved list the tiles are on screen already, and a refresh that finds nothing new
        // rightly stays silent: so "ready" is reported here instead of from the refresh.
        if (repo.loaded()) afterFirstLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        started = true;
        chrome.startClock();
        hero.start();
        calm.start();
        PhotoStore.onChanged = () -> runOnUiThread(() -> wallpaper.update());
        boolean returning = appOpen;
        appOpen = false;
        if (repo.loaded() && page.equals("home")) {
            if (returning) {
                // Back from an app: the tile it was opened from, and the hero says when.
                root.post(
                        () -> {
                            hero.hold(false);
                            pages.focusFirst();
                            pages.refreshHero();
                        });
            } else if (getCurrentFocus() == null) {
                // First start: the greeting, with the first app focused quietly.
                hero.showGreeting();
                root.post(
                        () -> {
                            hero.hold(true);
                            pages.focusFirst();
                        });
            }
        }
        repo.setActive(true);
        repo.refreshIfNeeded();
        lastInteraction = SystemClock.uptimeMillis();
        wallpaper.restore();
        privacy.refresh();
        setup.refresh(); // a system settings page may have changed a permission
        if (pausedWithOverlay) {
            // Coming back from a system page that was opened from a dialog: a Back press meant for
            // that
            // page (they need one or two) can arrive a moment late and would close this dialog.
            backGuardUntil = SystemClock.uptimeMillis() + BACK_GUARD_MS;
            pausedWithOverlay = false;
        }
        root.post(overlays::restoreFocus);
        // A sunrise or sunset that passed while an app was open switches a moment after Home
        // returns, once that
        // app has given back its GPU memory: switching at once could fail to allocate
        // (EGL_BAD_ALLOC) on this stick.
        daylightSync.checkSoon(900);
        wallpaper.syncAmbient();
        pages.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedWithOverlay = overlays.isOpen();
        repo.setActive(false);
        pages.pause();
        started = false;
        calm.stop();
        PhotoStore.onChanged = null;
        chrome.stopClock();
        hero.stop();
        daylightSync.stop();
        content.animate().cancel();
        content.setAlpha(1);
        wallpaper.pauseAmbient();
        wallpaper.rest();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideSystemBars(); // a system bar that was swiped in goes away again
        if (wallpaper != null) wallpaper.syncAmbient();
    }

    /**
     * Full screen: hide the system bars, and let a swipe from the edge show them for a moment (API
     * 30+).
     */
    private void hideSystemBars() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller == null) return;
        controller.hide(WindowInsets.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /**
     * The first list of apps is on screen: tell the system (measurable with `am start -W` and
     * logcat), and run the guide once.
     */
    private void afterFirstLoad() {
        if (fullyDrawn) return;
        fullyDrawn = true;
        root.post(
                () -> {
                    try {
                        reportFullyDrawn();
                    } catch (RuntimeException ignored) {
                    }
                    if (page.equals("home") && setup.shouldAutoShow())
                        setup.show(setup.resumeStep());
                });
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        repo.trim(level);
        if (level >= 40 && wallpaper != null)
            wallpaper.release(); // 40 = background: the process may be killed, so give back the
        // wallpaper's pictures
    }

    /**
     * Any key press: the wallpaper's light stops at once so it cannot compete with the focus
     * animations, and glides again after a quiet moment.
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastInteraction = SystemClock.uptimeMillis();
        if (wallpaper != null) wallpaper.onInteraction();
        if (calm != null) calm.poke();
    }

    @Override
    protected void onDestroy() {
        repo.stop();
        if (wallpaper != null) wallpaper.shutdown();
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        if (sonic != null) sonic.release();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // QA hooks (daylight preview, demo content, fake inputs) exist only in the debug build: the
        // Activity is exported,
        // so in release and offline builds another app could otherwise put fake content on the TV.
        if (Flavor.DEBUG) {
            String look = intent.getStringExtra("shangos.daylight");
            if (look != null && look.matches("day|night|auto")) {
                daylightSync.preview(look);
                if (started) daylightSync.sync();
            }
            String demo = intent.getStringExtra("shangos.demo");
            if (demo != null) {
                Promo.set(demo.equals("promo"));
                pages.resetWidgets();
                showPage("home", true);
            } // board=null: start on the first page
            String inputs = intent.getStringExtra("shangos.inputs");
            if (inputs != null) {
                InputsWidget.demo = inputs.equals("demo");
                showPage(page, true);
            } // arrives while paused
        }
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Home pressed on Home: back to the first app and the first widget page. Coming back
            // from an app keeps the app it was opened from.
            if (started) {
                pages.selectDefault();
                pages.firstWidgetPage();
            }
            showPage("home", true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        pages.refreshWidgets();
        hero.reloadWatchNext();
        privacy.refresh();
        setup.refresh();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        if (wallpaper != null) wallpaper.dump(writer, prefix, page);
        if (daylightSync != null) daylightSync.dump(writer, prefix, light);
    }

    private void createShell() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        wallpaper = new Wallpaper(this, wallpaperHost, loader, ui);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        root.addView(chrome.shade(), new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        chrome.addTo(root);
        chrome.paint();
        setContentView(root);
        showPage("home", false);
    }

    private void showPage(String next, boolean focus) {
        String previous = page;
        boolean transition = repo.loaded() && !previous.equals(next) && feedback.motion();
        for (Animator animation : pageAnimations) animation.cancel();
        pageAnimations.clear();
        pages.clear();
        content.animate().cancel();
        content.setAlpha(1);
        content.setTranslationX(0);
        content.removeAllViews();
        settings.clear(previous.equals("settings") && next.equals("settings"));
        page = next;
        hero.reset(page.equals("home"));
        light = daylightSync.pale();
        ui.frost.setEnabled(prefs.glass() && performance.tier() != Hardware.Tier.LIGHT);
        chrome.paint();
        chrome.setClockVisible(prefs.clock());
        chrome.moveIndicator(transition);
        wallpaper.update();
        wallpaper.rest();
        wallpaper.syncAmbient();
        chrome.shade().animate().cancel();
        chrome.shade().setAlpha(page.equals("settings") ? 1 : 0);
        if (page.equals("settings")) {
            settings.show(focus);
            animatePage(previous, transition);
            return;
        }
        pages.build(content, page.equals("home"));
        boolean cold = intro && repo.loaded() && !transition && feedback.motion();
        if (cold && pages.rise()) intro = false;
        final boolean greet = page.equals("home") && !appOpen;
        if (focus)
            root.post(
                    () -> {
                        hero.hold(greet);
                        pages.focusFirst();
                    });
        animatePage(previous, transition);
    }

    private void animatePage(String previous, boolean transition) {
        if (!transition) return;
        int before = previous.equals("home") ? 0 : previous.equals("apps") ? 1 : 2;
        int after = page.equals("home") ? 0 : page.equals("apps") ? 1 : 2;
        float direction = after >= before ? 1 : -1;
        // Animate real element bounds rather than blending two full-screen textures.
        // This preserves the soft page entrance within a small TV chip's fill-rate budget.
        ArrayList<View> entering = new ArrayList<>();
        for (int i = 0; i < content.getChildCount(); i++)
            if (!(content.getChildAt(i) instanceof ScrollView)
                    && !"scroll-frame".equals(content.getChildAt(i).getTag()))
                entering.add(content.getChildAt(i));
        entering.addAll(page.equals("settings") ? settings.rows() : pages.entering());
        for (int i = 0; i < entering.size(); i++) {
            View view = entering.get(i);
            float offset = ui.px(16) * direction;
            view.setAlpha(0);
            view.setTranslationX(offset);
            ObjectAnimator fade = ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1);
            fade.setDuration(270);
            ObjectAnimator slide = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, offset, 0);
            slide.setDuration(340);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(fade, slide);
            set.setInterpolator(Motion.EASE);
            set.setStartDelay(Math.min(i, 5) * 10);
            pageAnimations.add(set);
            set.start();
        }
    }

    /** Settings again, with row {@code row} selected. */
    private void reload(int row) {
        showPage("settings", false);
        settings.focusRow(row);
    }

    /**
     * Android 13+ delivers Back to the callback registered in onCreate; Android 12 still calls this
     * method.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (pages.cancelGrab()) return;
        int fromGroup = page.equals("settings") && !overlays.isOpen() ? settings.up() : -1;
        if (fromGroup >= 0) {
            reload(fromGroup); // a Settings page goes back to the first page
            return;
        }
        if (overlays.isOpen()) {
            if (SystemClock.uptimeMillis() < backGuardUntil) return; // see onResume
            overlays.close(true);
            return;
        }
        if (!page.equals("home")) showPage("home", true);
        else if (pages.rewindWidgets()) chrome.tab(0).requestFocus();
        else if (!pages.rewindGrid()) chrome.tab(0).requestFocus();
    }

    // ---- What the collaborators ask of this activity: one narrow interface each ----

    private final SetupGuide.Host setupHost =
            new SetupGuide.Host() {
                public void post(Runnable action) {
                    root.post(action);
                }

                public String performanceLabel() {
                    return performance.label();
                }

                public void lookChanged() {
                    if (wallpaper != null) wallpaper.update();
                }

                public void lookApplied() {
                    if (wallpaper != null) wallpaper.update();
                    daylightSync.sync(); // the dialog is closed now, so the text colours can follow
                }

                public void performanceChanged() {
                    if (wallpaper != null) wallpaper.syncAmbient();
                }

                public void requestListings() {
                    // After the system has stopped showing its dialog (denied for good) asking
                    // again does
                    // nothing, so send the user to this app's permissions page instead of a dead
                    // button.
                    boolean blocked =
                            prefs.askedListings()
                                    && !shouldShowRequestPermissionRationale(WatchNext.LISTINGS);
                    if (blocked) {
                        privacy.openSystem(
                                new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName())));
                    } else {
                        requestPermissions(new String[] {WatchNext.LISTINGS}, 7);
                        prefs.setAskedListings();
                    }
                }
            };

    /** What the full-screen panels (Privacy, the widget editor) need from this activity. */
    private final ScreenHost screenHost =
            new ScreenHost() {
                public boolean motion() {
                    return feedback.motion();
                }

                public void click() {
                    feedback.click();
                }

                public void step(int index) {
                    feedback.step(index);
                }

                public void post(Runnable action) {
                    root.post(action);
                }

                public void backToSettings(int row) {
                    root.post(() -> reload(row));
                }
            };

    private final Overlays.Host overlaysHost =
            new Overlays.Host() {
                public FrameLayout root() {
                    return root;
                }

                public ViewGroup content() {
                    return content;
                }

                public ViewGroup nav() {
                    return chrome.bar();
                }

                public boolean motion() {
                    return feedback.motion();
                }

                public void click() {
                    feedback.click();
                }

                public void step(int index) {
                    feedback.step(index);
                }

                public View currentFocus() {
                    return getCurrentFocus();
                }
            };

    private final Wallpaper.Host wallpaperHost =
            new Wallpaper.Host() {
                public boolean onScreen() {
                    return started && !page.equals("settings");
                }

                public boolean motion() {
                    return feedback.motion();
                }

                public boolean stageSetting() {
                    return prefs.stage();
                }

                public boolean ambientSetting() {
                    return prefs.ambient();
                }

                public Hardware.Tier tier() {
                    return performance.tier();
                }

                public long sinceInteractionMillis() {
                    return SystemClock.uptimeMillis() - lastInteraction;
                }

                public int savedLook() {
                    return Wallpaper.choice(prefs);
                }

                public boolean isDay() {
                    return daylightSync.isDay();
                }

                public boolean destroyed() {
                    return isDestroyed();
                }
            };

    private final DaylightSync.Host daylightHost =
            new DaylightSync.Host() {
                public boolean started() {
                    return started;
                }

                public boolean overlayOpen() {
                    return overlays.isOpen();
                }

                public boolean light() {
                    return light;
                }

                public boolean motion() {
                    return feedback.motion();
                }

                public FrameLayout content() {
                    return content;
                }

                public void updateWallpaper() {
                    wallpaper.update();
                }

                public void rebuild() {
                    showPage(page, true);
                }
            };

    private final Chrome.Host chromeHost =
            new Chrome.Host() {
                public boolean motion() {
                    return feedback.motion();
                }

                public boolean light() {
                    return light;
                }

                public void click() {
                    feedback.click();
                }

                public String page() {
                    return page;
                }

                public void showPage(String next) {
                    MainActivity.this.showPage(next, true);
                }

                public View firstWidget() {
                    return pages.firstWidget();
                }

                public void focusFirst() {
                    pages.focusFirst();
                }
            };

    private final AppPages.Host pagesHost =
            new AppPages.Host() {
                public boolean light() {
                    return light;
                }

                public boolean started() {
                    return started;
                }

                public Wallpaper wallpaper() {
                    return wallpaper;
                }

                public View tab(int index) {
                    return chrome.tab(index);
                }

                public View pageTab() {
                    return chrome.pageTab();
                }

                public void launch(App app, View origin) {
                    launcher.launch(app, origin);
                }

                public void menu(App app) {
                    launcher.menu(app);
                }
            };

    private final NameEditor.Host nameEditorHost =
            new NameEditor.Host() {
                public void reload(int row) {
                    MainActivity.this.reload(row);
                }
            };

    private final WallpaperPicker.Host wallpaperPickerHost =
            new WallpaperPicker.Host() {
                public View content() {
                    return content;
                }

                public View nav() {
                    return chrome.bar();
                }

                public View shade() {
                    return chrome.shade();
                }

                public Wallpaper wallpaper() {
                    return wallpaper;
                }

                public void reload(int row) {
                    MainActivity.this.reload(row);
                }
            };

    private final SettingsScreen.Host settingsHost =
            new SettingsScreen.Host() {
                public boolean light() {
                    return light;
                }

                public FrameLayout content() {
                    return content;
                }

                public View settingsTab() {
                    return chrome.tab(2);
                }

                public void clockVisible(boolean visible) {
                    chrome.setClockVisible(visible);
                }

                public void ambientChanged() {
                    wallpaper.syncAmbient();
                }

                public void glassChanged() {
                    ui.frost.setEnabled(prefs.glass() && performance.tier() != Hardware.Tier.LIGHT);
                }

                public void reload(int row) {
                    MainActivity.this.reload(row);
                }

                public String performanceLabel() {
                    return performance.label();
                }
            };
}
