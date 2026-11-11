package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Programme pictures for Up Next and the hero. content: and android.resource: links always work;
 * https: only when the user allows remote posters (Settings, Privacy). Everything goes through
 * {@link Net}, which refuses all else. Decoded pictures are kept for a while: Home rebuilds often,
 * and posters rarely change.
 */
final class Posters {
    private Posters() {}

    private static final LinkedHashMap<String, Bitmap> CACHE =
            new LinkedHashMap<String, Bitmap>(16, .75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
                    return size() > 12;
                }
            };

    /** The picture if it is already decoded, else null. */
    static Bitmap cached(String link) {
        synchronized (CACHE) {
            return CACHE.get(link);
        }
    }

    /**
     * Loads {@code link} off the main thread, scaled to {@code width} pixels. {@code done} runs on
     * the main thread (through {@code on}) with the picture; it does not run when loading fails.
     */
    static void load(
            Context context,
            Prefs prefs,
            View on,
            String link,
            int width,
            Consumer<Bitmap> done) {
        final Context app = context.getApplicationContext();
        WidgetBoard.IO.execute(
                () -> {
                    Bitmap b = null;
                    try {
                        byte[] data =
                                link.startsWith("res:")
                                        ? resource(app, link)
                                        : Net.image(app, prefs, link);
                        b = Images.decode(data, width, null);
                    } catch (Exception e) {
                        Dbg.d("posters", "poster: " + e.getClass().getSimpleName());
                    }
                    final Bitmap result = b;
                    if (result != null)
                        on.post(
                                () -> {
                                    synchronized (CACHE) {
                                        CACHE.put(link, result);
                                    }
                                    done.accept(result);
                                });
                });
    }

    /** The promo demo's pictures (debug builds only): a raw resource id after "res:". */
    private static byte[] resource(Context context, String link) throws Exception {
        try (InputStream s =
                        context.getResources().openRawResource(Integer.parseInt(link.substring(4)));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
