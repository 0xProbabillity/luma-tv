package dev.madusha.shangos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.view.View;

/**
 * Two small icons beside the clock that appear only when they matter: a lock while a VPN carries
 * the TV's traffic, and a crossed Wi-Fi symbol (in amber) when there is no network. Nothing shows
 * when all is as usual. Reads only whether a network exists (the network-state permission the app
 * already has); the offline build has no network permissions, so it shows nothing.
 */
final class StatusIcons extends View {
    private final Handler handler;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int ink = 0xE6FFFFFF;
    private boolean vpn, offline;
    private ConnectivityManager manager;

    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
                    set(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN), false);
                }

                @Override
                public void onLost(Network n) {
                    set(false, true);
                }
            };

    StatusIcons(Context context, Handler handler) {
        super(context);
        this.handler = handler;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setContentDescription("");
    }

    /** The colour of the lock: dark on a pale wallpaper. */
    void setInk(int color) {
        ink = color;
        invalidate();
    }

    /** Starts watching the default network; call {@link #stop} when Home leaves the screen. */
    void start() {
        if (!Flavor.NETWORK) return;
        try {
            manager = getContext().getSystemService(ConnectivityManager.class);
            if (manager == null) return;
            Network active = manager.getActiveNetwork();
            NetworkCapabilities caps =
                    active == null ? null : manager.getNetworkCapabilities(active);
            boolean onVpn = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            set(onVpn, active == null);
            manager.registerDefaultNetworkCallback(callback, handler);
        } catch (RuntimeException e) {
            manager = null; // no permission (the offline build): show nothing
        }
    }

    void stop() {
        if (manager == null) return;
        try {
            manager.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
        manager = null;
    }

    private void set(boolean vpnNow, boolean offlineNow) {
        if (vpn == vpnNow && offline == offlineNow) return;
        vpn = vpnNow;
        offline = offlineNow;
        setContentDescription(
                offline ? "No network" : vpn ? "VPN on" : "");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight(), unit = h / 36f; // drawn in a 36-unit-tall box, right-aligned
        float x = getWidth();
        stroke.setStrokeWidth(1.8f * unit);
        if (vpn) {
            x -= 20 * unit;
            lock(canvas, x, h / 2f, unit);
            x -= 6 * unit;
        }
        if (offline) {
            x -= 22 * unit;
            noWifi(canvas, x, h / 2f, unit);
        }
    }

    /** A padlock, 20 x 20 units, with its left edge at {@code left}. */
    private void lock(Canvas c, float left, float cy, float u) {
        stroke.setColor(ink);
        fill.setColor(ink);
        rect.set(left + 2 * u, cy - 1 * u, left + 18 * u, cy + 9 * u);
        c.drawRoundRect(rect, 2.5f * u, 2.5f * u, fill);
        path.reset();
        path.moveTo(left + 5.5f * u, cy - 1 * u);
        path.lineTo(left + 5.5f * u, cy - 4 * u);
        path.cubicTo(
                left + 5.5f * u,
                cy - 11 * u,
                left + 14.5f * u,
                cy - 11 * u,
                left + 14.5f * u,
                cy - 4 * u);
        path.lineTo(left + 14.5f * u, cy - 1 * u);
        c.drawPath(path, stroke);
    }

    /** Wi-Fi arcs with a slash, in amber, 22 x 20 units. */
    private void noWifi(Canvas c, float left, float cy, float u) {
        int amber = 0xFFFFB84D;
        stroke.setColor(amber);
        fill.setColor(amber);
        float cx = left + 11 * u, base = cy + 8 * u;
        for (int i = 1; i <= 3; i++) {
            float r = i * 5 * u;
            rect.set(cx - r, base - r, cx + r, base + r);
            c.drawArc(rect, -135, 90, false, stroke);
        }
        c.drawCircle(cx, base, 1.6f * u, fill);
        c.drawLine(left + 2 * u, cy - 8 * u, left + 20 * u, cy + 10 * u, stroke);
    }
}
