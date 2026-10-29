package com.madushas.shangos;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Dialogs drawn over Home: menus, notices and full-screen panels. One is open at a time; while it is, the page and tabs
 *  behind it take no focus, and closing it returns focus to where it was. */
final class Overlays {
    /** What the overlays need from the launcher around them. */
    interface Host {
        FrameLayout root();
        ViewGroup content();
        ViewGroup nav();
        boolean motion();
        /** The sound of choosing something / of moving to row {@code index}. */
        void click();
        void step(int index);
        View currentFocus();
    }

    private final Host host;
    private final Ui ui;
    private FrameLayout overlay;
    private View returnTo;
    private Runnable onCancel;

    Overlays(Host host, Ui ui) { this.host = host; this.ui = ui; }

    boolean isOpen() { return overlay != null; }
    /** What to do if the overlay is closed with Back (not when it is closed by choosing something). */
    void onCancel(Runnable action) { onCancel = action; }

    /** Closes the open overlay. {@code cancelled} means Back was pressed: the cancel action then runs. */
    void close(boolean cancelled) {
        if (overlay == null) return;
        final FrameLayout o = overlay; overlay = null;
        Runnable cancel = onCancel; onCancel = null;
        if (cancelled && cancel != null) cancel.run();
        trapFocus(false);
        // A fade's end action is skipped when the animation is cancelled, which left the picker stuck over Home:
        // the closing overlay takes no focus, and is removed on a timer, not by the animation.
        o.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); o.animate().cancel();
        if (host.motion()) { o.animate().alpha(0).setDuration(180).start(); host.root().postDelayed(() -> host.root().removeView(o), 200); }
        else host.root().removeView(o);
        if (returnTo != null) { View r = returnTo; returnTo = null; host.root().post(r::requestFocus); }
    }

    private void trapFocus(boolean on) {
        int mode = on ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_BEFORE_DESCENDANTS;
        host.content().setDescendantFocusability(mode); host.nav().setDescendantFocusability(mode);
    }

    /** Opens an empty overlay with the given scrim colour and returns it to fill. */
    FrameLayout open(int scrim) {
        // One overlay replacing another (About, then Privacy) keeps the original place to return to.
        View back = overlay != null ? returnTo : host.currentFocus(); returnTo = null; close(false); returnTo = back;
        trapFocus(true);
        overlay = new FrameLayout(host.root().getContext());
        overlay.setBackgroundColor(scrim); overlay.setClipChildren(false); overlay.setTranslationZ(ui.px(120));
        host.root().addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        if (host.motion()) { overlay.setAlpha(0); overlay.animate().alpha(1).setDuration(220).setInterpolator(Motion.EASE).start(); }
        return overlay;
    }

    /** A focusable row for a dark panel. */
    TextView row(LinearLayout list, String label, boolean checked, Runnable action) {
        TextView row = ui.line(label, 15, Color.WHITE, "sans-serif-medium");
        row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(ui.px(18), 0, ui.px(18), 0);
        row.setFocusable(true); row.setFocusableInTouchMode(true); row.setSoundEffectsEnabled(false);
        Runnable paint = () -> {
            boolean f = row.hasFocus();
            row.setBackground(f ? ui.shape(0xF7FFFFFF, 12) : null); row.setTextColor(f ? Theme.PANEL_FOCUS_INK : Color.WHITE);
            row.setText((checked ? "✓  " : "") + label); row.setContentDescription(label + (checked ? ", selected" : ""));
        };
        paint.run();
        row.setOnFocusChangeListener((v, f) -> {
            paint.run(); row.animate().cancel();
            if (host.motion()) row.animate().scaleX(f ? 1.02f : 1).scaleY(f ? 1.02f : 1).setDuration(240).setInterpolator(Motion.EASE).start();
            if (f) host.step(list.indexOfChild(row));
        });
        row.setOnClickListener(v -> { host.click(); action.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.px(42)); lp.bottomMargin = ui.px(4); list.addView(row, lp);
        return row;
    }

    /** A dark glass panel centred on the overlay. */
    FrameLayout panel(FrameLayout overlay, float width, float height) {
        FrameLayout panel = new FrameLayout(overlay.getContext());
        panel.setBackground(ui.shape(Theme.PANEL, 24)); panel.setElevation(ui.px(24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ui.px(width), ui.px(height)); lp.gravity = Gravity.CENTER;
        overlay.addView(panel, lp);
        return panel;
    }

    /** A centred glass menu. {@code pick} receives the chosen index. */
    void menu(String title, String[] items, int checked, IntConsumer pick) {
        FrameLayout o = open(Theme.SCRIM);
        int rows = Math.min(items.length, 7); float h = 74 + rows * 46 + 18;
        FrameLayout panel = panel(o, 400, h);
        panel.addView(ui.line(title, 20, Color.WHITE, "sans-serif-medium"), ui.pos(352, 28, 24, 24));
        ScrollView sv = new ScrollView(o.getContext()); sv.setVerticalScrollBarEnabled(false); sv.setClipToPadding(false); sv.setPadding(ui.px(10), 0, ui.px(10), 0);
        LinearLayout list = new LinearLayout(o.getContext()); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ui.px(4), ui.px(4), ui.px(4), ui.px(10)); list.setClipToPadding(false); list.setClipChildren(false); sv.addView(list);
        panel.addView(sv, ui.pos(400, h - 66, 0, 62));
        View first = null;
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            View r = row(list, items[i], i == checked, () -> { close(false); pick.accept(index); });
            if (i == Math.max(0, checked) || first == null && i == 0) first = r;
        }
        if (host.motion()) { panel.setScaleX(.96f); panel.setScaleY(.96f); panel.animate().scaleX(1).scaleY(1).setDuration(360).setInterpolator(Motion.TILE).start(); }
        final View focus = first; host.root().post(() -> { if (focus != null) focus.requestFocus(); });
    }

    /** A message with a Done button. */
    void notice(String title, String message) {
        FrameLayout o = open(Theme.SCRIM);
        FrameLayout panel = panel(o, 440, 260);
        panel.addView(ui.text(title, 22, Color.WHITE, "sans-serif-medium"), ui.pos(392, 30, 24, 26));
        TextView body = ui.text(message, 14, 0xC7FFFFFF, "sans-serif"); body.setLineSpacing(ui.px(5), 1); panel.addView(body, ui.pos(392, 140, 24, 66));
        LinearLayout list = new LinearLayout(o.getContext()); list.setOrientation(LinearLayout.VERTICAL); panel.addView(list, ui.pos(140, 42, 286, 200));
        TextView done = row(list, "Done", false, () -> close(false)); done.setGravity(Gravity.CENTER);
        host.root().post(done::requestFocus);
    }
}
