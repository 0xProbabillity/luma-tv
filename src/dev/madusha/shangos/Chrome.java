package dev.madusha.shangos;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * The parts that stay while pages change: the tab bar with its sliding pill, the clock, and the
 * shade that darkens the wallpaper behind Settings.
 */
final class Chrome {
    /** The pages, in tab order. */
    static final String[] PAGES = {"home", "apps", "settings"};

    private static final String[] TITLES = {"Home", "Apps", "Settings"};

    /** What the chrome needs from the launcher. */
    interface Host {
        boolean motion();

        /** True on a pale wallpaper (dark text). */
        boolean light();

        /** The sound of choosing something. */
        void click();

        /** The page being shown: one of {@link #PAGES}. */
        String page();

        void showPage(String page);

        /** Home's first widget, or null when Home has none. */
        View firstWidget();

        /** Focus the first (or the selected) app tile. */
        void focusFirst();
    }

    private final Context context;
    private final Ui ui;
    private final Handler handler;
    private final Host host;
    private final ArrayList<TextView> tabs = new ArrayList<>();
    private final FrameLayout bar;
    private final View indicator;
    private final GradientDrawable indicatorShape;
    private final TextView clock;
    private final View shade;
    private ValueAnimator indicatorMove;
    private int indicatorColor = 0x24FFFFFF;
    private boolean clockRunning;

    private final Runnable clockTick =
            new Runnable() {
                public void run() {
                    if (!clockRunning) return;
                    clock.setText(Formats.time(context, System.currentTimeMillis()));
                    handler.postDelayed(this, 15000);
                }
            };

    Chrome(Context context, Ui ui, Handler handler, Host host) {
        this.context = context;
        this.ui = ui;
        this.handler = handler;
        this.host = host;
        shade = new View(context);
        shade.setBackgroundColor(0x80000000);
        shade.setAlpha(0);
        LinearLayout navigation = new LinearLayout(context);
        navigation.setGravity(Gravity.CENTER);
        for (int i = 0; i < PAGES.length; i++) {
            TextView tab = newTab(i);
            tabs.add(tab);
            navigation.addView(tab, new LinearLayout.LayoutParams(-2, ui.px(36)));
        }
        bar = new FrameLayout(context);
        bar.setClipChildren(false);
        indicatorShape = ui.shape(0x24FFFFFF, 18);
        indicator = new View(context);
        indicator.setBackground(indicatorShape);
        bar.addView(indicator, new FrameLayout.LayoutParams(0, ui.px(36), Gravity.CENTER_VERTICAL));
        bar.addView(navigation, new FrameLayout.LayoutParams(-2, -1));
        bar.setPadding(ui.px(4), ui.px(2), ui.px(4), ui.px(2));
        bar.setBackground(ui.shape(0x1FFFFFFF, 22));
        clock = ui.text("", 18, 0xF2FFFFFF, "sans-serif-medium");
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        clock.setLetterSpacing(.01f);
    }

    private TextView newTab(int index) {
        TextView tab = ui.text(TITLES[index], 14, 0xB3FFFFFF, "sans-serif-medium");
        tab.setPadding(ui.px(17), ui.px(9), ui.px(17), ui.px(9));
        tab.setGravity(Gravity.CENTER);
        tab.setFocusable(true);
        tab.setFocusableInTouchMode(true);
        tab.setSoundEffectsEnabled(false);
        tab.setOnClickListener(
                v -> {
                    host.click();
                    host.showPage(PAGES[index]);
                });
        // One pill slides between tabs; leaving the bar it settles, translucent, on the current
        // page.
        tab.setOnFocusChangeListener(
                (v, focus) -> {
                    if (focus) moveIndicator(true);
                    else
                        tab.post(
                                () -> {
                                    if (focusedTab() < 0) moveIndicator(true);
                                });
                });
        tab.setOnKeyListener(
                (v, key, e) -> {
                    if (e.getAction() == KeyEvent.ACTION_DOWN
                            && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                        View widget = index == 0 ? host.firstWidget() : null;
                        if (!host.page().equals(PAGES[index])) host.showPage(PAGES[index]);
                        else if (widget != null) widget.requestFocus();
                        else host.focusFirst();
                        return true;
                    }
                    return false;
                });
        return tab;
    }

    /** The darkening layer that goes between the wallpaper and the page. */
    View shade() {
        return shade;
    }

