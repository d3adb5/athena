package com.limelight.binding.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeepPacerTest {
    private static final long PERIOD = 8350000;
    private static final long LEAD = 6000000;
    // System.nanoTime() is far from zero, where the pacer's last target starts.
    private static final long T = 1000000000000L;

    @Test
    public void firstFrameIsShownAsEarlyAsItCanBe() {
        assertEquals(T + LEAD, new KeepPacer().target(T, PERIOD, LEAD));
    }

    @Test
    public void framesInPhaseGetConsecutiveVsyncs() {
        KeepPacer pacer = new KeepPacer();
        long first = pacer.target(T, PERIOD, LEAD);
        // Decoded 1 ms early: its own vsync is one period after the last, not earlier.
        assertEquals(first + PERIOD, pacer.target(T + PERIOD - 1000000, PERIOD, LEAD));
        assertEquals(first + 2 * PERIOD, pacer.target(T + 2 * PERIOD - 2000000, PERIOD, LEAD));
    }

    @Test
    public void aLateFrameRestartsFromNow() {
        KeepPacer pacer = new KeepPacer();
        pacer.target(T, PERIOD, LEAD);
        long late = T + 3 * PERIOD;
        assertEquals(late + LEAD, pacer.target(late, PERIOD, LEAD));
    }

    @Test
    public void aRealDelayDrainsFiveMicrosecondsAFrame() {
        KeepPacer pacer = new KeepPacer();
        long first = pacer.target(T, PERIOD, LEAD);
        // The next frame is ready 3 ms before it is needed: over the 2.5 ms threshold.
        long now = T + PERIOD - 3000000;
        assertEquals(first + PERIOD - KeepPacer.DRAIN_NS, pacer.target(now, PERIOD, LEAD));
    }

    @Test
    public void aSmallDelayIsKept() {
        KeepPacer pacer = new KeepPacer();
        long first = pacer.target(T, PERIOD, LEAD);
        assertEquals(first + PERIOD, pacer.target(T + PERIOD - 2500000, PERIOD, LEAD));
    }

    @Test
    public void aWholeFrameBehindGivesOneUp() {
        KeepPacer pacer = new KeepPacer();
        long first = pacer.target(T, PERIOD, LEAD);
        // Three frames decoded together: the second takes the next vsync, the third would wait two periods.
        assertEquals(first + PERIOD - KeepPacer.DRAIN_NS, pacer.target(T, PERIOD, LEAD));
        assertEquals(T + LEAD, pacer.target(T, PERIOD, LEAD));
    }

    @Test
    public void leadStaysInsideThePresentationDeadline() {
        assertEquals(6800000, KeepPacer.leadFor(8300000));
        assertEquals(0, KeepPacer.leadFor(1000000));
    }

    @Test
    public void operatingRateFollowsTheFactor() {
        assertEquals(Short.MAX_VALUE, KeepPacer.operatingRate(0, 120));
        assertEquals(480, KeepPacer.operatingRate(4, 120));
        assertEquals(240, KeepPacer.operatingRate(2, 120));
        assertEquals(120, KeepPacer.operatingRate(1, 120));
        assertEquals(Short.MAX_VALUE, KeepPacer.operatingRate(4, 20000));
    }
}
