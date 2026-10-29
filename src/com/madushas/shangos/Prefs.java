package com.madushas.shangos;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** All saved settings, typed, with their defaults. The only class that knows the key names (CI checks this).
 *  Settings live in one private file; {@link #SCHEMA} lets future versions migrate old data instead of guessing. */
final class Prefs {
    /** Bump when a key changes meaning, and add a step to {@link #migrate}. */
    static final int SCHEMA = 1;
    static final int DEFAULT_WALLPAPER = 7; // Silver
    static final int POSTERS_OFF = 0, POSTERS_UNMETERED = 1, POSTERS_ANY = 2;

    private final SharedPreferences sp;

    Prefs(SharedPreferences sp) { this.sp = sp; migrate(sp); }
    static Prefs of(Context context) { return new Prefs(context.getSharedPreferences("shangos", Context.MODE_PRIVATE)); }

    /** Brings data written by an older version up to {@link #SCHEMA}. Newer data (a downgrade) is left alone. */
    static void migrate(SharedPreferences sp) {
        int version = sp.getInt("v", 0);
        if (version >= SCHEMA) return;
        SharedPreferences.Editor e = sp.edit();
        if (version < 1) e.remove("silver_default").remove("scene_app_packages"); // keys of the upstream project
        e.putInt("v", SCHEMA).apply();
    }

    // ---- Switches shown as toggles in Settings (all on unless stated) ----
    /** Keys the Settings toggle rows use. */
    static final String CLOCK = "clock", MOTION = "motion", AMBIENT = "ambient", SOUND = "sound";
    boolean flag(String key) { return sp.getBoolean(key, true); }
    void setFlag(String key, boolean on) { sp.edit().putBoolean(key, on).apply(); }
    boolean clock() { return flag(CLOCK); }
    /** The user's own switch; the system's animation setting is checked separately by the caller. */
    boolean motion() { return flag(MOTION); }
    boolean ambient() { return flag(AMBIENT); }
    boolean sound() { return flag(SOUND); }

    // ---- Performance ----
    static final int PERF_AUTO = 0, PERF_QUALITY = 1, PERF_LIGHT = 2;
    /** Auto picks by the TV's memory; Quality never trades looks for speed; Light keeps the wallpaper still. */
    int perf() { return Math.max(PERF_AUTO, Math.min(PERF_LIGHT, sp.getInt("perf", PERF_AUTO))); }
    void setPerf(int profile) { sp.edit().putInt("perf", profile).apply(); }

    // ---- Network (see Net) ----
    boolean offline() { return sp.getBoolean("offline", false); }
    void setOffline(boolean on) { sp.edit().putBoolean("offline", on).apply(); }
    /** Weather is opt-in: off until the user turns it on. */
    boolean weather() { return sp.getBoolean("weather", false); }
    void setWeather(boolean on) { sp.edit().putBoolean("weather", on).apply(); }
    int postersMode() { return Math.max(POSTERS_OFF, Math.min(POSTERS_ANY, sp.getInt("posters", POSTERS_UNMETERED))); }
    void setPostersMode(int mode) { sp.edit().putInt("posters", mode).apply(); }

    // ---- Personal ----
    String name() { return sp.getString("name", "").trim(); }
    void setName(String value) { sp.edit().putString("name", value.trim()).apply(); }
    int wallpaper() { return sp.getInt("wallpaper", DEFAULT_WALLPAPER); }
    void setWallpaper(int index) { sp.edit().putInt("wallpaper", index).apply(); }
    /** "auto", "c" or "f". */
    String tempUnit() { return sp.getString("temp_unit", "auto"); }
    void setTempUnit(String unit) { sp.edit().putString("temp_unit", unit).apply(); }

    // ---- Apps ----
    /** Package names in the user's order, comma separated. */
    String order() { return sp.getString("order", ""); }
    Set<String> hidden() { return new HashSet<>(sp.getStringSet("hidden", Collections.emptySet())); }
    void setOrderAndHidden(String order, Set<String> hidden) { sp.edit().putString("order", order).putStringSet("hidden", hidden).apply(); }
    /** "package:epochMillis" entries, newest first. */
    String recent() { return sp.getString("recent", ""); }
    void setRecent(String value) { sp.edit().putString("recent", value).apply(); }

    // ---- Home widgets ----
    String widgetOrder() { return sp.getString("widget_order", ""); }
    String widgetOff() { return sp.getString("widget_off", ""); }
    /** {@code order} null leaves the saved order alone (the promo demo must not overwrite the user's). */
    void saveWidgets(String order, String off, boolean weather) {
        SharedPreferences.Editor e = sp.edit().putString("widget_off", off).putBoolean("weather", weather);
        if (order != null) e.putString("widget_order", order);
        e.apply();
    }

    // ---- Caches and one-time flags ----
    String weatherJson() { return sp.getString("weather_json", ""); }
    String weatherKey() { return sp.getString("weather_key", ""); }
    long weatherAt() { return sp.getLong("weather_at", 0); }
    void setWeatherCache(String json, String key, long at) { sp.edit().putString("weather_json", json).putString("weather_key", key).putLong("weather_at", at).apply(); }
    boolean askedListings() { return sp.getBoolean("asked_listings", false); }
    void setAskedListings() { sp.edit().putBoolean("asked_listings", true).apply(); }
}
