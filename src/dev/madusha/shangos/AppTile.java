package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * One app on Home or the Apps tab: a card with its picture, a label that appears on focus, and the
 * remote-control keys that move between tiles. The tile never touches the Activity; everything it
 * needs from the launcher is in {@link Host}.
 */
final class AppTile extends FrameLayout {
    /** Tiles per row. Home shows one row; the Apps tab wraps at this many. */
    static final int COLUMNS = 6;

    /** What a tile needs from the page that holds it. */
    interface Host {
        boolean motion();

        /** True on a pale wallpaper (dark text). */
        boolean light();

        /** Direction of the last d-pad move: -1, 0 or 1. */
        int directionX();

        int directionY();

        /** How many tiles this page has. */
        int count();

        /** Focus arrived on {@code app}: the hero and the remembered selection follow it. */
        void selected(App app);

        /** The wallpaper's light follows the focused column. */
        void follow(int index);

        /** The first row was focused: scroll the grid back to its top. */
        void scrollToTop();

        /** OK: open {@code app}; its window grows out of {@code origin}, the card. */
        void launch(App app, View origin);

        /** Hold OK: pick the tile up to move it (and, if it is put down unmoved, its menu). */
        void hold(App app);

        /** True while {@code app}'s tile is picked up. */
        boolean grabbed(App app);

        /** While picked up, the d-pad moved it by {@code step} places (a row is COLUMNS). */
        void nudge(int step);

        /** While picked up, OK put it down. */
        void drop();

        /** Focus left this tile (it may land on another one next). */
        void blurred();

        /** D-pad up from the first row: the widget above x (screen pixels), else the tab bar. */
        void above(float x);

        /** D-pad move to tile {@code next}; the direction feeds the tilt and the sound. */
        void moveTo(int next, int directionX, int directionY);
    }

    final App app;
    final AppArt art;
    final FrameLayout face;
    final Shine shine;
    final GradientDrawable rim;
    final TextView label;
    final int index;
    final TileMotion movement;
    private final Ui ui;
    private final AppRepo repo;
    private final Host host;

    AppTile(Context context, Ui ui, AppRepo repo, Host host, App item, int number) {
        super(context);
        this.ui = ui;
        this.repo = repo;
        this.host = host;
        app = item;
        index = number;
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setSoundEffectsEnabled(false);
        setContentDescription(app.label + ". OK: open. Hold OK: organize app.");
        face = new FrameLayout(context);
        GradientDrawable faceBg = ui.shape(Tint.NEUTRAL, 16);
        faceBg.setStroke(Math.max(1, ui.px(.6f)), 0x1FFFFFFF);
        face.setBackground(faceBg);
        face.setClipToOutline(true);
        face.setElevation(ui.px(2));
        face.setCameraDistance(ui.px(900));
        art = new AppArt(context, ui, repo, app);
        face.addView(art, new FrameLayout.LayoutParams(-1, -1));
        shine = new Shine(context);
        shine.setAlpha(0);
        face.addView(shine, new FrameLayout.LayoutParams(-1, -1));
        rim = ui.shape(Color.TRANSPARENT, 16);
        rim.setStroke(ui.px(1.2f), 0xF0FFFFFF);
        rim.setAlpha(0);
        face.setForeground(rim);
        addView(face, ui.pos(132, 76, 0, 0));
        label =
                ui.text(
                        app.label, 13.5f, Theme.ink(host.light(), 0xFF), "sans-serif-medium");
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0);
        ui.shadow(label, 6, host.light());
        addView(label, ui.pos(160, 22, -14, 88));
        movement = new TileMotion(this, ui, host);
        setOnFocusChangeListener((v, focused) -> onFocus(focused));
        setOnClickListener(v -> host.launch(app, face));
        setOnLongClickListener(
                v -> {
                    host.hold(app);
                    return true;
                });
        setOnKeyListener((v, key, e) -> onKey(key, e));
    }

    private void onFocus(boolean f) {
        movement.focus(f);
        face.setElevation(ui.px(f ? 14 : 2));
        rim.setAlpha(f ? 210 : 0);
        label.animate().cancel();
        if (host.motion())
            label.animate()
                    .alpha(f ? 1 : 0)
                    .translationY(f ? 0 : -ui.px(3))
                    .setDuration(f ? 260 : 140)
                    .setInterpolator(Motion.EASE)
                    .start();
        else label.setAlpha(f ? 1 : 0);
        setTranslationZ(ui.px(f ? 20 : 0));
        if (!f) {
            host.blurred();
            return;
        }
        tintShadow();
        host.selected(app);
        host.follow(index);
        if (index < COLUMNS) host.scrollToTop();
        else
            requestRectangleOnScreen(
                    new Rect(
                            -ui.px(12),
                            -ui.px(14),
                            getWidth() + ui.px(12),
                            getHeight() + ui.px(5)),
                    false);
    }

    /**
     * The shadow under the lifted card takes the colour of the app's icon: a faint glow of its own.
     * The colour is known once the icon has been read (then this runs again).
     */
    private void tintShadow() {
        int accent = repo.accent(app, () -> {
            if (isAttachedToWindow() && isFocused()) tintShadow();
        });
        face.setOutlineSpotShadowColor(
                accent == Tint.NEUTRAL ? 0xFF000000 : 0xFF000000 | (accent & 0x00FFFFFF));
    }

    /** Picks the tile up or puts it down: bigger, with what to do next in the label. */
    void setGrabbed(boolean value) {
        movement.grab(value);
        label.setText(value ? "Move · OK to place" : app.label);
    }

    /** Keys while the tile is picked up: the d-pad moves it, OK puts it down. */
    private boolean onGrabKey(int key, KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (!down) host.drop();
            return true;
        }
        int step =
                key == KeyEvent.KEYCODE_DPAD_LEFT
                        ? -1
                        : key == KeyEvent.KEYCODE_DPAD_RIGHT
                                ? 1
                                : key == KeyEvent.KEYCODE_DPAD_UP
                                        ? -COLUMNS
                                        : key == KeyEvent.KEYCODE_DPAD_DOWN ? COLUMNS : 0;
        if (step == 0) return false;
        if (down && (e.getRepeatCount() == 0 || e.getRepeatCount() % 3 == 0)) host.nudge(step);
        return true;
    }

    private boolean onKey(int key, KeyEvent e) {
        if (host.grabbed(app)) return onGrabKey(key, e);
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            movement.press(e.getAction() == KeyEvent.ACTION_DOWN);
            return false;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (key == KeyEvent.KEYCODE_DPAD_UP && index < COLUMNS) {
            int[] l = new int[2];
            getLocationOnScreen(l);
            host.above(l[0] + getWidth() / 2f);
            return true;
        }
        int next = -1;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT && index % COLUMNS > 0) next = index - 1;
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT
                && index % COLUMNS < COLUMNS - 1
                && index + 1 < host.count()) next = index + 1;
        if (key == KeyEvent.KEYCODE_DPAD_DOWN && index + COLUMNS < host.count())
            next = index + COLUMNS;
        if (key == KeyEvent.KEYCODE_DPAD_UP && index >= COLUMNS) next = index - COLUMNS;
        if (next >= 0) {
            if (e.getRepeatCount() == 0 || e.getRepeatCount() % 2 == 0)
                host.moveTo(
                        next,
                        Integer.compare(next % COLUMNS, index % COLUMNS),
                        Integer.compare(next / COLUMNS, index / COLUMNS));
            return true;
        }
        return key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    @Override
    protected void onDetachedFromWindow() {
        movement.stop();
        super.onDetachedFromWindow();
    }
}
