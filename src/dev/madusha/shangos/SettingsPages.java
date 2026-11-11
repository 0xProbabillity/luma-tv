package dev.madusha.shangos;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.view.View;

import java.util.ArrayList;

/**
 * The rows of the Settings groups: Appearance, Motion and sound, Home and System. {@link
 * SettingsScreen} owns the pages, the list and the preview card; this builds what goes in them.
 */
final class SettingsPages {
    /** What a group needs from the page it is built into. */
    interface Kit {
        /** A row: a value with a chevron, or (with {@code toggle}) a switch. */
        View option(
                LinearLayout parent,
                String title,
                java.util.function.Supplier<String> value,
                String toggle,
                Runnable action,
                Runnable onFocus);

        /** Shows a thumbnail (0 for none), a title and some words in the card on the right. */
        void preview(int thumb, String title, String body);

        /** The wallpaper being shown. */
        int style();
    }

    // Positions in the Appearance group: the dialogs return to them.
    private static final int ROW_WALLPAPER = 0, ROW_NAME = 1;

    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost screens;
    private final SettingsScreen.Host host;
    private final SetupGuide setup;
    private final AboutScreen about;
    private final WallpaperPicker wallpaperPicker;
    private final NameEditor nameEditor;
    private final HomeScreen homeScreen;
    private final WeatherSettings weather;
    private final ScreenSaverSettings screenSaver;
    private final AppsSettings apps;
    private int widgetsRow, aboutRowIndex;

