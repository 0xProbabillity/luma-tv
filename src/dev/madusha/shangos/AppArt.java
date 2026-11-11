package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * A tile's picture. Every app gets the same treatment, so Home looks like one design and needs no
 * list of known apps: a glass card tinted from the app's own icon, with its TV banner filling it
 * or, without one, its icon on a soft light. Pictures come from {@link AppRepo}, decoded off the
 * main thread; until they arrive only the tint shows.
 */
final class AppArt extends View {
    private final Ui ui;
    private final AppRepo repo;
    final App app;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private final Runnable redraw = this::invalidate;
    private Shader tintShader, sheen, glow;
    private int tintedWith = Integer.MIN_VALUE;

    AppArt(Context context, Ui ui, AppRepo repo, App app) {
        super(context);
        this.ui = ui;
        this.repo = repo;
        this.app = app;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        tintedWith = Integer.MIN_VALUE; // the shaders depend on the size
        sheen =
                new LinearGradient(
                        0,
                        0,
                        0,
                        h,
                        new int[] {0x22FFFFFF, 0x00FFFFFF, 0x16000000},
                        new float[] {0, .46f, 1},
                        Shader.TileMode.CLAMP);
        glow =
                new RadialGradient(
                        w / 2f, h / 2f, h * .8f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), radius = ui.px(16);
        int accent = repo.accent(app, redraw);
        if (accent != tintedWith) {
            int[] colours = Tint.gradient(accent);
            tintShader =
                    new LinearGradient(0, 0, 0, h, colours[0], colours[1], Shader.TileMode.CLAMP);
            tintedWith = accent;
        }
        bounds.set(0, 0, w, h);
        paint.setShader(tintShader);
        c.drawRoundRect(bounds, radius, radius, paint);
        if (repo.hasBanner(app)) {
            // The banner is decoded on first use; until it arrives the tile shows only its tint (no
            // icon that then jumps).
            Bitmap banner = repo.banner(app, redraw);
            if (banner != null) drawFitted(c, banner, w, h, 1f);
        } else {
            paint.setShader(glow);
            c.drawRoundRect(bounds, radius, radius, paint);
            Bitmap icon = repo.icon(app, redraw);
            if (icon != null) drawFitted(c, icon, ui.px(48), ui.px(48), 1f);
        }
        paint.setShader(sheen);
        bounds.set(0, 0, w, h);
        c.drawRoundRect(bounds, radius, radius, paint);
        paint.setShader(null);
    }

    /**
     * Draws {@code bitmap} as large as fits in a box of {@code boxW} x {@code boxH}, centred on the
     * tile.
     */
    private void drawFitted(Canvas c, Bitmap bitmap, float boxW, float boxH, float alpha) {
        float ratio = Math.min(boxW / bitmap.getWidth(), boxH / bitmap.getHeight());
        float bw = bitmap.getWidth() * ratio, bh = bitmap.getHeight() * ratio;
        bounds.set(
                (getWidth() - bw) / 2,
                (getHeight() - bh) / 2,
                (getWidth() + bw) / 2,
                (getHeight() + bh) / 2);
        paint.setShader(null);
        paint.setAlpha(Math.round(255 * alpha));
        c.drawBitmap(bitmap, null, bounds, paint);
        paint.setAlpha(255);
    }
}

/** A separate compositor layer keeps the icon display list static during focus. */
