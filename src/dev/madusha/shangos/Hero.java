package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.function.BooleanSupplier;

/**
 * The big title and one line under it at the top of Home and the Apps tab: the greeting and date
 * while nothing is focused, then the focused app's name and when it was last opened (or what a
 * widget says). When the app has something to resume (Android TV's Watch Next), the line is "Continue:
 * Stranger Things · S4 E3 · 18 min left" with its picture at the right; otherwise it is when the app
 * was last opened. Cross-fades between them, and once a minute brings the line up to date.
 */
final class Hero {
    /** The key of the greeting; any other key is an app's package name or a widget's id. */
    private static final String GREETING = "@greeting";

    private final Context context;
    private final Ui ui;
    private final Prefs prefs;
    private final BooleanSupplier motionOn, paleOn;
    private final Handler handler;
    private final WatchNext watchNext;
    private TextView title, description;
    private ImageView poster, backdrop;
    private String posterLink;
    private String key = "";
    private boolean home;
    private boolean hold;
    private boolean running;

    /** The app whose line is shown, so the line can be brought up to date. */
    private App current;

    private final Runnable tick =
            new Runnable() {
                public void run() {
                    if (!running) return;
                    refresh();
                    schedule();
                }
            };

    Hero(Context context, Ui ui, Prefs prefs, Handler handler,
            WatchNext watchNext,
            BooleanSupplier motionOn,
            BooleanSupplier paleOn) {
        this.context = context;
        this.ui = ui;
        this.prefs = prefs;
        this.handler = handler;
        this.watchNext = watchNext;
        this.motionOn = motionOn;
        this.paleOn = paleOn;
    }

    /**
     * A page is about to be shown: {@code onHome} says whether it is Home (the only page whose hero
     * follows focus). Drops the views of the page before.
     */
    void reset(boolean onHome) {
        home = onHome;
        title = null;
        description = null;
        poster = null;
        backdrop = null;
        posterLink = null;
        current = null;
    }

