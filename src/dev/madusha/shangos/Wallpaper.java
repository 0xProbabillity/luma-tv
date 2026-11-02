package dev.madusha.shangos;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import java.io.PrintWriter;
import java.util.Random;
import java.util.concurrent.Executor;

/**
 * The wallpaper: one picture in an ordinary hardware-accelerated view, with a slow drifting light
 * above it.
 *
 * <p>Why this shape (measured on a 1.4 GB stick): the previous engine redrew the whole picture ten
 * times a second into separate surface layers to move it about one pixel per second. That cost 4%
 * of a core while idle and slowed the focus animations. Here nothing is redrawn: the picture is a
 * texture, and every movement (parallax, the light's glides) is a RenderThread property animation
 * that needs no work on the UI thread. Glides come in bursts with pauses between them, and stop at
 * once on any key press, so they never compete with the UI.
 *
 * <p>Memory: the composed picture lives in graphics memory only (a HARDWARE bitmap, no CPU copy); a
 * replaced one is released after it has faded out; {@link #release()} drops everything when the
 * system is short of memory.
 */
final class Wallpaper extends FrameLayout {
    /**
     * What the wallpaper needs to know about the launcher around it. All calls come from the main
     * thread.
     */
    interface Host {
        /** The launcher is on screen and may animate (started, and not behind Settings). */
        boolean onScreen();

        /** Animation is allowed (the user's switch and the system's animation setting). */
        boolean motion();

        boolean ambientSetting();

        /** How much the launcher may animate on its own on this TV. */
        Hardware.Tier tier();

        long sinceInteractionMillis();

        /** The saved wallpaper choice (an index into {@link #LOOKS}). */
        int savedLook();

        /**
         * After sunrise and before sunset where the TV is (see Daylight): picks Daybreak's picture.
         */
        boolean isDay();

        boolean destroyed();
    }

    /** One wallpaper choice. A look may have a second picture for after sunset. */
    static final class Look {
        final String name;
        final int day, night, thumb, glow;
        final boolean paleDay, paleNight;

        Look(
                String name,
                int day,
                int night,
                int thumb,
                int glow,
                boolean paleDay,
                boolean paleNight) {
            this.name = name;
            this.day = day;
            this.night = night;
            this.thumb = thumb;
            this.glow = glow;
            this.paleDay = paleDay;
            this.paleNight = paleNight;
        }
    }

    static final Look[] LOOKS = {
        new Look(
                "Daybreak",
                R.drawable.wp_daybreak_day,
                R.drawable.wp_daybreak_night,
                R.drawable.thumb_0,
                0xFFFFC9A8,
                true,
                false),
        new Look("Aurora", R.drawable.wp_aurora, 0, R.drawable.thumb_1, 0xFF38FFC9, false, false),
        new Look("Ember", R.drawable.wp_ember, 0, R.drawable.thumb_2, 0xFFFFA81E, false, false),
        new Look("Tide", R.drawable.wp_tide, 0, R.drawable.thumb_3, 0xFF63EEF0, false, false),
        new Look("Orchid", R.drawable.wp_orchid, 0, R.drawable.thumb_4, 0xFFFF5CB0, false, false),
        new Look("Glacier", R.drawable.wp_glacier, 0, R.drawable.thumb_5, 0xFFFFFFFF, true, true),
        new Look(
                "Milky Way",
                R.drawable.wp_milkyway,
                0,
                R.drawable.thumb_6,
                0xFFBFD4FF,
                false,
                false),
        new Look("Calm Sea", R.drawable.wp_sea, 0, R.drawable.thumb_7, 0xFFFFD08A, false, false),
    };
    static final String[] NAMES = new String[LOOKS.length];
    static final int[] THUMBS = new int[LOOKS.length];

    static {
        for (int i = 0; i < LOOKS.length; i++) {
            NAMES[i] = LOOKS[i].name;
            THUMBS[i] = LOOKS[i].thumb;
        }
    }

    /**
     * The picture to show for a choice: the choice itself, or {@code choice + LOOKS.length} for a
     * look's night picture (which only some looks have).
     */
    static int variant(int choice, boolean day) {
        return LOOKS[choice].night != 0 && !day ? choice + LOOKS.length : choice;
    }

    private static boolean isNight(int variant) {
        return variant >= LOOKS.length;
    }

