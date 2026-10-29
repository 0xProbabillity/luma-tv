package com.madushas.shangos;

import android.content.SharedPreferences;
import java.util.*;

/** In-memory SharedPreferences for unit tests. */
class FakePrefs implements SharedPreferences {
    final Map<String, Object> data = new HashMap<>();
    @Override public Map<String, ?> getAll() { return data; }
    @Override public String getString(String k, String d) { return data.containsKey(k) ? (String) data.get(k) : d; }
    @SuppressWarnings("unchecked") @Override public Set<String> getStringSet(String k, Set<String> d) { return data.containsKey(k) ? (Set<String>) data.get(k) : d; }
    @Override public int getInt(String k, int d) { return data.containsKey(k) ? (Integer) data.get(k) : d; }
    @Override public long getLong(String k, long d) { return data.containsKey(k) ? (Long) data.get(k) : d; }
    @Override public float getFloat(String k, float d) { return data.containsKey(k) ? (Float) data.get(k) : d; }
    @Override public boolean getBoolean(String k, boolean d) { return data.containsKey(k) ? (Boolean) data.get(k) : d; }
    @Override public boolean contains(String k) { return data.containsKey(k); }
    @Override public Editor edit() {
        return new Editor() {
            final Map<String, Object> pending = new HashMap<>(); final Set<String> removed = new HashSet<>(); boolean clear;
            @Override public Editor putString(String k, String v) { pending.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { pending.put(k, new HashSet<>(v)); return this; }
            @Override public Editor putInt(String k, int v) { pending.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { pending.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { pending.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
            @Override public Editor remove(String k) { removed.add(k); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() { if (clear) data.clear(); data.keySet().removeAll(removed); data.putAll(pending); return true; }
            @Override public void apply() { commit(); }
        };
    }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
}
