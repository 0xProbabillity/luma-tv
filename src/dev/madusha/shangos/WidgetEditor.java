package dev.madusha.shangos;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;

import java.util.*;

/**
 * Settings → Home widgets. OK turns a widget on or off; Hold OK picks it up, Up and Down move it,
 * OK or Back puts it down. On the right, miniature pages show how the widgets fill Home, updated
 * with every change.
 */
final class WidgetEditor {
    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost host;
    final ArrayList<String> order;
    final HashSet<String> off = new HashSet<>();
    final HashMap<String, Runnable> paints = new HashMap<>();
    final LinearLayout list;
    final FrameLayout pages;
    final TextView hint;
    String moving;
    boolean ignoreUp;

    /**
     * Opens the editor over Home. {@code settingsRow} is the Settings row to select when it closes.
     */
    WidgetEditor(
            Activity activity,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost host,
            int settingsRow) {
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.host = host;
        inputs = !InputsWidget.list(activity).isEmpty();
        order = WidgetBoard.order(prefs);
        list = new LinearLayout(activity);
        pages = new FrameLayout(activity);
        for (String id : WidgetBoard.IDS) if (!WidgetBoard.on(prefs, id)) off.add(id);
        FrameLayout o = overlays.open(0xB3000000);
        overlays.onCancel(() -> host.backToSettings(settingsRow));
        FrameLayout panel = new FrameLayout(activity);
        panel.setBackground(ui.shape(0xF51C1C1E, 26));
        panel.setElevation(ui.px(24));
        panel.setClipChildren(false);
        float h = Math.max(440, 98 + order.size() * 61 + 20); // grows with the number of widgets
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ui.px(760), ui.px(h));
        lp.gravity = Gravity.CENTER;
        o.addView(panel, lp);
        panel.addView(
                ui.text("Home widgets", 24, Color.WHITE, "sans-serif-medium"),
                ui.pos(420, 32, 28, 26));
        hint = ui.text("", 13, 0xB3FFFFFF, "sans-serif");
        hint.setSingleLine(true);
        panel.addView(hint, ui.pos(420, 18, 29, 64));
        setHint();
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(ui.px(8), 0, ui.px(8), 0);
        panel.addView(list, ui.pos(436, order.size() * 61, 20, 98));
        pages.setClipChildren(false);
        panel.addView(pages, ui.pos(262, 370, 472, 34));
        for (String id : order) list.addView(row(id), rowParams());
        drawPages();
        if (host.motion()) {
            panel.setScaleX(.96f);
            panel.setScaleY(.96f);
            panel.animate()
                    .scaleX(1)
                    .scaleY(1)
                    .setDuration(360)
                    .setInterpolator(Motion.TILE)
                    .start();
        }
        host.post(() -> list.getChildAt(0).requestFocus());
    }

    LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.px(54));
        p.bottomMargin = ui.px(7);
        return p;
    }

    final boolean inputs;

    boolean available(String id) {
        return id.equals("weather") ? Net.enabled(prefs) : id.equals("inputs") ? inputs : true;
    }

    String unavailable(String id) {
        return id.equals("inputs")
                ? "Only on TVs: a TV stick has no HDMI inputs."
                : Flavor.NETWORK ? "Offline mode is on." : "Not in the offline build.";
    }

    void setHint() {
        hint.setText(
                moving != null
                        ? "Up and Down move it. OK puts it down."
                        : "OK turns a widget on or off. Hold OK to move it.");
    }

    View row(String id) {
        FrameLayout row = new FrameLayout(activity);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setSoundEffectsEnabled(false);
        row.setClipChildren(false);
        row.setContentDescription(WidgetBoard.title(id));
        View disc = new View(activity);
        row.addView(disc, ui.pos(32, 32, 12, 11));
        Glyph glyph = new Glyph(activity).kind(WidgetBoard.glyph(id));
        row.addView(glyph, ui.pos(18, 18, 19, 18));
        TextView name = ui.text(WidgetBoard.title(id), 15, Color.WHITE, "sans-serif-medium");
        row.addView(name, ui.pos(280, 19, 56, 9));
        TextView about =
                ui.text(
                        available(id) ? WidgetBoard.about(id) : unavailable(id),
                        11.5f,
                        0x99FFFFFF,
                        "sans-serif");
        about.setSingleLine(true);
        about.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(about, ui.pos(288, 15, 56, 31));
        View track = new View(activity), knob = new View(activity);
        row.addView(track, ui.pos(40, 24, 368, 15));
        row.addView(knob, ui.pos(20, 20, 370, 17));
        TextView grip = ui.text("≡", 22, 0xFF111827, "sans-serif-medium");
        grip.setGravity(Gravity.CENTER);
        grip.setAlpha(0);
        row.addView(grip, ui.pos(40, 24, 368, 13));
        Runnable paint =
                () -> {
                    boolean f = row.hasFocus(),
                            m = id.equals(moving),
                            lit = f || m,
                            on = !off.contains(id) && available(id);
                    row.setBackground(ui.shape(lit ? 0xFAFFFFFF : 0x14FFFFFF, 14));
                    name.setTextColor(lit ? 0xFF111827 : Color.WHITE);
                    about.setTextColor(lit ? 0xFF5B6472 : 0x99FFFFFF);
                    disc.setBackground(ui.oval(on ? 0xFF0A84FF : lit ? 0x1A000000 : 0x26FFFFFF));
                    glyph.color(on ? Color.WHITE : lit ? 0xFF111827 : 0xCCFFFFFF);
                    track.setBackground(
                            ui.shape(on ? 0xFF34C759 : lit ? 0x2E111827 : 0x33FFFFFF, 12));
                    knob.setBackground(ui.shape(Color.WHITE, 10));
                    knob.setElevation(ui.px(1.5f));
                    float x = on ? ui.px(16) : 0;
                    knob.animate().cancel();
                    if (host.motion())
                        knob.animate()
                                .translationX(x)
                                .setDuration(260)
                                .setInterpolator(Motion.TILE)
                                .start();
                    else knob.setTranslationX(x);
                    track.setAlpha(m ? 0 : 1);
                    knob.setAlpha(m ? 0 : 1);
                    grip.setAlpha(m ? 1 : 0);
                    row.setAlpha(available(id) ? 1 : .5f);
                };
        paints.put(id, paint);
        paint.run();
        row.setOnFocusChangeListener(
                (v, f) -> {
                    paint.run();
                    lift(row, f);
                    if (f) host.step(list.indexOfChild(row));
                });
        row.setOnClickListener(
                v -> {
                    if (moving != null) return;
                    host.click();
                    if (!available(id)) {
                        row.animate().cancel();
                        row.setTranslationX(0);
                        if (host.motion())
                            row.animate()
                                    .translationX(ui.px(6))
                                    .setDuration(70)
                                    .withEndAction(
                                            () ->
                                                    row.animate()
                                                            .translationX(0)
                                                            .setDuration(320)
                                                            .setInterpolator(
                                                                    new OvershootInterpolator(3f))
                                                            .start())
                                    .start();
                        return;
                    }
                    if (!off.remove(id)) off.add(id);
                    save();
                    paint.run();
                    drawPages();
                    if (id.equals("weather") && !off.contains(id))
                        Toast.makeText(
                                        activity,
                                        "Weather now asks open-meteo.com for the forecast.",
                                        Toast.LENGTH_LONG)
                                .show();
                });
        row.setOnLongClickListener(
                v -> {
                    host.click();
                    moving = id;
                    ignoreUp = true;
                    setHint();
                    paint.run();
                    lift(row, true);
                    return true;
                });
        row.setOnKeyListener(
                (v, key, e) -> {
                    if (!id.equals(moving)) return false;
                    boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
                    if (key == KeyEvent.KEYCODE_DPAD_CENTER
                            || key == KeyEvent.KEYCODE_ENTER
                            || key == KeyEvent.KEYCODE_BACK) {
                        if (!down) {
                            if (ignoreUp && key != KeyEvent.KEYCODE_BACK) ignoreUp = false;
                            else drop(row);
                        }
                        return true;
                    }
                    if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (down) move(row, id, key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                        return true;
                    }
                    return key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
                });
        return row;
    }

    void lift(View row, boolean f) {
        boolean m = moving != null && row.hasFocus();
        float s = m ? 1.045f : f ? 1.02f : 1;
        row.animate().cancel();
        if (host.motion())
            row.animate().scaleX(s).scaleY(s).setDuration(300).setInterpolator(Motion.TILE).start();
        else {
            row.setScaleX(s);
            row.setScaleY(s);
        }
        row.setElevation(ui.px(m ? 22 : f ? 10 : 0));
        row.setTranslationZ(ui.px(m ? 10 : 0));
    }

    /**
     * Swaps the picked-up row with its neighbour. The neighbour moves in the list (the focused row
     * stays attached, so it keeps focus) and both glide from their old places.
     */
    void move(View row, String id, int d) {
        int i = order.indexOf(id), j = i + d;
        if (j < 0 || j >= order.size()) return;
        View other = list.getChildAt(j);
        int rowTop = row.getTop(), otherTop = other.getTop();
        Collections.swap(order, i, j);
        list.removeView(other);
        list.addView(other, i, rowParams());
        for (View v : new View[] {row, other}) {
            float from = v == row ? rowTop - otherTop : otherTop - rowTop;
            v.animate().cancel();
            v.setTranslationY(from);
            if (host.motion())
                v.animate().translationY(0).setDuration(300).setInterpolator(Motion.EASE).start();
            else v.setTranslationY(0);
        }
        row.animate().scaleX(1.045f).scaleY(1.045f).setDuration(300).start();
        host.step(j);
        save();
        drawPages();
    }

    void drop(View row) {
        moving = null;
        host.click();
        setHint();
        for (Runnable r : paints.values()) r.run();
        lift(row, true);
        save();
    }

    void save() {
        WidgetBoard.save(prefs, order, off);
    }

    /**
     * Miniature Home pages: the greeting, the widgets of each page, and the apps that stay
     * underneath.
     */
    void drawPages() {
        pages.removeAllViews();
        pages.addView(
                ui.text("Your Home", 13, 0x99FFFFFF, "sans-serif-medium"), ui.pos(240, 16, 2, 0));
        ArrayList<ArrayList<String>> layout = WidgetBoard.pages(prefs, inputs);
        if (layout.isEmpty()) {
            TextView t =
                    ui.text("Only the greeting and your apps.", 12.5f, 0x80FFFFFF, "sans-serif");
            pages.addView(t, ui.pos(250, 18, 2, 30));
            return;
        }
        float mw = 122, mh = 69, k = (mw - 16) / WidgetBoard.ROW_W;
        for (int p = 0; p < Math.min(6, layout.size()); p++) {
            float x = (p % 2) * (mw + 14), y = 28 + (p / 2) * (mh + 30);
            FrameLayout mini = new FrameLayout(activity);
            GradientDrawable bg = ui.shape(0x14FFFFFF, 8);
            bg.setStroke(Math.max(1, ui.px(.8f)), 0x26FFFFFF);
            mini.setBackground(bg);
            pages.addView(mini, ui.pos(mw, mh, x, y));
            View g1 = new View(activity);
            g1.setBackground(ui.shape(0x66FFFFFF, 1.5f));
            mini.addView(g1, ui.pos(30, 3.5f, 8, 8));
            View g2 = new View(activity);
            g2.setBackground(ui.shape(0x33FFFFFF, 1.5f));
            mini.addView(g2, ui.pos(20, 2.5f, 8, 14));
            ArrayList<String> ids = layout.get(p);
            float bx = 8;
            for (String id : ids) {
                float bw = WidgetBoard.width(id) * k;
                View b = new View(activity);
                b.setBackground(ui.shape(0x33FFFFFF, 3));
                mini.addView(b, ui.pos(bw, 24, bx, 24));
                Glyph g = new Glyph(activity).kind(WidgetBoard.glyph(id)).color(0xD9FFFFFF);
                mini.addView(g, ui.pos(12, 12, bx + bw / 2 - 6, 30));
                bx += bw + WidgetBoard.GAP * k;
            }
            float aw = (mw - 16 - 5 * 3) / 6f;
            for (int a = 0; a < 6; a++) {
                View app = new View(activity);
                app.setBackground(ui.shape(0x26FFFFFF, 2));
                mini.addView(app, ui.pos(aw, 9, 8 + a * (aw + 3), mh - 15));
            }
            ArrayList<String> names = new ArrayList<>();
            for (String id : ids) names.add(WidgetBoard.title(id));
            TextView label = ui.text(String.join(" · ", names), 11, 0xA6FFFFFF, "sans-serif");
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            pages.addView(label, ui.pos(mw, 15, x + 1, y + mh + 6));
        }
    }
}
