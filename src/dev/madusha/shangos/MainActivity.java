package dev.madusha.shangos;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import android.window.OnBackInvokedDispatcher;

import org.json.*;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** An offline, remote-first home screen. No network, analytics, or accessibility service. */
public final class MainActivity extends Activity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppRepo repo;
    private PrivacyScreen privacy;
    private LicensesScreen licences;
    private AboutScreen about;
    private SetupGuide setup;
    private boolean fullyDrawn;
    private final SetupGuide.Host setupHost =
            new SetupGuide.Host() {
                public void post(Runnable action) {
                    root.post(action);
                }

                public String performanceLabel() {
                    return MainActivity.this.performanceLabel();
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
                                    && !shouldShowRequestPermissionRationale(UpNextWidget.LISTINGS);
                    if (blocked) {
                        privacy.openSystem(
                                new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName())));
                    } else {
                        requestPermissions(new String[] {UpNextWidget.LISTINGS}, 7);
                        prefs.setAskedListings();
                    }
                }
            };

    /** What the full-screen panels (Privacy, the widget editor) need from this activity. */
    private final ScreenHost screenHost =
            new ScreenHost() {
                public boolean motion() {
                    return MainActivity.this.motion();
                }

                public void click() {
                    sound();
                }

                public void step(int index) {
                    navigationSound(index);
                }

                public void post(Runnable action) {
                    root.post(action);
                }

                public void backToSettings(int row) {
                    root.post(
                            () -> {
                                showPage("settings", false);
                                controlsFocus(row);
                            });
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
                    return (ViewGroup) MainActivity.this.nav();
                }

                public boolean motion() {
                    return MainActivity.this.motion();
                }

                public void click() {
                    sound();
                }

                public void step(int index) {
                    navigationSound(index);
                }

                public View currentFocus() {
                    return getCurrentFocus();
                }
            };
    private boolean lowRam;

    /**
     * How long after returning from another screen a Back press is ignored while a dialog is open.
     */
    private static final long BACK_GUARD_MS = 1000;

    private boolean pausedWithOverlay;
    private long backGuardUntil;
    private long lastInteraction = SystemClock.uptimeMillis();
    private final Wallpaper.Host wallpaperHost =
            new Wallpaper.Host() {
                public boolean onScreen() {
                    return started && !page.equals("settings");
                }

                public boolean motion() {
                    return MainActivity.this.motion();
                }

                public boolean ambientSetting() {
                    return prefs.ambient();
                }

                public Hardware.Tier tier() {
                    return performanceTier();
                }

                public long sinceInteractionMillis() {
                    return SystemClock.uptimeMillis() - lastInteraction;
                }

                public int savedLook() {
                    return wallpaperStyle();
                }

                public boolean isDay() {
                    return MainActivity.this.isDay();
                }

                public boolean destroyed() {
                    return isDestroyed();
                }
            };
    private final ArrayList<View> tabs = new ArrayList<>();
    private final ArrayList<Tile> tiles = new ArrayList<>();
    private Prefs prefs;
    private SonicFeedback sonic;
    private FrameLayout root, content, hero;
    private final ArrayList<Animator> pageAnimations = new ArrayList<>();
    private View sceneryShade;
    private Wallpaper wallpaper;
    private TextView heroTitle, heroDescription, clock;
    private String heroKey = "";
    private boolean holdGreeting;
    private ScrollView scroll;
    private LinearLayout rows;
    private String page = "home", selectedPackage = "com.google.android.youtube.tv";
    private TextView brand;

    /** The wallpaper is pale (Silver by day): text and glyphs turn dark and glass turns white. */
    private boolean light;

    private Daylight daylight;
    private final Runnable daylightTick = this::syncDaylight;
    private FrameLayout tabBar;

    /**
     * Home pages of widgets chosen in Settings (WidgetBoard); homePage is the page shown, kept
     * across rebuilds.
     */
    private WidgetBoard board;

    private int homePage;
    private boolean started, homeGreeting = true;
    private float unit = 1f;
    private Ui ui;
    private int lastDirectionX = 1, lastDirectionY = 0;
    private String heroPackage = "";
    private final Runnable clockTick =
            new Runnable() {
                public void run() {
                    if (!started) return;
                    if (clock != null)
                        clock.setText(
                                new SimpleDateFormat("HH:mm", Locale.getDefault())
                                        .format(new Date()));
                    handler.postDelayed(this, 15000);
                }
            };
    private static final String[] FIRST = {
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
        "com.google.android.youtube.tvmusic"
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Prefs.of(this);
        unit = getResources().getDisplayMetrics().widthPixels / 960f;
        ui = new Ui(this, unit);
        overlays = new Overlays(overlaysHost, ui);
        privacy = new PrivacyScreen(this, ui, prefs, overlays, screenHost, () -> privacyRowIndex);
        licences = new LicensesScreen(this, ui, overlays, screenHost, () -> aboutRowIndex);
        about = new AboutScreen(this, ui, overlays, screenHost, privacy, licences);
        setup = new SetupGuide(this, ui, prefs, overlays, privacy, setupHost);
        getWindow()
                .setDecorFitsSystemWindows(
                        false); // edge to edge: the layout draws under any system bar
        sonic = new SonicFeedback(this);
        daylight = new Daylight(this);
        HashSet<String> stock = new HashSet<>(Arrays.asList(STOCK_HOMES));
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
                            repo.prefetch(visibleApps(false), 6);
                            showPage(page, true);
                            afterFirstLoad();
                        });
        // Daybreak (pale by day, dark after sunset) is the default wallpaper, so the text colour
        // is decided before the first frame.
        light = Wallpaper.isPale(variant(wallpaperStyle()));
        // The previous launch's apps are already known: warm their pictures before the first frame.
        if (repo.loaded()) repo.prefetch(visibleApps(false), 6);
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
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        if (repo.loaded() && page.equals("home")) {
            selectedPackage = "com.google.android.youtube.tv";
            showGreeting();
            root.post(
                    () -> {
                        holdGreeting = true;
                        focusFirst();
                    });
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
        handler.removeCallbacks(daylightTick);
        handler.postDelayed(daylightTick, 900);
        wallpaper.syncAmbient();
        if (board != null) {
            board.setActive(true);
            board.refresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedWithOverlay = overlays.isOpen();
        repo.setActive(false);
        if (board != null) board.setActive(false);
        started = false;
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(daylightTick);
        content.animate().cancel();
        content.setAlpha(1);
        for (Tile tile : tiles) tile.movement.snap();
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
                daylightOverride = look;
                if (started) syncDaylight();
            }
            String demo = intent.getStringExtra("shangos.demo");
            if (demo != null) {
                Promo.set(demo.equals("promo"));
                board = null;
                homePage = 0;
                showPage("home", true);
            } // board=null: start on the first page
            String inputs = intent.getStringExtra("shangos.inputs");
            if (inputs != null) {
                InputsWidget.demo = inputs.equals("demo");
                showPage(page, true);
            } // arrives while paused
        }
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            selectedPackage = "com.google.android.youtube.tv";
            if (started) homePage = 0;
            showPage("home", true);
        } // Home pressed on Home: back to the first page
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (board != null) board.refresh();
        privacy.refresh();
        setup.refresh();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        if (wallpaper != null) wallpaper.dump(writer, prefix, page);
        if (daylight != null) {
            long now = System.currentTimeMillis();
            long[] sun = daylight.times(now);
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            writer.println(
                    prefix
                            + "shangOS daylight: zone="
                            + TimeZone.getDefault().getID()
                            + " sunrise="
                            + f.format(new Date(sun[0]))
                            + " sunset="
                            + f.format(new Date(sun[1]))
                            + " day="
                            + daylight.isDay(now)
                            + " next="
                            + f.format(new Date(daylight.nextChange(now)))
                            + " light="
                            + light);
        }
    }

    private int px(float value) {
        return ui.px(value);
    }

    private boolean motion() {
        return prefs.motion() && ValueAnimator.areAnimatorsEnabled();
    }

    private void sound() {
        if (prefs.sound()) sonic.open();
    }

    private void navigationSound(int index) {
        if (prefs.sound()) sonic.step((index % 6) / 5f, lastDirectionX + lastDirectionY);
    }

    private TextView text(String value, float size, int color, String family) {
        return ui.text(value, size, color, family);
    }

    private FrameLayout.LayoutParams pos(float w, float h, float x, float y) {
        return ui.pos(w, h, x, y);
    }

    private GradientDrawable shape(int color, float radius) {
        return ui.shape(color, radius);
    }

    /**
     * Text and glyph colour with the given alpha: near-black on a pale wallpaper, white elsewhere.
     */
    private int ink(int alpha) {
        return Theme.ink(light, alpha);
    }

    /** Unfocused glass: frosted white on a pale wallpaper, a faint white veil on dark ones. */
    private int glass() {
        return Theme.glass(light);
    }

    private void shadow(TextView t, float radius) {
        if (light) t.setShadowLayer(0, 0, 0, 0);
        else t.setShadowLayer(px(radius), 0, px(1), 0x55000000);
    }

    private int wallpaperStyle() {
        return Math.max(0, Math.min(Wallpaper.NAMES.length - 1, prefs.wallpaper()));
    }

    /**
     * The picture shown for a choice: Daybreak has a light one between sunrise and sunset and a
     * dark one at night.
     */
    private int variant(int style) {
        return Wallpaper.variant(style, isDay());
    }

    private Hardware.Tier detectedTier;

    /** "Auto (Light)": the choice and, for Auto, what the hardware check picked. */
    private String performanceLabel() {
        return PERFORMANCE[prefs.perf()]
                + (prefs.perf() == Prefs.PERF_AUTO ? " (" + performanceTier().label + ")" : "");
    }

    /**
     * How much this TV may animate: the user's choice, or for Auto what the hardware check
     * recommends.
     */
    private Hardware.Tier performanceTier() {
        switch (prefs.perf()) {
            case Prefs.PERF_QUALITY:
                return Hardware.Tier.QUALITY;
            case Prefs.PERF_LIGHT:
                return Hardware.Tier.LIGHT;
            default:
                if (detectedTier == null) detectedTier = Hardware.detect(this);
                return detectedTier;
        }
    }

    /**
     * QA (debug build): am start -n dev.madusha.shangos/.MainActivity --es shangos.daylight
     * day|night|auto previews either look (not saved).
     */
    private String daylightOverride = "auto";

    private boolean isDay() {
        return daylightOverride.equals("auto")
                ? daylight.isDay(System.currentTimeMillis())
                : daylightOverride.equals("day");
    }

    /**
     * Follows sunrise and sunset: switches Silver's picture and, with it, the colours of the page.
     */
    private void syncDaylight() {
        handler.removeCallbacks(daylightTick);
        if (!started) return;
        long now = System.currentTimeMillis();
        handler.postDelayed(
                daylightTick,
                Math.max(30000, Math.min(3600000, daylight.nextChange(now) - now + 2000)));
        if (overlays.isOpen())
            return; // the wallpaper picker previews freely; the theme follows once it closes
        wallpaper.update();
        boolean next = Wallpaper.isPale(variant(wallpaperStyle()));
        if (next == light) return;
        if (!motion()) {
            showPage(page, true);
            return;
        }
        content.animate().cancel();
        // Not while another app starts: a new page then competes with it for GPU memory, which
        // ended in EGL_BAD_ALLOC on this stick once. Paused, the colours are left as they are;
        // onResume switches them.
        content.animate()
                .alpha(0)
                .setDuration(220)
                .setInterpolator(Motion.EASE)
                .withEndAction(
                        () -> {
                            if (!started) {
                                content.setAlpha(1);
                                return;
                            }
                            showPage(page, true);
                            content.setAlpha(0);
                            content.animate()
                                    .alpha(1)
                                    .setDuration(420)
                                    .setInterpolator(Motion.EASE)
                                    .start();
                        })
                .start();
    }

    /** Colours of the parts that stay across pages: tab bar, clock and the settings scrim. */
    private void paintChrome() {
        if (tabBar != null) tabBar.setBackground(shape(light ? 0x47FFFFFF : 0x1FFFFFFF, 22));
        if (clock != null) {
            clock.setTextColor(ink(0xF2));
            shadow(clock, 6);
        }
        if (sceneryShade != null) sceneryShade.setBackgroundColor(light ? 0x73F2F2F5 : 0x80000000);
    }

    private void createShell() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        wallpaper = new Wallpaper(this, wallpaperHost, loader, ui);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        sceneryShade = new View(this);
        sceneryShade.setBackgroundColor(0x80000000);
        sceneryShade.setAlpha(0);
        root.addView(sceneryShade, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        // No wordmark: the page opens with the greeting, as on tvOS.
        brand = text("shangos", 23, Color.WHITE, "sans-serif-medium");
        brand.setVisibility(View.GONE);
        root.addView(brand, pos(120, 32, 48, 29));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        String[] titles = {"Home", "Apps", "Settings"};
        String[] pages = {"home", "apps", "settings"};
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            TextView tab = text(titles[i], 14, 0xB3FFFFFF, "sans-serif-medium");
            tab.setPadding(px(17), px(9), px(17), px(9));
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setFocusableInTouchMode(true);
            tab.setSoundEffectsEnabled(false);
            tab.setOnClickListener(
                    v -> {
                        sound();
                        showPage(pages[index], true);
                    });
            // One pill slides between tabs; leaving the bar it settles, translucent, on the current
            // page.
            tab.setOnFocusChangeListener(
                    (v, focus) -> {
                        if (focus) moveTabIndicator(true);
                        else
                            tab.post(
                                    () -> {
                                        if (focusedTab() < 0) moveTabIndicator(true);
                                    });
                    });
            tab.setOnKeyListener(
                    (v, key, e) -> {
                        if (e.getAction() == KeyEvent.ACTION_DOWN
                                && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                            View widget = index == 0 && board != null ? board.first() : null;
                            if (!page.equals(pages[index])) showPage(pages[index], true);
                            else if (widget != null) widget.requestFocus();
                            else focusFirst();
                            return true;
                        }
                        return false;
                    });
            tabs.add(tab);
            navigation.addView(tab, new LinearLayout.LayoutParams(-2, px(36)));
        }
        FrameLayout bar = new FrameLayout(this);
        bar.setClipChildren(false);
        tabBar = bar;
        tabIndicatorShape = shape(0x24FFFFFF, 18);
        tabIndicator = new View(this);
        tabIndicator.setBackground(tabIndicatorShape);
        bar.addView(tabIndicator, new FrameLayout.LayoutParams(0, px(36), Gravity.CENTER_VERTICAL));
        bar.addView(navigation, new FrameLayout.LayoutParams(-2, -1));
        bar.setPadding(px(4), px(2), px(4), px(2));
        bar.setBackground(shape(0x1FFFFFFF, 22));
        root.addView(bar, pos(-2, 40, 0, 26));
        bar.post(
                () -> {
                    bar.setTranslationX(
                            (getResources().getDisplayMetrics().widthPixels - bar.getWidth()) / 2f);
                    moveTabIndicator(false);
                });
        clock = text("", 18, 0xF2FFFFFF, "sans-serif-medium");
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        clock.setLetterSpacing(.01f);
        root.addView(clock, pos(130, 36, 782, 28));
        paintChrome();
        setContentView(root);
        showPage("home", false);
    }

    private ArrayList<String> order() {
        ArrayList<String> ids = new ArrayList<>();
        String saved = prefs.order();
        if (!saved.isEmpty()) ids.addAll(Arrays.asList(saved.split(",")));
        for (String pkg : FIRST) if (!ids.contains(pkg)) ids.add(pkg);
        for (App app : repo.apps()) if (!ids.contains(app.pkg)) ids.add(app.pkg);
        return ids;
    }

    private ArrayList<App> visibleApps(boolean all) {
        ArrayList<App> result = new ArrayList<>();
        Set<String> hidden = prefs.hidden();
        for (String id : order())
            for (App app : repo.apps())
                if (app.pkg.equals(id) && (all || !hidden.contains(id))) result.add(app);
        return result;
    }

    private void showPage(String next, boolean focus) {
        String previous = page;
        boolean transition = repo.loaded() && !previous.equals(next) && motion();
        for (Animator animation : pageAnimations) animation.cancel();
        pageAnimations.clear();
        for (Tile tile : tiles) tile.movement.stop();
        content.animate().cancel();
        content.setAlpha(1);
        content.setTranslationX(0);
        if (board != null) homePage = board.index;
        content.removeAllViews();
        board = null;
        content.setTag(null);
        page = next;
        homeGreeting = page.equals("home");
        heroPackage = homeGreeting ? "@greeting" : "";
        tiles.clear();
        light = Wallpaper.isPale(variant(wallpaperStyle()));
        paintChrome();
        clock.setVisibility(prefs.clock() ? View.VISIBLE : View.GONE);
        moveTabIndicator(transition);
        wallpaper.update();
        wallpaper.rest();
        wallpaper.syncAmbient();
        sceneryShade.animate().cancel();
        sceneryShade.setAlpha(page.equals("settings") ? 1 : 0);
        if (page.equals("settings")) {
            settings(focus);
            animatePage(previous, transition);
            return;
        }
        boolean home = page.equals("home");
        hero =
                new FrameLayout(this) {
                    @Override
                    public boolean hasOverlappingRendering() {
                        return false;
                    }
                };
        heroTitle = text(home ? welcome() : "All your apps", 42, ink(0xFF), "sans-serif-medium");
        heroTitle.setLetterSpacing(-.035f);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        shadow(heroTitle, 10);
        hero.addView(heroTitle, pos(home ? 392 : 860, 54, 0, 0));
        heroDescription =
                text(
                        home ? greetingLine() : "Everything installed on this TV.",
                        16,
                        ink(0xB3),
                        "sans-serif");
        heroDescription.setSingleLine(true);
        heroDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);
        shadow(heroDescription, 8);
        hero.addView(heroDescription, pos(home ? 392 : 860, 22, 1, 60));
        // Without widgets, the greeting moves down to sit just above the favourites and the
        // wallpaper stays open above it.
        ArrayList<ArrayList<String>> layout =
                home
                        ? WidgetBoard.pages(prefs, !InputsWidget.list(this).isEmpty())
                        : new ArrayList<>();
        content.addView(hero, pos(864, 88, 48, home ? (!layout.isEmpty() ? 96 : 272) : 100));
        heroKey = home ? "@greeting" : "";
        if (home && !layout.isEmpty()) {
            board = new WidgetBoard(this, boardHost, layout, homePage);
            homePage = board.index;
            content.addView(board, pos(WidgetBoard.W, WidgetBoard.H, 0, 0));
            board.setActive(started);
        }
        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        rows.setPadding(px(48), px(home ? 16 : 10), px(48), px(30));
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, pos(960, home ? 150 : 330, 0, home ? 388 : 200));
        ArrayList<App> list = visibleApps(!home);
        if (!repo.loaded()) {
            rows.addView(text("Finding your apps…", 15, ink(0xBB), "sans-serif"));
        } else if (list.isEmpty()) {
            rows.addView(
                    text(
                            "Your home is empty. Find your apps in the Apps tab.",
                            15,
                            ink(0xBB),
                            "sans-serif"));
        } else {
            int count = home ? Math.min(6, list.size()) : list.size();
            for (int start = 0; start < count; start += 6) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setGravity(Gravity.TOP);
                for (int i = start; i < Math.min(start + 6, count); i++) {
                    App app = list.get(i);
                    Tile tile = new Tile(app, i);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(px(132), px(108));
                    if (i > start) tp.leftMargin = px(14.4f);
                    row.addView(tile, tp);
                    tiles.add(tile);
                }
                rows.addView(row, new LinearLayout.LayoutParams(-1, px(112)));
            }
        }
        if (focus)
            root.post(
                    () -> {
                        holdGreeting = page.equals("home");
                        focusFirst();
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
        if (page.equals("settings")) {
            Object tag = content.getTag();
            if (tag instanceof ArrayList)
                for (Object row : (ArrayList<?>) tag)
                    if (row instanceof View) entering.add((View) row);
        } else {
            for (int i = 0; i < rows.getChildCount(); i++)
                if (rows.getChildAt(i) instanceof TextView) entering.add(rows.getChildAt(i));
            entering.addAll(tiles);
        }
        for (int i = 0; i < entering.size(); i++) {
            View view = entering.get(i);
            float offset = px(16) * direction;
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

    private void focusFirst() {
        for (Tile tile : tiles)
            if (tile.app.pkg.equals(selectedPackage)) {
                tile.requestFocus();
                return;
            }
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    /** Under the greeting: today's date, quietly. */
    private String greetingLine() {
        return today();
    }

    /**
     * "Hello, <name>" with the name saved in Settings, else the build's greeting: "Hello", or
     * SHANGOS_GREETING / .local/greeting.txt (see build.sh).
     */
    private String welcome() {
        String name = prefs.name();
        return name.isEmpty() ? getString(R.string.greeting) : "Hello, " + name;
    }

    /**
     * Settings → Your name: typed with the TV keyboard and kept only in this app's storage on the
     * TV.
     */
    private void nameEditor() {
        FrameLayout o = overlays.open(0xA6000000);
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(shape(0xF51C1C1E, 24));
        panel.setElevation(px(24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(440), px(232));
        lp.gravity = Gravity.CENTER;
        o.addView(panel, lp);
        panel.addView(
                text("Your name", 22, Color.WHITE, "sans-serif-medium"), pos(392, 30, 24, 24));
        panel.addView(
                text(
                        "Home will say Hello and your name. Leave it empty for a plain Hello.",
                        13,
                        0xB3FFFFFF,
                        "sans-serif"),
                pos(392, 20, 24, 60));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(prefs.name());
        field.setSelection(field.getText().length());
        field.setHint("Name");
        field.setHintTextColor(0x66FFFFFF);
        field.setTextColor(Color.WHITE);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(17));
        field.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(24)});
        field.setPadding(px(16), 0, px(16), 0);
        field.setImeOptions(
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        Runnable ring =
                () -> {
                    GradientDrawable bg = shape(0x1FFFFFFF, 12);
                    if (field.hasFocus()) bg.setStroke(px(1.5f), Color.WHITE);
                    field.setBackground(bg);
                };
        ring.run();
        field.setOnFocusChangeListener(
                (v, f) -> {
                    ring.run();
                    if (f) navigationSound(0);
                });
        panel.addView(field, pos(392, 46, 24, 94));
        Runnable save =
                () -> {
                    prefs.setName(field.getText().toString());
                    ((android.view.inputmethod.InputMethodManager)
                                    getSystemService(INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(field.getWindowToken(), 0);
                    overlays.close(false);
                    showPage("settings", false);
                    controlsFocus(1);
                };
        field.setOnEditorActionListener(
                (v, action, e) -> {
                    if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        save.run();
                        return true;
                    }
                    return false;
                });
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(buttons, pos(392, 42, 24, 166));
        LinearLayout a = new LinearLayout(this), b = new LinearLayout(this);
        a.setOrientation(LinearLayout.VERTICAL);
        b.setOrientation(LinearLayout.VERTICAL);
        buttons.addView(a, new LinearLayout.LayoutParams(px(120), -1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(px(120), -1);
        gap.leftMargin = px(10);
        buttons.addView(b, gap);
        TextView done = (TextView) overlays.row(a, "Save", false, save);
        done.setGravity(Gravity.CENTER);
        TextView clear =
                (TextView)
                        overlays.row(
                                b,
                                "Clear",
                                false,
                                () -> {
                                    field.setText("");
                                    field.requestFocus();
                                });
        clear.setGravity(Gravity.CENTER);
        root.post(
                () -> {
                    field.requestFocus();
                    ((android.view.inputmethod.InputMethodManager)
                                    getSystemService(INPUT_METHOD_SERVICE))
                            .showSoftInput(field, 0);
                });
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 5
                ? "Good night"
                : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    private String today() {
        return new SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(new Date());
    }

    private void focusNearestTile(float x) {
        Tile best = nearestTile(x);
        if (best != null) best.requestFocus();
    }

    private Tile nearestTile(float x) {
        Tile best = null;
        float d = Float.MAX_VALUE;
        int[] l = new int[2];
        for (Tile t : tiles) {
            if (t.index >= 6) break;
            t.getLocationOnScreen(l);
            float e = Math.abs(l[0] + t.getWidth() / 2f - x);
            if (e < d) {
                d = e;
                best = t;
            }
        }
        if (best != null) {
            lastDirectionY = 1;
            lastDirectionX = 0;
        }
        return best;
    }

    /**
     * What the Home widgets need from the launcher: sizes, colours, sounds, the hero line and app
     * details.
     */
    private final WidgetBoard.Host boardHost =
            new WidgetBoard.Host() {
                public float unit() {
                    return unit;
                }

                public boolean light() {
                    return light;
                }

                public boolean motion() {
                    return MainActivity.this.motion();
                }

                public void step(int index) {
                    navigationSound(index);
                }

                public void open() {
                    sound();
                }

                public void hero(String key, String title, String detail) {
                    holdGreeting = false;
                    homeGreeting = false;
                    setHero(key, title, detail);
                }

                public View up() {
                    return tabs.get(0);
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
                    return a == null ? null : new AppArt(MainActivity.this, ui, repo, a);
                }

                public void launch(String pkg) {
                    App a = repo.app(pkg);
                    if (a != null) MainActivity.this.launch(a);
                    else
                        Toast.makeText(
                                        MainActivity.this,
                                        "This app is not installed.",
                                        Toast.LENGTH_SHORT)
                                .show();
                }

                public void start(Intent intent) {
                    try {
                        startActivity(intent, Motion.instant(MainActivity.this));
                    } catch (RuntimeException e) {
                        Toast.makeText(
                                        MainActivity.this,
                                        "This can't be opened on this TV.",
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                }

                public Prefs prefs() {
                    return prefs;
                }

                public Activity activity() {
                    return MainActivity.this;
                }
            };

    /** Apps opened from shangOS, newest first: Up Next falls back to them without usage access. */
    private void remember(String pkg) {
        ArrayList<String> list = new ArrayList<>();
        list.add(pkg + ":" + System.currentTimeMillis());
        for (String e : prefs.recent().split(","))
            if (!e.isEmpty() && !e.startsWith(pkg + ":") && list.size() < 12) list.add(e);
        prefs.setRecent(String.join(",", list));
    }

    private void showGreeting() {
        homeGreeting = true;
        if (heroTitle == null || !page.equals("home")) return;
        setHero("@greeting", welcome(), greetingLine());
    }

    /** Cross-fades the hero to a new title and line. */
    private void setHero(String key, String title, String detail) {
        if (heroTitle == null || key.equals(heroKey)) return;
        heroKey = key;
        final TextView t = heroTitle, d = heroDescription;
        t.animate().cancel();
        d.animate().cancel();
        if (!motion()) {
            t.setText(title);
            d.setText(detail);
            t.setAlpha(1);
            d.setAlpha(1);
            t.setTranslationY(0);
            d.setTranslationY(0);
            return;
        }
        t.animate()
                .alpha(0)
                .translationY(-px(4))
                .setDuration(90)
                .withEndAction(
                        () -> {
                            if (heroTitle != t || !heroKey.equals(key)) return;
                            t.setText(title);
                            d.setText(detail);
                            t.setTranslationY(px(8));
                            d.setTranslationY(px(5));
                            d.setAlpha(0);
                            t.animate()
                                    .alpha(1)
                                    .translationY(0)
                                    .setDuration(300)
                                    .setInterpolator(Motion.EASE)
                                    .start();
                            d.animate()
                                    .alpha(1)
                                    .translationY(0)
                                    .setDuration(360)
                                    .setStartDelay(30)
                                    .setInterpolator(Motion.EASE)
                                    .start();
                        })
                .start();
        d.animate().alpha(0).setDuration(90).start();
    }

    private void selectApp(App app) {
        selectedPackage = app.pkg;
        if (holdGreeting) {
            holdGreeting = false;
            return;
        }
        if (heroTitle == null || !page.equals("home")) return;
        homeGreeting = false;
        setHero(app.pkg, app.label, app.description);
    }

    private void launch(App app) {
        try {
            Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
            if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(app.pkg);
            if (intent == null) throw new ActivityNotFoundException();
            sound();
            startActivity(intent, Motion.instant(this));
            remember(app.pkg);
        } catch (RuntimeException error) {
            Toast.makeText(this, "Could not open this app. It may be updating.", Toast.LENGTH_SHORT)
                    .show();
            repo.markDirty();
            repo.refreshIfNeeded();
        }
    }

    private String appLabel(String pkg) {
        if (pkg.isEmpty()) return "None";
        App app = repo.app(pkg);
        return app == null ? pkg : app.label;
    }

    /* ---------- overlays: menus, notices and the wallpaper picker (no stock dialogs) ---------- */
    private View nav() {
        return (View) tabs.get(0).getParent().getParent();
    }

    private View tabIndicator;
    private GradientDrawable tabIndicatorShape;
    private ValueAnimator tabMove;
    private int tabColor = 0x24FFFFFF;

    private int focusedTab() {
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).hasFocus()) return i;
        return -1;
    }

    private int pageTab() {
        return page.equals("apps") ? 1 : page.equals("settings") ? 2 : 0;
    }

    /**
     * Slides the tab pill under the focused tab (white), or the current page's tab (glass) when
     * focus is elsewhere.
     */
    private void moveTabIndicator(boolean animate) {
        if (tabIndicator == null || tabs.isEmpty()) return;
        int focused = focusedTab(), index = focused >= 0 ? focused : pageTab();
        View target = tabs.get(index), row = (View) target.getParent();
        if (target.getWidth() == 0) {
            target.post(() -> moveTabIndicator(false));
            return;
        }
        final float fromX = tabIndicator.getTranslationX(),
                toX = row.getLeft() + target.getLeft() - ((View) row.getParent()).getPaddingLeft();
        final int fromW = tabIndicator.getLayoutParams().width,
                toW = target.getWidth(),
                fromColor = tabColor,
                toColor =
                        focused >= 0
                                ? (light ? 0xFF1D1D1F : 0xF5FFFFFF)
                                : (light ? 0xE6FFFFFF : 0x24FFFFFF);
        final int[] fromText = new int[tabs.size()], toText = new int[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            fromText[i] = ((TextView) tabs.get(i)).getCurrentTextColor();
            toText[i] =
                    i == focused
                            ? (light ? Color.WHITE : 0xFF17202B)
                            : ink(i == pageTab() ? 0xFF : 0xA6);
        }
        if (tabMove != null) tabMove.cancel();
        ArgbEvaluator argb = new ArgbEvaluator();
        ValueAnimator.AnimatorUpdateListener step =
                a -> {
                    float f = (float) a.getAnimatedValue();
                    tabIndicator.setTranslationX(fromX + (toX - fromX) * f);
                    ViewGroup.LayoutParams lp = tabIndicator.getLayoutParams();
                    lp.width = Math.round(fromW + (toW - fromW) * f);
                    tabIndicator.setLayoutParams(lp);
                    tabColor = (int) argb.evaluate(f, fromColor, toColor);
                    tabIndicatorShape.setColor(tabColor);
                    float t =
                            Math.max(
                                    0,
                                    Math.min(
                                            1,
                                            (f - .12f) / .3f)); // text flips quickly, so it never
                    // matches the pill mid-way
                    for (int i = 0; i < tabs.size(); i++)
                        ((TextView) tabs.get(i))
                                .setTextColor((int) argb.evaluate(t, fromText[i], toText[i]));
                };
        tabMove = ValueAnimator.ofFloat(0, 1);
        tabMove.addUpdateListener(step);
        if (!animate || !motion() || fromW == 0) {
            tabMove.setDuration(0);
        } else {
            tabMove.setDuration(280);
            tabMove.setInterpolator(Motion.EASE);
        }
        tabMove.start();
    }

    private Overlays overlays;

    /** Full-screen wallpaper picker: moving between thumbnails previews each wallpaper live. */
    private void wallpaperPicker() {
        final int saved = wallpaperStyle();
        FrameLayout o = overlays.open(Color.TRANSPARENT);
        overlays.onCancel(
                () -> {
                    wallpaper.show(saved);
                    content.animate().alpha(1).setDuration(260).start();
                    nav().animate().alpha(1).setDuration(260).start();
                    sceneryShade.animate().alpha(1).setDuration(260).start();
                });
        content.animate().alpha(0).setDuration(240).start();
        nav().animate().alpha(0).setDuration(240).start();
        sceneryShade.animate().alpha(0).setDuration(300).start();
        View fade = new View(this);
        fade.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {0x00000000, 0x99000000, 0xE6000000}));
        o.addView(fade, pos(960, 260, 0, 280));
        TextView name = text(Wallpaper.NAMES[saved], 34, Color.WHITE, "sans-serif-medium");
        name.setLetterSpacing(-.02f);
        name.setShadowLayer(px(10), 0, px(1), 0x66000000);
        o.addView(name, pos(600, 44, 48, 338));
        TextView hint =
                text("OK to choose  ·  Back to keep the current one", 13, 0xB3FFFFFF, "sans-serif");
        o.addView(hint, pos(600, 20, 49, 386));
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        o.addView(row, pos(864, 80, 48, 430));
        ArrayList<View> cells = new ArrayList<>();
        for (int i = 0; i < Wallpaper.NAMES.length; i++) {
            final int index = i;
            FrameLayout cell = new FrameLayout(this);
            cell.setFocusable(true);
            cell.setFocusableInTouchMode(true);
            cell.setSoundEffectsEnabled(false);
            cell.setClipToOutline(true);
            cell.setBackground(shape(0xFF101521, 12));
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageResource(Wallpaper.THUMBS[i]);
            cell.addView(img, new FrameLayout.LayoutParams(-1, -1));
            GradientDrawable ring = shape(Color.TRANSPARENT, 12);
            ring.setStroke(px(1.5f), i == saved ? 0xCCFFFFFF : 0x33FFFFFF);
            cell.setForeground(ring);
            cell.setOnFocusChangeListener(
                    (v, f) -> {
                        GradientDrawable r = shape(Color.TRANSPARENT, 12);
                        r.setStroke(
                                px(f ? 2 : 1.5f),
                                f ? Color.WHITE : index == saved ? 0xCCFFFFFF : 0x33FFFFFF);
                        cell.setForeground(r);
                        cell.setElevation(px(f ? 18 : 2));
                        cell.animate().cancel();
                        float s = f ? 1.16f : 1;
                        if (motion())
                            cell.animate()
                                    .scaleX(s)
                                    .scaleY(s)
                                    .translationY(f ? -px(4) : 0)
                                    .setDuration(420)
                                    .setInterpolator(Motion.TILE)
                                    .start();
                        else {
                            cell.setScaleX(s);
                            cell.setScaleY(s);
                        }
                        if (f) {
                            name.setText(Wallpaper.NAMES[index]);
                            wallpaper.show(index);
                            navigationSound(index);
                        }
                    });
            cell.setOnClickListener(
                    v -> {
                        sound();
                        prefs.setWallpaper(index);
                        overlays.onCancel(null);
                        overlays.close(false);
                        content.animate().alpha(1).setDuration(260).start();
                        nav().animate().alpha(1).setDuration(260).start();
                        sceneryShade.animate().alpha(1).setDuration(260).start();
                        showPage("settings", false);
                        controlsFocus(0);
                    });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(100), px(56));
            if (i > 0) lp.leftMargin = px(9);
            row.addView(cell, lp);
            cells.add(cell);
        }
        root.post(() -> cells.get(saved).requestFocus());
    }

    // Stock launchers that shangos.sh may have switched off to let shangOS be the home screen.
    static final String[] STOCK_HOMES = {
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher"
    };

    private Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    private boolean shangosIsHome() {
        ResolveInfo r =
                getPackageManager()
                        .resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null
                && r.activityInfo != null
                && getPackageName().equals(r.activityInfo.packageName);
    }

    private void homeScreen() {
        if (!shangosIsHome()) {
            overlays.notice(
                    "Home screen",
                    "To make shangOS your home screen, run  ./shangos.sh home  on a computer with"
                        + " this TV on the same Wi-Fi. Some TVs also offer it in Settings → Apps →"
                        + " Default apps.");
            return;
        }
        overlays.menu(
                "Home screen",
                new String[] {"Keep shangOS", "Return to the TV's home screen"},
                0,
                choice -> {
                    if (choice == 1) restoreHome();
                });
    }

    /**
     * Clears the "always use shangOS as Home" choice. Deprecated since API 30 with no replacement
     * for this one job (RoleManager can grant the Home role but not take it back), and still works
     * for the app's own package.
     */
    @SuppressWarnings("deprecation")
    private void forgetPreferredHome(PackageManager pm) {
        pm.clearPackagePreferredActivities(getPackageName());
    }

    /**
     * Hands Home back to the TV's own launcher. A launcher switched off over ADB can only be
     * switched on over ADB.
     */
    private void restoreHome() {
        PackageManager pm = getPackageManager();
        boolean off = false;
        for (String pkg : STOCK_HOMES)
            try {
                int state = pm.getApplicationEnabledSetting(pkg);
                off |=
                        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            } catch (IllegalArgumentException notInstalled) {
            }
        ResolveInfo other = null;
        for (ResolveInfo r : pm.queryIntentActivities(homeIntent(), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())
                    || pkg.equals("com.android.tv.settings")
                    || pkg.contains("setupwraith")
                    || pkg.contains("provision")) continue;
            if (other == null || r.priority > other.priority) other = r;
        }
        if (off || other == null) {
            overlays.notice(
                    "Return to the TV's home screen",
                    "Your TV's own home screen was switched off from a computer so that shangOS"
                        + " could replace it. Only a computer can switch it back on: run "
                        + " ./shangos.sh restore  in the shangOS folder, with this TV on the same"
                        + " Wi-Fi.");
            return;
        }
        forgetPreferredHome(pm);
        startActivity(
                homeIntent()
                        .setComponent(
                                new ComponentName(
                                        other.activityInfo.packageName, other.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void appMenu(App app) {
        Set<String> hidden = prefs.hidden();
        String[] choices = {
            "Open",
            "Move to first position",
            "Move left",
            "Move right",
            hidden.contains(app.pkg) ? "Show on Home" : "Hide from Home",
            "App info"
        };
        overlays.menu(
                app.label,
                choices,
                -1,
                index -> {
                    if (index == 0) {
                        launch(app);
                        return;
                    }
                    ArrayList<String> ids = order();
                    int current = ids.indexOf(app.pkg);
                    if (index == 1) {
                        ids.remove(app.pkg);
                        ids.add(0, app.pkg);
                        hidden.remove(app.pkg);
                    }
                    if (index == 2 && current > 0) Collections.swap(ids, current, current - 1);
                    if (index == 3 && current < ids.size() - 1)
                        Collections.swap(ids, current, current + 1);
                    if (index == 4) {
                        if (hidden.contains(app.pkg)) hidden.remove(app.pkg);
                        else hidden.add(app.pkg);
                    }
                    if (index == 5) {
                        startActivity(
                                new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + app.pkg)));
                        return;
                    }
                    prefs.setOrderAndHidden(String.join(",", ids), hidden);
                    selectedPackage = app.pkg;
                    showPage(page, true);
                });
    }

    /* ---------- Settings ---------- */
    private ImageView previewImage;
    private TextView previewTitle, previewBody;

    private void preview(int thumb, String title, String body) {
        if (previewImage == null) return;
        if (thumb != 0) {
            previewImage.setImageResource(thumb);
            previewImage.setAlpha(1f);
        } else previewImage.setAlpha(.35f);
        previewTitle.setText(title);
        previewBody.setText(body);
    }

    private void settings(boolean focus) {
        TextView title = text("Settings", 42, ink(0xFF), "sans-serif-medium");
        title.setLetterSpacing(-.035f);
        shadow(title, 10);
        content.addView(title, pos(450, 52, 48, 84));
        // The list scrolls itself (reveal): the stock ScrollView stops a focused row flush with its
        // edge, where the
        // row's lift and shadow were cut off. Rows scroll away under a soft edge, not over the
        // title.
        ScrollView sv =
                new ScrollView(this) {
                    @Override
                    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                        return 0;
                    }
                };
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(px(40));
        // No padding on the ScrollView itself: Android fades only the area inside a view's padding,
        // which left the
        // shadow outside it unfaded, as a visible rectangle. The room for the focus lift is the
        // list's padding.
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(22), px(12), px(22), px(48));
        list.setClipChildren(false);
        list.setClipToPadding(false);
        sv.addView(list);
        settingsScroll = sv;
        FrameLayout frame = new FrameLayout(this);
        frame.setTag("scroll-frame");
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        content.addView(frame, pos(520, 400, 26, 138));
        // live preview on the right
        FrameLayout card = new FrameLayout(this);
        card.setBackground(shape(glass(), 22));
        card.setClipToOutline(true);
        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(previewImage, pos(352, 198, 0, 0));
        previewTitle = text("", 19, ink(0xFF), "sans-serif-medium");
        card.addView(previewTitle, pos(320, 26, 18, 214));
        previewBody = text("", 13, ink(0xC2), "sans-serif");
        previewBody.setLineSpacing(px(4), 1);
        card.addView(previewBody, pos(316, 80, 18, 246));
        content.addView(card, pos(352, 330, 560, 150));
        int wp = wallpaperStyle();
        ArrayList<View> controls = new ArrayList<>();
        caption(list, "Appearance", true);
        controls.add(
                option(
                        list,
                        "Wallpaper",
                        () -> Wallpaper.NAMES[wallpaperStyle()],
                        null,
                        this::wallpaperPicker,
                        () ->
                                preview(
                                        Wallpaper.THUMBS[wallpaperStyle()],
                                        Wallpaper.NAMES[wallpaperStyle()],
                                        "Eight wallpapers, from silver silk to the Milky Way."
                                            + " Silver is light by day and dark after sunset.")));
        controls.add(
                option(
                        list,
                        "Your name",
                        () -> {
                            String n = prefs.name();
                            return n.isEmpty() ? "Add" : n;
                        },
                        null,
                        this::nameEditor,
                        () ->
                                preview(
                                        0,
                                        "Your name",
                                        "Home greets you by name. It is saved only on this TV.")));
        controls.add(
                option(
                        list,
                        "Clock",
                        null,
                        "clock",
                        () -> {},
                        () ->
                                preview(
                                        0,
                                        "Clock",
                                        "Shows the time in the top-right corner of every page.")));
        caption(list, "Motion and sound", false);
        controls.add(
                option(
                        list,
                        "Fluid motion",
                        null,
                        "motion",
                        () -> {},
                        () ->
                                preview(
                                        0,
                                        "Fluid motion",
                                        "Springy focus, depth and the 3D flight into your TV. Turn"
                                                + " off for a calmer, simpler screen.")));
        controls.add(
                option(
                        list,
                        "Living wallpaper",
                        null,
                        "ambient",
                        () -> wallpaper.syncAmbient(),
                        () ->
                                preview(
                                        Wallpaper.THUMBS[wallpaperStyle()],
                                        "Living wallpaper",
                                        "Clouds drift and water moves very slowly. Pauses whenever"
                                                + " an app is open.")));
        final int performanceRow = controls.size();
        controls.add(
                option(
                        list,
                        "Performance",
                        MainActivity.this::performanceLabel,
                        null,
                        () ->
                                overlays.menu(
                                        "Performance",
                                        PERFORMANCE,
                                        prefs.perf(),
                                        choice -> {
                                            prefs.setPerf(choice);
                                            showPage("settings", false);
                                            controlsFocus(performanceRow);
                                            wallpaper.syncAmbient();
                                        }),
                        () ->
                                preview(
                                        0,
                                        "Performance",
                                        "Auto chooses for this TV ("
                                                + Hardware.describe(MainActivity.this)
                                                + "). Light keeps the wallpaper still, the lightest"
                                                + " and coolest. Balanced and Quality add slow"
                                                + " gliding light that pauses while you use the"
                                                + " remote and after a long time without a key"
                                                + " press.")));
        controls.add(
                option(
                        list,
                        "Navigation sounds",
                        null,
                        "sound",
                        () -> {},
                        () ->
                                preview(
                                        0,
                                        "Navigation sounds",
                                        "Soft, quiet feedback as you move and open apps.")));
        caption(list, "Home", false);
        widgetsRow = controls.size();
        controls.add(
                option(
                        list,
                        "Home widgets",
                        this::widgetSummary,
                        null,
                        this::widgetEditor,
                        () ->
                                preview(
                                        0,
                                        "Home widgets",
                                        "Choose what Home shows above your apps — Up Next, Weather"
                                                + " (off until you turn it on), "
                                                + (InputsWidget.count > 0 ? "Inputs, " : "")
                                                + "Now Playing and Control — and their order. Left"
                                                + " and Right turn the pages.")));
        controls.add(
                option(
                        list,
                        "Temperature",
                        () -> new String[] {"Automatic", "Celsius", "Fahrenheit"}[unitIndex()],
                        null,
                        this::temperatureMenu,
                        () ->
                                preview(
                                        0,
                                        "Temperature",
                                        "Weather in Celsius or Fahrenheit. Automatic follows the"
                                                + " TV's region. The forecast is for "
                                                + Daylight.city()
                                                + ", the main city of the TV's time zone.")));
        controls.add(
                option(
                        list,
                        "Home screen",
                        () -> shangosIsHome() ? "shangOS" : "TV default",
                        null,
                        this::homeScreen,
                        () ->
                                preview(
                                        0,
                                        "Home screen",
                                        shangosIsHome()
                                                ? "shangOS opens when you press Home. Choose to"
                                                        + " return to your TV's own home screen."
                                                : "Your TV's own home screen opens when you press"
                                                        + " Home.")));
        caption(list, "Privacy", false);
        privacyRowIndex = controls.size();
        controls.add(
                option(
                        list,
                        "Privacy",
                        () -> PrivacyScreen.summary(prefs),
                        null,
                        () -> privacy.show(0),
                        () ->
                                preview(
                                        0,
                                        "Privacy",
                                        "What "
                                                + getString(R.string.app_name)
                                                + " can reach, which permissions it has, and the"
                                                + " switch that turns the network off.")));
        caption(list, "System", false);
        controls.add(
                option(
                        list,
                        "Setup guide",
                        setup::progress,
                        null,
                        () -> setup.show(setup.resumeStep()),
                        () ->
                                preview(
                                        0,
                                        "Setup guide",
                                        "Privacy choices, optional permissions and the home screen,"
                                                + " one step at a time.")));
        controls.add(
                option(
                        list,
                        "Device settings",
                        () -> "",
                        null,
                        () -> startActivity(new Intent(Settings.ACTION_SETTINGS)),
                        () ->
                                preview(
                                        0,
                                        "Device settings",
                                        "Network, picture, sound and accounts.")));
        aboutRowIndex = controls.size();
        controls.add(
                option(
                        list,
                        "About " + getString(R.string.app_name),
                        () -> "",
                        null,
                        about::show,
                        () ->
                                preview(
                                        0,
                                        "About " + getString(R.string.app_name),
                                        "Version "
                                                + AboutScreen.version(this)
                                                + " · based on Luma TV (MIT)")));
        content.setTag(controls);
        preview(
                Wallpaper.THUMBS[wp],
                Wallpaper.NAMES[wp],
                "Eight wallpapers, from silver silk to the Milky Way. Silver is light by day and"
                        + " dark after sunset.");
        if (focus) root.post(() -> controls.get(0).requestFocus());
    }

    private void caption(LinearLayout list, String label, boolean first) {
        TextView c = text(label, 13, ink(0x99), "sans-serif-medium");
        c.setPadding(px(10), 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(20));
        lp.topMargin = px(first ? 0 : 12);
        lp.bottomMargin = px(6);
        list.addView(c, lp);
    }

    private ScrollView settingsScroll;
    private ValueAnimator settingsGlide;
    private int widgetsRow, privacyRowIndex, aboutRowIndex;

    /**
     * Scrolls the settings list so the focused row, with room for its lift and shadow (and its
     * group's caption above the first row of a group), clears the faded edges.
     */
    private void reveal(View row) {
        ScrollView sv = settingsScroll;
        if (sv == null || row.getParent() == null || row.getParent().getParent() != sv) return;
        ViewGroup list = (ViewGroup) row.getParent();
        int i = list.indexOfChild(row), top = row.getTop(), bottom = row.getBottom();
        if (i > 0 && !list.getChildAt(i - 1).isFocusable()) top = list.getChildAt(i - 1).getTop();
        if (i <= 1) top = 0;
        int margin = px(54),
                h = sv.getHeight(),
                y = sv.getScrollY(),
                max = Math.max(0, list.getHeight() - h),
                target = y;
        if (top - margin < y) target = top - margin;
        else if (bottom + margin > y + h) target = bottom + margin - h;
        target = Math.max(0, Math.min(max, target));
        if (settingsGlide != null) settingsGlide.cancel();
        if (target == y) return;
        if (!motion()) {
            sv.scrollTo(0, target);
            return;
        }
        settingsGlide = ValueAnimator.ofInt(y, target);
        settingsGlide.setDuration(420);
        settingsGlide.setInterpolator(Motion.EASE);
        settingsGlide.addUpdateListener(a -> sv.scrollTo(0, (int) a.getAnimatedValue()));
        settingsGlide.start();
    }

    private String widgetSummary() {
        boolean inputs = !InputsWidget.list(this).isEmpty();
        int n = 0;
        for (String id : WidgetBoard.IDS)
            if (WidgetBoard.on(prefs, id)
                    && (inputs || !id.equals("inputs"))
                    && (Net.enabled(prefs) || !id.equals("weather"))) n++;
        return n == 0 ? "Off" : n + " on";
    }

    private static final String[] PERFORMANCE = {"Auto", "Quality", "Light"};

    private int unitIndex() {
        String u = prefs.tempUnit();
        return u.equals("c") ? 1 : u.equals("f") ? 2 : 0;
    }

    private void temperatureMenu() {
        overlays.menu(
                "Temperature",
                new String[] {"Automatic", "Celsius", "Fahrenheit"},
                unitIndex(),
                choice -> {
                    prefs.setTempUnit(new String[] {"auto", "c", "f"}[choice]);
                    showPage("settings", false);
                    controlsFocus(widgetsRow + 1);
                });
    }

    private void widgetEditor() {
        new WidgetEditor(this, ui, prefs, overlays, screenHost, widgetsRow);
    }

    @SuppressWarnings("unchecked")
    private void controlsFocus(int i) {
        root.post(
                () -> {
                    Object tag = content.getTag();
                    if (tag instanceof ArrayList) ((ArrayList<View>) tag).get(i).requestFocus();
                });
    }

    /**
     * A settings row: either a value with a chevron, or an Apple-style switch bound to a
     * preference.
     */
    private View option(
            LinearLayout parent,
            String title,
            java.util.function.Supplier<String> value,
            String toggle,
            Runnable action,
            Runnable onFocus) {
        FrameLayout row = new FrameLayout(this);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setSoundEffectsEnabled(false);
        TextView name = text(title, 15, Color.WHITE, "sans-serif-medium");
        row.addView(name, pos(300, 20, 18, 12));
        TextView detail =
                value == null
                        ? null
                        : text(
                                value.get() + (value.get().equals("") ? "›" : "   ›"),
                                14,
                                0x99FFFFFF,
                                "sans-serif");
        if (detail != null) {
            detail.setGravity(Gravity.RIGHT);
            row.addView(detail, pos(200, 20, 258, 12));
        }
        final View track = new View(this), knob = new View(this);
        if (toggle != null) {
            row.addView(track, pos(40, 24, 418, 9));
            row.addView(knob, pos(20, 20, 420, 11));
        }
        Runnable paint =
                () -> {
                    boolean f = row.hasFocus();
                    row.setBackground(shape(f ? 0xFAFFFFFF : light ? 0x8CFFFFFF : 0x14FFFFFF, 14));
                    name.setTextColor(f ? 0xFF111827 : ink(0xFF));
                    if (detail != null) detail.setTextColor(f ? 0xFF5B6472 : ink(0x99));
                    if (toggle != null) {
                        boolean on = toggle.equals("motion") ? motion() : prefs.flag(toggle);
                        track.setBackground(
                                shape(on ? 0xFF34C759 : f || light ? 0x2E111827 : 0x33FFFFFF, 12));
                        knob.setBackground(shape(Color.WHITE, 10));
                        knob.setElevation(px(1.5f));
                        float x = on ? px(16) : 0;
                        knob.animate().cancel();
                        if (motion())
                            knob.animate()
                                    .translationX(x)
                                    .setDuration(260)
                                    .setInterpolator(Motion.TILE)
                                    .start();
                        else knob.setTranslationX(x);
                    }
                };
        paint.run();
        row.setOnFocusChangeListener(
                (v, f) -> {
                    paint.run();
                    row.animate().cancel();
                    if (motion())
                        row.animate()
                                .scaleX(f ? 1.02f : 1)
                                .scaleY(f ? 1.02f : 1)
                                .setDuration(300)
                                .setInterpolator(Motion.TILE)
                                .start();
                    else {
                        row.setScaleX(1);
                        row.setScaleY(1);
                    }
                    row.setElevation(px(f ? 10 : 0));
                    if (f) {
                        onFocus.run();
                        navigationSound(parent.indexOfChild(row));
                        reveal(row);
                    }
                });
        row.setOnClickListener(
                v -> {
                    sound();
                    if (toggle != null) {
                        boolean on = toggle.equals("motion") ? motion() : prefs.flag(toggle);
                        prefs.setFlag(toggle, !on);
                        if (toggle.equals("clock"))
                            clock.setVisibility(!on ? View.VISIBLE : View.GONE);
                        paint.run();
                    }
                    action.run();
                });
        row.setContentDescription(title);
        row.setOnKeyListener(
                (v, key, e) -> {
                    if (e.getAction() == KeyEvent.ACTION_DOWN
                            && key == KeyEvent.KEYCODE_DPAD_UP
                            && parent.getChildAt(1) == row) {
                        tabs.get(2).requestFocus();
                        return true;
                    }
                    return false;
                });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(44));
        lp.bottomMargin = px(6);
        parent.addView(row, lp);
        return row;
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
        if (overlays.isOpen()) {
            if (SystemClock.uptimeMillis() < backGuardUntil) return; // see onResume
            overlays.close(true);
            return;
        }
        if (!page.equals("home")) showPage("home", true);
        else if (board != null && board.index > 0) {
            board.showPage(0);
            tabs.get(0).requestFocus();
        } else if (scroll != null && scroll.getScrollY() > 0) {
            scroll.smoothScrollTo(0, 0);
            if (!tiles.isEmpty()) tiles.get(0).requestFocus();
        } else tabs.get(0).requestFocus();
    }

    private final class Tile extends FrameLayout {
        final App app;
        final AppArt art;
        final FrameLayout face;
        final Shine light;
        final GradientDrawable rim;
        final TextView label;
        final int index;
        final TileMotion movement;

        Tile(App item, int number) {
            super(MainActivity.this);
            app = item;
            index = number;
            setClipChildren(false);
            setClipToPadding(false);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setSoundEffectsEnabled(false);
            setContentDescription(app.label + ". OK: open. Hold OK: organize app.");
            face = new FrameLayout(MainActivity.this);
            GradientDrawable faceBg = shape(app.color, 16);
            faceBg.setStroke(Math.max(1, px(.6f)), 0x1FFFFFFF);
            face.setBackground(faceBg);
            face.setClipToOutline(true);
            face.setElevation(px(2));
            face.setCameraDistance(px(900));
            art = new AppArt(MainActivity.this, ui, repo, app);
            face.addView(art, new FrameLayout.LayoutParams(-1, -1));
            light = new Shine(MainActivity.this);
            light.setAlpha(0);
            face.addView(light, new FrameLayout.LayoutParams(-1, -1));
            rim = shape(Color.TRANSPARENT, 16);
            rim.setStroke(px(1.2f), 0xF0FFFFFF);
            rim.setAlpha(0);
            face.setForeground(rim);
            addView(face, pos(132, 76, 0, 0));
            label = text(app.label, 13.5f, ink(0xFF), "sans-serif-medium");
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setAlpha(0);
            shadow(label, 6);
            addView(label, pos(160, 22, -14, 88));
            movement = new TileMotion(this);
            setOnFocusChangeListener(
                    (v, f) -> {
                        movement.focus(f);
                        face.setElevation(px(f ? 14 : 2));
                        rim.setAlpha(f ? 210 : 0);
                        label.animate().cancel();
                        if (motion())
                            label.animate()
                                    .alpha(f ? 1 : 0)
                                    .translationY(f ? 0 : -px(3))
                                    .setDuration(f ? 260 : 140)
                                    .setInterpolator(Motion.EASE)
                                    .start();
                        else label.setAlpha(f ? 1 : 0);
                        setTranslationZ(px(f ? 20 : 0));
                        if (f) {
                            selectApp(app);
                            wallpaper.follow(index);
                            if (index < 6) scroll.post(() -> scroll.smoothScrollTo(0, 0));
                            else
                                requestRectangleOnScreen(
                                        new Rect(
                                                -px(12),
                                                -px(14),
                                                getWidth() + px(12),
                                                getHeight() + px(5)),
                                        false);
                        }
                    });
            setOnClickListener(v -> launch(app));
            setOnLongClickListener(
                    v -> {
                        sound();
                        appMenu(app);
                        return true;
                    });
            setOnKeyListener(
                    (v, key, e) -> {
                        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                            movement.press(e.getAction() == KeyEvent.ACTION_DOWN);
                            return false;
                        }
                        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                        if (key == KeyEvent.KEYCODE_DPAD_UP && index < 6) {
                            int[] l = new int[2];
                            getLocationOnScreen(l);
                            float x = l[0] + getWidth() / 2f;
                            View above =
                                    !page.equals("home") || board == null ? null : board.nearest(x);
                            if (above != null) above.requestFocus();
                            else tabs.get(pageTab()).requestFocus();
                            return true;
                        }
                        int next = -1;
                        if (key == KeyEvent.KEYCODE_DPAD_LEFT && index % 6 > 0) next = index - 1;
                        if (key == KeyEvent.KEYCODE_DPAD_RIGHT
                                && index % 6 < 5
                                && index + 1 < tiles.size()) next = index + 1;
                        if (key == KeyEvent.KEYCODE_DPAD_DOWN && index + 6 < tiles.size())
                            next = index + 6;
                        if (key == KeyEvent.KEYCODE_DPAD_UP && index >= 6) next = index - 6;
                        if (next >= 0) {
                            if (e.getRepeatCount() == 0 || e.getRepeatCount() % 2 == 0) {
                                homeGreeting = false;
                                lastDirectionX = Integer.compare(next % 6, index % 6);
                                lastDirectionY = Integer.compare(next / 6, index / 6);
                                navigationSound(next);
                                tiles.get(next).requestFocus();
                            }
                            return true;
                        }
                        return key == KeyEvent.KEYCODE_DPAD_LEFT
                                || key == KeyEvent.KEYCODE_DPAD_RIGHT
                                || key == KeyEvent.KEYCODE_DPAD_DOWN;
                    });
        }

        @Override
        protected void onDetachedFromWindow() {
            movement.stop();
            super.onDetachedFromWindow();
        }
    }

    /** Native property animations keep the static card artwork cached on the GPU. */
    private final class TileMotion {
        final Tile tile;
        boolean focused, pressed;

        TileMotion(Tile tile) {
            this.tile = tile;
        }

        void focus(boolean value) {
            focused = value;
            pressed = false;
            if (value && motion()) {
                tile.face.animate().cancel();
                tile.face.setRotationY(-lastDirectionX * 4.5f);
                tile.face.setRotationX(lastDirectionY * 3f);
            }
            start();
            if (motion()) {
                tile.art.animate().cancel();
                float depthX = focused ? px((tile.index % 6 - 2.5f) * .7f) : 0;
                float depthY = focused ? -px(1) : 0;
                if (focused) {
                    tile.art.setTranslationX(depthX - px(lastDirectionX * 4.5f));
                    tile.art.setTranslationY(depthY + px(lastDirectionY * 3));
                    tile.light.setTranslationX(px(lastDirectionX * 12));
                }
                tile.art
                        .animate()
                        .translationX(depthX)
                        .translationY(depthY)
                        .setDuration(520)
                        .setInterpolator(Motion.DRIFT)
                        .start();
                tile.light
                        .animate()
                        .translationX(focused ? -depthX * 2 : 0)
                        .alpha(focused ? .6f : 0)
                        .setDuration(580)
                        .setInterpolator(Motion.DRIFT)
                        .start();
            }
        }

        void press(boolean down) {
            pressed = down;
            start();
        }

        void start() {
            if (!motion()) {
                snap();
                return;
            }
            tile.face.animate().cancel();
            tile.label.animate().cancel();
            tile.light.animate().cancel();
            float scale = (focused ? 1.11f : 1) - (pressed ? .03f : 0);
            tile.face
                    .animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationY(focused ? -px(3) : 0)
                    .rotationX(0)
                    .rotationY(0)
                    .setDuration(pressed ? 65 : 420)
                    .setInterpolator(pressed ? Motion.EASE : Motion.TILE)
                    .withLayer()
                    .start();
            tile.label
                    .animate()
                    .scaleX(focused ? 1.025f : 1)
                    .scaleY(focused ? 1.025f : 1)
                    .setDuration(260)
                    .setInterpolator(Motion.EASE)
                    .start();
            tile.light
                    .animate()
                    .alpha(focused ? .6f : 0)
                    .setDuration(200)
                    .setInterpolator(Motion.EASE)
                    .start();
        }

        void stop() {
            tile.face.animate().cancel();
            tile.art.animate().cancel();
            tile.label.animate().cancel();
            tile.light.animate().cancel();
        }

        void snap() {
            stop();
            pressed = false;
            float scale = focused ? 1.11f : 1;
            tile.face.setScaleX(scale);
            tile.face.setScaleY(scale);
            tile.face.setTranslationY(focused ? -px(3) : 0);
            tile.face.setRotationX(0);
            tile.face.setRotationY(0);
            tile.art.setTranslationX(0);
            tile.art.setTranslationY(0);
            tile.light.setTranslationX(0);
            tile.light.setAlpha(focused ? .6f : 0);
            tile.label.setScaleX(focused ? 1.025f : 1);
            tile.label.setScaleY(focused ? 1.025f : 1);
        }
    }
}
