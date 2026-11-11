package dev.madusha.shangos;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings page: grouped rows on the left, a live preview card on the right. Rows are either a
 * value with a chevron or a switch bound to a preference; the dialogs they open (wallpaper, name,
 * home screen, ...) are separate classes.
 */
final class SettingsScreen {
    /** What Settings needs from the launcher, besides the shared {@link ScreenHost}. */
    interface Host {
        /** True on a pale wallpaper (dark text). */
        boolean light();

        /** The frame the page is drawn into. */
        FrameLayout content();

        /** The Settings tab: focus moves there when going up from the first row. */
        View settingsTab();

        void clockVisible(boolean visible);

        /** The living wallpaper or the performance choice changed. */
        void ambientChanged();

        /** Rebuild Settings and select row {@code row}. */
        void reload(int row);

        /** "Auto (Light)": the performance choice and what Auto picked. */
        String performanceLabel();
    }

    // Positions of these rows in show(): the dialogs return to them.
    private static final int ROW_WALLPAPER = 0, ROW_NAME = 1;

    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost screens;
    private final Host host;
    private final PrivacyScreen privacy;
    private final SetupGuide setup;
    private final AboutScreen about;
    private final WallpaperPicker wallpaperPicker;
    private final NameEditor nameEditor;
    private final HomeScreen homeScreen;
    private final WeatherSettings weather;

