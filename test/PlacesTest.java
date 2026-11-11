package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PlacesTest {
    private static Places.Place place(String name, String country) {
        return new Places.Place(name, "", country, 1, 2);
    }

    @Test
    public void joinLeavesOutRepeatsAndEmptyParts() {
        assertEquals("Ohio, United States", Places.join("Chicago", "Ohio", "United States"));
        assertEquals("United States", Places.join("Paris", "Paris", "United States"));
        assertEquals("France", Places.join("Paris", "France", "France"));
        assertEquals("", Places.join("Oslo", "", null));
    }

    @Test
    public void plainIgnoresCaseAndAccents() {
        assertEquals("malmo", Places.plain(" Malm\u00f6 "));
        assertEquals("sao paulo", Places.plain("S\u00e3o Paulo"));
    }

    @Test
    public void latinNamesAreSearchedInEnglishOnly() {
        assertEquals(Arrays.asList("en"), new ArrayList<>(Places.languages("Berlin")));
        assertEquals(Arrays.asList("en"), new ArrayList<>(Places.languages("12345")));
    }

    @Test
    public void otherAlphabetsTryTheLanguagesWrittenInThem() {
        String berlin = "\u0411\u0435\u0440\u043b\u0438\u043d";
        List<String> cyrillic = new ArrayList<>(Places.languages(berlin));
        assertEquals("en", cyrillic.get(0));
        assertTrue(cyrillic.contains("ru"));
        assertTrue(cyrillic.contains("uk"));
        assertTrue(new ArrayList<>(Places.languages("\u6771\u4eac")).contains("ja"));
        assertTrue(new ArrayList<>(Places.languages("\uc11c\uc6b8")).contains("ko"));
    }

    @Test
    public void onlyTownsAndCitiesAreOffered() {
        assertTrue(Places.isTown(""));
        assertTrue(Places.isTown("PPL"));
        assertTrue(Places.isTown("PPLA2"));
        assertTrue(Places.isTown("PPLC"));
        assertFalse(Places.isTown("PPLX")); // a city district
        assertFalse(Places.isTown("PPLH")); // a historical place
        assertFalse(Places.isTown("MT")); // a mountain
        assertFalse(Places.isTown("AIRP"));
    }

    @Test
    public void placesCalledWhatWasTypedComeFirst() {
        List<Places.Place> found =
                new ArrayList<>(
                        Arrays.asList(
                                place("Willard", "US"),
                                place("Chicago", "US"),
                                place("Chicago Heights", "US")));
        Places.rank(found, "chicago");
        assertEquals("Chicago", found.get(0).name);
        assertEquals("Chicago Heights", found.get(1).name);
        assertEquals("Willard", found.get(2).name);
    }

    @Test
    public void unitFollowsTheCountryOfTheCity() {
        assertTrue(Places.fahrenheitCountry(place("Miami", "US")));
        assertTrue(Places.fahrenheitCountry(place("Nassau", "BS")));
        assertFalse(Places.fahrenheitCountry(place("Toronto", "CA")));
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
