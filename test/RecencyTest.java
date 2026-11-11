package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RecencyTest {
    private static final long M = 60_000L, H = 60 * M, D = 24 * H;

    @Test
    public void describesEachRange() {
        long now = 100 * D;
        assertEquals("Opened just now", Recency.describe(now, now - 20_000));
        assertEquals("Opened 1 minute ago", Recency.describe(now, now - M));
        assertEquals("Opened 59 minutes ago", Recency.describe(now, now - 59 * M));
        assertEquals("Opened 1 hour ago", Recency.describe(now, now - H));
        assertEquals("Opened 5 hours ago", Recency.describe(now, now - 5 * H - 20 * M));
        assertEquals("Opened yesterday", Recency.describe(now, now - 30 * H));
        assertEquals("Opened 3 days ago", Recency.describe(now, now - 3 * D));
        assertEquals("Opened over a month ago", Recency.describe(now, now - 45 * D));
    }

    @Test
    public void neverOpenedAndClockSkew() {
        assertEquals("Not opened from shangOS yet", Recency.describe(1000, 0));
        assertEquals("Opened just now", Recency.describe(1000, 5000)); // the clock moved back
    }

    @Test
    public void readsTheSavedList() {
        String recent = "a.b:1000,c.d:2000,e.f:oops";
        assertEquals(2000, Recency.lastOpened(recent, "c.d"));
        assertEquals(1000, Recency.lastOpened(recent, "a.b"));
        assertEquals(0, Recency.lastOpened(recent, "e.f"));
        assertEquals(0, Recency.lastOpened(recent, "missing"));
        assertEquals(0, Recency.lastOpened("", "a.b"));
        assertEquals(0, Recency.lastOpened(null, "a.b"));
    }

    @Test
    public void aPackageIsNotMatchedByPrefix() {
        assertEquals(0, Recency.lastOpened("com.example.app:5000", "com.example"));
    }
}
