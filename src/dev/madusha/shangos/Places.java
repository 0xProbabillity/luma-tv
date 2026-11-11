package dev.madusha.shangos;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where the weather is for. By default the main city of the TV's time zone (the place {@link
 * Daylight} uses for sunrise), which needs no request at all. Settings → Weather location can pin
 * any city instead, found with Open-Meteo's geocoding (free, no account or key); only what the
 * user types in that search leaves the TV, and only while they search.
 *
 * <p>Adapted from Luma TV 2.5 (MIT). Its automatic lookup of the city of the TV's internet address
 * (GeoJS, ipwho.is) is deliberately not part of shangOS: it would send the TV's address to two more
 * services.
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

    /**
     * Cities matching a name: towns and cities only, those called what was typed first. Blocks:
     * call it off the main thread, and only when the user allows weather (see {@link Net}).
     */
    static ArrayList<Place> search(String query) throws IOException, JSONException {
        ArrayList<Place> out = new ArrayList<>();
        final String typed = plain(query);
        for (String language : languages(query)) {
            ArrayList<Place> found = new ArrayList<>();
            boolean named = false;
            String url =
                    "https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language="
                            + language
                            + "&name="
                            + URLEncoder.encode(query.trim(), "UTF-8");
            JSONArray rows = new JSONObject(Net.json(url)).optJSONArray("results");
            for (int i = 0; rows != null && i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                String name = r.optString("name");
                if (name.isEmpty() || !r.has("latitude") || !r.has("longitude")) continue;
                if (!isTown(r.optString("feature_code"))) continue;
                Place p =
                        new Place(
                                name,
                                join(name, r.optString("admin1"), r.optString("country")),
                                r.optString("country_code"),
                                r.getDouble("latitude"),
                                r.getDouble("longitude"));
                boolean duplicate = false;
                for (Place q : found) duplicate |= q.full().equals(p.full());
                if (!duplicate) {
                    found.add(p);
                    named |= plain(name).startsWith(typed);
                }
            }
            // A language that finds only other names ("Минск" in English finds Minskoye)
            // leaves the next one a try.
            if (out.isEmpty() || named) out = found;
            if (named) break;
        }
        rank(out, typed);
        return out;
    }

    /** Towns and cities only: no peaks, airports, dams, city districts or abandoned places. */
    static boolean isTown(String featureCode) {
        return featureCode.isEmpty()
                || (featureCode.startsWith("PPL")
                        && !Arrays.asList("PPLX", "PPLH", "PPLQ", "PPLW", "PPLCH")
                                .contains(featureCode));
    }

    /** Places called what was typed first ("Chicago" before Willard, Ohio, which also has it). */
    static void rank(List<Place> places, String typed) {
        Collections.sort(
                places,
                (a, b) -> {
                    boolean x = !plain(a.name).startsWith(typed);
                    boolean y = !plain(b.name).startsWith(typed);
                    return Boolean.compare(x, y);
                });
    }

    /** Lower case without accents: "Malmö" and "malmo" match. */
    static String plain(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Open-Meteo finds names in the language it is asked for: English first; then, for a name in
     * another alphabet, the TV's language and the languages written in that alphabet ("Берлин"
     * is found in Russian, "Київ" in Ukrainian). The cities then come back in that language.
     */
    static Set<String> languages(String query) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add("en");
        Character.UnicodeScript script = null;
        for (int i = 0; i < query.length() && script == null; i = query.offsetByCodePoints(i, 1)) {
            int c = query.codePointAt(i);
            if (!Character.isLetter(c)) continue;
            Character.UnicodeScript s = Character.UnicodeScript.of(c);
            if (s != Character.UnicodeScript.LATIN) script = s;
        }
        if (script == null) return out;
        out.add(Locale.getDefault().getLanguage());
        switch (script) {
            case CYRILLIC:
                out.addAll(Arrays.asList("ru", "uk", "bg", "sr", "be", "kk", "mk"));
                break;
            case GREEK:
                out.add("el");
                break;
            case ARABIC:
                out.addAll(Arrays.asList("ar", "fa", "ur"));
                break;
            case HEBREW:
                out.add("he");
                break;
            case HAN:
                out.addAll(Arrays.asList("zh", "ja"));
                break;
            case HIRAGANA:
            case KATAKANA:
                out.add("ja");
                break;
            case HANGUL:
                out.add("ko");
                break;
            case THAI:
                out.add("th");
                break;
            case GEORGIAN:
                out.add("ka");
                break;
            case ARMENIAN:
                out.add("hy");
                break;
            case DEVANAGARI:
                out.addAll(Arrays.asList("hi", "mr", "ne"));
                break;
            default:
                break;
        }
        return out;
    }

    /** "Region, Country", leaving out parts that repeat the city or each other. */
    static String join(String city, String region, String country) {
        StringBuilder b = new StringBuilder();
        for (String part : new String[] {region, country}) {
            part = part == null ? "" : part.trim();
            boolean repeat = part.equalsIgnoreCase(city) || b.toString().equalsIgnoreCase(part);
            if (part.isEmpty() || repeat) continue;
            if (b.length() > 0) b.append(", ");
            b.append(part);
        }
        return b.toString();
    }
}
