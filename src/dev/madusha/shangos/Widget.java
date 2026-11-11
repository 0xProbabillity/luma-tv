package dev.madusha.shangos;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/**
 * A Home widget: a glass card in the widget row (design units, like the rest of Home). Subclasses
 * add their own focusable items; WidgetBoard moves focus between them, across widgets and across
 * pages.
 */
abstract class Widget extends FrameLayout {
    final WidgetBoard.Host host;
    final float w;
    final Ui ui;
    boolean active;

    Widget(Context context, WidgetBoard.Host host, float width) {
        super(context);
        this.host = host;
        ui = new Ui(context, host.unit(), host.frost());
        w = width;
        setClipChildren(false);
        setClipToPadding(false);
        setBackground(restBackground());
    }

    /** The card at rest: frosted glass, or the plain translucent fill. */
    final Drawable restBackground() {
        return ui.glass(this, light(), 22);
    }

    /**
     * Starts or stops the widget's own work (listeners, timers); only the visible page is active.
     */
    void setActive(boolean value) {
        active = value;
    }

    /** Home came back from an app: re-read anything that may have changed meanwhile. */
    void refresh() {}

    boolean light() {
        return host.light();
    }

    boolean motion() {
        return host.motion();
    }

    int px(float v) {
        return ui.px(v);
    }

    int ink(int alpha) {
        return Theme.ink(light(), alpha);
    }

    /**
     * Colours of a focused control: a dark pill with white text on the pale wallpaper, a white pill
     * elsewhere.
     */
    int focusFill() {
        return Theme.focusFill(light());
    }

    int focusInk() {
        return Theme.focusInk(light());
    }

    int restFill() {
        return Theme.restFill(light());
    }

    LayoutParams pos(float width, float height, float x, float y) {
        return ui.pos(width, height, x, y);
    }

    TextView text(String value, float size, int color, String family) {
        return ui.line(value, size, color, family);
    }

    GradientDrawable round(int color, float radius) {
        return ui.shape(color, radius);
    }

    GradientDrawable oval(int color) {
        return ui.oval(color);
    }

    /** Makes a view a focusable control with the shared lift, sound and hero text. */
    void control(
            View v,
            int index,
            float scale,
            java.util.function.Consumer<Boolean> paint,
            java.util.function.Supplier<String[]> hero,
            Runnable click) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setSoundEffectsEnabled(false);
        v.setOnFocusChangeListener(
                (view, f) -> {
                    paint.accept(f);
                    v.animate().cancel();
                    float s = f ? scale : 1;
                    if (motion())
                        v.animate()
                                .scaleX(s)
                                .scaleY(s)
                                .setDuration(f ? 460 : 300)
                                .setInterpolator(f ? Motion.WIDGET : Motion.EASE)
                                .start();
                    else {
                        v.setScaleX(s);
                        v.setScaleY(s);
                    }
                    v.setElevation(px(f ? 12 : 0));
                    if (f) {
                        String[] h = hero.get();
                        host.hero(h[0], h[1], h[2]);
                        host.step(index);
                    }
                });
        v.setOnClickListener(
                view -> {
                    host.open();
                    press(v, scale);
                    click.run();
                });
        paint.accept(false);
    }

    /**
     * A tile in the style of Control Center: a round glyph, a name and a status line; the glyph
     * turns blue when on. Control and Inputs use it.
     */
    final class Tile extends FrameLayout {
        final View disc;
        final Glyph glyph;
        final TextView name, state;
        boolean focused, on;
        String key, detail = "";
        int tint = 0xFF0A84FF;

        Tile(String kind, String title, float tileW, float tileH, int index, Runnable action) {
            super(Widget.this.getContext());
            setClipChildren(false);
            key = title;
            disc = new View(getContext());
            addView(disc, pos(36, 36, 12, (tileH - 36) / 2));
            glyph = new Glyph(getContext()).kind(kind);
            addView(glyph, pos(20, 20, 20, (tileH - 20) / 2));
            name = text(title, 13.5f, ink(0xF2), "sans-serif-medium");
            addView(name, pos(tileW - 62, 17, 58, tileH / 2 - 17));
            state = text("", 11.5f, ink(0x99), "sans-serif");
            addView(state, pos(tileW - 62, 15, 58, tileH / 2 + 3));
            setPivotX(px(tileW / 2));
            setPivotY(px(tileH / 2));
            control(
                    this,
                    index,
                    1.06f,
                    f -> {
                        focused = f;
                        paint();
                    },
                    () -> new String[] {"tile:" + key, name.getText().toString(), detail},
                    action);
        }

        void paint() {
            setBackground(round(focused ? focusFill() : restFill(), 20));
            int discFill =
                    on
                            ? tint
                            : focused
                                    ? (light() ? 0x33FFFFFF : 0x1A000000)
                                    : (light() ? 0x14000000 : 0x24FFFFFF);
            disc.setBackground(oval(discFill));
            glyph.color(on ? Color.WHITE : focused ? focusInk() : ink(0xE6));
            name.setTextColor(focused ? focusInk() : ink(0xF2));
            state.setTextColor(focused ? (focusInk() & 0x00FFFFFF | 0xA6000000) : ink(0x99));
        }

        void set(String status, String hero, boolean active) {
            if (!status.contentEquals(state.getText())) state.setText(status);
            detail = hero;
            if (active != on) {
                on = active;
                paint();
            }
        }
    }

    void press(View v, float scale) {
        if (!motion()) return;
        v.animate().cancel();
        v.animate()
                .scaleX(scale * .96f)
                .scaleY(scale * .96f)
                .setDuration(70)
                .setInterpolator(Motion.EASE)
                .withEndAction(
                        () ->
                                v.animate()
                                        .scaleX(v.hasFocus() ? scale : 1)
                                        .scaleY(v.hasFocus() ? scale : 1)
                                        .setDuration(420)
                                        .setInterpolator(Motion.WIDGET)
                                        .start())
                .start();
    }
}
