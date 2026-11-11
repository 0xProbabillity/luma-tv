package dev.madusha.shangos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

/**
 * The soft picture behind the hero when an app has something to resume: the programme's artwork,
 * fading out to the left (where the text is) and downwards (into the wallpaper), so it needs no
 * overlay views and no blur at draw time. Made once per programme from a small poster.
 */
final class Backdrop {
    private Backdrop() {}

    /** How much of the width, from the left, stays clear for the text. */
    static final float CLEAR = .32f;

    /**
     * {@code poster} scaled to cover {@code width} x {@code height} (cropped in the middle), with
     * its alpha faded left to right and top to bottom.
     */
    static Bitmap make(Bitmap poster, int width, int height) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float scale =
                Math.max(width / (float) poster.getWidth(), height / (float) poster.getHeight());
        float w = poster.getWidth() * scale, h = poster.getHeight() * scale;
        c.save();
        c.translate((width - w) / 2f, (height - h) / 2f);
        c.scale(scale, scale);
        c.drawBitmap(poster, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        c.restore();
        Paint mask = new Paint();
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mask.setShader(
                new LinearGradient(
                        0,
                        0,
                        width,
                        0,
                        new int[] {0x00000000, 0x00000000, 0xFF000000},
                        new float[] {0, CLEAR, 1},
                        Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, mask);
        mask.setShader(
                new LinearGradient(
                        0,
                        0,
                        0,
                        height,
                        new int[] {0xFF000000, 0xB3000000, 0x00000000},
                        new float[] {0, .45f, 1},
                        Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, mask);
        return out;
    }
}
