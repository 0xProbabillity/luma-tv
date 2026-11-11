package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class TintTest {
    private static int[] fill(int count, int argb) {
        int[] a = new int[count];
        Arrays.fill(a, argb);
        return a;
    }

    @Test
    public void aRedIconOnAWhiteSquareIsRedNotPink() {
        int[] pixels = fill(1000, 0xFFFFFFFF);
        Arrays.fill(pixels, 0, 300, 0xFFE02020);
        int c = Tint.dominant(pixels);
        assertEquals(0xE0, (c >> 16) & 255, 2);
        assertEquals(0x20, (c >> 8) & 255, 2);
    }

    @Test
    public void transparentGreyWhiteAndBlackPixelsDoNotVote() {
        assertEquals(Tint.NEUTRAL, Tint.dominant(fill(100, 0x00FF0000)));
        assertEquals(Tint.NEUTRAL, Tint.dominant(fill(100, 0xFF808080)));
        assertEquals(Tint.NEUTRAL, Tint.dominant(fill(100, 0xFFFFFFFF)));
        assertEquals(Tint.NEUTRAL, Tint.dominant(fill(100, 0xFF000000)));
        assertEquals(Tint.NEUTRAL, Tint.dominant(new int[0]));
    }

    @Test
    public void theMoreSaturatedColourWins() {
        int[] pixels = new int[1000];
        Arrays.fill(pixels, 0, 500, 0xFF3060FF); // vivid blue
        Arrays.fill(pixels, 500, 1000, 0xFF9A9AB0); // dull grey-blue: below the saturation floor
        int c = Tint.dominant(pixels);
        assertEquals(0x30, (c >> 16) & 255, 3);
        assertEquals(0xFF, c & 255, 3);
    }

    @Test
    public void gradientIsLighterOnTopAndDarkerBelow() {
        int[] g = Tint.gradient(0xFF3050C0);
        assertTrue((g[0] & 255) > 0xC0 && (g[1] & 255) < 0xC0);
        assertEquals(0xFF000000, g[0] & 0xFF000000);
    }

    @Test
    public void mixEndpoints() {
        assertEquals(0xFF102030, Tint.mix(0xFF102030, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, Tint.mix(0xFF102030, 0xFFFFFFFF, 1f));
    }

    @Test
    public void lightness() {
        assertTrue(Tint.isLight(0xFFF5F5F0));
        assertFalse(Tint.isLight(Tint.NEUTRAL));
    }
}
