package dev.madusha.shangos;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.function.IntConsumer;

/**
 * Settings → Weather location and Temperature. The location is Automatic (the main city of the
 * TV's time zone) or any city found by name; the search starts only when the user types, and sends
 * only what they typed (see {@link Places}).
 */
final class WeatherSettings {
    /** Results shown under the field. */
    private static final int SHOWN = 5;

    private static final String[] UNITS = {"Automatic", "Celsius", "Fahrenheit"};
    private static final String[] UNIT_KEYS = {"auto", "c", "f"};

    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost screens;
    private final IntConsumer reload;

    /** {@code reload} shows Settings again with a row selected. */
    WeatherSettings(
            Activity activity,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost screens,
            IntConsumer reload) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.screens = screens;
        this.reload = reload;
    }

    // ---- Temperature ----

    private int unitIndex() {
        String u = prefs.tempUnit();
        return u.equals("c") ? 1 : u.equals("f") ? 2 : 0;
    }

    String unitLabel() {
        return UNITS[unitIndex()];
    }

    String unitAbout() {
        return "Weather in Celsius or Fahrenheit. Automatic follows the country of the weather's"
                + " city.";
    }

    void unitMenu(int settingsRow) {
        overlays.menu(
                "Temperature",
                UNITS,
                unitIndex(),
                choice -> {
                    prefs.setTempUnit(UNIT_KEYS[choice]);
                    reload.accept(settingsRow);
                });
    }

    // ---- Weather location ----

    String locationLabel() {
        Places.Place p = Places.pinned(prefs);
        return p == null ? "Automatic" : p.name;
    }

    String locationAbout() {
        Places.Place pinned = Places.pinned(prefs);
        if (pinned != null)
            return "The forecast is for "
                    + pinned.full()
                    + ". Choose Automatic to use the main city of the TV's time zone.";
        return "Automatic is the main city of the TV's time zone ("
                + Daylight.city()
                + "), with no request at all. Or search for any city.";
    }

    /** Opens the picker; choosing returns to Settings with {@code settingsRow} selected. */
    void pickLocation(int settingsRow) {
        new LocationPicker(settingsRow);
    }

    /** Automatic, or a city found by typing its name. Search on the keyboard picks the first. */
    private final class LocationPicker {
        final int settingsRow;
        final FrameLayout o = overlays.open(0xA6000000);
        final EditText field = new EditText(activity);
        final LinearLayout list = new LinearLayout(activity);
        final Runnable search = this::search;
        ArrayList<Places.Place> found;
        String status;
        int generation;
        boolean jump, closed;

        LocationPicker(int settingsRow) {
            this.settingsRow = settingsRow;
            overlays.onCancel(
                    () -> {
                        closed = true;
                        keyboard(false);
                    });
            FrameLayout panel = new FrameLayout(activity);
            panel.setBackground(ui.shape(0xF51C1C1E, 24));
            panel.setElevation(ui.px(24));
            panel.setClipChildren(false);
            // Near the top, not centred: the TV's keyboard covers the lower part of the screen,
            // and the window does not pan (adjustNothing in the manifest).
            FrameLayout.LayoutParams lp =
                    new FrameLayout.LayoutParams(ui.px(520), ui.px(152 + (SHOWN + 1) * 46 + 14));
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = ui.px(28);
            o.addView(panel, lp);
            panel.addView(
                    ui.text("Weather location", 22, Color.WHITE, "sans-serif-medium"),
                    ui.pos(472, 30, 24, 24));
            panel.addView(
                    ui.text(
                            "Type a city to search, or keep the time zone's city.",
                            13,
                            0xB3FFFFFF,
                            "sans-serif"),
                    ui.pos(472, 20, 24, 60));
            field.setSingleLine(true);
            field.setHint("Search for a city");
            field.setHintTextColor(0x66FFFFFF);
            field.setTextColor(Color.WHITE);
            field.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(17));
            field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(48)});
            field.setPadding(ui.px(16), 0, ui.px(16), 0);
            field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            field.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            Runnable ring =
                    () -> {
                        GradientDrawable bg = ui.shape(0x1FFFFFFF, 12);
                        if (field.hasFocus()) bg.setStroke(ui.px(1.5f), Color.WHITE);
                        field.setBackground(bg);
                    };
            ring.run();
            field.setOnFocusChangeListener(
                    (v, f) -> {
                        ring.run();
                        if (f) screens.step(0);
                    });
            panel.addView(field, ui.pos(472, 46, 24, 94));
            field.addTextChangedListener(
                    new TextWatcher() {
                        public void beforeTextChanged(CharSequence c, int a, int b, int d) {}

                        public void onTextChanged(CharSequence c, int a, int b, int d) {}

                        public void afterTextChanged(Editable e) {
                            field.removeCallbacks(search);
                            generation++;
                            if (query().length() < 2) {
                                found = null;
                                status = null;
                                rows();
                            } else if (!Net.weatherEnabled(prefs)) {
                                found = null;
                                status = "Turn Weather on in Home widgets to search.";
                                rows();
                            } else {
                                status = "Searching…";
                                if (found == null) rows();
                                field.postDelayed(search, 350);
                            }
                        }
                    });
            field.setOnEditorActionListener(
                    (v, action, e) -> {
                        if (action != EditorInfo.IME_ACTION_SEARCH
                                && action != EditorInfo.IME_ACTION_DONE) return false;
                        keyboard(false);
                        if (query().length() < 2 || !Net.weatherEnabled(prefs)) return true;
                        if (found != null && !found.isEmpty() && status == null)
                            list.getChildAt(1).requestFocus();
                        else {
                            jump = true;
                            field.removeCallbacks(search);
                            search();
                        }
                        return true;
                    });
            list.setOrientation(LinearLayout.VERTICAL);
            list.setClipChildren(false);
            list.setClipToPadding(false);
            panel.addView(list, ui.pos(488, (SHOWN + 1) * 46, 16, 152));
            rows();
            screens.post(
                    () -> {
                        field.requestFocus();
                        keyboard(true);
                    });
        }

        String query() {
            return field.getText().toString().trim();
        }

        void keyboard(boolean show) {
            InputMethodManager im =
                    (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (show) im.showSoftInput(field, 0);
            else im.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }

        void search() {
            final int ticket = generation;
            final String q = query();
            if (q.length() < 2 || !Net.weatherEnabled(prefs)) return;
            WidgetBoard.IO.execute(
                    () -> {
                        ArrayList<Places.Place> result = null;
                        try {
                            result = Places.search(q);
                        } catch (Exception ignored) {
                            // Offline or refused: the status line says so.
                        }
                        final ArrayList<Places.Place> done = result;
                        field.post(
                                () -> {
                                    if (closed || ticket != generation) return; // text changed
                                    found = done == null ? new ArrayList<>() : done;
                                    status =
                                            done == null
                                                    ? "Can't search right now. Check the"
                                                            + " connection."
                                                    : done.isEmpty()
                                                            ? "No cities called “" + q + "”"
                                                            : null;
                                    rows();
                                    if (jump) {
                                        jump = false;
                                        (found.isEmpty() ? list.getChildAt(0) : list.getChildAt(1))
                                                .requestFocus();
                                    }
                                });
                    });
        }

        /**
         * Automatic first, then the search results (or, before a search, the pinned city). Focus
         * stays on the same row when the list is rebuilt under it.
         */
        void rows() {
            View had = activity.getCurrentFocus();
            int at = had != null && had.getParent() == list ? list.indexOfChild(had) : -1;
            list.removeAllViews();
            Places.Place pinned = Places.pinned(prefs), auto = Places.zone(activity);
            overlays.row(
                    list,
                    auto == null ? "Automatic" : "Automatic  ·  " + auto.name,
                    pinned == null,
                    () -> pick(null));
            java.util.List<Places.Place> shown =
                    found != null
                            ? found.subList(0, Math.min(SHOWN, found.size()))
                            : pinned != null
                                    ? java.util.Collections.singletonList(pinned)
                                    : java.util.Collections.<Places.Place>emptyList();
            for (Places.Place p : shown)
                overlays.row(list, p.full(), p.same(pinned), () -> pick(p));
            boolean searching = status != null && status.startsWith("Search");
            if (status != null && (found == null || found.isEmpty() || !searching)) {
                TextView t = ui.text(status, 14, 0x99FFFFFF, "sans-serif");
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(ui.px(18), 0, ui.px(18), 0);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                list.addView(t, new LinearLayout.LayoutParams(-1, ui.px(42)));
            }
            if (at >= 0) {
                int n = list.getChildCount();
                View v = null;
                for (int i = Math.min(at, n - 1); i >= 0 && v == null; i--)
                    if (list.getChildAt(i).isFocusable()) v = list.getChildAt(i);
                if (v != null) v.requestFocus();
            }
        }

        void pick(Places.Place place) {
            closed = true;
            Places.pin(prefs, place);
            keyboard(false);
            overlays.onCancel(null);
            overlays.close(false);
            reload.accept(settingsRow);
        }
    }
}
