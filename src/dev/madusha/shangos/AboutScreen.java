package dev.madusha.shangos;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Name, version, one paragraph about the app, and the way on to Privacy and Licences. */
final class AboutScreen {
    private final Activity activity;
    private final Ui ui;
    private final Overlays overlays;
    private final ScreenHost host;
    private final PrivacyScreen privacy;
    private final LicensesScreen licences;

    AboutScreen(
            Activity activity,
            Ui ui,
            Overlays overlays,
            ScreenHost host,
            PrivacyScreen privacy,
            LicensesScreen licences) {
        this.activity = activity;
        this.ui = ui;
        this.overlays = overlays;
        this.host = host;
        this.privacy = privacy;
        this.licences = licences;
    }

    static String version(Activity activity) {
        try {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0)
                    .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    void show() {
        String name = activity.getString(R.string.app_name);
        FrameLayout o = overlays.open(Theme.SCRIM);
        FrameLayout panel = overlays.panel(o, 560, 400);
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(activity.getDrawable(R.mipmap.app_icon));
        panel.addView(icon, ui.pos(64, 64, 26, 24));
        panel.addView(
                ui.text(name, 26, Color.WHITE, "sans-serif-medium"), ui.pos(400, 34, 106, 30));
        String build =
                "Version "
                        + version(activity)
                        + (Flavor.NETWORK ? "" : " · offline build")
                        + (Flavor.DEBUG ? " · debug" : "");
        panel.addView(ui.text(build, 13, 0x99FFFFFF, "sans-serif"), ui.pos(400, 18, 107, 66));
        panel.addView(
                paragraph(
                        "A calm, private home screen for Android TV. No ads, no sponsored rows, no"
                                + " tracking: your settings stay on this TV. Hold OK on an app to"
                                + " organise your Home.",
                        13,
                        0xC7FFFFFF),
                ui.pos(500, 56, 27, 112));
        panel.addView(
                paragraph(
                        "Open source under the MIT licence, and based on Luma TV by 0xProbability."
                            + " Wallpapers include CC0 panoramas from Poly Haven. Licences shows"
                            + " everything it uses.",
                        12,
                        0x99FFFFFF),
                ui.pos(500, 54, 27, 182));
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(buttons, ui.pos(500, 96, 26, 282));
        LinearLayout left = column(), right = column();
        buttons.addView(left, new LinearLayout.LayoutParams(ui.px(200), -1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ui.px(110), -1);
        gap.leftMargin = ui.px(10);
        buttons.addView(right, gap);
        overlays.row(left, "Privacy", false, () -> privacy.show(0));
        overlays.row(left, "Licences", false, () -> licences.show(0));
        TextView done = overlays.row(right, "Done", false, () -> overlays.close(false));
        done.setGravity(Gravity.CENTER);
        host.post(done::requestFocus);
    }

    private TextView paragraph(String text, float size, int color) {
        TextView t = ui.text(text, size, color, "sans-serif");
        t.setLineSpacing(ui.px(4), 1);
        t.setSingleLine(false);
        return t;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(activity);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }
}
