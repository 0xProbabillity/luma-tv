package dev.madusha.shangos;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * The screen saver: a large quiet clock over a dimmed copy of the wallpaper. The clock moves to a
 * new place every minute, so no pixel stays lit (burn-in), and that move is the only thing that
 * ever animates. It uses no network and no permission: it shows the time, the date and the user's
 * own wallpaper. Chosen in the TV's screen saver settings, or by the setup script.
 */
public class AmbientDream extends DreamService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable tick = this::update;
    private FrameLayout root;
    private LinearLayout block;
    private TextView time, date;
    private Bitmap picture;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // any key press ends the screen saver
        setFullscreen(true);
        setScreenBright(false); // a screen saver should not be at full brightness
        build();
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        update();
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (picture != null) {
            picture.recycle();
            picture = null;
        }
        super.onDetachedFromWindow();
    }

    private void build() {
        int width = getResources().getDisplayMetrics().widthPixels;
        Ui ui = new Ui(this, width / 960f);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        Prefs prefs = Prefs.of(this);
        int choice = Wallpaper.choice(prefs);
        int variant =
                Wallpaper.variant(choice, new Daylight(this, prefs).isDay(System.currentTimeMillis()));
        picture = decode(Wallpaper.picture(variant), width);
        if (picture != null) {
            ImageView background = new ImageView(this);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            background.setImageBitmap(picture);
            // Dimmed for the dark room; a pale picture needs more, or it would light the whole
            // room.
            background.setColorFilter(
                    Wallpaper.isPale(variant) ? 0xC4000000 : 0x99000000, PorterDuff.Mode.SRC_OVER);
            root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        }

        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        time = ui.text("", 128, 0xF2FFFFFF, "sans-serif-light");
        time.setIncludeFontPadding(false);
        date = ui.text("", 28, 0xB3FFFFFF, "sans-serif");
        block.addView(time, new LinearLayout.LayoutParams(-2, -2));
        block.addView(date, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(block, where);
    }

    /** Decodes a picture no larger than the screen needs. */
    private Bitmap decode(int resource, int screenWidth) {
        if (resource == Wallpaper.PHOTO_PICTURE) return PhotoStore.decode(this, screenWidth);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(getResources(), resource, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= screenWidth) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeResource(getResources(), resource, options);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null; // a plain black screen with the clock is still a fine screen saver
        }
    }

    /**
     * Shows the time, moves the clock somewhere new, and schedules the next change for the start of
     * the next minute.
     */
    private void update() {
        long now = System.currentTimeMillis();
        time.setText(Formats.time(this, now));
        date.setText(Formats.date(now));
        float w = root.getWidth(), h = root.getHeight();
        if (w > 0 && h > 0) {
            block.animate()
                    .translationX((random.nextFloat() - .5f) * .30f * w)
                    .translationY((random.nextFloat() - .5f) * .36f * h)
                    .setDuration(1800)
                    .setInterpolator(Motion.SOFT)
                    .start();
        }
        handler.postDelayed(tick, 60_000 - now % 60_000 + 50);
    }
}
