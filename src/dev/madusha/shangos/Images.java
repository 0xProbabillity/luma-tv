package dev.madusha.shangos;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Safe bitmap decoding: sampled to the size it is drawn at, and refusing absurd dimensions. */
final class Images {
    private Images() {}

    /**
     * Largest picture (in pixels) that will be decoded at all: guards against decompression bombs.
     */
    static final long MAX_PIXELS = 16_000_000L;

    /**
     * Decodes {@code data} no wider than needed for {@code targetWidth}; null when unreadable or
     * too large.
     */
    static Bitmap decode(byte[] data, int targetWidth, Bitmap.Config config) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0
                    || bounds.outHeight <= 0
                    || (long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= Math.max(1, targetWidth)) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            if (config != null) o.inPreferredConfig = config;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (RuntimeException | OutOfMemoryError e) {
            Dbg.d("images", "decode failed: " + e.getClass().getSimpleName());
            return null;
        }
    }
}
