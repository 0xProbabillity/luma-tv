package dev.madusha.shangos;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Android TV's Watch Next list: the "Continue watching" programmes that apps such as YouTube and
 * Netflix share with the system. Reading it needs the TV listings permission. The Up Next widget
 * shows all of them; the hero shows the newest one of the focused app.
 */
final class WatchNext {
    static final Uri CONTENT = Uri.parse("content://android.media.tv/watch_next_program");
    static final String LISTINGS = "android.permission.READ_TV_LISTINGS";

    /** One entry of the list. */
    static final class Program {
        long id, position, duration, engaged;
        String pkg, title, episodeTitle, season, episode, poster, thumbnail, intentUri;
        int type;

        /** The landscape picture if the app gave one, else its poster; null when it gave neither. */
        String art() {
            return thumbnail != null ? thumbnail : poster;
        }
    }

    private final Context context;
    private final Executor io;
    private volatile Map<String, Program> newest = Collections.emptyMap();

    WatchNext(Context context, Executor io) {
        this.context = context.getApplicationContext();
        this.io = io;
    }

    /** The programme the app most recently reported, or null. */
    Program latest(String pkg) {
        return newest.get(pkg);
    }

    /** Reads the list again off the main thread; {@code done} runs on a background thread. */
    void refresh(Runnable done) {
        io.execute(
                () -> {
                    HashMap<String, Program> map = new HashMap<>();
                    for (Program p : query(context)) map.putIfAbsent(p.pkg, p); // newest first
                    newest = map;
                    done.run();
                });
    }

    /**
     * Every browsable programme, most recently watched first. Empty when the permission is not
     * granted or the provider refuses (some TVs have none). Blocking: never on the main thread.
     */
    static ArrayList<Program> query(Context context) {
        ArrayList<Program> out = new ArrayList<>();
        if (context.checkSelfPermission(LISTINGS) != PackageManager.PERMISSION_GRANTED) return out;
        String[] columns = {
            "_id",
            "package_name",
            "title",
            "episode_title",
            "season_display_number",
            "episode_display_number",
            "poster_art_uri",
            "thumbnail_uri",
            "intent_uri",
            "last_playback_position_millis",
            "duration_millis",
            "last_engagement_time_utc_millis",
            "watch_next_type",
            "browsable"
        };
        try (Cursor c = context.getContentResolver().query(CONTENT, columns, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                if (!c.isNull(13) && c.getInt(13) == 0) continue;
                String pkg = c.getString(1);
                if (pkg == null) continue;
                Program p = new Program();
                p.id = c.getLong(0);
                p.pkg = pkg;
                p.title = c.getString(2);
                p.episodeTitle = c.getString(3);
                p.season = c.getString(4);
                p.episode = c.getString(5);
                p.poster = c.getString(6);
                p.thumbnail = c.getString(7);
                p.intentUri = c.getString(8);
                p.position = c.getLong(9);
                p.duration = c.getLong(10);
                p.engaged = c.getLong(11);
                p.type = c.getInt(12);
                out.add(p);
            }
        } catch (RuntimeException ignored) {
            // The provider can refuse a query; callers then fall back to when the app was opened.
        }
        Collections.sort(out, (a, b) -> Long.compare(b.engaged, a.engaged));
        return out;
    }
}
