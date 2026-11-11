package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * A placeholder while something loads: a soft rounded block with a faint highlight sliding across.
 * All blocks read the same clock, so a row shimmers as one. With motion off it is a still block.
 */
final class Shimmer extends View {
    private static final long PERIOD_MS = 1500;

    private final float radius;
    private final boolean moving;
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private LinearGradient gradient;

    /** {@code light} is a pale wallpaper; {@code radius} is in pixels. */
    Shimmer(Context context, boolean light, float radius, boolean moving) {
        super(context);
        this.radius = radius;
        this.moving = moving;
        base.setColor(light ? 0x1A000000 : 0x1FFFFFFF);
        sheen.setAlpha(255);
        setSoundEffectsEnabled(false);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int white = base.getColor() == 0x1A000000 ? 0x33000000 : 0x40FFFFFF;
        gradient =
                new LinearGradient(
                        0,
                        0,
                        w * .6f,
                        0,
                        new int[] {white & 0x00FFFFFF, white, white & 0x00FFFFFF},
                        new float[] {0, .5f, 1},
                        Shader.TileMode.CLAMP);
        sheen.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, radius, radius, base);
        if (!moving || gradient == null) return;
        float phase = (SystemClock.uptimeMillis() % PERIOD_MS) / (float) PERIOD_MS;
        matrix.setTranslate(-getWidth() * .6f + phase * getWidth() * 1.8f, 0);
        gradient.setLocalMatrix(matrix);
        canvas.drawRoundRect(rect, radius, radius, sheen);
        postInvalidateOnAnimation();
    }
}
