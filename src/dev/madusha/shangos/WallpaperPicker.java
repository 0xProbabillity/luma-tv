package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** Full-screen wallpaper picker: moving between thumbnails previews each wallpaper live. */
final class WallpaperPicker {
    /** What the picker needs from the launcher. */
    interface Host {
        /** The page and the bars that fade out while the picker is open. */
        View content();

        View nav();

        View shade();

        Wallpaper wallpaper();

        /** Show Settings again with row {@code row} selected. */
        void reload(int row);
    }

    private final Context context;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost screens;
    private final Host host;

    WallpaperPicker(
            Context context,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost screens,
            Host host) {
        this.context = context;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.screens = screens;
        this.host = host;
    }

    private void fadeBackIn() {
        host.content().animate().alpha(1).setDuration(260).start();
        host.nav().animate().alpha(1).setDuration(260).start();
        host.shade().animate().alpha(1).setDuration(260).start();
    }

    /** Opens the picker; choosing returns to Settings with {@code settingsRow} selected. */
    void show(int settingsRow) {
        final int saved = Wallpaper.choice(prefs);
        final Wallpaper wallpaper = host.wallpaper();
        FrameLayout o = overlays.open(Color.TRANSPARENT);
        overlays.onCancel(
                () -> {
                    wallpaper.show(saved);
                    fadeBackIn();
                });
        host.content().animate().alpha(0).setDuration(240).start();
        host.nav().animate().alpha(0).setDuration(240).start();
        host.shade().animate().alpha(0).setDuration(300).start();
        View fade = new View(context);
        fade.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {0x00000000, 0x99000000, 0xE6000000}));
        o.addView(fade, ui.pos(960, 260, 0, 280));
        TextView name = ui.text(Wallpaper.NAMES[saved], 34, Color.WHITE, "sans-serif-medium");
        name.setLetterSpacing(-.02f);
        name.setShadowLayer(ui.px(10), 0, ui.px(1), 0x66000000);
        o.addView(name, ui.pos(600, 44, 48, 338));
        TextView hint =
                ui.text(
                        "OK to choose  ·  Back to keep the current one",
                        13,
                        0xB3FFFFFF,
                        "sans-serif");
        o.addView(hint, ui.pos(600, 20, 49, 386));
        LinearLayout row = new LinearLayout(context);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        o.addView(row, ui.pos(864, 80, 48, 430));
        ArrayList<View> cells = new ArrayList<>();
        final int count = Wallpaper.count(prefs);
        // Nine looks (with a photo) need narrower cells than eight to fit the row.
        final int cellWidth = Math.min(100, (864 - 9 * (count - 1)) / count);
        for (int i = 0; i < count; i++) {
            final int index = i;
            FrameLayout cell = new FrameLayout(context);
            cell.setFocusable(true);
            cell.setFocusableInTouchMode(true);
            cell.setSoundEffectsEnabled(false);
            cell.setClipToOutline(true);
            cell.setBackground(ui.shape(0xFF101521, 12));
            ImageView img = new ImageView(context);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (Wallpaper.THUMBS[i] == Wallpaper.PHOTO_THUMB)
                img.setImageBitmap(PhotoStore.thumbnail(context));
            else img.setImageResource(Wallpaper.THUMBS[i]);
            cell.addView(img, new FrameLayout.LayoutParams(-1, -1));
            GradientDrawable ring = ui.shape(Color.TRANSPARENT, 12);
            ring.setStroke(ui.px(1.5f), i == saved ? 0xCCFFFFFF : 0x33FFFFFF);
            cell.setForeground(ring);
            cell.setOnFocusChangeListener(
                    (v, f) -> {
                        GradientDrawable r = ui.shape(Color.TRANSPARENT, 12);
                        r.setStroke(
                                ui.px(f ? 2 : 1.5f),
                                f ? Color.WHITE : index == saved ? 0xCCFFFFFF : 0x33FFFFFF);
                        cell.setForeground(r);
                        cell.setElevation(ui.px(f ? 18 : 2));
                        cell.animate().cancel();
                        float s = f ? 1.16f : 1;
                        if (screens.motion())
                            cell.animate()
                                    .scaleX(s)
                                    .scaleY(s)
                                    .translationY(f ? -ui.px(4) : 0)
                                    .setDuration(420)
                                    .setInterpolator(Motion.TILE)
                                    .start();
                        else {
                            cell.setScaleX(s);
                            cell.setScaleY(s);
                        }
                        if (f) {
                            name.setText(Wallpaper.NAMES[index]);
                            wallpaper.show(index);
                            screens.step(index);
                        }
                    });
            cell.setOnClickListener(
                    v -> {
                        screens.click();
                        prefs.setWallpaper(index);
                        overlays.onCancel(null);
                        overlays.close(false);
                        fadeBackIn();
                        host.reload(settingsRow);
                    });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.px(cellWidth), ui.px(56));
            if (i > 0) lp.leftMargin = ui.px(9);
            row.addView(cell, lp);
            cells.add(cell);
        }
        screens.post(() -> cells.get(saved).requestFocus());
    }
}