    private static Look look(int variant) {
        return LOOKS[variant % LOOKS.length];
    }

    /** The pale wallpaper: text and glass turn dark on it. */
    static boolean isPale(int variant) {
        return isNight(variant) ? look(variant).paleNight : look(variant).paleDay;
    }

    /** Fill behind the picture while it loads. */
    private static int base(int variant) {
        return isPale(variant) ? 0xFFE9EAF0 : 0xFF0B0B0D;
    }

    /** No glide starts, and a running one stops, within this long of a key press. */
    private static final long QUIET_MS = 2500;

    private final Host host;
    private final Executor loader;
    private final Ui ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final PictureView picture;
    private final FrameLayout glowHolder;
    private final View glow;
    volatile boolean ambientRunning;
    private int requestedVariant = -1;
    private int glides;
    private boolean released;
    private ValueAnimator fadeAnimator;

    private final Runnable nextGlide = this::glide;
    private final Runnable resume = this::syncAmbient;

    Wallpaper(Context context, Host host, Executor loader, Ui ui) {
        super(context);
        this.host = host;
        this.loader = loader;
        this.ui = ui;
        setClipChildren(false);
        setClipToPadding(false);
        int margin = ui.px(30);
        int width = getResources().getDisplayMetrics().widthPixels + margin * 2;
        int height = getResources().getDisplayMetrics().heightPixels + margin * 2;
        picture = new PictureView(context);
        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(width, height);
        full.leftMargin = -margin;
        full.topMargin = -margin;
        addView(picture, full);
        // The light sits in a holder so parallax can move the holder while a glide moves the light
        // inside it.
        glowHolder = new FrameLayout(context);
        glowHolder.setClipChildren(false);
        addView(glowHolder, new FrameLayout.LayoutParams(width, height));
        glow = new View(context);
        int size = Math.round(width * .85f);
        FrameLayout.LayoutParams light = new FrameLayout.LayoutParams(size, size);
        light.leftMargin = (width - size) / 2 - margin;
        light.topMargin = (height - size) / 2 - margin;
        glowHolder.addView(glow, light);
        glow.setAlpha(0);
        update();
    }

    // ---- Parallax: the picture and the light shift a little as focus moves along the row ----
    void follow(int index) {
        picture.animate().cancel();
        glowHolder.animate().cancel();
        float position = (index % 6 - 2.5f) / 2.5f;
        float arc = 1 - position * position;
        if (host.motion()) {
            picture.animate()
                    .translationX(-position * ui.px(11))
                    .translationY(ui.px(arc * 2.2f))
                    .setDuration(1000)
                    .setInterpolator(Motion.DRIFT)
                    .start();
            glowHolder
                    .animate()
                    .translationX(-position * ui.px(20))
                    .translationY(ui.px(arc * 3f))
                    .setDuration(1000)
                    .setInterpolator(Motion.DRIFT)
                    .start();
        } else rest();
    }

    void rest() {
        picture.animate().cancel();
        glowHolder.animate().cancel();
        picture.setTranslationX(0);
        picture.setTranslationY(0);
        glowHolder.setTranslationX(0);
        glowHolder.setTranslationY(0);
    }

    // ---- Ambient light ----
    private boolean idle() {
        long limit = host.tier().idleLimitMillis();
        return limit > 0 && host.sinceInteractionMillis() > limit;
    }

    private boolean ambientEligible() {
        return host.tier().animates()
                && host.onScreen()
                && hasWindowFocus()
                && host.motion()
                && host.ambientSetting()
                && picture.scene != null
                && !idle()
                && host.sinceInteractionMillis() > QUIET_MS;
    }

    /**
     * Starts or stops the glides to match the situation: on screen, allowed, and nobody pressing
     * keys.
     */
    void syncAmbient() {
        if (ambientEligible()) {
            if (!ambientRunning) {
                ambientRunning = true;
                main.removeCallbacks(nextGlide);
                main.post(nextGlide);
            }
        } else {
            pauseAmbient();
            if (!host.tier().animates() || !host.motion() || !host.ambientSetting() || idle()) {
                fadeGlowOut();
            } else if (host.onScreen()) {
                // Only the quiet period is missing: make sure something starts the glides again
                // when it is over.
                long remaining = QUIET_MS - host.sinceInteractionMillis();
                if (remaining > 0) {
                    main.removeCallbacks(resume);
                    main.postDelayed(resume, remaining + 150);
                }
            }
        }
    }

