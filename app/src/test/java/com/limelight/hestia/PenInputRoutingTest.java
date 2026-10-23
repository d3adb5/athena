package com.limelight.hestia;

import android.view.InputDevice;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@Config(sdk = 33)
@RunWith(RobolectricTestRunner.class)
public class PenInputRoutingTest {
    private static MotionEvent pointers(int action, int... tools) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[tools.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[tools.length];
        for (int i = 0; i < tools.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i + 11;
            props[i].toolType = tools[i];
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = 100 + i * 20;
            coords[i].y = 200 + i * 20;
            coords[i].pressure = 0.65f;
        }
        return MotionEvent.obtain(0, 12, action, tools.length, props, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    @Test public void penIsDetectedAtAnyPointerIndex() {
        MotionEvent event = pointers(MotionEvent.ACTION_MOVE,
                MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS);
        try {
            assertTrue(PenInputRouting.hasPenPointer(event));
            assertTrue(PenInputRouting.hasFingerPointer(event));
            assertTrue(PenInputRouting.isFinger(event, 0));
            assertFalse(PenInputRouting.isFinger(event, 1));
        } finally {
            event.recycle();
        }
    }

    @Test public void eraserIsNotClassifiedAsFingerOrMouse() {
        MotionEvent event = pointers(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_ERASER);
        try {
            assertTrue(PenInputRouting.hasPenPointer(event));
            assertFalse(PenInputRouting.hasFingerPointer(event));
            assertTrue(PenInputRouting.isPen(event.getToolType(0)));
        } finally {
            event.recycle();
        }
    }

    @Test public void regularFingerInputDoesNotEnterPenPath() {
        MotionEvent event = pointers(MotionEvent.ACTION_MOVE,
                MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_FINGER);
        try {
            assertFalse(PenInputRouting.hasPenPointer(event));
            assertTrue(PenInputRouting.hasFingerPointer(event));
        } finally {
            event.recycle();
        }
    }

    @Test public void pressureIsBoundedAndRejectsInvalidValues() {
        assertEquals(0.0f, PenInputRouting.boundedPressure(-1.0f), 0);
        assertEquals(0.55f, PenInputRouting.boundedPressure(0.55f), 0);
        assertEquals(1.0f, PenInputRouting.boundedPressure(2.0f), 0);
        assertEquals(0.0f, PenInputRouting.boundedPressure(Float.NaN), 0);
        assertEquals(0.0f, PenInputRouting.boundedPressure(Float.POSITIVE_INFINITY), 0);
    }

    @Test public void radiansAreConvertedToSafeTiltDegrees() {
        assertEquals(0, PenInputRouting.tiltDegrees(0));
        assertEquals(45, PenInputRouting.tiltDegrees((float)Math.PI / 4));
        assertEquals(90, PenInputRouting.tiltDegrees((float)Math.PI));
        assertEquals(0, PenInputRouting.tiltDegrees(Float.NaN));
    }
}