    /** The tab bar, for the parts that fade it with the page. */
    FrameLayout bar() {
        return bar;
    }

    View tab(int index) {
        return tabs.get(index);
    }

    /** The tab of the page being shown. */
    View pageTab() {
        return tabs.get(pageTabIndex());
    }

    /** Puts the tab bar (centred at the top) and the clock (top right) over {@code root}. */
    void addTo(FrameLayout root) {
        root.addView(bar, ui.pos(-2, 40, 0, 26));
        bar.post(
                () -> {
                    bar.setTranslationX(
                            (context.getResources().getDisplayMetrics().widthPixels
                                            - bar.getWidth())
                                    / 2f);
                    moveIndicator(false);
                });
        root.addView(clock, ui.pos(130, 36, 782, 28));
    }

    /** Colours of the tab bar, clock and shade for the current wallpaper. */
    void paint() {
        boolean light = host.light();
        bar.setBackground(ui.shape(light ? 0x47FFFFFF : 0x1FFFFFFF, 22));
        clock.setTextColor(Theme.ink(light, 0xF2));
        ui.shadow(clock, 6, light);
        shade.setBackgroundColor(light ? 0x73F2F2F5 : 0x80000000);
    }

    void setClockVisible(boolean visible) {
        clock.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Shows the time now and every 15 seconds until {@link #stopClock}. */
    void startClock() {
        handler.removeCallbacks(clockTick);
        clockRunning = true;
        handler.post(clockTick);
    }

    void stopClock() {
        clockRunning = false;
        handler.removeCallbacks(clockTick);
    }

    private int focusedTab() {
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).hasFocus()) return i;
        return -1;
    }

    private int pageTabIndex() {
        String page = host.page();
        return page.equals("apps") ? 1 : page.equals("settings") ? 2 : 0;
    }

    /**
     * Slides the tab pill under the focused tab (white), or the current page's tab (glass) when
     * focus is elsewhere.
     */
    void moveIndicator(boolean animate) {
        if (tabs.isEmpty()) return;
        boolean light = host.light();
        int focused = focusedTab(), current = pageTabIndex();
        int index = focused >= 0 ? focused : current;
        View target = tabs.get(index), row = (View) target.getParent();
        if (target.getWidth() == 0) {
            target.post(() -> moveIndicator(false));
            return;
        }
        final float fromX = indicator.getTranslationX(),
                toX = row.getLeft() + target.getLeft() - ((View) row.getParent()).getPaddingLeft();
        final int fromW = indicator.getLayoutParams().width,
                toW = target.getWidth(),
                fromColor = indicatorColor,
                toColor =
                        focused >= 0
                                ? (light ? 0xFF1D1D1F : 0xF5FFFFFF)
                                : (light ? 0xE6FFFFFF : 0x24FFFFFF);
        final int[] fromText = new int[tabs.size()], toText = new int[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            fromText[i] = tabs.get(i).getCurrentTextColor();
            toText[i] =
                    i == focused
                            ? (light ? Color.WHITE : 0xFF17202B)
                            : Theme.ink(light, i == current ? 0xFF : 0xA6);
        }
        if (indicatorMove != null) indicatorMove.cancel();
        ArgbEvaluator argb = new ArgbEvaluator();
        ValueAnimator.AnimatorUpdateListener step =
                a -> {
                    float f = (float) a.getAnimatedValue();
                    indicator.setTranslationX(fromX + (toX - fromX) * f);
                    ViewGroup.LayoutParams lp = indicator.getLayoutParams();
                    lp.width = Math.round(fromW + (toW - fromW) * f);
                    indicator.setLayoutParams(lp);
                    indicatorColor = (int) argb.evaluate(f, fromColor, toColor);
                    indicatorShape.setColor(indicatorColor);
                    // The text flips quickly, so it never matches the pill mid-way.
                    float t = Math.max(0, Math.min(1, (f - .12f) / .3f));
                    for (int i = 0; i < tabs.size(); i++)
                        tabs.get(i)
                                .setTextColor((int) argb.evaluate(t, fromText[i], toText[i]));
                };
        indicatorMove = ValueAnimator.ofFloat(0, 1);
        indicatorMove.addUpdateListener(step);
        if (!animate || !host.motion() || fromW == 0) {
            indicatorMove.setDuration(0);
        } else {
            indicatorMove.setDuration(280);
            indicatorMove.setInterpolator(Motion.EASE);
        }
        indicatorMove.start();
    }
}
