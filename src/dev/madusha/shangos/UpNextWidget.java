package dev.madusha.shangos;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Process;
import android.view.*;
import android.widget.*;

import java.util.*;

/**
 * Up Next: what you were watching, from Android TV's Watch Next list (the "Continue watching" row
 * that apps such as YouTube and Netflix fill in), then the apps you used most recently. OK resumes
 * the programme in its app. Reading Watch Next needs the TV listings permission; recent apps come
 * from usage access when granted, else from the apps opened from shangOS. Nothing leaves the TV
 * except poster pictures loaded from the apps' own links.
 */
final class UpNextWidget extends Widget {
    static final int SLOTS = 3;
    static final float PAD = 18, GAP_X = 12, CARD_Y = 38;

    static final class Item {
        String key, title, line, detail, pkg, art;
        Intent intent;
        float progress = -1;
        boolean app, allow;
    }

    final float cardW = (w - PAD * 2 - GAP_X * (SLOTS - 1)) / SLOTS, cardH = cardW * 9 / 16;
    final FrameLayout cards;
    final TextView empty;
    String shown = null; // null: nothing shown yet, so an empty list still shows its message

    UpNextWidget(Context context, WidgetBoard.Host host) {
        super(context, host, WidgetBoard.width("upnext"));
        TextView caption = text("Up Next", 12.5f, ink(0x99), "sans-serif-medium");
        addView(caption, pos(200, 16, PAD + 2, 14));
        cards = new FrameLayout(context);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        addView(cards, new LayoutParams(-1, -1));
        empty = text("Shows and videos you start will appear here.", 13, ink(0x99), "sans-serif");
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        addView(empty, pos(w - PAD * 2, 20, PAD, 88));
        refresh();
    }

    @Override
    void refresh() {
        final Context app = getContext().getApplicationContext();
        WidgetBoard.IO.execute(
                () -> {
                    ArrayList<Item> items = load(app);
                    post(() -> show(items));
                });
    }

    private ArrayList<Item> load(Context context) {
        ArrayList<Item> items = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        if (Promo.on) {
            for (Object[] v : Promo.VIDEOS) {
                Item i = new Item();
                i.pkg = "com.google.android.youtube.tv";
                i.key = "promo:" + v[0];
                i.title = (String) v[0];
                i.line = (String) v[1];
                i.detail = (String) v[2];
                i.art = "res:" + v[3];
                i.progress = (Float) v[4] > 0 ? (Float) v[4] : -1;
                items.add(i);
            }
            return items;
        }
        boolean listings =
                context.checkSelfPermission(WatchNext.LISTINGS) == PackageManager.PERMISSION_GRANTED;
        boolean exists = true;
        try {
            context.getPackageManager().getPermissionInfo(WatchNext.LISTINGS, 0);
        } catch (PackageManager.NameNotFoundException e) {
            exists = false;
        } // no TV provider (not a TV): no Watch Next to ask for
        if (listings)
            for (Item i : watchNext(context))
                if (items.size() < SLOTS && seen.add(i.key)) {
                    items.add(i);
                }
        HashSet<String> withPrograms = new HashSet<>();
        for (Item i : items) withPrograms.add(i.pkg);
        int room = SLOTS - items.size() - (listings || !exists ? 0 : 1);
        for (String pkg : recent(context)) {
            if (room <= 0) break;
            if (withPrograms.contains(pkg)) continue;
            App a = host.app(pkg);
            if (a == null) continue;
            Item i = new Item();
            i.app = true;
            i.pkg = pkg;
            i.key = "app:" + pkg;
            i.title = a.label;
            long at = lastUsed.containsKey(pkg) ? lastUsed.get(pkg) : 0;
            i.line = at > 0 ? ago(at) : "Recently used";
            i.detail =
                    at > 0
                            ? "Opened "
                                    + (ago(at).equals("Just now") || ago(at).equals("Yesterday")
                                            ? ago(at).toLowerCase(Locale.ENGLISH)
                                            : ago(at))
                            : "Recently used";
            items.add(i);
            room--;
        }
        if (!listings && exists) {
            Item i = new Item();
            i.allow = true;
            i.key = "allow";
            i.title = "Continue watching";
            i.line = "Allow access";
            i.detail =
                    "Allow shangOS to read the Continue watching list that your apps share with"
                            + " Android TV.";
            items.add(i);
        }
        return items;
    }