    SettingsPages(
            Activity activity,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost screens,
            SettingsScreen.Host host,
            SetupGuide setup,
            AboutScreen about,
            WallpaperPicker wallpaperPicker,
            NameEditor nameEditor,
            HomeScreen homeScreen,
            WeatherSettings weather,
            ScreenSaverSettings screenSaver,
            AppsSettings apps) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.screens = screens;
        this.host = host;
        this.setup = setup;
        this.about = about;
        this.wallpaperPicker = wallpaperPicker;
        this.nameEditor = nameEditor;
        this.homeScreen = homeScreen;
        this.weather = weather;
        this.screenSaver = screenSaver;
        this.apps = apps;
    }

    /** The row of About in the System group (where its dialogs return to). */
    int aboutRow() {
        return aboutRowIndex;
    }

    /** Settings, Appearance. */
    void appearance(Kit kit, LinearLayout list, ArrayList<View> controls) {
        int wp = kit.style();
        controls.add(
                kit.option(
                        list,
                        "Wallpaper",
                        () -> Wallpaper.NAMES[kit.style()],
                        null,
                        () -> wallpaperPicker.show(ROW_WALLPAPER),
                        () ->
                                kit.preview(
                                        Wallpaper.THUMBS[kit.style()],
                                        Wallpaper.NAMES[kit.style()],
                                        "Eight wallpapers, from Daybreak to Calm Sea. Daybreak is"
                                            + " light by day and dark after sunset.")));
        controls.add(
                kit.option(
                        list,
                        "Your name",
                        () -> {
                            String n = prefs.name();
                            return n.isEmpty() ? "Add" : n;
                        },
                        null,
                        () -> nameEditor.show(ROW_NAME),
                        () ->
                                kit.preview(
                                        0,
                                        "Your name",
                                        "Home greets you by name. It is saved only on this TV.")));
        controls.add(
                kit.option(
                        list,
                        "Clock",
                        null,
                        "clock",
                        () -> {},
                        () ->
                                kit.preview(
                                        0,
                                        "Clock",
                                        "Shows the time in the top-right corner of every page.")));
    }

    /** Settings, Motion and sound. */
    void motion(Kit kit, LinearLayout list, ArrayList<View> controls) {
        int wp = kit.style();
        controls.add(
                kit.option(
                        list,
                        "Fluid motion",
                        null,
                        "motion",
                        () -> {},
                        () ->
                                kit.preview(
                                        0,
                                        "Fluid motion",
                                        "Springy focus, depth and the 3D flight into your TV. Turn"
                                                + " off for a calmer, simpler screen.")));
        controls.add(
                kit.option(
                        list,
                        "Living wallpaper",
                        null,
                        "ambient",
                        host::ambientChanged,
                        () ->
                                kit.preview(
                                        Wallpaper.THUMBS[kit.style()],
                                        "Living wallpaper",
                                        "Clouds drift and water moves very slowly. Pauses whenever"
                                                + " an app is open.")));
        controls.add(
                kit.option(
                        list,
                        "Room lighting",
                        null,
                        Prefs.STAGE,
                        () -> {},
                        () ->
                                kit.preview(
                                        Wallpaper.THUMBS[kit.style()],
                                        "Room lighting",
                                        "The light behind your apps takes the colour of the one you"
                                                + " are on, and the wallpaper dims a little. Off"
                                                + " with the Light performance setting.")));
        controls.add(
                kit.option(
                        list,
                        "Frosted glass",
                        null,
                        Prefs.GLASS,
                        host::glassChanged,
                        () ->
                                kit.preview(
                                        Wallpaper.THUMBS[kit.style()],
                                        "Frosted glass",
                                        "Bars, cards and menus show a blurred copy of the"
                                                + " wallpaper behind them. Off with the Light"
                                                + " performance setting.")));
        controls.add(
                kit.option(
                        list,
                        "Calm mode",
                        null,
                        Prefs.CALM,
                        () -> {},
                        () ->
                                kit.preview(
                                        Wallpaper.THUMBS[kit.style()],
                                        "Calm mode",
                                        "After a quiet moment the bar and clock fade back so"
                                                + " the wallpaper has the room. Any key brings"
                                                + " them back.")));
        final int performanceRow = controls.size();
        controls.add(
                kit.option(
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
                                kit.preview(
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
                kit.option(
                        list,
                        "Navigation sounds",
                        null,
                        "sound",
                        () -> {},
                        () ->
                                kit.preview(
                                        0,
                                        "Navigation sounds",
                                        "Soft, quiet feedback as you move and open apps.")));
    }

    /** Settings, Home. */
    void home(Kit kit, LinearLayout list, ArrayList<View> controls) {
        int wp = kit.style();
        widgetsRow = controls.size();
        controls.add(
                kit.option(
                        list,
                        "Home widgets",
                        this::widgetSummary,
                        null,
                        this::widgetEditor,
                        () ->
                                kit.preview(
                                        0,
                                        "Home widgets",
                                        "Choose what Home shows above your apps — Up Next, Weather"
                                                + " (off until you turn it on), "
                                                + (InputsWidget.count > 0 ? "Inputs, " : "")
                                                + "Now Playing and Control — and their order. Left"
                                                + " and Right turn the pages.")));
        final int locationRow = controls.size();
        controls.add(
                kit.option(
                        list,
                        "City",
                        weather::cityLabel,
                        null,
                        () -> weather.pickCity(locationRow),
                        () -> kit.preview(0, "City", weather.cityAbout())));
        controls.add(
                kit.option(
                        list,
                        "Temperature",
                        weather::unitLabel,
                        null,
                        () -> weather.unitMenu(locationRow + 1),
                        () -> kit.preview(0, "Temperature", weather.unitAbout())));
        controls.add(
                kit.option(
                        list,
                        "Home screen",
                        () -> homeScreen.isDefault() ? "shangOS" : "TV default",
                        null,
                        homeScreen::show,
                        () ->
                                kit.preview(
                                        0,
                                        "Home screen",
                                        homeScreen.isDefault()
                                                ? "shangOS opens when you press Home. Choose to"
                                                        + " return to your TV's own home screen."
                                                : "Your TV's own home screen opens when you press"
                                                        + " Home.")));
        final int appsRow = controls.size();
        controls.add(
                kit.option(
                        list,
                        "Apps order",
                        apps::sortLabel,
                        null,
                        () -> apps.sortMenu(appsRow),
                        () -> kit.preview(0, "Apps order", apps.sortAbout())));
        controls.add(
                kit.option(
                        list,
                        "Hidden apps",
                        apps::hiddenLabel,
                        null,
                        () -> apps.showHidden(appsRow + 1),
                        () -> kit.preview(0, "Hidden apps", apps.hiddenAbout())));
    }

    /** Settings, System. */
    void system(Kit kit, LinearLayout list, ArrayList<View> controls) {
        int wp = kit.style();
        controls.add(
                kit.option(
                        list,
                        "Setup guide",
                        setup::progress,
                        null,
                        () -> setup.show(setup.resumeStep()),
                        () ->
                                kit.preview(
                                        0,
                                        "Setup guide",
                                        "Privacy choices, optional permissions and the home screen,"
                                                + " one step at a time.")));
        controls.add(
                kit.option(
                        list,
                        "Screen saver",
                        screenSaver::label,
                        null,
                        screenSaver::menu,
                        () ->
                                kit.preview(
                                        0,
                                        "Screen saver",
                                        "shangOS Ambient: a quiet clock over your wallpaper that"
                                                + " moves every minute. No network, nothing to"
                                                + " allow.")));
        controls.add(
                kit.option(
                        list,
                        "Device settings",
                        () -> "",
                        null,
                        () -> activity.startActivity(new Intent(Settings.ACTION_SETTINGS)),
                        () ->
                                kit.preview(
                                        0,
                                        "Device settings",
                                        "Network, picture, sound and accounts.")));
        aboutRowIndex = controls.size();
        controls.add(
                kit.option(
                        list,
                        "About " + activity.getString(R.string.app_name),
                        () -> "",
                        null,
                        about::show,
                        () ->
                                kit.preview(
                                        0,
                                        "About " + activity.getString(R.string.app_name),
                                        "Version "
                                                + AboutScreen.version(activity)
                                                + " · based on Luma TV (MIT)")));
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
}
