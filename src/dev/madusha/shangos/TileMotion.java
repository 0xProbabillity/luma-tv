package dev.madusha.shangos;

/** Native property animations keep the static card artwork cached on the GPU. */
final class TileMotion {
    private final AppTile tile;
    private final Ui ui;
    private final AppTile.Host host;
    private boolean focused, pressed, grabbed;

    TileMotion(AppTile tile, Ui ui, AppTile.Host host) {
        this.tile = tile;
        this.ui = ui;
        this.host = host;
    }

    void focus(boolean value) {
        focused = value;
        pressed = false;
        boolean motion = host.motion();
        if (value && motion) {
            tile.face.animate().cancel();
            tile.face.setRotationY(-host.directionX() * 4.5f);
            tile.face.setRotationX(host.directionY() * 3f);
        }
        start();
        if (motion) {
            tile.art.animate().cancel();
            float depthX = focused ? ui.px((tile.index % AppTile.COLUMNS - 2.5f) * .7f) : 0;
            float depthY = focused ? -ui.px(1) : 0;
            if (focused) {
                tile.art.setTranslationX(depthX - ui.px(host.directionX() * 4.5f));
                tile.art.setTranslationY(depthY + ui.px(host.directionY() * 3));
                tile.shine.setTranslationX(ui.px(host.directionX() * 12));
            }
            tile.art
                    .animate()
                    .translationX(depthX)
                    .translationY(depthY)
                    .setDuration(520)
                    .setInterpolator(Motion.DRIFT)
                    .start();
            tile.shine
                    .animate()
                    .translationX(focused ? -depthX * 2 : 0)
                    .alpha(focused ? .6f : 0)
                    .setDuration(580)
                    .setInterpolator(Motion.DRIFT)
                    .start();
        }
    }

    /** Picked up to be moved: lifted higher than a focused tile. */
    void grab(boolean value) {
        grabbed = value;
        start();
    }

    void press(boolean down) {
        pressed = down;
        start();
    }

    private void start() {
        if (!host.motion()) {
            snap();
            return;
        }
        tile.face.animate().cancel();
        tile.label.animate().cancel();
        tile.shine.animate().cancel();
        float scale = (focused ? 1.11f : 1) + (grabbed ? .07f : 0) - (pressed ? .03f : 0);
        tile.face
                .animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationY(focused ? -ui.px(3) : 0)
                .rotationX(0)
                .rotationY(0)
                .setDuration(pressed ? 65 : 420)
                .setInterpolator(pressed ? Motion.EASE : Motion.TILE)
                .withLayer()
                .start();
        tile.label
                .animate()
                .scaleX(focused ? 1.025f : 1)
                .scaleY(focused ? 1.025f : 1)
                .setDuration(260)
                .setInterpolator(Motion.EASE)
                .start();
        tile.shine
                .animate()
                .alpha(focused ? .6f : 0)
                .setDuration(200)
                .setInterpolator(Motion.EASE)
                .start();
    }

    void stop() {
        tile.face.animate().cancel();
        tile.art.animate().cancel();
        tile.label.animate().cancel();
        tile.shine.animate().cancel();
    }

    void snap() {
        stop();
        pressed = false;
        float scale = (focused ? 1.11f : 1) + (grabbed ? .07f : 0);
        tile.face.setScaleX(scale);
        tile.face.setScaleY(scale);
        tile.face.setTranslationY(focused ? -ui.px(3) : 0);
        tile.face.setRotationX(0);
        tile.face.setRotationY(0);
        tile.art.setTranslationX(0);
        tile.art.setTranslationY(0);
        tile.shine.setTranslationX(0);
        tile.shine.setAlpha(focused ? .6f : 0);
        tile.label.setScaleX(focused ? 1.025f : 1);
        tile.label.setScaleY(focused ? 1.025f : 1);
    }
}
