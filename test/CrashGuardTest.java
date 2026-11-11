package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrashGuardTest {
    private static final long MIN = 60_000L;

    @Test
    public void oneCrashIsNotEnough() {
        assertFalse(CrashGuard.tripped("", 10 * MIN));
        assertFalse(CrashGuard.tripped(String.valueOf(9 * MIN), 10 * MIN));
    }

    @Test
    public void twoCrashesInFiveMinutesStandAside() {
        assertTrue(CrashGuard.tripped(8 * MIN + "," + 9 * MIN, 10 * MIN));
    }

    @Test
    public void oldCrashesStopCounting() {
        assertFalse(CrashGuard.tripped(1 * MIN + "," + 9 * MIN, 10 * MIN));
        // five minutes after the second crash it tries again
        assertTrue(CrashGuard.tripped(8 * MIN + "," + 9 * MIN, 12 * MIN));
        assertFalse(CrashGuard.tripped(8 * MIN + "," + 9 * MIN, 14 * MIN + 1));
    }

    @Test
    public void recordKeepsRecentCrashesAndAddsNow() {
        assertEquals("1000", CrashGuard.record(null, 1000));
        assertEquals(9 * MIN + "," + 10 * MIN, CrashGuard.record(String.valueOf(9 * MIN), 10 * MIN));
        // a crash from half an hour ago is forgotten
        assertEquals(String.valueOf(40 * MIN), CrashGuard.record(String.valueOf(10 * MIN), 40 * MIN));
    }

    @Test
    public void recordCapsTheListAndIgnoresDamage() {
        String list = "";
        for (int i = 0; i < 15; i++) list = CrashGuard.record(list, 1000 + i);
        assertEquals(10, list.split(",").length);
        assertEquals("1014", list.substring(list.lastIndexOf(',') + 1));
        assertEquals("1000", CrashGuard.record("junk,,x", 1000));
        assertFalse(CrashGuard.tripped("junk", 1000));
    }
}
