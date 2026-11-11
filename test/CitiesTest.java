package dev.madusha.shangos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public class CitiesTest {
    // Largest first, as tools/cities.js writes it.
    private static final String DATA =
            "# comment line\n"
                    + "Colombo|LK|36|6.94|79.85|648034\n"
                    + "New York City|US|NY|40.71|-74.01|8804190\n"
                    + "Portland|US|OR|45.52|-122.68|632309\n"
                    + "Colombo|BR|18|-25.29|-49.22|217000\n"
                    + "Malm\u00f6|SE|27|55.61|13.00|301706\n"
                    + "Portland|US|ME|43.66|-70.26|66881\n"
                    + "York|GB|ENG|53.96|-1.08|153717\n";

    private static Cities cities() throws IOException {
        return Cities.parse(new ByteArrayInputStream(DATA.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void readsTheListAndSkipsComments() throws IOException {
        assertEquals(7, cities().size());
    }

    @Test
    public void namesStartingWithTheTextComeFirstLargestFirst() throws IOException {
        List<Places.Place> found = cities().find("colo", 5);
        assertEquals(2, found.size());
        assertEquals("Colombo", found.get(0).name);
        assertEquals("LK", found.get(0).country);
        assertEquals("BR", found.get(1).country);
    }

    @Test
    public void accentsAndCaseDoNotMatter() throws IOException {
        assertEquals("Malm\u00f6", cities().find("MALMO", 5).get(0).name);
        assertEquals("Malm\u00f6", cities().find("malm\u00f6", 5).get(0).name);
    }

    @Test
    public void aLaterWordMatchesToo() throws IOException {
        List<Places.Place> found = cities().find("york", 5);
        assertEquals("York", found.get(0).name); // starts with it: first
        assertEquals("New York City", found.get(1).name); // a word of it
    }

    @Test
    public void aCommaNarrowsByCountryNameOrCode() throws IOException {
        assertEquals("LK", cities().find("colombo, sri", 5).get(0).country);
        assertEquals(1, cities().find("colombo, sri", 5).size());
        assertEquals("BR", cities().find("colombo, br", 5).get(0).country);
        assertEquals(0, cities().find("colombo, norway", 5).size());
    }

    @Test
    public void twoCitiesWithTheSameNameAreToldApart() throws IOException {
        List<Places.Place> found = cities().find("portland", 5);
        assertEquals("OR, United States", found.get(0).detail);
        assertEquals("ME, United States", found.get(1).detail);
        assertEquals("Portland, OR, United States", found.get(0).full());
        // Different countries need no help: the country already differs.
        assertEquals("Sri Lanka", cities().find("colombo", 1).get(0).detail);
        assertEquals("United States", cities().find("new y", 5).get(0).detail);
    }

    @Test
    public void limitsAndEmptyQueries() throws IOException {
        assertEquals(1, cities().find("p", 1).size());
        assertTrue(cities().find("", 5).isEmpty());
        assertTrue(cities().find("zzz", 5).isEmpty());
    }

    @Test
    public void theGeneratedFileParses() throws IOException {
        java.io.File file = new java.io.File("res/raw/cities.txt");
        if (!file.isFile()) return; // run from another folder
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            Cities all = Cities.parse(in);
            assertTrue(all.size() > 10000);
            assertEquals("Colombo", all.find("colombo, sri lanka", 1).get(0).name);
            assertEquals("Shanghai", all.find("shang", 1).get(0).name);
        }
    }
}
