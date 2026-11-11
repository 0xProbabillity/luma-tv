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

    Ui(Context context, float unit) {
        this.context = context;
        this.unit = unit;
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
