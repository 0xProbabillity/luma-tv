package dev.madusha.shangos;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The time and date as this TV is set to show them: its language and region, and its 12 or 24 hour
 * choice. Used by the Home clock, the greeting's date and the screen saver, so all three agree.
 */
final class Formats {
    private Formats() {}

    /** "3:45 PM" or "15:45". */
    static String time(Context context, long millis) {
        Locale locale = Locale.getDefault();
        String skeleton = DateFormat.is24HourFormat(context) ? "Hm" : "hm";
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
                .format(new Date(millis));
    }

    /** "Saturday, November 7" in the TV's language. */
    static String date(long millis) {
        Locale locale = Locale.getDefault();
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
                .format(new Date(millis));
    }
}
