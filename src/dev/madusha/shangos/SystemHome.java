package dev.madusha.shangos;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

/** The TV's own home screen, which shangOS can hand Home back to. */
final class SystemHome {
    private SystemHome() {}

    static Intent intent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    /**
     * The other app that answers the Home key, leaving out shangOS, the system settings and the
     * setup apps. Null when shangOS is the only one.
     */
    static ComponentName other(Context context) {
        ResolveInfo best = null;
        for (ResolveInfo r : context.getPackageManager().queryIntentActivities(intent(), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName())
                    || pkg.equals("com.android.tv.settings")
                    || pkg.contains("setupwraith")
                    || pkg.contains("provision")) continue;
            if (best == null || r.priority > best.priority) best = r;
        }
        return best == null
                ? null
                : new ComponentName(best.activityInfo.packageName, best.activityInfo.name);
    }
}
