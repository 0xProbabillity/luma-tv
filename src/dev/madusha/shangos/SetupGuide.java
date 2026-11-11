package dev.madusha.shangos;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * First-run setup, also reachable from Settings: a few short steps that explain the privacy
 * choices, ask for each optional permission one at a time (with its state shown, and nothing
 * granted silently), and check whether this is the home screen. Every step can be skipped and
 * nothing here is required for the launcher to work.
 */
final class SetupGuide {
    /** What the guide needs from the launcher. */
    interface Host {
        void post(Runnable action);

        /** Shows the system's own permission dialog for reading the Continue watching list. */
        void requestListings();

        /**
         * "Auto (Light)", "Quality"...: the current choice and, for Auto, what the hardware check
         * picked.
         */
        String performanceLabel();

        /** The choice changed: apply it to what is on screen. */
        void performanceChanged();

        /** A wallpaper was chosen: show it now (the guide is still open over Home). */
        void lookChanged();

        /**
         * The guide has closed: bring the text and glass colours in line with the chosen wallpaper.
         */
        void lookApplied();
    }

    private static final int WELCOME = 0,
            LOOK = 1,
            PERFORMANCE = 2,
            PRIVACY = 3,
            PLACE = 4,
            PERMISSIONS = 5,
            HOME = 6,
            STEPS = 7;

    /**
     * Where the default-launcher guide lives on the project website; the QR code on the last step
     * encodes exactly this.
     */
    static final String SETUP_URL = "https://shangos.madusha.dev/setup";

    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final PrivacyScreen privacy;
    private final CityPicker cities;
    private final Host host;
    private ArrayList<View> rows = new ArrayList<>();

