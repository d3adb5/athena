package com.limelight.hestia;

import android.view.MotionEvent;

/**
 * Tool-aware pointer routing for Android tablets.
 *
 * An Android gesture may contain both finger and stylus pointers, and the
 * stylus is not guaranteed to be pointer index zero.
 */
public final class PenInputRouting {
    private PenInputRouting() {}

    public static boolean isPen(int toolType) {
        return toolType == MotionEvent.TOOL_TYPE_STYLUS
                || toolType == MotionEvent.TOOL_TYPE_ERASER;
    }

    public static boolean hasPenPointer(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (isPen(event.getToolType(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasFingerPointer(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFinger(MotionEvent event, int pointerIndex) {
        return event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_FINGER;
    }

    /** Hardware may report pressure slightly outside [0,1] under load. */
    public static float boundedPressure(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** Convert the Android tilt axis (radians) to protocol degrees. */
    public static byte tiltDegrees(float radians) {
        if (Float.isNaN(radians) || Float.isInfinite(radians)) {
            return 0;
        }
        double degrees = Math.toDegrees(radians);
        return (byte) Math.max(0, Math.min(90, Math.round(degrees)));
    }
}
