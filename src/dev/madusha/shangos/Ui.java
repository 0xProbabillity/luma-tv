package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * View helpers. Layout is in "design units": one unit is 1/960 of the screen width, so the design
 * scales to any TV.
 */
final class Ui {
    final Context context;
    final float unit;

    /** The blurred wallpaper behind glass panels; shared by every Ui of the launcher. */
    final Frost frost;

    Ui(Context context, float unit) {
        this(context, unit, new Frost());
    }

    Ui(Context context, float unit, Frost frost) {
        this.context = context;
        this.unit = unit;
        this.frost = frost;
    }

    int px(float units) {
        return Math.round(units * unit);
    }

    /**
     * Frame layout params in design units; a negative size is passed through (MATCH_PARENT /
     * WRAP_CONTENT).
     */
    FrameLayout.LayoutParams pos(float w, float h, float x, float y) {
        FrameLayout.LayoutParams lp =
                new FrameLayout.LayoutParams(w < 0 ? (int) w : px(w), h < 0 ? (int) h : px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        return lp;
    }

    GradientDrawable shape(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(radius));
        return d;
    }

    /**
     * The glass of a card or bar on the wallpaper: the frosted picture behind {@code view} with a
     * veil, or the plain translucent fill when frost is off. {@code light} is a pale wallpaper.
     */
    android.graphics.drawable.Drawable glass(android.view.View view, boolean light, float radius) {
        return frost.glass(
                view,
                light ? 0x8CFFFFFF : 0x1FFFFFFF,
                light ? 0x70FFFFFF : 0x30FFFFFF,
                light ? 0x14000000 : 0x1FFFFFFF,
                px(radius),
                Math.max(1, px(.8f)));
    }

    /** Dialogs and menus: always dark glass, frosted when it can be. */
    android.graphics.drawable.Drawable darkGlass(android.view.View view, float radius) {
        return frost.glass(
                view, Theme.PANEL, 0xD01C1C1E, 0x1FFFFFFF, px(radius), Math.max(1, px(.8f)));
    }

    /** A soft shadow under text on a wallpaper; none on a pale one, where it would smudge. */
    void shadow(TextView t, float radius, boolean light) {
        if (light) t.setShadowLayer(0, 0, 0, 0);
        else t.setShadowLayer(px(radius), 0, px(1), 0x55000000);
    }

    GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    TextView text(String value, float size, int color, String family) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** {@link #text} on a single line, ending in an ellipsis. */
    TextView line(String value, float size, int color, String family) {
        TextView t = text(value, size, color, family);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }
}