    SetupGuide(
            Activity activity,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            PrivacyScreen privacy,
            CityPicker cities,
            Host host) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.privacy = privacy;
        this.cities = cities;
        this.host = host;
    }

    /** True when this app is what the Home button opens. */
    static boolean isHome(Activity activity) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo found =
                activity.getPackageManager()
                        .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return found != null
                && found.activityInfo != null
                && activity.getPackageName().equals(found.activityInfo.packageName);
    }

    /** First run only: the guide opens by itself until it has been finished or skipped once. */
    boolean shouldAutoShow() {
        return !prefs.setupDone() && !overlays.isOpen();
    }

    /** Statuses may have changed while a system page was open: read them again. */
    void refresh() {
        if (overlays.isOpen()) for (View r : rows) Overlays.repaint(r);
    }

    /** The step to open: where an unfinished guide was left, else the first. */
    int resumeStep() {
        return prefs.setupDone() ? 0 : Math.min(STEPS - 1, prefs.setupStep());
    }

    /** True when the guide was started and left before the end. */
    boolean inProgress() {
        return !prefs.setupDone() && prefs.setupStep() > 0;
    }

    /** "Step 3 of 4" for Settings while the guide is unfinished, otherwise empty. */
    String progress() {
        return inProgress() ? "Step " + (resumeStep() + 1) + " of " + STEPS : "";
    }

    /**
     * Back goes to the previous step, so one Back too many (system permission pages need one or two
     * of their own) costs a step, not the whole guide. Back on the first step leaves the guide:
     * that counts as skipping, and Settings, Setup guide opens it again.
     */
    private void back(int current) {
        if (current > 0) show(current - 1);
        else {
            prefs.setSetupDone();
            host.lookApplied();
        }
    }

    private void finish() {
        prefs.setSetupDone();
        overlays.close(false);
        host.lookApplied();
    }

    void show(int step) {
        step = Math.max(0, Math.min(STEPS - 1, step));
        final int current = step;
        // Leaving for a system page, or by mistake, must not restart the guide.
        prefs.setSetupStep(step);
        // The look step lets the wallpaper show through, so the choice can be judged.
        FrameLayout o = overlays.open(step == LOOK ? 0x8C060810 : 0xF20B0B0D);
        overlays.onCancel(() -> back(current));
        rows = new ArrayList<>();
        o.addView(
                ui.text("Step " + (step + 1) + " of " + STEPS, 13, 0x99FFFFFF, "sans-serif-medium"),
                ui.pos(300, 18, 49, 40));
        TextView title = ui.text(title(step), 34, Color.WHITE, "sans-serif-medium");
        title.setLetterSpacing(-.02f);
        o.addView(title, ui.pos(860, 44, 48, 62));
        TextView body = ui.text(body(step), 14, 0xC7FFFFFF, "sans-serif");
        body.setLineSpacing(ui.px(5), 1);
        body.setSingleLine(false);
        boolean showCode = step == HOME && !isHome(activity);
        o.addView(body, ui.pos(showCode ? 580 : 740, 90, 49, 124));

        LinearLayout list = column();
        o.addView(list, ui.pos(560, 220, 40, 218));
        switch (step) {
            case LOOK:
                lookRows(list, current);
                break;
            case PERFORMANCE:
                performanceRows(list, current);
                break;
            case PRIVACY:
                privacyRows(list, current);
                break;
            case PLACE:
                placeRows(list, current);
                break;
            case PERMISSIONS:
                permissionRows(list);
                break;
            case HOME:
                homeRows(list);
                if (showCode) addCode(o);
                break;
            default:
                break;
        }

        // Buttons: Back, Skip and Next side by side at the bottom.
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        o.addView(buttons, ui.pos(560, 44, 40, 456));
        View primary;
        if (step > 0) addButton(buttons, "Back", () -> show(current - 1));
        if (step < STEPS - 1) {
            primary = addButton(buttons, "Next", () -> show(current + 1));
            addButton(buttons, "Skip setup", this::finish);
        } else primary = addButton(buttons, "Done", this::finish);
        final View focus = primary;
        host.post(
                () -> {
                    if (!rows.isEmpty() && current != WELCOME) rows.get(0).requestFocus();
                    else focus.requestFocus();
                });
    }

    // ---- The steps ----
    private void lookRows(LinearLayout list, int current) {
        rows.add(
                overlays.valueRow(
                        list,
                        "Wallpaper",
                        () ->
                                Wallpaper.NAMES[
                                        Math.min(prefs.wallpaper(), Wallpaper.NAMES.length - 1)],
                        () ->
                                overlays.menu(
                                        "Wallpaper",
                                        Wallpaper.NAMES,
                                        Math.min(prefs.wallpaper(), Wallpaper.NAMES.length - 1),
                                        choice -> {
                                            prefs.setWallpaper(choice);
                                            host.lookChanged();
                                            host.post(() -> show(current));
                                        }),
                        null));
    }

    private void performanceRows(LinearLayout list, int current) {
        rows.add(
                overlays.valueRow(
                        list,
                        "Performance",
                        host::performanceLabel,
                        () ->
                                overlays.menu(
                                        "Performance",
                                        new String[] {"Auto", "Quality", "Light"},
                                        prefs.perf(),
                                        choice -> {
                                            prefs.setPerf(choice);
                                            host.performanceChanged();
                                            host.post(() -> show(current));
                                        }),
                        null));
    }

    private void placeRows(LinearLayout list, int current) {
        rows.add(
                overlays.valueRow(
                        list,
                        "City",
                        cities::label,
                        () -> cities.show(() -> host.post(() -> show(current))),
                        null));
    }

    private void privacyRows(LinearLayout list, int current) {
        rows.add(
                overlays.valueRow(
                        list,
                        "Weather",
                        () -> Net.weatherEnabled(prefs) ? "On" : "Off",
                        privacy::toggleWeather,
                        null));
        rows.add(
                overlays.valueRow(
                        list,
                        "Remote posters",
                        privacy::postersLabel,
                        () -> privacy.pickPosters(() -> host.post(() -> show(current))),
                        null));
    }

    private void permissionRows(LinearLayout list) {
        rows.add(
                overlays.valueRow(
                        list,
                        "Up Next access",
                        () -> privacy.granted(WatchNext.LISTINGS) ? "Allowed" : "Not allowed",
                        () -> {
                            if (!privacy.granted(WatchNext.LISTINGS)) host.requestListings();
                        },
                        null));
        rows.add(
                overlays.valueRow(
                        list,
                        "Usage access",
                        () -> privacy.usageAccess() ? "Allowed" : "Not allowed",
                        () -> privacy.openSystem(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)),
                        null));
        rows.add(
                overlays.valueRow(
                        list,
                        "Notification access",
                        () -> privacy.notificationAccess() ? "Allowed" : "Not allowed",
                        () ->
                                privacy.openSystem(
                                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
                        null));
    }

    private void homeRows(LinearLayout list) {
        rows.add(
                overlays.valueRow(
                        list,
                        "Home screen",
                        () -> isHome(activity) ? "This app" : "Another app",
                        () -> privacy.openSystem(new Intent(Settings.ACTION_HOME_SETTINGS)),
                        null));
        rows.add(
                overlays.valueRow(
                        list,
                        "App permissions",
                        () -> "Open",
                        () ->
                                privacy.openSystem(
                                        new Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:" + activity.getPackageName()))),
                        null));
    }

    /**
     * The QR code for the website's guide, on a white card (a QR code needs a light margin), with
     * the same address in words.
     */
    private void addCode(FrameLayout o) {
        FrameLayout card = new FrameLayout(activity);
        card.setBackground(ui.shape(Color.WHITE, 16));
        ImageView code = new ImageView(activity);
        code.setImageResource(R.drawable.qr_setup);
        code.setScaleType(ImageView.ScaleType.FIT_XY);
        code.setContentDescription("QR code for " + SETUP_URL);
        card.addView(code, ui.pos(148, 148, 12, 12));
        o.addView(card, ui.pos(172, 172, 690, 130));
        TextView caption =
                ui.text(
                        "Scan it, or open\n" + SETUP_URL.replace("https://", ""),
                        12,
                        0xC7FFFFFF,
                        "sans-serif");
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setSingleLine(false);
        caption.setLineSpacing(ui.px(3), 1);
        o.addView(caption, ui.pos(240, 40, 656, 312));
        TextView script =
                ui.text("From a computer:  ./shangos.sh wizard", 12, 0x99FFFFFF, "sans-serif");
        script.setGravity(Gravity.CENTER_HORIZONTAL);
        o.addView(script, ui.pos(280, 18, 636, 360));
    }

    private String title(int step) {
        switch (step) {
            case WELCOME:
                return "Welcome to " + activity.getString(R.string.app_name);
            case LOOK:
                return "Pick a look";
            case PERFORMANCE:
                return "Made to fit your TV";
            case PRIVACY:
                return "Your privacy";
            case PLACE:
                return "Where is this TV?";
            case PERMISSIONS:
                return "Optional permissions";
            default:
                return "Make it your home screen";
        }
    }

    private String body(int step) {
        switch (step) {
            case WELCOME:
                return "A calm, fast home screen with no ads, no sponsored rows and no tracking."
                        + " This takes about a minute, every step can be skipped, and everything"
                        + " can be changed later in Settings.";
            case LOOK:
                return "Choose a wallpaper. Daybreak follows the sun: light by day, dark after"
                        + " sunset. Change it any time in Settings.";
            case PERFORMANCE:
                return "It checked this TV: "
                        + Hardware.describe(activity)
                        + ". "
                        + recommendation(Hardware.detect(activity))
                        + " Auto follows that advice; Quality and Light override it. You can change"
                        + " it any time in Settings.";
            case PRIVACY:
                return Flavor.NETWORK
                        ? "It works fully offline. Only two optional features use the internet, and"
                                + " both can be turned off here. Nothing else ever leaves this TV."
                        : "This build has no internet permission at all, so the system blocks every"
                                + " connection. There is nothing to configure here.";
            case PLACE:
                return "Choose your city for the weather and the sunrise from a list built into the"
                        + " app: nothing is looked up and nothing leaves this TV. Skip it to use the main city"
                        + " of the TV's time zone. You can change it in Settings.";
            case PERMISSIONS:
                return "Each one switches on a feature of Home, and none is required. They are"
                        + " asked for one at a time, and you can take any of them back in"
                        + " Settings, Privacy.";
            default:
                return isHome(activity)
                        ? "All set: this is the app your Home button opens."
                        : "Not yet. Many TVs do not offer a choice on screen. If yours does not,"
                                + " the website explains how to set it from a computer in a minute,"
                                + " and the shangos.sh helper does it step by step. It is"
                                + " reversible.";
        }
    }

    private static String recommendation(Hardware.Tier tier) {
        switch (tier) {
            case LIGHT:
                return "A still wallpaper keeps it quick and cool, so Light is recommended.";
            case QUALITY:
                return "It has room to spare, so slow gliding light behind Home is on.";
            default:
                return "It can afford slow gliding light behind Home, kept gentle.";
        }
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(activity);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private View addButton(LinearLayout row, String label, Runnable action) {
        LinearLayout cell = column();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.px(170), -1);
        lp.rightMargin = ui.px(10);
        row.addView(cell, lp);
        TextView button = overlays.row(cell, label, false, action);
        button.setGravity(android.view.Gravity.CENTER);
        return button;
    }
}
