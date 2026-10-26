package com.limelight.preferences;

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class ManualHostAddressTest {
    @Test public void bareIpv6DoesNotTurnLastHextetIntoPort() {
        for (String host : new String[]{"2001:db8::1", "2001:db8::1234", "::1", "2001:db8::abcd"}) {
            Uri uri = ManualHostAddress.parse(host);
            assertNotNull(host, uri);
            assertEquals("[" + host + "]", uri.getHost());
            assertEquals(-1, uri.getPort());
        }
    }

    @Test public void bracketedIpv6AcceptsExplicitPort() {
        Uri uri = ManualHostAddress.parse("[2001:db8::1]:47989");
        assertNotNull(uri);
        assertEquals("[2001:db8::1]", uri.getHost());
        assertEquals(47989, uri.getPort());
        assertEquals(-1, ManualHostAddress.parse("[2001:db8::1]").getPort());
    }

    @Test public void keepsIpv4HostnamesAndPairingQuery() {
        Uri uri = ManualHostAddress.parse(" 192.168.1.10:48000?pin=1234&passphrase=a%2Bb ");
        assertNotNull(uri);
        assertEquals("192.168.1.10", uri.getHost());
        assertEquals(48000, uri.getPort());
        assertEquals("1234", uri.getQueryParameter("pin"));
        assertEquals("a+b", uri.getQueryParameter("passphrase"));
        assertEquals("hermes.local", ManualHostAddress.parse("hermes.local").getHost());
        assertEquals(-1, ManualHostAddress.parse("192.168.1.10").getPort());
    }

    @Test public void rejectsInvalidHostsAndPorts() {
        for (String input : new String[]{"", "[2001:db8::1", "[2001:db8::1]:bad",
                "192.168.1.10:65536", "192.168.1.10:0", "user@host", "host/path",
                "https://host", "2001:db8:::1", "host#fragment"}) {
            assertNull(input, ManualHostAddress.parse(input));
        }
        assertNull(ManualHostAddress.parse(null));
    }
}
