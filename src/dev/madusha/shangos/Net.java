package dev.madusha.shangos;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * The ONLY class in the app that opens a network connection (CI greps for this).
 *
 * <p>Rules: https only, port 443 only, no credentials in URLs; every address the host resolves to
 * must be a public one (checked before connecting, and again on every redirect, at most {@link
 * #MAX_REDIRECTS}); the response must have the expected content type and stay under a byte cap;
 * fixed timeouts; one generic User-Agent shared by every install. JSON is fetched only from {@link
 * #JSON_HOSTS}.
 *
 * <p>Known limit: DNS is resolved once for the check and again by the HTTP stack when it connects,
 * so a hostile DNS server could answer differently the second time (DNS rebinding). The impact is
 * bounded: only image bytes or the weather JSON are fetched, nothing is shown from the response
 * except a decoded picture, and no cookies or credentials are sent.
 *
 * <p>In the offline build ({@code Flavor.NETWORK == false}) the app has no INTERNET permission and
 * every call here fails with {@link Disabled} before touching a socket.
 */
final class Net {
    private Net() {}

    enum Kind {
        IMAGE,
        JSON
    }

    /**
     * Networking is switched off: offline build, or the user's Offline mode, or a feature that is
     * off.
     */
    static final class Disabled extends IOException {
        Disabled(String why) {
            super(why);
        }
    }

    /** The request broke a rule above (scheme, address, size, type...). Never retried. */
    static final class Blocked extends IOException {
        Blocked(String why) {
            super(why);
        }
    }

    static final String UA = "Mozilla/5.0 (Linux; Android)";
    static final int MAX_IMAGE = 3_000_000, MAX_JSON = 200_000, MAX_REDIRECTS = 2;

    /** The only hosts JSON is fetched from. */
    static final String[] JSON_HOSTS = {"api.open-meteo.com"};

    // ---- Policy (what the user has allowed) ----
    /** The build has networking and the user has not switched on Offline mode. */
    static boolean enabled(Prefs p) {
        return Flavor.NETWORK && !p.offline();
    }

    /** Weather is opt-in: off until the user turns it on. */
    static boolean weatherEnabled(Prefs p) {
        return enabled(p) && p.weather();
    }

    /**
     * Remote posters: allowed by the mode, and for "unmetered" only on Wi-Fi or Ethernet (not a
     * metered link).
     */
    static boolean postersEnabled(Context c, Prefs p) {
        if (!enabled(p)) return false;
        int mode = p.postersMode();
        if (mode == Prefs.POSTERS_OFF) return false;
        if (mode == Prefs.POSTERS_ANY) return true;
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- Validation (pure: no sockets, testable on a laptop) ----
    interface Resolver {
        InetAddress[] resolve(String host) throws IOException;
    }

    static final Resolver SYSTEM = InetAddress::getAllByName;

    /** Returns the URL if it may be fetched; otherwise throws {@link Blocked}. */
    static URL check(String link, Kind kind, Resolver resolver) throws IOException {
        if (link == null || link.length() > 2048) throw new Blocked("length");
        URL url;
        try {
            url = new URL(link);
        } catch (MalformedURLException e) {
            throw new Blocked("malformed");
        }
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new Blocked("scheme");
        if (url.getUserInfo() != null) throw new Blocked("credentials");
        if (url.getPort() != -1 && url.getPort() != 443) throw new Blocked("port");
        String host = url.getHost();
        if (host == null || host.isEmpty()) throw new Blocked("host");
        if (kind == Kind.JSON) {
            boolean ok = false;
            for (String h : JSON_HOSTS) ok |= h.equalsIgnoreCase(host);
            if (!ok) throw new Blocked("host not allowed");
        }
        InetAddress[] all = resolver.resolve(host);
        if (all == null || all.length == 0) throw new UnknownHostException(host);
        for (InetAddress a : all) if (!isPublic(a)) throw new Blocked("address");
        return url;
    }

    /**
     * True only for addresses on the public internet. Written as explicit byte-range checks because
     * InetAddress.isSiteLocalAddress() knows only the deprecated IPv6 fec0::/10, not the fc00::/7
     * that home networks use, and none of the helpers know CGNAT, documentation or transition
     * ranges.
     */
    static boolean isPublic(InetAddress a) {
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 255, b1 = b[1] & 255, b2 = b[2] & 255;
            if (b0 == 0 || b0 == 10 || b0 == 127 || b0 >= 224)
                return false; // this-net, private, loopback, multicast/reserved/broadcast
            if (b0 == 100 && (b1 & 0xC0) == 64) return false; // 100.64.0.0/10 carrier-grade NAT
            if (b0 == 169 && b1 == 254) return false; // link-local
            if (b0 == 172 && (b1 & 0xF0) == 16) return false; // 172.16.0.0/12
            if (b0 == 192 && b1 == 168) return false; // 192.168.0.0/16
            if (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2))
                return false; // IETF protocol, TEST-NET-1
            if (b0 == 192 && b1 == 88 && b2 == 99) return false; // 6to4 relay
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false; // benchmarking
            if (b0 == 198 && b1 == 51 && b2 == 100) return false; // TEST-NET-2
            if (b0 == 203 && b1 == 0 && b2 == 113) return false; // TEST-NET-3
            return true;
        }
        if (b.length == 16) {
            // NAT64 (64:ff9b::/96) carries an IPv4 address in its last 4 bytes: judge that one.
            boolean nat64 =
                    b[0] == 0 && b[1] == 0x64 && (b[2] & 255) == 0xff && (b[3] & 255) == 0x9b;
            for (int i = 4; i < 12 && nat64; i++) nat64 = b[i] == 0;
            if (nat64) {
                try {
                    return isPublic(
                            InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            if ((b[0] & 0xE0) != 0x20) return false; // global unicast is 2000::/3 only
            int w0 = ((b[0] & 255) << 8) | (b[1] & 255), w1 = ((b[2] & 255) << 8) | (b[3] & 255);
            if (w0 == 0x2001 && (w1 == 0x0000 || w1 == 0x0db8))
                return false; // Teredo, documentation
            if (w0 == 0x2002) return false; // 6to4 embeds an IPv4 address
            return true;
        }
        return false;
    }

    // ---- Fetching ----
    static byte[] fetch(String link, Kind kind, int maxBytes) throws IOException {
        return fetch(link, kind, maxBytes, SYSTEM);
    }

    static byte[] fetch(String link, Kind kind, int maxBytes, Resolver resolver)
            throws IOException {
        if (!Flavor.NETWORK) throw new Disabled("offline build");
        URL url = check(link, kind, resolver);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
            try {
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                c.setUseCaches(false);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", kind == Kind.IMAGE ? "image/*" : "application/json");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) throw new Blocked("redirect");
                    url =
                            check(
                                    new URL(url, loc).toString(),
                                    kind,
                                    resolver); // same rules on every hop
                    continue;
                }
                if (code != 200) throw new IOException("http " + code);
                if (c.getContentLengthLong() > maxBytes) throw new Blocked("too large");
                String type = c.getContentType();
                type = type == null ? "" : type.toLowerCase(Locale.ROOT);
                if (kind == Kind.IMAGE ? !type.startsWith("image/") : !type.contains("json"))
                    throw new Blocked("content type");
                try (InputStream in = c.getInputStream()) {
                    return readCapped(in, maxBytes);
                }
            } finally {
                c.disconnect();
            }
        }
        throw new Blocked("too many redirects");
    }

    /** The weather JSON (host-locked to {@link #JSON_HOSTS}). */
    static String json(String link) throws IOException {
        return new String(fetch(link, Kind.JSON, MAX_JSON), StandardCharsets.UTF_8);
    }

    /**
     * An image from another app's metadata: a content: or android.resource: URI, or (if the user
     * allows) https.
     */
    static byte[] image(Context context, Prefs prefs, String link) throws IOException {
        String scheme =
                link == null
                        ? ""
                        : String.valueOf(Uri.parse(link).getScheme()).toLowerCase(Locale.ROOT);
        switch (scheme) {
            case "https":
                if (!postersEnabled(context, prefs)) throw new Disabled("remote posters off");
                return fetch(link, Kind.IMAGE, MAX_IMAGE);
            case "content":
            case "android.resource":
                try (InputStream in =
                        context.getContentResolver().openInputStream(Uri.parse(link))) {
                    if (in == null) throw new FileNotFoundException();
                    return readCapped(in, MAX_IMAGE);
                } catch (SecurityException e) {
                    throw new Blocked("no access");
                }
            default:
                throw new Blocked("scheme"); // http:, file:, and anything else
        }
    }

    private static byte[] readCapped(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > max) throw new Blocked("too large");
        }
        return out.toByteArray();
    }
}
