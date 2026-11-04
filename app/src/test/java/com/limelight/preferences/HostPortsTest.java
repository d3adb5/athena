package com.limelight.preferences;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class HostPortsTest {
    @Test
    public void blankIsAutomaticAndOutOfRangeIsInvalid() {
        assertEquals(0, HostPorts.parse(""));
        assertEquals(0, HostPorts.parse("  "));
        assertEquals(0, HostPorts.parse(null));
        assertEquals(48010, HostPorts.parse(" 48010 "));
        assertEquals(-1, HostPorts.parse("65536"));
        assertEquals(-1, HostPorts.parse("-5"));
        assertEquals(-1, HostPorts.parse("http"));
    }

    @Test
    public void wakeOnLanListAcceptsCommasAndSpaces() {
        assertArrayEquals(new int[] {9, 47009}, HostPorts.parseList("9, 47009"));
        assertArrayEquals(new int[] {7, 9}, HostPorts.parseList("7 9"));
        assertArrayEquals(new int[0], HostPorts.parseList(""));
        assertNull(HostPorts.parseList("9, 0"));
        assertNull(HostPorts.parseList("9, x"));
    }

    @Test
    public void settingsOverrideOnlyWhatIsSet() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("host_ports_test", Context.MODE_PRIVATE);
        prefs.edit().clear().putString(HostPorts.VIDEO, "50998").putString(HostPorts.WOL, "bad").commit();
        HostPorts ports = HostPorts.from(prefs);
        assertEquals(HostPorts.DEFAULT_HTTP, ports.http);
        assertEquals(0, ports.https);
        assertEquals(50998, ports.video);
        assertEquals(0, ports.audio);
        assertArrayEquals(new int[] {9, 47009}, ports.wolPorts());
        assertEquals(47990, ports.hestiaApiFor(47984));
        assertEquals(51990, ports.hestiaApiFor(51984));
        assertTrue(HostPorts.isPortKey(HostPorts.MIC));
        assertTrue(HostPorts.isPortKey(HostPorts.WOL));
    }

    @Test
    public void hestiaApiSettingWins() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("host_ports_test", Context.MODE_PRIVATE);
        prefs.edit().clear().putString(HostPorts.HESTIA_API, "443").commit();
        assertEquals(443, HostPorts.from(prefs).hestiaApiFor(47984));
    }
}
