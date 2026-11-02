package dev.madusha.shangos;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class PrefsTest {
    @Test
    public void freshInstallDefaults() {
        Prefs p = new Prefs(new FakePrefs());
        assertTrue(p.clock() && p.motion() && p.ambient() && p.sound());
        assertFalse("weather is opt-in", p.weather());
        assertFalse(p.offline());
        assertEquals(Prefs.POSTERS_UNMETERED, p.postersMode());
        assertEquals(Prefs.DEFAULT_WALLPAPER, p.wallpaper());
        assertEquals("auto", p.tempUnit());
        assertEquals("", p.name());
        assertTrue(p.hidden().isEmpty());
        assertFalse(p.askedListings());
    }

    @Test
    public void postersModeIsClamped() {
        FakePrefs f = new FakePrefs();
        Prefs p = new Prefs(f);
        f.data.put("posters", 99);
        assertEquals(Prefs.POSTERS_ANY, p.postersMode());
        f.data.put("posters", -5);
        assertEquals(Prefs.POSTERS_OFF, p.postersMode());
    }

    @Test
    public void migrationDropsUpstreamKeysOnceAndStampsTheSchema() {
        FakePrefs f = new FakePrefs();
        f.data.put("silver_default", true);
        f.data.put("scene_app_packages", "x");
        f.data.put("name", "Sam");
        new Prefs(f);
        assertFalse(f.contains("silver_default"));
        assertFalse(f.contains("scene_app_packages"));
        assertEquals("user data untouched", "Sam", f.getString("name", ""));
        assertEquals(Prefs.SCHEMA, f.getInt("v", 0));
    }

    @Test
    public void aNewerSchemaIsNotDowngraded() {
        FakePrefs f = new FakePrefs();
        f.data.put("v", Prefs.SCHEMA + 5);
        f.data.put("silver_default", true);
        new Prefs(f);
        assertEquals(Prefs.SCHEMA + 5, f.getInt("v", 0));
        assertTrue("later versions' data is left alone", f.contains("silver_default"));
    }

    @Test
    public void roundTrips() {
        Prefs p = new Prefs(new FakePrefs());
        p.setName("  Alex ");
        assertEquals("Alex", p.name());
        p.setFlag(Prefs.MOTION, false);
        assertFalse(p.motion());
        assertTrue(p.clock());
        p.setWeather(true);
        p.setOffline(true);
        assertTrue(p.weather() && p.offline());
        p.setOrderAndHidden("a,b", new HashSet<>(Arrays.asList("c")));
        assertEquals("a,b", p.order());
        assertEquals(Collections.singleton("c"), p.hidden());
        p.setWeatherCache("{}", "1.00,2.00C", 42L);
        assertEquals("{}", p.weatherJson());
        assertEquals("1.00,2.00C", p.weatherKey());
        assertEquals(42L, p.weatherAt());
    }

    @Test
    public void hiddenIsACopy() {
        Prefs p = new Prefs(new FakePrefs());
        p.setOrderAndHidden("", new HashSet<>(Arrays.asList("x")));
        Set<String> h = p.hidden();
        h.add("y");
        assertEquals(1, p.hidden().size());
    }

    @Test
    public void saveWidgetsKeepsTheOrderWhenNull() {
        Prefs p = new Prefs(new FakePrefs());
        p.saveWidgets("weather,upnext", "music", true);
        assertEquals("weather,upnext", p.widgetOrder());
        p.saveWidgets(null, "", false);
        assertEquals("order untouched", "weather,upnext", p.widgetOrder());
        assertFalse(p.weather());
    }

    @Test
    public void setupGuideRemembersItsStepUntilFinished() {
        Prefs p = new Prefs(new FakePrefs());
        assertEquals("fresh install starts at the first step", 0, p.setupStep());
        p.setSetupStep(2);
        assertEquals(2, p.setupStep());
        assertFalse(p.setupDone());
        p.setSetupDone();
        assertTrue(p.setupDone());
        assertEquals("finishing clears the saved step", 0, p.setupStep());
    }

    @Test
    public void setupStepCannotBeNegative() {
        FakePrefs f = new FakePrefs();
        f.data.put("setup_step", -4);
        assertEquals(0, new Prefs(f).setupStep());
    }
}
