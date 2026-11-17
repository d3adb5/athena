package com.limelight.binding.video;

/**
 * Frame pacing "keep": each frame gets a vsync of its own, as early as it can be shown. SurfaceFlinger shows only
 * the newest of the frames ready at a wake-up, so a late frame would replace the one after it; it holds back a
 * frame whose desired present time is after the present it is composing for. Plain arithmetic, so it has tests.
 */
public final class KeepPacer {
    /** A delay over this is real and drains; a smaller one keeps a frame in phase with its panel. */
    static final long REAL_DELAY_NS = 2500000;
    static final long DRAIN_NS = 5000;
    /** Over a period plus this behind, one frame is given up to get the latency back. */
    static final long BEHIND_SLACK_NS = 1000000;

    private long lastTargetNs;

    /**
     * @param nowNs    System.nanoTime() at release
     * @param periodNs the panel's measured period
     * @param leadNs   how far ahead SurfaceFlinger needs a frame (its presentation deadline, less a margin)
     * @return the desired present time for releaseOutputBuffer
     */
    public long target(long nowNs, long periodNs, long leadNs) {
        final long earliest = nowNs + leadNs;
        long target = lastTargetNs + periodNs;
        if (target - earliest > REAL_DELAY_NS) {
            target -= DRAIN_NS;
        }
        target = Math.max(earliest, target);
        if (target - earliest > periodNs + BEHIND_SLACK_NS) {
            target = earliest;
        }
        lastTargetNs = target;
        return target;
    }

    /** SurfaceFlinger's lead to stay just inside of, so a frame that makes the next wake-up is not held for the one after. */
    public static long leadFor(long presentationDeadlineNs) {
        return Math.max(0, presentationDeadlineNs - 1500000);
    }

    /** KEY_OPERATING_RATE for a "Decoder speed" factor: 0 = as fast as it can (what the decoder was always asked). */
    public static int operatingRate(int factor, int refreshRate) {
        return factor <= 0 ? Short.MAX_VALUE : Math.min(Short.MAX_VALUE, Math.max(1, refreshRate) * factor);
    }
}