    void pauseAmbient() {
        ambientRunning = false;
        main.removeCallbacks(nextGlide);
        glow.animate().cancel();
    }

    /** The activity is going away: no callback may run afterwards. */
    void shutdown() {
        pauseAmbient();
        main.removeCallbacksAndMessages(null);
    }

    /**
     * Any key press: the light stops where it is (so it cannot compete with the focus animations)
     * and restarts after a quiet moment.
     */
    void onInteraction() {
        if (ambientRunning) pauseAmbient();
        main.removeCallbacks(resume);
        main.postDelayed(resume, QUIET_MS + 150);
    }

    private void fadeGlowOut() {
        if (glow.getAlpha() > 0) glow.animate().alpha(0).setDuration(1500).start();
    }

    /** One slow wander of the light to a new place and brightness, then a pause. */
    private void glide() {
        if (!ambientEligible()) {
            syncAmbient();
            return;
        }
        Hardware.Tier tier = host.tier();
        float w = getWidth(), h = getHeight();
        glow.animate()
                .translationX((random.nextFloat() - .5f) * .30f * w)
                .translationY((random.nextFloat() - .5f) * .22f * h)
                .alpha(.10f + random.nextFloat() * .10f)
                .setDuration(tier == Hardware.Tier.QUALITY ? 10_000 : 8_000)
                .setInterpolator(Motion.SOFT)
                .withEndAction(
                        () -> {
                            glides++;
                            long pause =
                                    tier == Hardware.Tier.QUALITY
                                            ? 3_000 + random.nextInt(3_000)
                                            : 9_000 + random.nextInt(9_000);
                            main.postDelayed(nextGlide, pause);
                        })
                .start();
    }

    // ---- Pictures ----
    void update() {
        show(host.savedLook());
    }

    /** Shows a wallpaper without saving it (the picker previews live). */
    void show(int choice) {
        final int variant = variant(Math.max(0, Math.min(LOOKS.length - 1, choice)), host.isDay());
        if (requestedVariant == variant) return;
        requestedVariant = variant;
        released = false;
        final int width = picture.getLayoutParams().width,
                height = picture.getLayoutParams().height;
        loader.execute(
                () -> {
                    // Moving through the picker queues one job per thumbnail passed: only the last
                    // is worth decoding.
                    if (host.destroyed() || requestedVariant != variant) return;
                    Bitmap scene = load(variant, width, height);
                    main.post(() -> apply(variant, scene));
                });
    }

    private Bitmap load(int variant, int width, int height) {
        Look look = look(variant);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap source =
                BitmapFactory.decodeResource(
                        getResources(), isNight(variant) ? look.night : look.day, options);
        Bitmap composed = compose(source, isPale(variant), width, height);
        source.recycle();
        // A HARDWARE bitmap lives in graphics memory only: no second copy in the app's own memory.
        Bitmap gpu = composed.copy(Bitmap.Config.HARDWARE, false);
        if (gpu == null) return composed;
        composed.recycle();
        return gpu;
    }

