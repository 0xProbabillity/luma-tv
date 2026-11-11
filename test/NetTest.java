package dev.madusha.shangos;

import static org.junit.Assert.*;

import org.junit.Test;

import java.net.InetAddress;

/** Pure-logic tests for Net: no device, no DNS. Run with test/run.sh. */
public class NetTest {
    private static Net.Resolver to(String... ips) {
        return host -> {
            InetAddress[] r = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) r[i] = InetAddress.getByName(ips[i]);
            return r;
        };
    }

    private static void blocked(String url, Net.Kind k, Net.Resolver r) {
        try {
            Net.check(url, k, r);
            fail("should be blocked: " + url);
        } catch (Net.Blocked expected) {
        } catch (Exception e) {
            fail(url + " -> " + e);
        }
    }

    private static final Net.Resolver PUBLIC = to("93.184.216.34");

    @Test
    public void acceptsPublicHttps() throws Exception {
        assertNotNull(Net.check("https://img.example.com/a.jpg", Net.Kind.IMAGE, PUBLIC));
        assertNotNull(Net.check("https://img.example.com:443/a.jpg", Net.Kind.IMAGE, PUBLIC));
        assertNotNull(
                Net.check(
                        "https://img.example.com/a",
                        Net.Kind.IMAGE,
                        to("2606:2800:220:1:248:1893:25c8:1946")));
    }

    @Test
    public void rejectsBadSchemes() {
        for (String u :
                new String[] {
                    "http://example.com/a.jpg",
                    "file:///etc/passwd",
                    "ftp://example.com/a",
                    "content://x/y",
                    "javascript:alert(1)",
                    "//example.com/a",
                    "example.com/a",
                    "",
                    null
                }) blocked(u, Net.Kind.IMAGE, PUBLIC);
    }

    @Test
    public void rejectsCredentialsPortsAndLongUrls() {
        blocked("https://user:pw@example.com/a", Net.Kind.IMAGE, PUBLIC);
        blocked("https://example.com:8443/a", Net.Kind.IMAGE, PUBLIC);
        blocked("https://example.com:80/a", Net.Kind.IMAGE, PUBLIC);
        blocked("https://example.com/" + "a".repeat(2100), Net.Kind.IMAGE, PUBLIC);
    }

    @Test
    public void rejectsNonPublicIPv4() {
        for (String ip :
                new String[] {
                    "127.0.0.1",
                    "127.255.255.254",
                    "0.0.0.0",
                    "10.0.0.5",
                    "172.16.0.1",
                    "172.31.255.255",
                    "192.168.1.1",
                    "169.254.169.254",
                    "100.64.0.1",
                    "100.127.255.255",
                    "224.0.0.1",
                    "239.255.255.250",
                    "255.255.255.255",
                    "240.0.0.1",
                    "192.0.0.1",
                    "192.0.2.1",
                    "198.18.0.1",
                    "198.51.100.7",
                    "203.0.113.9"
                }) blocked("https://x.example.com/a", Net.Kind.IMAGE, to(ip));
    }

    @Test
    public void publicNeighboursOfPrivateRangesAreAllowed() throws Exception {
        for (String ip :
                new String[] {
                    "172.15.255.255",
                    "172.32.0.1",
                    "100.63.255.255",
                    "100.128.0.1",
                    "11.0.0.1",
                    "192.167.1.1",
                    "169.253.1.1",
                    "8.8.8.8"
                }) assertNotNull(ip, Net.check("https://x.example.com/a", Net.Kind.IMAGE, to(ip)));
    }

    @Test
    public void rejectsNonPublicIPv6() {
        for (String ip :
                new String[] {
                    "::1",
                    "::",
                    "fe80::1",
                    "fc00::1",
                    "fd12:3456:789a::1",
                    "fec0::1",
                    "ff02::1",
                    "::ffff:10.0.0.1",
                    "::ffff:192.168.1.1",
                    "2001:db8::1",
                    "2002:c0a8:101::1",
                    "2001:0:4136:e378:8000:63bf:3fff:fdd2",
                    "64:ff9b::7f00:1",
                    "64:ff9b::c0a8:101"
                }) blocked("https://x.example.com/a", Net.Kind.IMAGE, to(ip));
    }

    @Test
    public void nat64WrapsPublicIPv4ForIPv6OnlyNetworks() throws Exception {
        assertNotNull(
                Net.check(
                        "https://x.example.com/a",
                        Net.Kind.IMAGE,
                        to("64:ff9b::5db8:d822"))); // 93.184.216.34
    }

    @Test
    public void oneBadAddressBlocksTheWholeAnswer() {
        blocked(
                "https://x.example.com/a",
                Net.Kind.IMAGE,
                to("93.184.216.34", "192.168.1.1")); // multi-record rebinding
    }

    @Test
    public void ipLiteralsAreJudgedToo() {
        blocked("https://192.168.1.1/admin", Net.Kind.IMAGE, Net.SYSTEM);
        blocked("https://127.0.0.1/a", Net.Kind.IMAGE, Net.SYSTEM);
        blocked("https://[::1]/a", Net.Kind.IMAGE, Net.SYSTEM);
        blocked("https://localhost/a", Net.Kind.IMAGE, Net.SYSTEM);
    }

    @Test
    public void jsonIsHostLocked() throws Exception {
        assertNotNull(
                Net.check("https://api.open-meteo.com/v1/forecast?x=1", Net.Kind.JSON, PUBLIC));
        blocked("https://evil.example.com/v1/forecast", Net.Kind.JSON, PUBLIC);
        blocked("https://api.open-meteo.com.evil.example.com/x", Net.Kind.JSON, PUBLIC);
        blocked("https://notapi.open-meteo.com/x", Net.Kind.JSON, PUBLIC);
    }

    @Test
    public void emptyResolutionIsAnError() {
        try {
            Net.check("https://x.example.com/a", Net.Kind.IMAGE, h -> new InetAddress[0]);
            fail();
        } catch (java.io.IOException expected) {
        }
    }
}
