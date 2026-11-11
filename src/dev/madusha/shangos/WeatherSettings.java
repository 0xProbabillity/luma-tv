package dev.madusha.shangos;

import java.util.function.IntConsumer;

/**
 * Settings → City and Temperature: the words and menus of the rows. Choosing the city is {@link
 * CityPicker}, shared with setup.
 */
final class WeatherSettings {
    private static final String[] UNITS = {"Automatic", "Celsius", "Fahrenheit"};
    private static final String[] UNIT_KEYS = {"auto", "c", "f"};

    private final Prefs prefs;
    private final Overlays overlays;
    private final CityPicker cities;
    private final IntConsumer reload;

    /** {@code reload} shows Settings again with a row selected. */
    WeatherSettings(Prefs prefs, Overlays overlays, CityPicker cities, IntConsumer reload) {
        this.prefs = prefs;
        this.overlays = overlays;
        this.cities = cities;
        this.reload = reload;
    }

    private int unitIndex() {
        String u = prefs.tempUnit();
        return u.equals("c") ? 1 : u.equals("f") ? 2 : 0;
    }

    String unitLabel() {
        return UNITS[unitIndex()];
    }

    String unitAbout() {
        return "Weather in Celsius or Fahrenheit. Automatic follows the country of the weather's"
                + " city.";
    }

    void unitMenu(int settingsRow) {
        overlays.menu(
                "Temperature",
                UNITS,
                unitIndex(),
                choice -> {
                    prefs.setTempUnit(UNIT_KEYS[choice]);
                    reload.accept(settingsRow);
                });
    }

    String cityLabel() {
        return cities.label();
    }

    String cityAbout() {
        return cities.about();
    }

    void pickCity(int settingsRow) {
        cities.show(() -> reload.accept(settingsRow));
    }
}
