package dev.madusha.shangos;

import static org.junit.Assert.*;

import org.junit.Test;

public class SpringTest {
    /** The closed form the original code evaluated on every frame. */
    private static float original(float t, double frequency, double damping) {
        if (t >= 1) return 1;
        double time = t * .42, w = frequency * Math.sqrt(1 - damping * damping);
        return (float)
                (1
                        - Math.exp(-damping * frequency * time)
                                * (Math.cos(w * time)
                                        + damping * frequency / w * Math.sin(w * time)));
    }

    @Test
    public void tableMatchesTheClosedForm() {
        for (double[] p : new double[][] {{25, .72}, {24, .7}}) {
            Spring s = new Spring(p[0], p[1]);
            for (int i = 0; i <= 1000; i++) {
                float t = i / 1000f;
                assertEquals("t=" + t, original(t, p[0], p[1]), s.getInterpolation(t), 0.004f);
            }
        }
    }

    @Test
    public void endpointsAndClamping() {
        Spring s = new Spring(25, .72);
        assertEquals(0f, s.getInterpolation(0f), 0f);
        assertEquals(1f, s.getInterpolation(1f), 0f);
        assertEquals(0f, s.getInterpolation(-1f), 0f);
        assertEquals(1f, s.getInterpolation(5f), 0f);
    }

    @Test
    public void overshootsThenSettles() {
        Spring s = new Spring(25, .72);
        float peak = 0;
        for (int i = 0; i <= 100; i++) peak = Math.max(peak, s.getInterpolation(i / 100f));
        assertTrue("overshoot expected, peak=" + peak, peak > 1.0f && peak < 1.2f);
        assertEquals(1f, s.getInterpolation(.999f), .05f);
    }
}
