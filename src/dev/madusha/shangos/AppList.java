package dev.madusha.shangos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which apps Home shows, and in what order. Pure: package names in, package names out, no Views, no
 * Activity, no Android classes, so it is unit tested off-device. {@code MainActivity} maps the
 * result back to {@link App} objects through {@link AppRepo#app}.
 *
 * <p>Edits never mutate their inputs; each returns an {@link Edit} to save with {@link
 * Prefs#setOrderAndHidden}.
 */
final class AppList {
    private AppList() {}

    /** The saved order and hidden set after one change. */
    static final class Edit {
        final ArrayList<String> order;
        final Set<String> hidden;

        Edit(ArrayList<String> order, Set<String> hidden) {
            this.order = order;
            this.hidden = hidden;
        }

        String orderCsv() {
            return String.join(",", order);
        }
    }

    /**
     * The full order: the saved one first, then {@code first} (apps pinned to the front on a fresh
     * install), then any other installed app, in the installer's order. Never repeats a package.
     */
    static ArrayList<String> order(String saved, String[] first, Collection<String> installed) {
        ArrayList<String> ids = new ArrayList<>();
        if (saved != null && !saved.isEmpty()) ids.addAll(Arrays.asList(saved.split(",")));
        for (String pkg : first) if (!ids.contains(pkg)) ids.add(pkg);
        for (String pkg : installed) if (!ids.contains(pkg)) ids.add(pkg);
        return ids;
    }

    /**
     * The packages to show: those of {@code order} that are installed, minus the hidden ones unless
     * {@code all} is set (the Apps tab shows everything).
     */
    static ArrayList<String> visible(
            List<String> order, Set<String> hidden, Set<String> installed, boolean all) {
        ArrayList<String> result = new ArrayList<>();
        for (String id : order)
            if (installed.contains(id) && (all || !hidden.contains(id))) result.add(id);
        return result;
    }

    /** The Apps tab keeps the saved order (and grab to move works). */
    static final String MANUAL = "manual";

    /** The Apps tab is A to Z. */
    static final String AZ = "az";

    /** The Apps tab shows the most recently opened first. */
    static final String RECENT = "recent";

    /**
     * {@code pkgs} in the order a sort mode asks for: {@link #AZ} by label (ignoring case),
     * {@link #RECENT} by when each was last opened (newest first, those never opened after them
     * in their old order), anything else unchanged. A new list; the sort is stable.
     */
    static ArrayList<String> sorted(
            String mode,
            List<String> pkgs,
            java.util.function.Function<String, String> label,
            java.util.function.ToLongFunction<String> lastOpened) {
        ArrayList<String> out = new ArrayList<>(pkgs);
        if (AZ.equals(mode))
            out.sort((a, b) -> label.apply(a).compareToIgnoreCase(label.apply(b)));
        else if (RECENT.equals(mode))
            out.sort((a, b) -> Long.compare(lastOpened.applyAsLong(b), lastOpened.applyAsLong(a)));
        return out;
    }

    /** Puts {@code pkg} first. Moving an app to the front also un-hides it. */
    static Edit moveFirst(List<String> order, Set<String> hidden, String pkg) {
        ArrayList<String> ids = new ArrayList<>(order);
        ids.remove(pkg);
        ids.add(0, pkg);
        Set<String> h = new HashSet<>(hidden);
        h.remove(pkg);
        return new Edit(ids, h);
    }

    /**
     * Moves {@code pkg} into the place of {@code target}: just before it when moving towards the
     * front, just after it when moving towards the back, so the two swap places on screen even
     * when hidden or uninstalled apps sit between them in the saved order. Nothing changes if
     * either is unknown.
     */
    static Edit moveOver(
            List<String> order, Set<String> hidden, String pkg, String target, boolean forward) {
        ArrayList<String> ids = new ArrayList<>(order);
        if (ids.contains(pkg) && ids.contains(target) && !pkg.equals(target)) {
            ids.remove(pkg);
            int at = ids.indexOf(target);
            ids.add(forward ? at + 1 : at, pkg);
        }
        return new Edit(ids, new HashSet<>(hidden));
    }

    static Edit toggleHidden(List<String> order, Set<String> hidden, String pkg) {
        Set<String> h = new HashSet<>(hidden);
        if (!h.remove(pkg)) h.add(pkg);
        return new Edit(new ArrayList<>(order), h);
    }

    /**
     * The saved "recently opened" list after opening {@code pkg} at {@code now}: comma-separated
     * {@code package:epochMillis}, newest first, one entry per app, at most {@code max} entries.
     * Format shared with {@link Recency#lastOpened}.
     */
    static String remember(String recent, String pkg, long now, int max) {
        ArrayList<String> list = new ArrayList<>();
        list.add(pkg + ":" + now);
        if (recent != null)
            for (String entry : recent.split(","))
                if (!entry.isEmpty() && !entry.startsWith(pkg + ":") && list.size() < max)
                    list.add(entry);
        return String.join(",", list);
    }
}
