package dev.madusha.shangos;

import android.animation.ValueAnimator;
import android.app.Activity;
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

        /** Frosted glass was switched on or off. */
        void glassChanged();

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
    private final WeatherSettings weather;
    private final SettingsPages pages;

    SettingsScreen(
            Activity activity,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost screens,
            Host host,
            PrivacyScreen privacy,
            WeatherSettings weather,
            SettingsPages pages) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.screens = screens;
        this.host = host;
        this.privacy = privacy;
        this.weather = weather;
        this.pages = pages;
    }

    /** The focusable rows of the page last shown; they animate in with the page. */
    List<View> rows() {
        return rows == null ? new ArrayList<>() : rows;
    }

    /** Another page is shown. {@code staying}: this is Settings again (a dialog came back). */
    void clear(boolean staying) {
        rows = null;
        if (!staying) {
            group = ROOT;
            startRow = 0;
        }
    }

    int privacyRow() {
        return privacyRowIndex;
    }

    int aboutRow() {
        return pages.aboutRow();
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
        String heading = group == ROOT ? "Settings" : TITLES[group];
        TextView title = ui.text(heading, 42, ink(0xFF), "sans-serif-medium");
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
        card.setBackground(ui.glass(card, host.light(), 22));
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
        ArrayList<View> controls = new ArrayList<>();
        switch (group) {
            case APPEARANCE:
                pages.appearance(kit, list, controls);
                break;
            case MOTION:
                pages.motion(kit, list, controls);
                break;
            case HOME:
                pages.home(kit, list, controls);
                break;
            case SYSTEM:
                pages.system(kit, list, controls);
                break;
            default:
                root(list, controls);
        }
        rows = controls;
        int wp = style();
        preview(
                Wallpaper.THUMBS[wp],
                group == ROOT ? "Settings" : TITLES[group],
                group == ROOT
                        ? "Look, motion, home, privacy and system. Back returns to the page before."
                        : "Back returns to Settings.");
        int first = Math.min(startRow, controls.size() - 1);
        if (focus) screens.post(() -> controls.get(first).requestFocus());
    }

    /** The first page: a row for each group of settings, and Privacy, which is one screen. */
    private void root(LinearLayout list, ArrayList<View> controls) {
        controls.add(
                group(
                        list,
                        APPEARANCE,
                        () -> Wallpaper.NAMES[style()],
                        "Wallpaper, your name, the clock, frosted glass and room lighting."));
        controls.add(
                group(
                        list,
                        MOTION,
                        host::performanceLabel,
                        "Motion, sounds, calm mode and performance."));
        controls.add(
                group(
                        list,
                        HOME,
                        weather::cityLabel,
                        "Your city, temperature, widgets and how your apps are ordered."));
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
        controls.add(
                group(
                        list,
                        SYSTEM,
                        () -> AboutScreen.version(activity),
                        "Screen saver, home screen, the setup guide and about."));
    }

    private ScrollView settingsScroll;
    private ValueAnimator settingsGlide;
    private int privacyRowIndex;

    private final SettingsPages.Kit kit =
            new SettingsPages.Kit() {
                public View option(
                        LinearLayout parent,
                        String title,
                        java.util.function.Supplier<String> value,
                        String toggle,
                        Runnable action,
                        Runnable onFocus) {
                    return SettingsScreen.this.option(
                            parent, title, value, toggle, action, onFocus);
                }

                public void preview(int thumb, String title, String body) {
                    SettingsScreen.this.preview(thumb, title, body);
                }

                public int style() {
                    return SettingsScreen.this.style();
                }
            };

    // The pages: the first lists the groups, each group is a page of its own.
    private static final int ROOT = -1, APPEARANCE = 0, MOTION = 1, HOME = 2, SYSTEM = 3;
    private static final String[] TITLES = {"Appearance", "Motion and sound", "Home", "System"};
    private int group = ROOT;

    /** The row selected when the page is built with focus: the group just left, going back. */
    private int startRow;

    /** A row on the first page that opens a group. */
    private View group(
            LinearLayout list,
            int target,
            java.util.function.Supplier<String> value,
            String about) {
        return option(
                list,
                TITLES[target],
                value,
                null,
                () -> open(target),
                () -> preview(Wallpaper.THUMBS[style()], TITLES[target], about));
    }

    private void open(int target) {
        group = target;
        startRow = 0;
        host.reload(0);
    }

    /**
     * Back inside a group goes to the first page. Returns the row to select there, or -1 when
     * already on the first page (Back then leaves Settings).
     */
    int up() {
        if (group == ROOT) return -1;
        // Privacy sits between Home and System on the first page.
        int row = group == SYSTEM ? group + 1 : group;
        group = ROOT;
        startRow = row;
        return row;
    }
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
