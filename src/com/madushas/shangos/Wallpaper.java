package com.madushas.shangos;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import java.io.PrintWriter;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The wallpaper: independent compositor surfaces (a drifting sky behind a cut-out landscape, or one full-height
 * photo) drawn on their own thread, so the launcher's UI thread never waits for them.
 * <p>Memory: a replaced picture is recycled as soon as it has faded out; the Fjord source images are decoded, composed
 * and freed (never kept); {@link #release()} drops everything when the system is short of memory.
 */
final class Wallpaper extends FrameLayout {
    /** What the wallpaper needs to know about the launcher around it. All calls come from the main thread. */
    interface Host {
        /** The launcher is on screen and may animate (started, and not behind Settings). */
        boolean onScreen();
        /** Animation is allowed (the user's switch and the system's animation setting). */
        boolean motion();
        boolean ambientSetting();
        /** The Light performance profile: no ambient animation at all. */
        boolean lightProfile();
        /** The ambient animation pauses after this long without a key press; 0 means never. */
        long idleLimitMillis();
        long sinceInteractionMillis();
        /** The saved wallpaper choice (an index into {@link #NAMES}). */
        int savedStyle();
        /** Resolves a choice to the picture to show: Silver is one choice with a day and a night picture. */
        int variant(int choice);
        boolean destroyed();
    }

    static final String[] NAMES = {"Fjord", "Dusk", "Graphite", "Milky Way", "Calm Sea", "Dawn", "Silk", "Silver"};
    /** Silver is one choice with two pictures: SILVER by day, SILVER_NIGHT after sunset (see Host.variant). */
    static final int SILVER = 7, SILVER_NIGHT = 8;
    static final int[] PHOTOS = {R.drawable.wp_milkyway, R.drawable.wp_sea, R.drawable.wp_dawn, R.drawable.wp_silk, R.drawable.wp_silver_day, R.drawable.wp_silver_night};
    static final int[] THUMBS = {R.drawable.thumb_0, R.drawable.thumb_1, R.drawable.thumb_2, R.drawable.thumb_3, R.drawable.thumb_4, R.drawable.thumb_5, R.drawable.thumb_6, R.drawable.thumb_7};
    /** The pale wallpaper: text and glass turn dark on it. */
    static boolean isPale(int variant) { return variant == SILVER; }
    /** Fill under the picture: pale silver, graphite, or the night blue of the original scenes. */
    static int base(int style) { return isPale(style) ? 0xFFE4E5E8 : style >= SILVER ? 0xFF0B0B0D : 0xFF080C14; }

    private final Host host;
    private final Executor loader;
    private final Ui ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    final HandlerThread surfaceThread = new HandlerThread("shangOS scenery", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler surfaceHandler;
    private final SceneLayer sky, terrain;
    private final FrameLayout skyTrack;
    volatile boolean ambientRunning;
    volatile float ambientSeconds, cloudX, cloudY;
    long lastAmbientTick, ambientTicks;
    int requestedStyle = -1;
    volatile float blend = 1;
    private ValueAnimator sceneTransition;
    private boolean released;

    private final Runnable ambientTick = new Runnable() { public void run() {
        if (!ambientEligible()) { syncAmbient(); return; }
        long now = SystemClock.uptimeMillis();
        ambientSeconds += Math.min(.25f, (now - lastAmbientTick) / 1000f); lastAmbientTick = now;
        cloudX = ui.px(14) * (float) Math.sin(ambientSeconds * Math.PI * 2 / 112);
        cloudY = ui.px(.65f) * (float) Math.sin(ambientSeconds * Math.PI * 2 / 83);
        if (blend >= 1) { sky.drawSurface(); terrain.drawSurface(); ambientTicks++; }
        main.postDelayed(this, 100);
    }};

    Wallpaper(Context context, Host host, Executor loader, Ui ui) {
        super(context);
        this.host = host; this.loader = loader; this.ui = ui;
        surfaceThread.start(); surfaceHandler = new Handler(surfaceThread.getLooper());
        setClipChildren(false); setClipToPadding(false);
        sky = new SceneLayer(false); terrain = new SceneLayer(true);
        skyTrack = new FrameLayout(context); skyTrack.setClipChildren(false); skyTrack.setClipToPadding(false);
        skyTrack.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        int margin = ui.px(30);
        int width = getResources().getDisplayMetrics().widthPixels + margin * 2;
        int height = getResources().getDisplayMetrics().heightPixels + margin * 2;
        FrameLayout.LayoutParams back = new FrameLayout.LayoutParams(width, ui.px(220) + margin);
        back.leftMargin = -margin; back.topMargin = -margin; addView(skyTrack, back);
        FrameLayout.LayoutParams front = new FrameLayout.LayoutParams(width, height);
        front.leftMargin = -margin; front.topMargin = -margin; addView(terrain, front);
        update();
    }

    // ---- Parallax ----
    void follow(int index) {
        skyTrack.animate().cancel(); terrain.animate().cancel();
        float position = (index % 6 - 2.5f) / 2.5f;
        float arc = 1 - position * position;
        if (host.motion()) {
            skyTrack.animate().translationX(-position * ui.px(requestedStyle >= 3 ? 11 : 5)).translationY(ui.px(arc * (requestedStyle >= 3 ? 2.2f : .7f))).setDuration(1000).setInterpolator(Motion.DRIFT).start();
            terrain.animate().translationX(-position * ui.px(22)).translationY(ui.px(arc * 3 - Math.min(index / 6, 2) * 2)).setDuration(680).setInterpolator(Motion.DRIFT).start();
        } else rest();
    }
    void rest() {
        skyTrack.animate().cancel(); terrain.animate().cancel();
        skyTrack.setTranslationX(0); skyTrack.setTranslationY(0); terrain.setTranslationX(0); terrain.setTranslationY(0);
    }

    // ---- Ambient drift ----
    private boolean idle() { long limit = host.idleLimitMillis(); return limit > 0 && host.sinceInteractionMillis() > limit; }
    private boolean ambientEligible() {
        return host.onScreen() && hasWindowFocus() && requestedStyle != 2 && host.motion() && host.ambientSetting() && !host.lightProfile()
            && !idle() && sky.scene != null && terrain.scene != null;
    }
    /** Starts or stops the drift to match the situation: on screen, focused, allowed, and the viewer is not away. */
    void syncAmbient() {
        if (ambientEligible()) {
            if (!ambientRunning) { ambientRunning = true; lastAmbientTick = SystemClock.uptimeMillis(); main.postDelayed(ambientTick, 100); }
        } else {
            pauseAmbient();
            if (!host.motion() || !host.ambientSetting() || host.lightProfile() || requestedStyle == 2) { cloudX = 0; cloudY = 0; sky.drawSurface(); terrain.drawSurface(); }
        }
    }
    void pauseAmbient() { ambientRunning = false; main.removeCallbacks(ambientTick); }

    // ---- Pictures ----
    private Bitmap compose(Bitmap source, int style, boolean foreground, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (!foreground || style == 2) c.drawColor(base(style));
        if (source != null && style != 2) {
            // Shared destination bounds keep the horizon registered; only the terrain has alpha.
            // Normalize almost-opaque generated terrain pixels, preserving antialiased skyline edges.
            // Otherwise a moving sky faintly shines through the mountain interiors.
            if (foreground) paint.setColorFilter(new ColorMatrixColorFilter(new float[]{1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1.04f,0}));
            c.drawBitmap(source, null, new RectF(0, 0, width, height), paint); paint.setColorFilter(null);
            if (foreground) paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            if (style == 1) { paint.setColor(0x504B3159); c.drawRect(0, 0, width, height, paint); paint.setAlpha(255); }
        }
        if (foreground) paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        if (isPale(style)) {
            // A white veil behind the greeting and the favourites keeps dark text crisp on the pale silk.
            paint.setShader(new LinearGradient(0, 0, width * .75f, 0, new int[]{0x8CF6F6F8, 0x33F6F6F8, 0x00F6F6F8}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP)); c.drawRect(0, 0, width, height, paint);
            paint.setShader(new LinearGradient(0, 0, 0, height, new int[]{0x00F2F2F5, 0x00F2F2F5, 0x59F2F2F5}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP)); c.drawRect(0, 0, width, height, paint);
        } else {
            // Silver's night picture is neutral graphite: its shading is black, not the blue of the older scenes.
            int tint = style >= SILVER ? 0x000000 : 0x080D18;
            paint.setShader(new LinearGradient(0, 0, width * .88f, 0, new int[]{0xA0000000 | tint, 0x30000000 | tint, tint}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP)); c.drawRect(0, 0, width, height, paint);
            paint.setShader(new LinearGradient(0, 0, 0, height, new int[]{0x40000000 | tint, 0x08000000 | tint, 0x88000000 | tint, (style >= SILVER ? 0xC8000000 : 0xE8000000) | tint}, new float[]{0, .3f, .68f, 1}, Shader.TileMode.CLAMP)); c.drawRect(0, 0, width, height, paint);
        }
        return result;
    }

    void update() { show(host.savedStyle()); }

    /** Shows a wallpaper without saving it (the picker previews live). {@code choice} may be a variant already. */
    void show(int choice) {
        final int style = host.variant(choice);
        if (requestedStyle == style) return;
        requestedStyle = style; released = false;
        final int width = getResources().getDisplayMetrics().widthPixels;
        final int height = getResources().getDisplayMetrics().heightPixels;
        loader.execute(() -> {
            // Moving through the picker queues one job per thumbnail passed: only the last one is worth decoding.
            if (host.destroyed() || requestedStyle != style) return;
            Bitmap back, front;
            if (style >= 3) {
                // Photographic and rendered wallpapers: one full-height layer, no cut-out terrain.
                BitmapFactory.Options full = new BitmapFactory.Options(); full.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap photo = BitmapFactory.decodeResource(getResources(), PHOTOS[Math.min(PHOTOS.length - 1, style - 3)], full);
                back = compose(photo, style, false, width, height); photo.recycle();
                front = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            } else {
                BitmapFactory.Options rgb = new BitmapFactory.Options(); rgb.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap skySource = BitmapFactory.decodeResource(getResources(), R.drawable.fjord_sky, rgb);
                Bitmap terrainSource = BitmapFactory.decodeResource(getResources(), R.drawable.fjord_terrain);
                back = compose(skySource, style, false, width, height);
                front = compose(terrainSource, style, true, width, height);
                skySource.recycle(); terrainSource.recycle(); // composed: the sources are not needed again
            }
            main.post(() -> {
                if (host.destroyed() || requestedStyle != style) { back.recycle(); front.recycle(); return; }
                // Photos and renders have no cut-out terrain: drop its full-screen surface (about 28 MB of buffers).
                terrain.setVisibility(style >= 3 ? View.GONE : View.VISIBLE);
                FrameLayout.LayoutParams track = (FrameLayout.LayoutParams) skyTrack.getLayoutParams();
                int depth = style >= 3 ? getResources().getDisplayMetrics().heightPixels + ui.px(60) : ui.px(220) + ui.px(30);
                if (track.height != depth) { track.height = depth; skyTrack.setLayoutParams(track); }
                if (sceneTransition != null) sceneTransition.cancel(); // its end listener frees the picture that was fading out
                sky.previous = sky.scene; terrain.previous = terrain.scene; sky.scene = back; terrain.scene = front;
                if (sky.previous != null && host.motion()) {
                    sceneTransition = ValueAnimator.ofFloat(0, 1); sceneTransition.setDuration(420); sceneTransition.setInterpolator(Motion.EASE);
                    sceneTransition.addUpdateListener(a -> { blend = (float) a.getAnimatedValue(); sky.drawSurface(); terrain.drawSurface(); });
                    sceneTransition.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { finishTransition(); } });
                    sceneTransition.start();
                } else finishTransition();
                syncAmbient();
            });
        });
    }

    /** The fade is over (or was cut short): show the new picture alone and free the old one. */
    private void finishTransition() {
        Bitmap oldSky = sky.previous, oldTerrain = terrain.previous;
        sky.previous = null; terrain.previous = null; blend = 1;
        drop(oldSky); drop(oldTerrain);
        sky.drawSurface(); terrain.drawSurface();
    }

    /** Recycles on the drawing thread, after any draw already queued there that might still be using it. */
    private void drop(Bitmap bitmap) { if (bitmap != null) surfaceHandler.post(bitmap::recycle); }

    /** Frees every picture (the system wants memory back). {@link #restore} brings it back when Home is shown again. */
    void release() {
        pauseAmbient();
        if (sceneTransition != null) sceneTransition.cancel();
        Bitmap a = sky.scene, b = terrain.scene;
        sky.scene = null; terrain.scene = null; finishTransition();
        drop(a); drop(b);
        requestedStyle = -1; released = true;
    }
    void restore() { if (released) update(); }

    void dump(PrintWriter writer, String prefix, String page) {
        writer.println(prefix + "shangOS ambient: running=" + ambientRunning + " ticks=" + ambientTicks + " seconds=" + ambientSeconds + " style=" + requestedStyle + " page=" + page + " released=" + released);
    }

    private final class SceneLayer extends SurfaceView implements SurfaceHolder.Callback {
        final boolean foreground;
        volatile Bitmap scene, previous;
        final AtomicBoolean drawPending = new AtomicBoolean();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF bounds = new RectF();
        WaterMesh water;
        int waterWidth, waterHeight;
        SceneLayer(boolean front) {
            super(Wallpaper.this.getContext()); foreground = front;
            if (front) { setZOrderMediaOverlay(true); getHolder().setFormat(PixelFormat.TRANSLUCENT); }
            getHolder().addCallback(this);
        }
        @Override public void surfaceCreated(SurfaceHolder holder) { drawSurface(); }
        @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { drawSurface(); }
        @Override public void surfaceDestroyed(SurfaceHolder holder) { animate().cancel(); }
        void drawSurface() {
            if (!drawPending.compareAndSet(false, true)) return;
            surfaceHandler.post(() -> { try { drawSurfaceNow(); } finally { drawPending.set(false); } });
        }
        void drawSurfaceNow() {
            if (!getHolder().getSurface().isValid()) return;
            // One read of each field: the main thread swaps and clears them while this thread draws.
            final Bitmap current = scene, fading = previous;
            Canvas c = null;
            try {
                c = getHolder().lockHardwareCanvas(); if (c == null) return;
                c.drawColor(foreground && current != null ? Color.TRANSPARENT : base(requestedStyle), PorterDuff.Mode.SRC);
                if (!foreground) c.translate(cloudX, cloudY);
                // Sky uses a shallow surface, but the same virtual full-scene crop as terrain.
                bounds.set(0, 0, c.getWidth(), getResources().getDisplayMetrics().heightPixels + ui.px(60));
                float fade = blend;
                if (fading != null && !fading.isRecycled() && fade < 1) {
                    paint.setAlpha(Math.round(255 * (1 - fade))); c.drawBitmap(fading, null, bounds, paint);
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); paint.setAlpha(Math.round(255 * fade));
                }
                if (current != null && !current.isRecycled()) {
                    if (foreground && ambientRunning && fading == null && fade >= 1 && requestedStyle < 2) {
                        if (water == null || waterWidth != c.getWidth() || waterHeight != (int) bounds.height()) {
                            waterWidth = c.getWidth(); waterHeight = (int) bounds.height(); water = new WaterMesh(waterWidth, waterHeight, ui.unit);
                        }
                        water.draw(c, current, ambientSeconds, paint);
                    } else c.drawBitmap(current, null, bounds, paint);
                }
                paint.setXfermode(null); paint.setAlpha(255);
            } catch (IllegalStateException | IllegalArgumentException ignored) { /* the surface may disappear while the TV sleeps */ }
            finally { if (c != null) try { getHolder().unlockCanvasAndPost(c); } catch (RuntimeException ignored) {} }
        }
    }
}
