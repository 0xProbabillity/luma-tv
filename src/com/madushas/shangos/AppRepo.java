package com.madushas.shangos;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The installed apps. The list is read from the system only when something changed (a package was added, removed or
 * updated, or the language changed), never on every return to Home. Pictures are decoded when first drawn, off the
 * main thread, into byte-bounded caches that shrink under memory pressure.
 * <p>Why not LauncherApps: it lists only CATEGORY_LAUNCHER activities, and many TV apps declare only
 * CATEGORY_LEANBACK_LAUNCHER, so PackageManager queries plus a package-change receiver are still the way on TV.
 */
final class AppRepo {
    /** Called on the main thread when the list of apps changed (or was read for the first time). */
    interface Listener { void onAppsChanged(); }

    static final int ICON_PX = 120, BANNER_W = 320, BANNER_H = 180;
    /** Stands in for an icon that could not be loaded: draws as nothing. */
    private static final Bitmap BLANK = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

    private final Context context;
    private final PackageManager pm;
    private final Set<String> excluded;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "shangOS apps"); t.setPriority(Thread.NORM_PRIORITY - 1); return t; });
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final LruCache<String, Bitmap> icons, banners;
    private final Set<String> noBanner = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, List<Runnable>> waiting = new HashMap<>();
    private volatile List<App> apps = Collections.emptyList();
    private volatile Map<String, App> index = Collections.emptyMap();
    private volatile boolean loaded, dirty = true, active;
    private boolean registered;

    AppRepo(Context context, Set<String> excludedPackages, boolean lowMemory, Listener listener) {
        this.context = context.getApplicationContext(); this.pm = context.getPackageManager();
        this.excluded = excludedPackages; this.listener = listener;
        int scale = lowMemory ? 1 : 2; // MB: icons are 56 KB each, banners 225 KB each
        icons = new BytesCache(2 * scale * 1024 * 1024); banners = new BytesCache(3 * scale * 1024 * 1024);
    }

    private static final class BytesCache extends LruCache<String, Bitmap> {
        BytesCache(int bytes) { super(bytes); }
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    }

    // ---- The list ----
    List<App> apps() { return apps; }
    /** Looks an app up by package from any thread; null when it is not installed. */
    App app(String pkg) { return index.get(pkg); }
    boolean loaded() { return loaded; }

    /** Listens for installs, removals and updates. Call {@link #stop} when the owner goes away. */
    void start() {
        if (registered) return;
        IntentFilter packages = new IntentFilter();
        for (String action : new String[]{Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REPLACED})
            packages.addAction(action);
        packages.addDataScheme("package");
        listen(packages);
        listen(new IntentFilter(Intent.ACTION_LOCALE_CHANGED)); // labels are translated: read them again
        registered = true;
    }
    /** These are system broadcasts, so the receiver need not be exported. (The flag exists from API 33; before that it is the default.) */
    private void listen(IntentFilter filter) {
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
    }
    void stop() { if (registered) { context.unregisterReceiver(receiver); registered = false; } main.removeCallbacks(debounced); }

    /** True while the launcher is visible: a change then refreshes the list by itself instead of waiting for the next resume. */
    void setActive(boolean value) { active = value; }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            String pkg = intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
            if (pkg != null) forget(pkg);
            markDirty();
        }
    };
    private final Runnable debounced = () -> { if (active) refresh(); };

    /** The list may be out of date; it is read again when next needed (a burst of changes becomes one read). */
    void markDirty() { dirty = true; main.removeCallbacks(debounced); main.postDelayed(debounced, 1000); }
    /** Reads the list if nothing has read it yet or something changed since. Cheap when neither is true. */
    void refreshIfNeeded() { if (dirty || !loaded) refresh(); }

    private void refresh() {
        if (!refreshing.compareAndSet(false, true)) return;
        dirty = false;
        io.execute(() -> {
            List<App> found;
            try { found = query(); } catch (RuntimeException e) { Dbg.d("apps", "query failed: " + e.getClass().getSimpleName()); found = null; }
            final List<App> result = found;
            main.post(() -> {
                refreshing.set(false);
                if (result != null) publish(result);
                if (dirty) refresh(); // something changed while reading
            });
        });
    }

    private List<App> query() {
        ArrayList<App> found = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = info.activityInfo.packageName;
                if (excluded.contains(pkg) || !seen.add(pkg)) continue;
                if (pm.getLaunchIntentForPackage(pkg) == null && pm.getLeanbackLaunchIntentForPackage(pkg) == null) continue;
                try { found.add(new App(pkg, info.loadLabel(pm).toString(), new android.content.ComponentName(pkg, info.activityInfo.name))); }
                catch (RuntimeException ignored) { /* a package can disappear during an update */ }
            }
        }
        Collections.sort(found, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return found;
    }

    private void publish(List<App> found) {
        List<App> old = apps;
        boolean changed = !loaded || old.size() != found.size();
        for (int i = 0; !changed && i < found.size(); i++) changed = !old.get(i).pkg.equals(found.get(i).pkg) || !old.get(i).label.equals(found.get(i).label);
        if (!changed) return;
        HashMap<String, App> byPackage = new HashMap<>();
        for (App a : found) byPackage.put(a.pkg, a);
        apps = Collections.unmodifiableList(found); index = byPackage; loaded = true;
        listener.onAppsChanged();
    }

    // ---- Pictures ----
    /** The app's icon if it is cached, else null; in that case it is loaded in the background and {@code onLoaded} runs on the main thread. */
    Bitmap icon(App app, Runnable onLoaded) { return picture(app, icons, false, onLoaded); }
    /** The app's TV banner if it has one and it is cached; null while loading or when the app has none (see {@link #hasBanner}). */
    Bitmap banner(App app, Runnable onLoaded) { return noBanner.contains(app.pkg) ? null : picture(app, banners, true, onLoaded); }
    boolean hasBanner(App app) { return !noBanner.contains(app.pkg); }

    private Bitmap picture(App app, LruCache<String, Bitmap> cache, boolean banner, Runnable onLoaded) {
        Bitmap hit = cache.get(app.pkg);
        if (hit != null) return hit;
        String key = (banner ? "b:" : "i:") + app.pkg;
        synchronized (waiting) {
            List<Runnable> list = waiting.get(key);
            if (list != null) { if (onLoaded != null) list.add(onLoaded); return null; }
            list = new ArrayList<>(); if (onLoaded != null) list.add(onLoaded); waiting.put(key, list);
        }
        io.execute(() -> {
            Bitmap b = banner ? loadBanner(app) : loadIcon(app);
            // A failed load is remembered too, otherwise the redraw that follows would ask again, forever.
            if (b != null) cache.put(app.pkg, b); else if (banner) noBanner.add(app.pkg); else cache.put(app.pkg, BLANK);
            List<Runnable> done;
            synchronized (waiting) { done = waiting.remove(key); }
            if (done != null) main.post(() -> { for (Runnable r : done) r.run(); });
        });
        return null;
    }

    /** Loads the first few apps' pictures ahead of time (Home's favourites), so they are ready when drawn. */
    void prefetch(List<App> list, int count) {
        for (int i = 0; i < Math.min(count, list.size()); i++) { icon(list.get(i), null); banner(list.get(i), null); }
    }

    private Bitmap loadIcon(App app) {
        try { return render(pm.getActivityIcon(app.component), ICON_PX, ICON_PX); } catch (PackageManager.NameNotFoundException | RuntimeException e) { return null; }
    }
    private Bitmap loadBanner(App app) {
        try { Drawable d = pm.getApplicationBanner(app.pkg); return d == null ? null : render(d, BANNER_W, BANNER_H); } catch (PackageManager.NameNotFoundException | RuntimeException e) { return null; }
    }
    private static Bitmap render(Drawable drawable, int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, w, h); drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private void forget(String pkg) { icons.remove(pkg); banners.remove(pkg); noBanner.remove(pkg); }

    /** Frees pictures when the system wants memory back. Home hidden: keep a third (the favourites come back instantly). */
    void trim(int level) {
        if (level >= 40) { icons.evictAll(); banners.evictAll(); }               // the process is a candidate to be killed
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) { icons.trimToSize(icons.maxSize() / 3); banners.trimToSize(banners.maxSize() / 3); }
    }
}
