package dev.madusha.shangos;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** The soft highlight that follows focus across a tile. */
final class Shine extends View {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Shine(Context context) {
        super(context);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        paint.setShader(
                new LinearGradient(
                        -w * .4f,
                        0,
                        w * .95f,
                        h,
                        new int[] {0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF},
                        new float[] {0, .5f, 1},
                        Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
