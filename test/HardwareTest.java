package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.madusha.shangos.Hardware.Tier;

import org.junit.Test;

public class HardwareTest {
    private static final long GB = 1L << 30;

    @Test
    public void theMeasuredStickIsLight() {
        // onn Full HD Streaming Device: MemTotal 1,483,088 kB, 32-bit userspace, 4 cores
        assertEquals(Tier.LIGHT, Hardware.classify(1_483_088L * 1024, 4, false, false));
    }

    @Test
    public void theSystemsLowRamFlagAlwaysWins() {
        assertEquals(Tier.LIGHT, Hardware.classify(8 * GB, 8, true, true));
    }

    @Test
    public void under1p5GbIsLightEvenIf64Bit() {
        assertEquals(Tier.LIGHT, Hardware.classify(GB, 4, true, false));
    }

    @Test
    public void twoGbIs64BitBalancedButThirtyTwoBitBalancedOnlyFrom2Gb() {
        assertEquals(Tier.BALANCED, Hardware.classify(2 * GB, 4, true, false));
        assertEquals(Tier.BALANCED, Hardware.classify(2 * GB, 4, false, false));
        assertEquals(Tier.LIGHT, Hardware.classify(2 * GB - 1, 4, false, false));
        assertEquals(Tier.BALANCED, Hardware.classify(2 * GB - 1, 4, true, false));
    }

    @Test
    public void threeGbFastEnoughAnd64BitIsQuality() {
        assertEquals(Tier.QUALITY, Hardware.classify(3 * GB, 4, true, false));
        assertEquals(
                "a 32-bit process never reaches Quality",
                Tier.BALANCED,
                Hardware.classify(4 * GB, 8, false, false));
        assertEquals("too few cores", Tier.BALANCED, Hardware.classify(4 * GB, 2, true, false));
    }

    @Test
    public void onlyLightStaysStill() {
        assertFalse(Tier.LIGHT.animates());
        assertTrue(Tier.BALANCED.animates() && Tier.QUALITY.animates());
        assertEquals(0, Tier.LIGHT.idleLimitMillis());
        assertTrue(Tier.QUALITY.idleLimitMillis() > Tier.BALANCED.idleLimitMillis());
    }
}
