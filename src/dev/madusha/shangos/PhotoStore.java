package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * "Your photo", the one wallpaper that is not built in: a picture pushed from a computer with
 * {@code shangos.sh wallpaper photo.jpg}. It lives in the app's private storage, so no media
 * permission, no cloud account and no network are involved. {@link PhotoProvider} receives it.
 */
final class PhotoStore {
    private PhotoStore() {}

    /** Larger files are refused. */
    static final long MAX_BYTES = 24L << 20;

    /** Told (on a background thread) when the photo was replaced or removed; may be null. */
    static volatile Runnable onChanged;

    static File file(Context context) {
        return new File(context.getFilesDir(), "photo.jpg");
    }

    /** Where a push is written; it only becomes the photo once it has been checked. */
    static File incoming(Context context) {
        return new File(context.getFilesDir(), "photo.part");
    }

    /** When the photo was stored, 0 if there is none: a newer photo replaces a shown one. */
    static long stamp(Context context) {
        return file(context).lastModified();
    }

    /** The photo scaled to about {@code targetWidth} pixels wide, or null when there is none. */
    static Bitmap decode(Context context, int targetWidth) {
        File f = file(context);
        if (!f.isFile() || f.length() > MAX_BYTES) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) return null;
                read += n;
            }
            return Images.decode(data, targetWidth, Bitmap.Config.ARGB_8888);
        } catch (IOException | RuntimeException e) {
            Dbg.d("photo", "read failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** A small picture for the wallpaper picker and the Settings preview. */
    static Bitmap thumbnail(Context context) {
        return decode(context, 240);
    }

    /**
     * A push has been written to {@link #incoming}: keeps it as the photo and chooses it as the
     * wallpaper if it is a picture of a sensible size, else throws it away and keeps the old one.
     */
    static boolean accept(Context context) {
        File part = incoming(context);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(part.getPath(), bounds);
            boolean ok =
                    part.length() > 0
                            && part.length() <= MAX_BYTES
                            && bounds.outWidth > 0
                            && bounds.outHeight > 0
                            && (long) bounds.outWidth * bounds.outHeight <= Images.MAX_PIXELS;
            if (!ok || !part.renameTo(file(context))) {
                // Not a picture, too big, or could not be kept: the old photo (if any) stays.
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                return false;
            }
        } catch (RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            return false;
        }
        Prefs prefs = Prefs.of(context);
        prefs.setHasPhoto(true);
        prefs.setWallpaper(Wallpaper.PHOTO);
        notifyChanged();
        return true;
    }

    /** Deletes the photo; if it was the wallpaper, the default look comes back. */
    static void remove(Context context) {
        //noinspection ResultOfMethodCallIgnored
        file(context).delete();
        Prefs prefs = Prefs.of(context);
        prefs.setHasPhoto(false);
        if (prefs.wallpaper() == Wallpaper.PHOTO) prefs.setWallpaper(Prefs.DEFAULT_WALLPAPER);
        notifyChanged();
    }

    private static void notifyChanged() {
        Runnable r = onChanged;
        if (r != null) r.run();
    }
}
