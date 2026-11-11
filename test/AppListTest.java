package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class AppListTest {
    private static final String[] FIRST = {"yt", "nf"};

    private static List<String> order(String... v) {
        return Arrays.asList(v);
    }

    private static Set<String> set(String... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    @Test
    public void orderPutsSavedThenPinnedThenTheRestWithoutRepeats() {
        List<String> o = AppList.order("b,yt", FIRST, Arrays.asList("a", "b", "nf", "c"));
        assertEquals(Arrays.asList("b", "yt", "nf", "a", "c"), o);
    }

    @Test
    public void orderOnFreshInstallStartsWithPinnedApps() {
        assertEquals(
                Arrays.asList("yt", "nf", "x"),
                AppList.order("", FIRST, Arrays.asList("x", "yt")));
        assertEquals(Arrays.asList("yt", "nf"), AppList.order(null, FIRST, Collections.emptyList()));
    }

    @Test
    public void visibleSkipsUninstalledAndHidden() {
        List<String> order = Arrays.asList("a", "gone", "b", "c");
        Set<String> installed = set("a", "b", "c");
        assertEquals(
                Arrays.asList("a", "c"), AppList.visible(order, set("b"), installed, false));
        assertEquals(
                Arrays.asList("a", "b", "c"), AppList.visible(order, set("b"), installed, true));
    }

    @Test
    public void moveFirstAlsoUnhides() {
        AppList.Edit e = AppList.moveFirst(Arrays.asList("a", "b", "c"), set("c"), "c");
        assertEquals(Arrays.asList("c", "a", "b"), e.order);
        assertTrue(e.hidden.isEmpty());
    }

    @Test
    public void moveOverSwapsPlacesOnScreen() {
        List<String> o = Arrays.asList("a", "b", "c", "d");
        assertEquals(order("b", "a", "c", "d"), AppList.moveOver(o, set(), "a", "b", true).order);
        assertEquals(order("a", "c", "b", "d"), AppList.moveOver(o, set(), "c", "b", false).order);
        assertEquals(order("b", "c", "a", "d"), AppList.moveOver(o, set(), "a", "c", true).order);
        assertEquals(order("d", "a", "b", "c"), AppList.moveOver(o, set(), "d", "a", false).order);
    }

    @Test
    public void moveOverSkipsHiddenAndMissingAppsBetween() {
        // "x" is hidden and "gone" is uninstalled: on screen a, c, d are neighbours.
        List<String> o = Arrays.asList("a", "x", "gone", "c", "d");
        Set<String> hidden = set("x");
        AppList.Edit e = AppList.moveOver(o, hidden, "a", "c", true);
        assertEquals(Arrays.asList("x", "gone", "c", "a", "d"), e.order);
        assertEquals(hidden, e.hidden);
    }

    @Test
    public void moveOverIgnoresUnknownAppsAndItself() {
        List<String> o = Arrays.asList("a", "b");
        assertEquals(o, AppList.moveOver(o, set(), "a", "zzz", true).order);
        assertEquals(o, AppList.moveOver(o, set(), "zzz", "a", true).order);
        assertEquals(o, AppList.moveOver(o, set(), "a", "a", true).order);
    }

    @Test
    public void editsNeverMutateTheirInputs() {
        List<String> o = Arrays.asList("a", "b");
        Set<String> h = set("b");
        AppList.moveFirst(o, h, "b");
        AppList.toggleHidden(o, h, "a");
        assertEquals(Arrays.asList("a", "b"), o);
        assertEquals(set("b"), h);
    }

    @Test
    public void toggleHiddenFlips() {
        List<String> o = Arrays.asList("a");
        assertEquals(set("a"), AppList.toggleHidden(o, set(), "a").hidden);
        assertTrue(AppList.toggleHidden(o, set("a"), "a").hidden.isEmpty());
        List<String> ab = Arrays.asList("a", "b");
        assertEquals("b,a", AppList.moveOver(ab, set(), "a", "b", true).orderCsv());
    }

    @Test
    public void rememberKeepsNewestFirstOncePerAppAndCapped() {
        assertEquals("a:5", AppList.remember("", "a", 5, 12));
        assertEquals("b:9,a:5", AppList.remember("a:5", "b", 9, 12));
        assertEquals("a:9,b:3", AppList.remember("a:1,b:3", "a", 9, 12));
        assertEquals("c:9,b:3", AppList.remember("b:3,a:1", "c", 9, 2));
        assertEquals("a:5", AppList.remember(null, "a", 5, 12));
    }

    @Test
    public void rememberedEntriesAreReadByRecency() {
        String saved = AppList.remember("b:3", "a", 77, 12);
        assertEquals(77, Recency.lastOpened(saved, "a"));
        assertEquals(3, Recency.lastOpened(saved, "b"));
    }

    @Test
    public void sortModesOrderTheAppsTab() {
        List<String> pkgs = Arrays.asList("c", "a", "b", "d");
        java.util.Map<String, String> label = new java.util.HashMap<>();
        label.put("a", "Zoom");
        label.put("b", "apple TV");
        label.put("c", "Calc");
        label.put("d", "Chess");
        java.util.Map<String, Long> opened = new java.util.HashMap<>();
        opened.put("a", 50L);
        opened.put("b", 10L);
        // c and d were never opened: they keep their old order, after the opened ones.
        assertEquals(
                Arrays.asList("c", "a", "b", "d"),
                AppList.sorted(AppList.MANUAL, pkgs, label::get, k -> opened.getOrDefault(k, 0L)));
        assertEquals(
                Arrays.asList("b", "c", "d", "a"),
                AppList.sorted(AppList.AZ, pkgs, label::get, k -> opened.getOrDefault(k, 0L)));
        assertEquals(
                Arrays.asList("a", "b", "c", "d"),
                AppList.sorted(AppList.RECENT, pkgs, label::get, k -> opened.getOrDefault(k, 0L)));
        assertEquals(Arrays.asList("c", "a", "b", "d"), pkgs); // never changes its input
    }
}
