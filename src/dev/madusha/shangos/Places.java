package dev.madusha.shangos;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/**
 * Where the weather is for: the city chosen in setup or Settings (from the list built into the app,
 * see {@link Cities}), else the main city of the TV's time zone (the place {@link Daylight} uses
 * for sunrise). No location service, no network and no lookup of the TV's address, ever.
 */
final class Places {
    private Places() {}

    /** Countries that show Fahrenheit, for the automatic temperature unit. */
    private static final List<String> FAHRENHEIT =
            Arrays.asList(
                    "US", "PR", "GU", "VI", "AS", "MP", "LR", "FM", "MH", "PW", "BS", "BZ", "KY");

    /** A city: its name, where it is, its country code and its coordinates. */
    static final class Place {
        final String name, detail, country;
        final double lat, lon;

        Place(String name, String detail, String country, double lat, double lon) {
            this.name = name;
            this.detail = detail;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }

        /** "Porto, Portugal": the name with its region and country, for lists. */
        String full() {
            return detail.isEmpty() ? name : name + ", " + detail;
        }

        boolean same(Place other) {
            return other != null
                    && Math.abs(lat - other.lat) < .01
                    && Math.abs(lon - other.lon) < .01;
        }

        String json() {
            try {
                return new JSONObject()
                        .put("name", name)
                        .put("detail", detail)
                        .put("country", country)
                        .put("lat", lat)
                        .put("lon", lon)
                        .toString();
            } catch (JSONException e) {
                return "";
            }
        }

        static Place parse(String raw) {
            if (raw == null || raw.isEmpty()) return null;
            try {
                JSONObject o = new JSONObject(raw);
                return new Place(
                        o.getString("name"),
                        o.optString("detail"),
                        o.optString("country"),
                        o.getDouble("lat"),
                        o.getDouble("lon"));
            } catch (JSONException e) {
                return null;
            }
        }
    }

    /** The city the user pinned, or null for Automatic. */
    static Place pinned(Prefs prefs) {
        return Place.parse(prefs.placePinned());
    }

    /** Pins {@code place}, or goes back to Automatic for null. The forecast is fetched again. */
    static void pin(Prefs prefs, Place place) {
        prefs.setPlacePinned(place == null ? "" : place.json());
    }

    /** The main city of the TV's time zone, or null for a zone that is not known. */
    static Place zone(Context context) {
        double[] p = Daylight.place(context);
        return p == null ? null : new Place(Daylight.city(), "", "", p[0], p[1]);
    }

    /** The place the forecast is for, at once and without the network. */
    static Place current(Context context, Prefs prefs) {
        Place pinned = pinned(prefs);
        return pinned != null ? pinned : zone(context);
    }

    /** Whether {@code place} (null: the TV's time zone) shows Fahrenheit by default. */
    static boolean fahrenheitCountry(Place place) {
        return place != null && !place.country.isEmpty() && FAHRENHEIT.contains(place.country);
    }
}
