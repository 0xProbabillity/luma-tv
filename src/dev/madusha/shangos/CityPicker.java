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

import java.util.Collections;
import java.util.List;

/**
 * "Where is this TV?": Automatic (the main city of the TV's time zone) or a city typed into the
 * search. The cities are built into the app ({@link Cities}), so searching needs no network and no
 * location service, and nothing typed leaves the TV. Used by setup and by Settings.
 */
final class CityPicker {
    /** Results shown under the field. */
    private static final int SHOWN = 5;

    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost screens;

    CityPicker(Activity activity, Ui ui, Prefs prefs, Overlays overlays, ScreenHost screens) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.screens = screens;
    }

    /** What the row shows: the chosen city, or Automatic. */
    String label() {
        Places.Place p = Places.pinned(prefs);
        return p == null ? "Automatic" : p.name;
    }

    String about() {
        Places.Place pinned = Places.pinned(prefs);
        if (pinned != null)
            return "The weather and the sunrise that changes Daybreak are for "
                    + pinned.full()
                    + ". Choose Automatic to use the main city of the TV's time zone.";
        return "Automatic is the main city of the TV's time zone ("
                + Daylight.city()
                + "). Or choose your own city from the list built into the app: nothing is looked"
                + " up and nothing leaves this TV.";
    }

    /** Opens the picker; {@code done} runs after a choice (not after Back). */
    void show(Runnable done) {
        new Session(done);
    }

    private final class Session {
        final Runnable done;
        final FrameLayout overlay = overlays.open(0xA6000000);
        final EditText field = new EditText(activity);
        final LinearLayout list = new LinearLayout(activity);
        Cities cities;
        boolean closed, failed;

        Session(Runnable done) {
            this.done = done;
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
            overlay.addView(panel, lp);
            panel.addView(
                    ui.text("Your city", 22, Color.WHITE, "sans-serif-medium"),
                    ui.pos(472, 30, 24, 24));
            panel.addView(
                    ui.text(
                            "Type a city, or add a country: portland, us. Nothing leaves the TV.",
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
                            rows();
                        }
                    });
            field.setOnEditorActionListener(
                    (v, action, e) -> {
                        if (action != EditorInfo.IME_ACTION_SEARCH
                                && action != EditorInfo.IME_ACTION_DONE) return false;
                        keyboard(false);
                        // The first city, if there is one.
                        if (list.getChildCount() > 1 && list.getChildAt(1).isFocusable())
                            list.getChildAt(1).requestFocus();
                        return true;
                    });
            list.setOrientation(LinearLayout.VERTICAL);
            list.setClipChildren(false);
            list.setClipToPadding(false);
            panel.addView(list, ui.pos(488, (SHOWN + 1) * 46, 16, 152));
            rows();
            // The list (about 800 KB) is read once, off the main thread.
            WidgetBoard.IO.execute(
                    () -> {
                        Cities loaded = null;
                        try {
                            loaded = Cities.of(activity);
                        } catch (Exception e) {
                            failed = true;
                        }
                        final Cities result = loaded;
                        field.post(
                                () -> {
                                    cities = result;
                                    if (!closed) rows();
                                });
                    });
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

        /**
         * Automatic first, then the cities for what is typed (or, before anything is typed, the
         * city already chosen). Focus stays on the same row when the list is rebuilt under it.
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
            String status = null;
            List<Places.Place> shown = Collections.emptyList();
            String q = query();
            if (q.length() == 0) {
                if (pinned != null) shown = Collections.singletonList(pinned);
            } else if (failed) {
                status = "The list of cities is missing from this build.";
            } else if (cities == null) {
                status = "Loading the cities…";
            } else {
                shown = cities.find(q, SHOWN);
                if (shown.isEmpty()) status = "No city called “" + q + "”";
            }
            for (Places.Place p : shown)
                overlays.row(list, p.full(), p.same(pinned), () -> pick(p));
            if (status != null) {
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
            done.run();
        }
    }
}
