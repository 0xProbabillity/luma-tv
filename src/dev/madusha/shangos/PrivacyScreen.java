package dev.madusha.shangos;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * The About and Privacy screens: what the app can reach, what you allowed, and which build this is.
 */
final class PrivacyScreen {
    private final Activity activity;
    private final Ui ui;
    private final Prefs prefs;
    private final Overlays overlays;
    private final ScreenHost host;
    private final IntSupplier settingsRow;
    private TextView infoTitle, infoBody;
    private ArrayList<View> rows;

    PrivacyScreen(
            Activity activity,
            Ui ui,
            Prefs prefs,
            Overlays overlays,
            ScreenHost host,
            IntSupplier settingsRow) {
        this.settingsRow = settingsRow;
        this.activity = activity;
        this.ui = ui;
        this.prefs = prefs;
        this.overlays = overlays;
        this.host = host;
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

    /** One line for Settings: what, if anything, this build may send over the network. */
    static String summary(Prefs prefs) {
        if (!Flavor.NETWORK) return "No network";
        if (prefs.offline()) return "Offline mode";
        ArrayList<String> uses = new ArrayList<>();
        if (prefs.weather()) uses.add("Weather");
        if (prefs.postersMode() != Prefs.POSTERS_OFF) uses.add("Posters");
        return uses.isEmpty() ? "Idle" : String.join(" + ", uses);
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

    // ---- State of each permission ----
    boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    boolean usageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager) activity.getSystemService(Activity.APP_OPS_SERVICE);
            return ops.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            activity.getPackageName())
                    == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    boolean notificationAccess() {
        try {
            NotificationManager nm =
                    (NotificationManager) activity.getSystemService(Activity.NOTIFICATION_SERVICE);
            return nm.isNotificationListenerAccessGranted(
                    new ComponentName(activity, MediaListener.class));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * SHA-256 of the certificate this build is signed with, as apksigner prints it: compare it with
     * the fingerprint published for a release before trusting a sideloaded file.
     */
    private String signerSha256() {
        try {
            SigningInfo info =
                    activity.getPackageManager()
                            .getPackageInfo(
                                    activity.getPackageName(),
                                    PackageManager.GET_SIGNING_CERTIFICATES)
                            .signingInfo;
            if (info == null) return "";
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(info.getApkContentsSigners()[0].toByteArray());
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < digest.length; i++)
                s.append(i == 16 ? "\n" : i > 0 ? ":" : "")
                        .append(String.format(Locale.US, "%02X", digest[i]));
            return s.toString();
        } catch (Exception e) {
            return "";
        }
    }

    void openSystem(Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(activity, "This screen is not available on this TV.", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    String postersLabel() {
        return !Net.enabled(prefs)
                ? "Off"
                : new String[] {"Off", "Wi-Fi and Ethernet", "Always"}[prefs.postersMode()];
    }

    boolean networkAvailable() {
        if (Net.enabled(prefs)) return true;
        Toast.makeText(
                        activity,
                        "Network is off in this build or by Offline mode.",
                        Toast.LENGTH_SHORT)
                .show();
        return false;
    }

    /** Weather is opt-in: this flips it, naming the service when it is turned on. */
    void toggleWeather() {
        if (!networkAvailable()) return;
        boolean on = !prefs.weather();
        prefs.setWeather(on);
        if (on)
            Toast.makeText(
                            activity,
                            "Weather now asks open-meteo.com for the forecast.",
                            Toast.LENGTH_LONG)
                    .show();
    }

    /** The posters menu; {@code after} runs once a choice was made. */
    void pickPosters(Runnable after) {
        if (!networkAvailable()) return;
        overlays.menu(
                "Remote posters",
                new String[] {"Off", "Wi-Fi and Ethernet only", "Always"},
                prefs.postersMode(),
                choice -> {
                    prefs.setPostersMode(choice);
                    after.run();
                });
    }

    /** Statuses may have changed while a system settings page was open: read them again. */
    void refresh() {
        if (rows != null && overlays.isOpen()) for (View r : rows) Overlays.repaint(r);
    }

    // ---- The Privacy screen ----
    private void info(String title, String body) {
        if (infoTitle != null) {
            infoTitle.setText(title);
            infoBody.setText(body);
        }
    }

    /**
     * Everything this app can reach, in one place: the network, the two opt-in features, each
     * permission with its state, and the fingerprint of this build. OK on a row changes it or opens
     * the matching system screen.
     */
    void show(int focusRow) {
        FrameLayout o = overlays.open(0xF20B0B0D);
        overlays.onCancel(() -> host.backToSettings(settingsRow.getAsInt()));
        TextView title = ui.text("Privacy", 34, Color.WHITE, "sans-serif-medium");
        title.setLetterSpacing(-.02f);
        o.addView(title, ui.pos(500, 44, 48, 32));
        o.addView(
                ui.line(
                        Flavor.NETWORK
                                ? "What this app can reach, and what you have allowed. Nothing"
                                        + " leaves this TV except what is listed here."
                                : "This build has no network permission: Android itself blocks"
                                        + " every connection.",
                        13,
                        0xC7FFFFFF,
                        "sans-serif"),
                ui.pos(820, 20, 49, 80));
        LinearLayout list = column();
        list.setClipChildren(false);
        o.addView(list, ui.pos(500, 370, 40, 116));
        FrameLayout card = new FrameLayout(activity);
        card.setBackground(ui.shape(0x1AFFFFFF, 22));
        o.addView(card, ui.pos(352, 360, 560, 122));
        infoTitle = ui.text("", 19, Color.WHITE, "sans-serif-medium");
        card.addView(infoTitle, ui.pos(316, 26, 18, 18));
        infoBody = paragraph("", 13, 0xC7FFFFFF);
        card.addView(infoBody, ui.pos(316, 300, 18, 56));

        rows = new ArrayList<>();
        rows.add(
                overlays.valueRow(
                        list,
                        "Network",
                        () ->
                                !Flavor.NETWORK
                                        ? "None in this build"
                                        : prefs.offline() ? "Offline mode" : "Allowed",
                        () -> {
                            if (!Flavor.NETWORK) {
                                Toast.makeText(
                                                activity,
                                                "This build has no network permission.",
                                                Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }
                            prefs.setOffline(!prefs.offline());
                        },
                        () ->
                                info(
                                        "Network",
                                        Flavor.NETWORK
                                                ? "Only two optional features use the network:"
                                                      + " Weather and remote posters. There is no"
                                                      + " analytics, no crash reporting, no account"
                                                      + " and no update check. Offline mode"
                                                      + " switches both off at once. OK turns it on"
                                                      + " or off."
                                                : "This build was made without the Internet"
                                                      + " permission, so the system refuses every"
                                                      + " connection before it starts, whatever the"
                                                      + " settings say.")));
        rows.add(
                overlays.valueRow(
                        list,
                        "Weather",
                        () -> Net.weatherEnabled(prefs) ? "On" : "Off",
                        this::toggleWeather,
                        () ->
                                info(
                                        "Weather",
                                        "Off until you turn it on. When on, the launcher asks"
                                            + " open-meteo.com for the forecast. That service sees"
                                            + " this TV's internet address and the rounded"
                                            + " coordinates of your time zone's main city (about 1"
                                            + " km), and nothing else: no name, no account, no"
                                            + " device identifier.")));
        rows.add(
                overlays.valueRow(
                        list,
                        "Remote posters",
                        this::postersLabel,
                        () -> pickPosters(() -> host.post(() -> show(2))),
                        () ->
                                info(
                                        "Remote posters",
                                        "Up Next can show cover pictures that apps publish as web"
                                            + " links. The launcher fetches them itself, from"
                                            + " secure links only and never from addresses inside"
                                            + " your home network. Pictures an app supplies on this"
                                            + " TV load whatever you choose here.")));
        rows.add(
                overlays.valueRow(
                        list,
                        "Up Next access",
                        () ->
                                granted("android.permission.READ_TV_LISTINGS")
                                        ? "Allowed"
                                        : "Not allowed",
                        () ->
                                openSystem(
                                        new Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:" + activity.getPackageName()))),
                        () ->
                                info(
                                        "Up Next access",
                                        "Lets the launcher read the Continue watching list that"
                                            + " your apps share with Android TV, for the Up Next"
                                            + " card and for the line under an app's name on Home."
                                            + " It only reads, and what it reads stays on this TV."
                                            + " OK opens this app's permissions.")));
        rows.add(
                overlays.valueRow(
                        list,
                        "Usage access",
                        () -> usageAccess() ? "Allowed" : "Not allowed",
                        () -> openSystem(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)),
                        () ->
                                info(
                                        "Usage access",
                                        "Optional. Lets Up Next order your recent apps by when you"
                                                + " really used them. Without it the launcher only"
                                                + " knows the apps you opened from it. OK opens the"
                                                + " system screen.")));
        rows.add(
                overlays.valueRow(
                        list,
                        "Notification access",
                        () -> notificationAccess() ? "Allowed" : "Not allowed",
                        () ->
                                openSystem(
                                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
                        () ->
                                info(
                                        "Notification access",
                                        "Needed only so Now Playing can see and control the music"
                                            + " or video playing on this TV. It does not read your"
                                            + " notifications: the service that holds this access"
                                            + " contains no code at all. OK opens the system"
                                            + " screen.")));
        rows.add(
                overlays.valueRow(
                        list,
                        "This build",
                        () -> version(activity),
                        () -> {},
                        () ->
                                info(
                                        "This build",
                                        "Signed with a certificate whose SHA-256 fingerprint"
                                                + " is:\n\n"
                                                + signerSha256()
                                                + "\n\n"
                                                + "Compare it with the fingerprint published with"
                                                + " the release before you trust a sideloaded"
                                                + " file.")));
        o.addView(ui.text("Back to close", 12, 0x80FFFFFF, "sans-serif"), ui.pos(300, 16, 49, 500));
        host.post(() -> rows.get(Math.max(0, Math.min(rows.size() - 1, focusRow))).requestFocus());
    }
}
