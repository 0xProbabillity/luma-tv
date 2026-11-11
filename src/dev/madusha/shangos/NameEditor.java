package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Settings → Your name: typed with the TV keyboard and kept only in this app's storage on the TV.
 */
final class NameEditor {
    /** What the editor needs from the launcher. */
    interface Host {
        /** Show Settings again with row {@code row} selected. */
        void reload(int row);
    }

    private final Context context;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost screens;
    private final Host host;

    NameEditor(
            Context context,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost screens,
            Host host) {
        this.context = context;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.screens = screens;
        this.host = host;
    }

    private InputMethodManager keyboard() {
        return (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /** Opens the editor; when it closes with Save, Settings shows {@code settingsRow} selected. */
    void show(int settingsRow) {
        FrameLayout o = overlays.open(0xA6000000);
        FrameLayout panel = new FrameLayout(context);
        panel.setBackground(ui.shape(0xF51C1C1E, 24));
        panel.setElevation(ui.px(24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ui.px(440), ui.px(232));
        lp.gravity = Gravity.CENTER;
        o.addView(panel, lp);
        panel.addView(
                ui.text("Your name", 22, Color.WHITE, "sans-serif-medium"),
                ui.pos(392, 30, 24, 24));
        panel.addView(
                ui.text(
                        "Home will say Hello and your name. Leave it empty for a plain Hello.",
                        13,
                        0xB3FFFFFF,
                        "sans-serif"),
                ui.pos(392, 20, 24, 60));
        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setText(prefs.name());
        field.setSelection(field.getText().length());
        field.setHint("Name");
        field.setHintTextColor(0x66FFFFFF);
        field.setTextColor(Color.WHITE);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(17));
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(24)});
        field.setPadding(ui.px(16), 0, ui.px(16), 0);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
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
        panel.addView(field, ui.pos(392, 46, 24, 94));
        Runnable save =
                () -> {
                    prefs.setName(field.getText().toString());
                    keyboard().hideSoftInputFromWindow(field.getWindowToken(), 0);
                    overlays.close(false);
                    host.reload(settingsRow);
                };
        field.setOnEditorActionListener(
                (v, action, e) -> {
                    if (action == EditorInfo.IME_ACTION_DONE) {
                        save.run();
                        return true;
                    }
                    return false;
                });
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(buttons, ui.pos(392, 42, 24, 166));
        LinearLayout a = new LinearLayout(context), b = new LinearLayout(context);
        a.setOrientation(LinearLayout.VERTICAL);
        b.setOrientation(LinearLayout.VERTICAL);
        buttons.addView(a, new LinearLayout.LayoutParams(ui.px(120), -1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ui.px(120), -1);
        gap.leftMargin = ui.px(10);
        buttons.addView(b, gap);
        TextView done = overlays.row(a, "Save", false, save);
        done.setGravity(Gravity.CENTER);
        TextView clear =
                overlays.row(
                        b,
                        "Clear",
                        false,
                        () -> {
                            field.setText("");
                            field.requestFocus();
                        });
        clear.setGravity(Gravity.CENTER);
        screens.post(
                () -> {
                    field.requestFocus();
                    keyboard().showSoftInput(field, 0);
                });
    }
}
