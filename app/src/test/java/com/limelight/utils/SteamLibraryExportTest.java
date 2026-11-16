package com.limelight.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class SteamLibraryExportTest {
    private static final String APPLIST = "<?xml version=\"1.0\" encoding=\"utf-8\"?><root status_code=\"200\">" +
            "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Steam</AppTitle><UUID>4BFE6951-352C-1002-879F-6B349CA226CA</UUID><IDX>0</IDX><ID>1033022821</ID></App>" +
            "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Portal 2</AppTitle><UUID>5354454D-0000-4000-8000-00000000026C</UUID><IDX>2</IDX><ID>881448767</ID><SteamAppId>620</SteamAppId></App>" +
            "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Batman: Arkham City GOTY</AppTitle><UUID>5354454D-0000-4000-8000-0000000030E44</UUID><IDX>3</IDX><ID>12</ID><SteamAppId>200260</SteamAppId></App>" +
            "</root>";

    @Test
    public void readsSteamAppIdFromTheAppList() throws Exception {
        List<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(APPLIST));
        assertEquals(3, apps.size());
        assertFalse(apps.get(0).isSteamLibraryGame());
        assertEquals("620", apps.get(1).getSteamAppId());
    }

    @Test
    public void writesOneArtFilePerSteamGame() throws Exception {
        List<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(APPLIST));
        Map<String, String> files = SteamLibraryExport.files("HOST-UUID", "gilgamesh", apps);
        assertEquals(2, files.size());
        assertTrue(files.containsKey("Portal 2.art"));
        assertTrue(files.containsKey("Batman Arkham City GOTY.art"));
        String portal = files.get("Portal 2.art");
        // The launcher's Steam id regex takes the first match: it must be [steamappid], and no [app_id].
        assertTrue(portal.indexOf("[steamappid] 620") < portal.indexOf("[app_uuid]"));
        assertFalse(portal.contains("[app_id]"));
        assertTrue(portal.contains("[host_uuid] HOST-UUID\n"));
        assertTrue(portal.contains("[app_uuid] 5354454D-0000-4000-8000-00000000026C\n"));
    }

    @Test
    public void namesCollidingTitlesApart() {
        List<NvApp> apps = new ArrayList<>();
        for (String id : new String[] {"1", "2"}) {
            NvApp app = new NvApp("Same / Name", "5354454D-0000-4000-8000-00000000000" + id, 5, false);
            app.setSteamAppId(id);
            apps.add(app);
        }
        Map<String, String> files = SteamLibraryExport.files("H", "h", apps);
        assertTrue(files.containsKey("Same Name.art"));
        assertTrue(files.containsKey("Same Name (2).art"));
    }
}
