package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlacesTest {
    private static Places.Place place(String name, String country) {
        return new Places.Place(name, "", country, 1, 2);
    }

    @Test
    public void unitFollowsTheCountryOfTheCity() {
        assertTrue(Places.fahrenheitCountry(place("Miami", "US")));
        assertTrue(Places.fahrenheitCountry(place("Nassau", "BS")));
        assertFalse(Places.fahrenheitCountry(place("Colombo", "LK")));
        assertFalse(Places.fahrenheitCountry(place("Oslo", "")));
        assertFalse(Places.fahrenheitCountry(null));
    }

    @Test
    public void samePlaceMeansNearlyTheSameCoordinates() {
        Places.Place a = new Places.Place("A", "", "", 59.91, 10.75);
        assertTrue(a.same(new Places.Place("B", "x", "NO", 59.912, 10.748)));
        assertFalse(a.same(new Places.Place("C", "", "", 60.39, 5.32)));
        assertFalse(a.same(null));
        assertEquals("Porto, Portugal", new Places.Place("Porto", "Portugal", "PT", 41, -8).full());
        assertEquals("Oslo", place("Oslo", "NO").full());
    }
}
