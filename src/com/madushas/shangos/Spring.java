package com.madushas.shangos;

import android.view.animation.Interpolator;

/** A damped spring (overshoots a little, then settles), sampled once into a table so a frame costs one lookup
 *  instead of exp, cos and sin. {@code frequency} is the stiffness, {@code damping} below 1 means it overshoots. */
final class Spring implements Interpolator {
    private static final int STEPS = 256;
    /** The spring is evaluated over this much of its natural time: the whole animation is one short settle. */
    private static final double SPAN = .42;
    private final float[] table = new float[STEPS + 2];
    Spring(double frequency, double damping) {
        double w = frequency * Math.sqrt(1 - damping * damping);
        for (int i = 0; i <= STEPS; i++) {
            double t = i / (double) STEPS * SPAN;
            table[i] = (float) (1 - Math.exp(-damping * frequency * t) * (Math.cos(w * t) + damping * frequency / w * Math.sin(w * t)));
        }
        table[STEPS] = 1; table[STEPS + 1] = 1;
    }
    @Override public float getInterpolation(float t) {
        if (t >= 1) return 1;
        if (t <= 0) return 0;
        float x = t * STEPS; int i = (int) x;
        return table[i] + (table[i + 1] - table[i]) * (x - i);
    }
}