    /** Android TV's Watch Next programmes, most recently watched first. */
    private ArrayList<Item> watchNext(Context context) {
        ArrayList<Item> out = new ArrayList<>();
        for (WatchNext.Program p : WatchNext.query(context)) {
            App app = host.app(p.pkg);
            if (app == null) continue;
            Item i = new Item();
            i.pkg = p.pkg;
            i.key = "tv:" + p.id;
            i.title = p.title == null ? app.label : p.title;
            i.art = p.art();
            try {
                if (p.intentUri != null)
                    i.intent = Intent.parseUri(p.intentUri, Intent.URI_INTENT_SCHEME);
            } catch (Exception ignored) {
            }
            i.progress = Resume.progress(p.position, p.duration);
            String episode = Resume.episode(p.season, p.episode);
            String state =
                    Resume.started(p.position, p.duration)
                            ? Resume.left(p.duration - p.position)
                            : p.type == Resume.NEXT
                                    ? "Next episode"
                                    : p.type == Resume.NEW
                                            ? "New"
                                            : p.type == Resume.WATCHLIST ? "Watchlist" : "";
            i.line = join(episode.isEmpty() ? state : episode, app.label);
            i.detail =
                    join(
                            join(episode, p.episodeTitle == null ? "" : p.episodeTitle),
                            join(state, "in " + app.label));
            out.add(i);
        }
        return out;
    }

    final HashMap<String, Long> lastUsed = new HashMap<>();

