package dev.madusha.shangos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Frosted glass without a blur at draw time. The wallpaper is blurred once into a tiny picture
 * (about 96 pixels wide) when it loads; a glass panel then draws the part of that picture that lies
 * behind it, with a veil and a hairline edge on top. Drawing one small bitmap costs almost nothing,
 * so it runs on weak TVs, and panels line up with the wallpaper because the picture is the
 * wallpaper.
 *
 * <p>When frost is off (Settings, Frosted glass, or the Light performance setting) the panels draw
 * the plain translucent fill they always had.
 */
final class Frost {
    /** Width of the blurred picture; its height follows the screen. */
    static final int WIDTH = 96;

    private volatile Bitmap bitmap;
    private volatile boolean enabled;
    private final ArrayList<WeakReference<Panel>> panels = new ArrayList<>();

    /** True when panels should draw the frosted picture. */
    boolean ready() {
        Bitmap b = bitmap;
        return enabled && b != null && !b.isRecycled();
    }

    /** The blurred wallpaper (main thread); panels redraw. */
    void set(Bitmap blurred) {
        bitmap = blurred;
        refresh();
    }

    void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        refresh();
    }

    private void refresh() {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i).get();
            if (p == null) panels.remove(i);
            else p.invalidateSelf();
        }
    }

    /**
     * A glass background for {@code view}: {@code plain} is the translucent fill without frost,
     * {@code veil} goes over the frosted picture, {@code stroke} is the hairline edge.
     */
    Drawable glass(View view, int plain, int veil, int stroke, float radius, float strokeWidth) {
        Panel p = new Panel(view, plain, veil, stroke, radius, strokeWidth);
        panels.add(new WeakReference<>(p));
        return p;
    }

    /** The wallpaper blurred into a {@link #WIDTH}-pixel-wide picture (software bitmap). */
    static Bitmap blurred(Bitmap wallpaper) {
        int w = WIDTH;
        int h = Math.max(1, Math.round(w * wallpaper.getHeight() / (float) wallpaper.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(wallpaper, w, h, true);
        Bitmap mutable = small.copy(Bitmap.Config.ARGB_8888, true);
        if (small != wallpaper && small != mutable) small.recycle();
        int[] px = new int[w * h];
        mutable.getPixels(px, 0, w, 0, 0, w, h);
        blur(px, w, h, 3, 3);
        mutable.setPixels(px, 0, w, 0, 0, w, h);
        return mutable;
    }

    /**
     * A box blur of opaque ARGB pixels, {@code passes} times (three of them look like a Gaussian).
     * Edges repeat. Pure, so it is unit tested.
     */
    static void blur(int[] px, int w, int h, int radius, int passes) {
        int[] tmp = new int[px.length];
        for (int i = 0; i < passes; i++) {
            line(px, tmp, w, h, radius, true);
            line(tmp, px, w, h, radius, false);
        }
    }

    /** One direction of the box blur: {@code src} to {@code dst}. */
    private static void line(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int length = horizontal ? w : h, lines = horizontal ? h : w;
        int step = horizontal ? 1 : w, stride = horizontal ? w : 1;
        int window = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * stride;
            long a = 0, rr = 0, g = 0, b = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[base + Math.max(0, Math.min(length - 1, i)) * step];
                a += c >>> 24;
                rr += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
            }
            for (int i = 0; i < length; i++) {
                dst[base + i * step] =
                        ((int) (a / window) << 24)
                                | ((int) (rr / window) << 16)
                                | ((int) (g / window) << 8)
                                | (int) (b / window);
                int out = src[base + Math.max(0, i - r) * step];
                int in = src[base + Math.min(length - 1, i + r + 1) * step];
                a += (in >>> 24) - (out >>> 24);
                rr += ((in >> 16) & 255) - ((out >> 16) & 255);
                g += ((in >> 8) & 255) - ((out >> 8) & 255);
                b += (in & 255) - (out & 255);
            }
        }
    }

    /** The glass drawable: the frosted picture behind the view, a veil, an edge. */
    private final class Panel extends Drawable {
        private final View view;
        private final int plain, veil, stroke;
        private final float radius, strokeWidth;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final Rect source = new Rect();
        private final int[] where = new int[2];

        Panel(View view, int plain, int veil, int stroke, float radius, float strokeWidth) {
            this.view = view;
            this.plain = plain;
            this.veil = veil;
            this.stroke = stroke;
            this.radius = radius;
            this.strokeWidth = strokeWidth;
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(strokeWidth);
            edge.setColor(stroke);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float inset = strokeWidth / 2;
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            path.reset();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            Bitmap picture = ready() ? bitmap : null;
            int sw = view.getRootView().getWidth(), sh = view.getRootView().getHeight();
            if (picture != null && sw > 0 && sh > 0 && canvas.isHardwareAccelerated()) {
                view.getLocationOnScreen(where);
                float kx = picture.getWidth() / (float) sw, ky = picture.getHeight() / (float) sh;
                source.set(
                        Math.round((where[0] + b.left) * kx),
                        Math.round((where[1] + b.top) * ky),
                        Math.round((where[0] + b.right) * kx),
                        Math.round((where[1] + b.bottom) * ky));
                source.intersect(0, 0, picture.getWidth(), picture.getHeight());
                if (!source.isEmpty()) {
                    canvas.save();
                    canvas.clipPath(path);
                    canvas.drawBitmap(picture, source, b, this.picture);
                    fill.setColor(veil);
                    canvas.drawPath(path, fill);
                    canvas.restore();
                    canvas.drawPath(path, edge);
                    return;
                }
            }
            fill.setColor(plain);
            canvas.drawPath(path, fill);
            canvas.drawPath(path, edge);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter filter) {}

        @Override
        @SuppressWarnings("deprecation") // abstract until API 29 and still has to be implemented
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
