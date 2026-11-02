package dev.madusha.shangos;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppCacheTest {
    private static List<String[]> rows(String[]... r) {
        return new ArrayList<>(Arrays.asList(r));
    }

    @Test
    public void roundTrips() {
        List<String[]> in =
                rows(
                        new String[] {"a.b", "Alpha", "a.b.Main"},
                        new String[] {"c.d", "Zeta", "c.d.Home"});
        List<String[]> out = AppCache.decode(AppCache.encode(in));
        assertEquals(2, out.size());
        assertArrayEquals(in.get(0), out.get(0));
        assertArrayEquals(in.get(1), out.get(1));
    }

    @Test
    public void labelsWithTabsAndNewlinesCannotBreakTheFormat() {
        List<String[]> out =
                AppCache.decode(
                        AppCache.encode(
                                rows(
                                        new String[] {"a.b", "Tab\there\nand\rline", "a.b.Main"},
                                        new String[] {"x.y", "Next", "x.y.Main"})));
        assertEquals(2, out.size());
        assertEquals("Tab here and line", out.get(0)[1]);
        assertEquals("Next", out.get(1)[1]);
    }

    @Test
    public void unicodeLabelsSurvive() {
        List<String[]> out =
                AppCache.decode(
                        AppCache.encode(
                                rows(new String[] {"a.b", "ශ්‍රී ලංකා TV · 日本語", "a.b.Main"})));
        assertEquals("ශ්‍රී ලංකා TV · 日本語", out.get(0)[1]);
    }

    @Test
    public void anotherVersionsFileOrGarbageGivesAnEmptyList() {
        assertTrue(AppCache.decode(null).isEmpty());
        assertTrue(AppCache.decode("").isEmpty());
        assertTrue(AppCache.decode("shangos-apps 2\na\tb\tc\n").isEmpty());
        assertTrue(AppCache.decode("random text").isEmpty());
    }

    @Test
    public void badLinesAreSkippedNotFatal() {
        String text =
                "shangos-apps 1\n"
                        + "ok.pkg\tOK\tok.pkg.Main\n"
                        + "too\tfew\n"
                        + "\t\t\n"
                        + "also.ok\tAlso\talso.ok.Main\n";
        List<String[]> out = AppCache.decode(text);
        assertEquals(2, out.size());
        assertEquals("ok.pkg", out.get(0)[0]);
        assertEquals("also.ok", out.get(1)[0]);
    }

    @Test
    public void anEmptyLabelIsKept() {
        List<String[]> out =
                AppCache.decode(AppCache.encode(rows(new String[] {"a.b", "", "a.b.Main"})));
        assertEquals(1, out.size());
        assertEquals("", out.get(0)[1]);
    }
}