    /** The views for Home ({@code onHome}) or the Apps tab; the caller places them. */
    FrameLayout build(boolean onHome) {
        FrameLayout view =
                new FrameLayout(context) {
                    @Override
                    public boolean hasOverlappingRendering() {
                        return false;
                    }
                };
        boolean light = paleOn.getAsBoolean();
        title =
                ui.text(
                        onHome ? welcome() : "All your apps",
                        42,
                        Theme.ink(light, 0xFF),
                        "sans-serif-medium");
        title.setLetterSpacing(-.035f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        ui.shadow(title, 10, light);
        view.addView(title, ui.pos(onHome ? 392 : 860, 54, 0, 0));
        description =
                ui.text(
                        onHome ? today() : "Everything installed on this TV.",
                        16,
                        Theme.ink(light, 0xB3),
                        "sans-serif");
        description.setSingleLine(true);
        description.setEllipsize(TextUtils.TruncateAt.END);
        ui.shadow(description, 8, light);
        view.addView(description, ui.pos(onHome ? 392 : 860, 22, 1, 60));
        key = onHome ? GREETING : "";
        if (onHome) {
            backdrop = new ImageView(context);
            backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
            backdrop.setAlpha(0f);
            // The programme's picture, when there is one to resume; it sits clear of the text.
            poster = new ImageView(context);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(ui.shape(0x14FFFFFF, 10));
            poster.setClipToOutline(true);
            poster.setAlpha(0f);
            view.addView(poster, ui.pos(128, 72, 736, 8));
        }
        return view;
    }

    /**
     * While true, the next focus change keeps the greeting instead of showing that app: Home
     * focuses the first tile on arrival without saying so.
     */
    void hold(boolean value) {
        hold = value;
    }

    /** Brings the line up to date now and at the start of every minute until {@link #stop}. */
    void start() {
        handler.removeCallbacks(tick);
        running = true;
        refresh();
        schedule();
        reloadWatchNext();
    }

    /** Reads Continue watching again (it changes while another app plays) and updates the line. */
    void reloadWatchNext() {
        watchNext.refresh(() -> handler.post(this::refresh));
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void schedule() {
        handler.postDelayed(tick, 60_000 - System.currentTimeMillis() % 60_000 + 50);
    }

    /** The shown line again, with the time since the app was opened and today's date as of now. */
    private void refresh() {
        if (title == null || !home) return;
        if (current != null && key.equals(current.pkg)) showApp(current);
        else if (key.equals(GREETING)) show(GREETING, welcome(), today());
    }

    /** Back to "Hello" and the date. */
    void showGreeting() {
        if (title == null || !home) return;
        setPoster(null);
        show(GREETING, welcome(), today());
    }

    /** Focus arrived on {@code app}: show its name and when it was last opened. */
    void select(App app) {
        if (hold) {
            hold = false;
            return;
        }
        if (title == null || !home) return;
        showApp(app);
    }

    private void showApp(App app) {
        current = app;
        WatchNext.Program p = watchNext.latest(app.pkg);
        String resume =
                p == null
                        ? ""
                        : Resume.line(p.title, p.season, p.episode, p.position, p.duration, p.type);
        String detail =
                resume.isEmpty()
                        ? Recency.describe(
                                System.currentTimeMillis(),
                                Recency.lastOpened(prefs.recent(), app.pkg))
                        : resume;
        show(app.pkg, app.label, detail);
        setPoster(resume.isEmpty() ? null : p.art());
    }

    /** The soft artwork behind the hero (Home only): add it to the page before the hero. */
    ImageView backdrop() {
        return backdrop;
    }

    private void setBackdrop(String link) {
        final ImageView view = backdrop;
        if (view == null) return;
        if (link == null) {
            fadePoster(view, 0);
            return;
        }
        final int width = 120;
        Bitmap cached = Posters.cached(link, width);
        if (cached != null) {
            showBackdrop(view, cached);
            return;
        }
        Posters.load(
                context,
                prefs,
                view,
                link,
                width,
                bitmap -> {
                    if (backdrop == view && link.equals(posterLink)) showBackdrop(view, bitmap);
                });
    }

    private void showBackdrop(ImageView view, Bitmap poster) {
        view.setImageBitmap(Backdrop.make(poster, 240, 75));
        fadePoster(view, paleOn.getAsBoolean() ? .3f : .6f);
    }

    /** Fades the programme's picture in (loading it if need be), or out for {@code null}. */
    private void setPoster(String link) {
        if (poster == null || (link == null ? posterLink == null : link.equals(posterLink))) return;
        posterLink = link;
        setBackdrop(link);
        final ImageView view = poster;
        if (link == null) {
            fadePoster(view, 0);
            return;
        }
        Bitmap cached = Posters.cached(link, ui.px(128) * 2);
        if (cached != null) {
            view.setImageBitmap(cached);
            fadePoster(view, 1);
            return;
        }
        fadePoster(view, 0);
        Posters.load(
                context,
                prefs,
                view,
                link,
                ui.px(128) * 2,
                bitmap -> {
                    if (poster != view || !link.equals(posterLink)) return;
                    view.setImageBitmap(bitmap);
                    fadePoster(view, 1);
                });
    }

    private void fadePoster(ImageView view, float alpha) {
        view.animate().cancel();
        if (motionOn.getAsBoolean())
            view.animate().alpha(alpha).setDuration(260).setInterpolator(Motion.EASE).start();
        else view.setAlpha(alpha);
    }

    /** A widget has something to say about the focused card. */
    void widget(String widgetKey, String widgetTitle, String detail) {
        hold = false;
        setPoster(null);
        show(widgetKey, widgetTitle, detail);
    }

    /**
     * "Hello, <n>" with the name saved in Settings, else the build's greeting: "Hello", or
     * SHANGOS_GREETING / .local/greeting.txt (see build.sh).
     */
    private String welcome() {
        String name = prefs.name();
        return name.isEmpty() ? context.getString(R.string.greeting) : "Hello, " + name;
    }

    /** Under the greeting: today's date, quietly. */
    private static String today() {
        return Formats.date(System.currentTimeMillis());
    }

    /** Cross-fades to a new title and line. */
    private void show(String newKey, String newTitle, String detail) {
        if (title == null) return;
        if (newKey.equals(key)) {
            // Same subject, newer facts (a minute passed): change the line without a cross-fade.
            if (!detail.contentEquals(description.getText())) description.setText(detail);
            return;
        }
        key = newKey;
        final TextView t = title, d = description;
        t.animate().cancel();
        d.animate().cancel();
        if (!motionOn.getAsBoolean()) {
            t.setText(newTitle);
            d.setText(detail);
            t.setAlpha(1);
            d.setAlpha(1);
            t.setTranslationY(0);
            d.setTranslationY(0);
            return;
        }
        t.animate()
                .alpha(0)
                .translationY(-ui.px(4))
                .setDuration(90)
                .withEndAction(
                        () -> {
                            if (title != t || !key.equals(newKey)) return;
                            t.setText(newTitle);
                            d.setText(detail);
                            t.setTranslationY(ui.px(8));
                            d.setTranslationY(ui.px(5));
                            d.setAlpha(0);
                            t.animate()
                                    .alpha(1)
                                    .translationY(0)
                                    .setDuration(300)
                                    .setInterpolator(Motion.EASE)
                                    .start();
                            d.animate()
                                    .alpha(1)
                                    .translationY(0)
                                    .setDuration(360)
                                    .setStartDelay(30)
                                    .setInterpolator(Motion.EASE)
                                    .start();
                        })
                .start();
        d.animate().alpha(0).setDuration(90).start();
    }
}
