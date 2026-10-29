package com.madushas.shangos;

/** Debug-only logger. In release and offline builds Flavor.DEBUG is a compile-time false, so calls vanish.
 *  Never pass titles, URLs, names or anything the user would call personal. */
final class Dbg {
    private Dbg() {}
    static void d(String tag, String message) { if (Flavor.DEBUG) android.util.Log.d("shangOS/" + tag, message); }
}
