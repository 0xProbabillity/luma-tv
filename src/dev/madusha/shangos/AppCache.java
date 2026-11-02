package dev.madusha.shangos;

import java.util.ArrayList;
import java.util.List;

/**
 * The last list of apps as text, so Home can show its tiles at once on the next launch while the
 * real list is read again behind it. One line per app: package, label, activity class, separated by
 * tabs. Pure Java, so it is unit tested.
 */
final class AppCache {
    private AppCache() {}

    private static final String HEADER = "shangos-apps 1";
    private static final int MAX_ROWS = 2000;

    /** Rows are {package, label, activity class}. */
    static String encode(List<String[]> rows) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (String[] row : rows) {
            out.append(clean(row[0]))
                    .append('\t')
                    .append(clean(row[1]))
                    .append('\t')
                    .append(clean(row[2]))
                    .append('\n');
        }
        return out.toString();
    }

    /**
     * Returns the rows, or an empty list if the text is not a list this version wrote. Bad lines
     * are skipped, never fatal.
     */
    static List<String[]> decode(String text) {
        ArrayList<String[]> rows = new ArrayList<>();
        if (text == null) return rows;
        String[] lines = text.split("\n");
        if (lines.length == 0 || !HEADER.equals(lines[0])) return rows;
        for (int i = 1; i < lines.length && rows.size() < MAX_ROWS; i++) {
            String[] parts = lines[i].split("\t", -1);
            if (parts.length != 3 || parts[0].isEmpty() || parts[2].isEmpty()) continue;
            rows.add(parts);
        }
        return rows;
    }

    /** Tabs and line breaks would break the format (a label could contain anything). */
    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
