package com.limelight.nvstream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.shadows.ShadowMoonBridge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {ShadowMoonBridge.class})
public class StreamConfigurationTest {

    @Test
    public void microphoneFlagDefaultsDisabled() {
        StreamConfiguration config = new StreamConfiguration.Builder().build();

        assertFalse(config.getEnableMicrophone());
    }

    @Test
    public void microphoneFlagCanBeEnabled() {
        StreamConfiguration config = new StreamConfiguration.Builder()
                .setEnableMicrophone(true)
                .build();

        assertTrue(config.getEnableMicrophone());
    }

    @Test
    public void hdrDisplayDataSaysNothingByDefault() {
        assertEquals("0x0x0x0x0x0x0x0x0x0x0", new StreamConfiguration.Builder().build().getHdrDisplayData());
    }

    @Test
    public void hdrDisplayDataCarriesThePanelsLuminance() {
        // The RP6: 420 nits peak, 210.16 average, 0.323 minimum (0.0001 cd/m2 units).
        StreamConfiguration config = new StreamConfiguration.Builder()
                .setHdrDisplayLuminance(420f, 210.1615f, 0.323f)
                .build();
        assertEquals("0x0x0x0x0x0x0x0x420x3230x210", config.getHdrDisplayData());
        assertEquals("0x0x0x0x0x0x0x0x65535x0x0", StreamConfiguration.hdrDisplayData(1e6f, Float.NaN, -1f));
    }
}
