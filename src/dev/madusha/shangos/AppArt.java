package dev.madusha.shangos;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * A tile's picture: the app's TV banner or icon on its colour, drawn by hand (a few apps have their
 * own drawings). Pictures come from {@link AppRepo} and are decoded off the main thread; until one
 * arrives only the colour shows.
 */
final class AppArt extends View {
    private final Ui ui;
    private final AppRepo repo;
    final App app;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF bounds = new RectF();
    final Path path = new Path();
    final Runnable redraw = this::invalidate;
    Shader finish;

    AppArt(Context context, Ui ui, AppRepo repo, App app) {
        super(context);
        this.ui = ui;
        this.repo = repo;
        this.app = app;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        finish =
                new LinearGradient(
                        0,
                        0,
                        0,
                        h,
                        new int[] {0x20FFFFFF, 0x00FFFFFF, 0x14000000},
                        new float[] {0, .46f, 1},
                        Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        bounds.set(0, 0, w, h);
        p.setColor(app.color);
        p.setShader(null);
        c.drawRoundRect(bounds, ui.px(16), ui.px(16), p);
        String pkg = app.pkg;
        if (pkg.equals("com.google.android.youtube.tv")) {
            float cx = w / 2, cy = h / 2;
            p.setColor(0xFFFF0033);
            bounds.set(cx - ui.px(25), cy - ui.px(17), cx + ui.px(25), cy + ui.px(17));
            c.drawRoundRect(bounds, ui.px(10), ui.px(10), p);
            p.setColor(Color.WHITE);
            path.reset();
            path.moveTo(cx - ui.px(5), cy - ui.px(9));
            path.lineTo(cx + ui.px(10), cy);
            path.lineTo(cx - ui.px(5), cy + ui.px(9));
            path.close();
            c.drawPath(path, p);
        } else if (pkg.equals("com.netflix.ninja")) {
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            p.setTextSize(ui.px(54));
            p.setColor(0xFFE50914);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("N", w / 2, h / 2 + ui.px(19), p);
        } else if (pkg.equals("com.soundcloud.android")) {
            drawIcon(c, repo.icon(app, redraw), 48);
        } else if (pkg.equals("com.ted.android.tv")) {
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            p.setTextSize(ui.px(33));
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFF302B);
            c.drawText("TED", w / 2, h / 2 + ui.px(12), p);
        } else if (pkg.equals("stream.tv.online")) {
            float cx = w / 2, cy = h / 2 - ui.px(2);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ui.px(2.5f));
            bounds.set(cx - ui.px(27), cy - ui.px(18), cx + ui.px(27), cy + ui.px(18));
            c.drawRoundRect(bounds, ui.px(6), ui.px(6), p);
            c.drawLine(cx, cy + ui.px(18), cx, cy + ui.px(25), p);
            c.drawLine(cx - ui.px(10), cy + ui.px(25), cx + ui.px(10), cy + ui.px(25), p);
            p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(cx - ui.px(5), cy - ui.px(9));
            path.lineTo(cx + ui.px(10), cy);
            path.lineTo(cx - ui.px(5), cy + ui.px(9));
            path.close();
            c.drawPath(path, p);
        } else if (pkg.equals("com.phlox.tvwebbrowser")) {
            float cx = w / 2, cy = h / 2, r = ui.px(26);
            p.setColor(0xF4FFFFFF);
            c.drawCircle(cx, cy, r, p);
            p.setColor(0xFFB4C6D6);
            p.setStrokeWidth(ui.px(1));
            for (int tick = 0; tick < 24; tick++) {
                double a = tick * Math.PI / 12;
                float inner = r - ui.px(tick % 3 == 0 ? 6 : 3);
                c.drawLine(
                        cx + (float) Math.cos(a) * inner,
                        cy + (float) Math.sin(a) * inner,
                        cx + (float) Math.cos(a) * (r - ui.px(2)),
                        cy + (float) Math.sin(a) * (r - ui.px(2)),
                        p);
            }
            c.save();
            c.rotate(38, cx, cy);
            path.reset();
            path.moveTo(cx, cy - ui.px(21));
            path.lineTo(cx + ui.px(5), cy);
            path.lineTo(cx - ui.px(5), cy);
            path.close();
            p.setColor(0xFFE65E62);
            c.drawPath(path, p);
            path.reset();
            path.moveTo(cx, cy + ui.px(21));
            path.lineTo(cx + ui.px(5), cy);
            path.lineTo(cx - ui.px(5), cy);
            path.close();
            p.setColor(0xFF42677F);
            c.drawPath(path, p);
            c.restore();
        } else if (pkg.equals("com.google.android.vending") || pkg.equals("com.android.vending")) {
            drawIcon(c, repo.icon(app, redraw), 47);
        } else if (repo.hasBanner(app)) {
            // The banner is decoded on first use; until it arrives the tile shows only its
            // colour (no icon that then jumps).
            Bitmap banner = repo.banner(app, redraw);
            if (banner != null) {
                float ratio = Math.min(w / banner.getWidth(), h / banner.getHeight());
                float bw = banner.getWidth() * ratio, bh = banner.getHeight() * ratio;
                bounds.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
                p.setAlpha(255);
                c.drawBitmap(banner, null, bounds, p);
            }
        } else drawIcon(c, repo.icon(app, redraw), 48);
        p.setShader(finish);
        bounds.set(0, 0, w, h);
        c.drawRoundRect(bounds, ui.px(16), ui.px(16), p);
        p.setShader(null);
    }

    private void drawIcon(Canvas c, Bitmap icon, float size) {
        if (icon == null) return;
        float s = ui.px(size);
        bounds.set(
                (getWidth() - s) / 2,
                (getHeight() - s) / 2,
                (getWidth() + s) / 2,
                (getHeight() + s) / 2);
        p.setAlpha(255);
        c.drawBitmap(icon, null, bounds, p);
    }
}

/** A separate compositor layer keeps the icon display list static during focus. */