    SettingsScreen(
            Activity activity,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost screens,
            Host host,
            PrivacyScreen privacy,
            SetupGuide setup,
            AboutScreen about,
            WallpaperPicker wallpaperPicker,
            NameEditor nameEditor,
            HomeScreen homeScreen,
            WeatherSettings weather) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.screens = screens;
        this.host = host;
        this.privacy = privacy;
        this.setup = setup;
        this.about = about;
        this.wallpaperPicker = wallpaperPicker;
        this.nameEditor = nameEditor;
        this.homeScreen = homeScreen;
        this.weather = weather;
    }

    /** The focusable rows of the page last shown; they animate in with the page. */
    List<View> rows() {
        return rows == null ? new ArrayList<>() : rows;
    }

    /** Another page is shown: forget the rows. */
    void clear() {
        rows = null;
    }

    int privacyRow() {
        return privacyRowIndex;
    }

    int aboutRow() {
        return aboutRowIndex;
    }

    /** A switch row's state: "motion" also follows the system's animation setting. */
    private boolean isOn(String toggle) {
        return toggle.equals("motion") ? screens.motion() : prefs.flag(toggle);
    }

    private int style() {
        return Wallpaper.choice(prefs);
    }

    private int ink(int alpha) {
        return Theme.ink(host.light(), alpha);
    }

    private void shadow(TextView t, float radius) {
        ui.shadow(t, radius, host.light());
    }

    /**
     * True when shangOS Ambient is the TV's chosen screen saver (readable by any app; nothing is
     * requested).
     */
    private boolean ambientActive() {
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
    private void screenSaverMenu() {
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

    private ImageView previewImage;
    private TextView previewTitle, previewBody;

    private void preview(int thumb, String title, String body) {
        if (previewImage == null) return;
        if (thumb != 0) {
            if (thumb == Wallpaper.PHOTO_THUMB)
                previewImage.setImageBitmap(PhotoStore.thumbnail(activity));
            else previewImage.setImageResource(thumb);
            previewImage.setAlpha(1f);
        } else previewImage.setAlpha(.35f);
        previewTitle.setText(title);
        previewBody.setText(body);
    }

    /** Builds the Settings page into the launcher's content frame. */
    void show(boolean focus) {
        TextView title = ui.text("Settings", 42, ink(0xFF), "sans-serif-medium");
        title.setLetterSpacing(-.035f);
        shadow(title, 10);
        host.content().addView(title, ui.pos(450, 52, 48, 84));
        // The list scrolls itself (reveal): the stock ScrollView stops a focused row flush with its
        // edge, where the
        // row's lift and shadow were cut off. Rows scroll away under a soft edge, not over the
        // title.
        ScrollView sv =
                new ScrollView(activity) {
                    @Override
                    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                        return 0;
                    }
                };
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(ui.px(40));
        // No padding on the ScrollView itself: Android fades only the area inside a view's padding,
        // which left the
        // shadow outside it unfaded, as a visible rectangle. The room for the focus lift is the
        // list's padding.
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ui.px(22), ui.px(12), ui.px(22), ui.px(48));
        list.setClipChildren(false);
        list.setClipToPadding(false);
        sv.addView(list);
        settingsScroll = sv;
        FrameLayout frame = new FrameLayout(activity);
        frame.setTag("scroll-frame");
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        host.content().addView(frame, ui.pos(520, 400, 26, 138));
        // live preview on the right
        FrameLayout card = new FrameLayout(activity);
        card.setBackground(ui.shape(Theme.glass(host.light()), 22));
        card.setClipToOutline(true);
        previewImage = new ImageView(activity);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(previewImage, ui.pos(352, 198, 0, 0));
        previewTitle = ui.text("", 19, ink(0xFF), "sans-serif-medium");
        card.addView(previewTitle, ui.pos(320, 26, 18, 214));
        previewBody = ui.text("", 13, ink(0xC2), "sans-serif");
        previewBody.setLineSpacing(ui.px(4), 1);
        card.addView(previewBody, ui.pos(316, 80, 18, 246));
        host.content().addView(card, ui.pos(352, 330, 560, 150));
        int wp = style();
        ArrayList<View> controls = new ArrayList<>();
        caption(list, "Appearance", true);
        controls.add(
                option(
                        list,
                        "Wallpaper",
                        () -> Wallpaper.NAMES[style()],
                        null,
                        () -> wallpaperPicker.show(ROW_WALLPAPER),
                        () ->
                                preview(
                                        Wallpaper.THUMBS[style()],
                                        Wallpaper.NAMES[style()],
                                        "Eight wallpapers, from Daybreak to Calm Sea. Daybreak is"
                                            + " light by day and dark after sunset.")));
        controls.add(
                option(
                        list,
                        "Your name",
                        () -> {
                            String n = prefs.name();
                            return n.isEmpty() ? "Add" : n;
                        },
                        null,
                        () -> nameEditor.show(ROW_NAME),
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
                        host::ambientChanged,
                        () ->
                                preview(
                                        Wallpaper.THUMBS[style()],
                                        "Living wallpaper",
                                        "Clouds drift and water moves very slowly. Pauses whenever"
                                                + " an app is open.")));
        controls.add(
                option(
                        list,
                        "Room lighting",
                        null,
                        Prefs.STAGE,
                        () -> {},
                        () ->
                                preview(
                                        Wallpaper.THUMBS[style()],
                                        "Room lighting",
                                        "The light behind your apps takes the colour of the one you"
                                                + " are on, and the wallpaper dims a little. Off"
                                                + " with the Light performance setting.")));
        final int performanceRow = controls.size();
        controls.add(
                option(
                        list,
                        "Performance",
                        host::performanceLabel,
                        null,
                        () ->
                                overlays.menu(
                                        "Performance",
                                        Prefs.PERF_NAMES,
                                        prefs.perf(),
                                        choice -> {
                                            prefs.setPerf(choice);
                                            host.reload(performanceRow);
                                            host.ambientChanged();
                                        }),
                        () ->
                                preview(
                                        0,
                                        "Performance",
                                        "Auto chooses for this TV ("
                                                + Hardware.describe(activity)
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
        final int locationRow = controls.size();
        controls.add(
                option(
                        list,
                        "Weather location",
                        weather::locationLabel,
                        null,
                        () -> weather.pickLocation(locationRow),
                        () -> preview(0, "Weather location", weather.locationAbout())));
        controls.add(
                option(
                        list,
                        "Temperature",
                        weather::unitLabel,
                        null,
                        () -> weather.unitMenu(locationRow + 1),
                        () -> preview(0, "Temperature", weather.unitAbout())));
        controls.add(
                option(
                        list,
                        "Home screen",
                        () -> homeScreen.isDefault() ? "shangOS" : "TV default",
                        null,
                        homeScreen::show,
                        () ->
                                preview(
                                        0,
                                        "Home screen",
                                        homeScreen.isDefault()
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
                                                + activity.getString(R.string.app_name)
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
                        "Screen saver",
                        () -> ambientActive() ? "shangOS Ambient" : "Not set",
                        null,
                        this::screenSaverMenu,
                        () ->
                                preview(
                                        0,
                                        "Screen saver",
                                        "shangOS Ambient: a quiet clock over your wallpaper that"
                                                + " moves every minute. No network, nothing to"
                                                + " allow.")));
        controls.add(
                option(
                        list,
                        "Device settings",
                        () -> "",
                        null,
                        () -> activity.startActivity(new Intent(Settings.ACTION_SETTINGS)),
                        () ->
                                preview(
                                        0,
                                        "Device settings",
                                        "Network, picture, sound and accounts.")));
        aboutRowIndex = controls.size();
        controls.add(
                option(
                        list,
                        "About " + activity.getString(R.string.app_name),
                        () -> "",
                        null,
                        about::show,
                        () ->
                                preview(
                                        0,
                                        "About " + activity.getString(R.string.app_name),
                                        "Version "
                                                + AboutScreen.version(activity)
                                                + " · based on Luma TV (MIT)")));
        rows = controls;
        preview(
                Wallpaper.THUMBS[wp],
                Wallpaper.NAMES[wp],
                "Eight wallpapers, from Daybreak to Calm Sea. Daybreak is light by day and dark"
                        + " after sunset.");
        if (focus) screens.post(() -> controls.get(0).requestFocus());
    }

    private void caption(LinearLayout list, String label, boolean first) {
        TextView c = ui.text(label, 13, ink(0x99), "sans-serif-medium");
        c.setPadding(ui.px(10), 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.px(20));
        lp.topMargin = ui.px(first ? 0 : 12);
        lp.bottomMargin = ui.px(6);
        list.addView(c, lp);
    }

    private ScrollView settingsScroll;
    private ValueAnimator settingsGlide;
    private int widgetsRow, privacyRowIndex, aboutRowIndex;
    private ArrayList<View> rows;

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
        int margin = ui.px(54),
                h = sv.getHeight(),
                y = sv.getScrollY(),
                max = Math.max(0, list.getHeight() - h),
                target = y;
        if (top - margin < y) target = top - margin;
        else if (bottom + margin > y + h) target = bottom + margin - h;
        target = Math.max(0, Math.min(max, target));
        if (settingsGlide != null) settingsGlide.cancel();
        if (target == y) return;
        if (!screens.motion()) {
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
        boolean inputs = !InputsWidget.list(activity).isEmpty();
        int n = 0;
        for (String id : WidgetBoard.IDS)
            if (WidgetBoard.on(prefs, id)
                    && (inputs || !id.equals("inputs"))
                    && (Net.enabled(prefs) || !id.equals("weather"))) n++;
        return n == 0 ? "Off" : n + " on";
    }

    private void widgetEditor() {
        new WidgetEditor(activity, ui, prefs, overlays, screens, widgetsRow);
    }

    /** Selects row {@code i} of the Settings page once it is laid out. */
    void focusRow(int i) {
        screens.post(
                () -> {
                    if (rows != null && i < rows.size()) rows.get(i).requestFocus();
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
        FrameLayout row = new FrameLayout(activity);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setSoundEffectsEnabled(false);
        TextView name = ui.text(title, 15, Color.WHITE, "sans-serif-medium");
        row.addView(name, ui.pos(300, 20, 18, 12));
        TextView detail =
                value == null
                        ? null
                        : ui.text(
                                value.get() + (value.get().equals("") ? "›" : "   ›"),
                                14,
                                0x99FFFFFF,
                                "sans-serif");
        if (detail != null) {
            detail.setGravity(Gravity.RIGHT);
            row.addView(detail, ui.pos(200, 20, 258, 12));
        }
        final View track = new View(activity), knob = new View(activity);
        if (toggle != null) {
            row.addView(track, ui.pos(40, 24, 418, 9));
            row.addView(knob, ui.pos(20, 20, 420, 11));
        }
        Runnable paint =
                () -> {
                    boolean f = row.hasFocus();
                    boolean pale = host.light();
                    row.setBackground(
                            ui.shape(f ? 0xFAFFFFFF : pale ? 0x8CFFFFFF : 0x14FFFFFF, 14));
                    name.setTextColor(f ? 0xFF111827 : ink(0xFF));
                    if (detail != null) detail.setTextColor(f ? 0xFF5B6472 : ink(0x99));
                    if (toggle != null) {
                        boolean on = isOn(toggle);
                        track.setBackground(
                                ui.shape(on ? 0xFF34C759 : f || pale ? 0x2E111827 : 0x33FFFFFF, 12));
                        knob.setBackground(ui.shape(Color.WHITE, 10));
                        knob.setElevation(ui.px(1.5f));
                        float x = on ? ui.px(16) : 0;
                        knob.animate().cancel();
                        if (screens.motion())
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
                    if (screens.motion())
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
                    row.setElevation(ui.px(f ? 10 : 0));
                    if (f) {
                        onFocus.run();
                        screens.step(parent.indexOfChild(row));
                        reveal(row);
                    }
                });
        row.setOnClickListener(
                v -> {
                    screens.click();
                    if (toggle != null) {
                        boolean on = isOn(toggle);
                        prefs.setFlag(toggle, !on);
                        if (toggle.equals("clock"))
                            host.clockVisible(!on);
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
                        host.settingsTab().requestFocus();
                        return true;
                    }
                    return false;
                });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.px(44));
        lp.bottomMargin = ui.px(6);
        parent.addView(row, lp);
        return row;
    }
}
