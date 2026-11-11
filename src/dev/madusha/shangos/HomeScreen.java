package dev.madusha.shangos;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * Settings → Home screen: whether shangOS is the TV's home screen, and the way back to the TV's own
 * launcher.
 */
final class HomeScreen {
    // Stock launchers that shangos.sh may have switched off to let shangOS be the home screen.
    static final String[] STOCK_HOMES = {
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher"
    };

    private final Context context;
    private final Overlays overlays;

    HomeScreen(Context context, Overlays overlays) {
        this.context = context;
        this.overlays = overlays;
    }

    private static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    /** True when pressing Home on the remote opens shangOS. */
    boolean isDefault() {
        ResolveInfo r =
                context.getPackageManager()
                        .resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null
                && r.activityInfo != null
                && context.getPackageName().equals(r.activityInfo.packageName);
    }

    void show() {
        if (!isDefault()) {
            overlays.notice(
                    "Home screen",
                    "To make shangOS your home screen, run  ./shangos.sh home  on a computer with"
                        + " this TV on the same Wi-Fi. Some TVs also offer it in Settings → Apps →"
                        + " Default apps.");
            return;
        }
        overlays.menu(
                "Home screen",
                new String[] {"Keep shangOS", "Return to the TV's home screen"},
                0,
                choice -> {
                    if (choice == 1) restore();
                });
    }

    /**
     * Clears the "always use shangOS as Home" choice. Deprecated since API 30 with no replacement
     * for this one job (RoleManager can grant the Home role but not take it back), and still works
     * for the app's own package.
     */
    @SuppressWarnings("deprecation")
    private void forgetPreferredHome(PackageManager pm) {
        pm.clearPackagePreferredActivities(context.getPackageName());
    }

    /**
     * Hands Home back to the TV's own launcher. A launcher switched off over ADB can only be
     * switched on over ADB.
     */
    private void restore() {
        PackageManager pm = context.getPackageManager();
        boolean off = false;
        for (String pkg : STOCK_HOMES)
            try {
                int state = pm.getApplicationEnabledSetting(pkg);
                off |=
                        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            } catch (IllegalArgumentException notInstalled) {
            }
        ResolveInfo other = null;
        for (ResolveInfo r : pm.queryIntentActivities(homeIntent(), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName())
                    || pkg.equals("com.android.tv.settings")
                    || pkg.contains("setupwraith")
                    || pkg.contains("provision")) continue;
            if (other == null || r.priority > other.priority) other = r;
        }
        if (off || other == null) {
            overlays.notice(
                    "Return to the TV's home screen",
                    "Your TV's own home screen was switched off from a computer so that shangOS"
                        + " could replace it. Only a computer can switch it back on: run "
                        + " ./shangos.sh restore  in the shangOS folder, with this TV on the same"
                        + " Wi-Fi.");
            return;
        }
        forgetPreferredHome(pm);
        context.startActivity(
                homeIntent()
                        .setComponent(
                                new ComponentName(
                                        other.activityInfo.packageName, other.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