    /**
     * Recently used apps: usage access when granted, else shangOS's own record of the apps it
     * opened.
     */
    private ArrayList<String> recent(Context context) {
        ArrayList<String> out = new ArrayList<>();
        lastUsed.clear();
        long now = System.currentTimeMillis();
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            Process.myUid(),
                            context.getPackageName())
                    == AppOpsManager.MODE_ALLOWED) {
                UsageStatsManager usage =
                        (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                ArrayList<UsageStats> stats =
                        new ArrayList<>(
                                usage.queryAndAggregateUsageStats(now - 14L * 86400000L, now)
                                        .values());
                Collections.sort(
                        stats, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
                for (UsageStats s : stats) {
                    String pkg = s.getPackageName();
                    if (s.getTotalTimeInForeground() < 15000 || skip(context, pkg)) continue;
                    out.add(pkg);
                    lastUsed.put(pkg, s.getLastTimeUsed());
                }
            }
        } catch (RuntimeException ignored) {
        }
        for (String entry : host.prefs().recent().split(",")) {
            String[] f = entry.split(":");
            if (f.length != 2 || out.contains(f[0]) || skip(context, f[0])) continue;
            try {
                long at = Long.parseLong(f[1]);
                int i = 0;
                while (i < out.size() && lastUsed.getOrDefault(out.get(i), 0L) > at) i++;
                out.add(i, f[0]);
                lastUsed.put(f[0], at);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static boolean skip(Context c, String pkg) {
        return pkg.equals(c.getPackageName())
                || pkg.startsWith("com.android.")
                || pkg.startsWith("com.google.android.tv")
                || pkg.equals("com.google.android.katniss")
                || pkg.contains("launcher")
                || pkg.contains("setupwraith");
    }

    static String join(String a, String b) {
        return Resume.join(a, b);
    }

    static String ago(long at) {
        long m = Math.max(0, (System.currentTimeMillis() - at) / 60000);
        if (m < 2) return "Just now";
        if (m < 60) return m + " min ago";
        if (m < 1440) return m / 60 + " h ago";
        long d = m / 1440;
        return d == 1 ? "Yesterday" : d + " days ago";
    }

    /** Rebuilds the cards only when the list changed, and keeps focus on the same slot. */
    private void show(ArrayList<Item> items) {
        StringBuilder key = new StringBuilder();
        for (Item i : items)
            key.append(i.key).append('|').append(i.line).append('|').append(i.progress).append(';');
        if (key.toString().equals(shown) || !isAttachedToWindow()) return;
        shown = key.toString();
        int focusedSlot = -1;
        for (int s = 0; s < cards.getChildCount(); s++)
            if (cards.getChildAt(s).hasFocus()) focusedSlot = s;
        cards.removeAllViews();
        empty.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        for (int s = 0; s < items.size(); s++)
            cards.addView(
                    card(items.get(s), s),
                    pos(cardW, cardH + 46, PAD + s * (cardW + GAP_X), CARD_Y));
        if (focusedSlot >= 0 && cards.getChildCount() > 0)
            cards.getChildAt(Math.min(focusedSlot, cards.getChildCount() - 1)).requestFocus();
    }

    private View card(Item item, int slot) {
        Context c = getContext();
        FrameLayout cell = new FrameLayout(c);
        cell.setClipChildren(false);
        FrameLayout face = new FrameLayout(c);
        face.setClipToOutline(true);
        face.setElevation(px(2));
        GradientDrawable bg =
                round(item.allow ? (light() ? 0x14000000 : 0x1AFFFFFF) : 0xFF1C1C1E, 12);
        face.setBackground(bg);
        cell.addView(face, pos(cardW, cardH, 0, 0));
        if (item.allow) {
            Glyph g = new Glyph(c).kind("tv").color(ink(0xD9));
            face.addView(g, pos(30, 30, (cardW - 30) / 2, cardH / 2 - 24));
            TextView t = text("Allow access", 12, ink(0xD9), "sans-serif-medium");
            t.setGravity(Gravity.CENTER);
            face.addView(t, pos(cardW, 16, 0, cardH / 2 + 12));
        } else if (item.app) {
            View art = host.art(item.pkg);
            if (art != null) face.addView(art, new LayoutParams(-1, -1));
        } else {
            ImageView poster = new ImageView(c);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            face.addView(poster, new LayoutParams(-1, -1));
            View art = host.art(item.pkg);
            if (art != null) {
                art.setAlpha(1);
                face.addView(art, 0, new LayoutParams(-1, -1));
            }
            if (item.art != null) showPoster(item.art, poster, art);
            View shade = new View(c);
            shade.setBackground(
                    new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[] {0x00000000, 0x00000000, 0x8C000000}));
            face.addView(shade, new LayoutParams(-1, -1));
            if (item.progress > 0) {
                View track = new View(c);
                track.setBackground(round(0x59FFFFFF, 1.5f));
                face.addView(track, pos(cardW - 20, 3, 10, cardH - 10));
                View fill = new View(c);
                fill.setBackground(round(Color.WHITE, 1.5f));
                face.addView(fill, pos((cardW - 20) * item.progress, 3, 10, cardH - 10));
            }
        }
        GradientDrawable rim = round(Color.TRANSPARENT, 12);
        rim.setStroke(px(1.2f), 0xF0FFFFFF);
        rim.setAlpha(0);
        face.setForeground(rim);
        TextView title = text(item.title, 13, ink(0xF2), "sans-serif-medium");
        cell.addView(title, pos(cardW, 17, 1, cardH + 9));
        TextView line = text(item.line == null ? "" : item.line, 11.5f, ink(0x99), "sans-serif");
        cell.addView(line, pos(cardW, 15, 1, cardH + 28));
        face.setPivotX(px(cardW / 2));
        face.setPivotY(px(cardH / 2));
        cell.setFocusable(true);
        cell.setFocusableInTouchMode(true);
        cell.setSoundEffectsEnabled(false);
        cell.setContentDescription(item.title);
        cell.setOnFocusChangeListener(
                (v, f) -> {
                    face.animate().cancel();
                    title.animate().cancel();
                    line.animate().cancel();
                    float s = f ? 1.08f : 1;
                    if (motion()) {
                        face.animate()
                                .scaleX(s)
                                .scaleY(s)
                                .translationY(f ? -px(2) : 0)
                                .setDuration(f ? 460 : 300)
                                .setInterpolator(f ? Motion.WIDGET : Motion.EASE)
                                .start();
                        title.animate()
                                .translationY(f ? px(5) : 0)
                                .setDuration(300)
                                .setInterpolator(Motion.EASE)
                                .start();
                        line.animate()
                                .translationY(f ? px(5) : 0)
                                .setDuration(300)
                                .setInterpolator(Motion.EASE)
                                .start();
                    } else {
                        face.setScaleX(s);
                        face.setScaleY(s);
                    }
                    face.setElevation(px(f ? 14 : 2));
                    rim.setAlpha(f ? 220 : 0);
                    cell.setTranslationZ(px(f ? 20 : 0));
                    if (f) {
                        host.hero("upnext:" + item.key, item.title, item.detail);
                        host.step(slot);
                    }
                });
        cell.setOnClickListener(
                v -> {
                    host.open();
                    press(face, 1.08f);
                    open(item);
                });
        return cell;
    }

    private void open(Item item) {
        if (item.allow) {
            android.app.Activity a = host.activity();
            if (a.shouldShowRequestPermissionRationale(WatchNext.LISTINGS) || !host.prefs().askedListings()) {
                host.prefs().setAskedListings();
                a.requestPermissions(new String[] {WatchNext.LISTINGS}, 7);
            } else
                host.start(
                        new Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + a.getPackageName())));
            return;
        }
        if (item.intent != null) {
            try {
                item.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.start(item.intent);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        host.launch(item.pkg);
    }

    /** Fills a card's picture: at once if it is already decoded, else when it has loaded. */
    private void showPoster(String link, ImageView view, View fallback) {
        Bitmap cached = Posters.cached(link, px(cardW) * 2);
        if (cached != null) {
            view.setImageBitmap(cached);
            if (fallback != null) fallback.setVisibility(INVISIBLE);
            return;
        }
        Posters.load(
                getContext(),
                host.prefs(),
                view,
                link,
                px(cardW) * 2,
                result -> {
                    view.setImageBitmap(result);
                    view.setAlpha(0f);
                    view.animate()
                            .alpha(1)
                            .setDuration(motion() ? 320 : 0)
                            .withEndAction(
                                    () -> {
                                        if (fallback != null) fallback.setVisibility(INVISIBLE);
                                    })
                            .start();
                });
    }
}