    private void apply(int variant, Bitmap scene) {
        if (host.destroyed() || requestedVariant != variant) {
            scene.recycle();
            return;
        }
        Look look = look(variant);
        picture.base = base(variant);
        GradientDrawable light = new GradientDrawable();
        light.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        light.setGradientRadius(glow.getLayoutParams().width / 2f);
        light.setColors(new int[] {look.glow, look.glow & 0x00FFFFFF});
        glow.setBackground(light);
        if (fadeAnimator != null)
            fadeAnimator.cancel(); // its end listener releases the picture that was fading out
        picture.previous = picture.scene;
        picture.scene = scene;
        if (picture.previous != null && host.motion()) {
            fadeAnimator = ValueAnimator.ofFloat(0, 1);
            fadeAnimator.setDuration(420);
            fadeAnimator.setInterpolator(Motion.EASE);
            fadeAnimator.addUpdateListener(
                    a -> {
                        picture.fade = (float) a.getAnimatedValue();
                        picture.invalidate();
                    });
            fadeAnimator.addListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator a) {
                            finishFade();
                        }
                    });
            fadeAnimator.start();
        } else finishFade();
        syncAmbient();
    }

    /** The fade is over (or was cut short): show the new picture alone and let the old one go. */
    private void finishFade() {
        Bitmap old = picture.previous;
        picture.previous = null;
        picture.fade = 1;
        picture.invalidate();
        letGo(old);
    }

    /**
     * Frees a bitmap a moment later: the render thread may still be drawing the frame that used it.
     */
    private void letGo(Bitmap bitmap) {
        if (bitmap != null)
            main.postDelayed(
                    () -> {
                        if (!bitmap.isRecycled()) bitmap.recycle();
                    },
                    600);
    }

    /**
     * Frees every picture (the system wants memory back). {@link #restore} brings it back when Home
     * is shown again.
     */
    void release() {
        pauseAmbient();
        if (fadeAnimator != null) fadeAnimator.cancel();
        Bitmap current = picture.scene;
        picture.scene = null;
        finishFade();
        letGo(current);
        requestedVariant = -1;
        released = true;
    }

    void restore() {
        if (released) update();
    }

    void dump(PrintWriter writer, String prefix, String page) {
        writer.println(
                prefix
                        + "shangOS wallpaper: variant="
                        + requestedVariant
                        + " tier="
                        + host.tier()
                        + " running="
                        + ambientRunning
                        + " glides="
                        + glides
                        + " page="
                        + page
                        + " released="
                        + released);
    }

    /**
     * The picture: a bitmap scaled to cover the view, with the previous one fading out beneath it.
     */
    private static final class PictureView extends View {
        volatile Bitmap scene, previous;
        float fade = 1;
        int base = 0xFF0B0B0D;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF bounds = new RectF();

        PictureView(Context context) {
            super(context);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(base);
            // A GPU-only bitmap cannot be drawn on a software canvas (an in-process snapshot): show
            // the plain fill instead of crashing.
            if (!canvas.isHardwareAccelerated()) return;
            bounds.set(0, 0, getWidth(), getHeight());
            Bitmap now = scene, before = previous;
            if (before != null && !before.isRecycled() && fade < 1) {
                paint.setAlpha(255);
                canvas.drawBitmap(before, null, bounds, paint);
            }
            if (now != null && !now.isRecycled()) {
                paint.setAlpha(before != null && fade < 1 ? Math.round(255 * fade) : 255);
                canvas.drawBitmap(now, null, bounds, paint);
            }
        }
    }

    /**
     * Scales the picture to cover the target (cropping the excess) and adds the veil that keeps the
     * UI's text legible.
     */
    private static Bitmap compose(Bitmap source, boolean pale, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float scale =
                Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        float w = source.getWidth() * scale, h = source.getHeight() * scale;
        c.drawBitmap(
                source,
                null,
                new RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2),
                paint);
        if (pale) {
            // A white veil behind the greeting and the favourites keeps dark text crisp on a pale
            // picture.
            paint.setShader(
                    new LinearGradient(
                            0,
                            0,
                            width * .75f,
                            0,
                            new int[] {0x8CF6F6F8, 0x33F6F6F8, 0x00F6F6F8},
                            new float[] {0, .5f, 1},
                            Shader.TileMode.CLAMP));
            c.drawRect(0, 0, width, height, paint);
            paint.setShader(
                    new LinearGradient(
                            0,
                            0,
                            0,
                            height,
                            new int[] {0x00F2F2F5, 0x00F2F2F5, 0x59F2F2F5},
                            new float[] {0, .55f, 1},
                            Shader.TileMode.CLAMP));
            c.drawRect(0, 0, width, height, paint);
        } else {
            paint.setShader(
                    new LinearGradient(
                            0,
                            0,
                            width * .88f,
                            0,
                            new int[] {0x88000000, 0x28000000, 0x00000000},
                            new float[] {0, .5f, 1},
                            Shader.TileMode.CLAMP));
            c.drawRect(0, 0, width, height, paint);
            paint.setShader(
                    new LinearGradient(
                            0,
                            0,
                            0,
                            height,
                            new int[] {0x30000000, 0x06000000, 0x70000000, 0xA8000000},
                            new float[] {0, .3f, .68f, 1},
                            Shader.TileMode.CLAMP));
            c.drawRect(0, 0, width, height, paint);
        }
        return result;
    }
}
