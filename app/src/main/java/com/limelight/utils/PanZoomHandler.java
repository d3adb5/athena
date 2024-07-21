package com.limelight.utils;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.limelight.preferences.PreferenceConfiguration;

public class PanZoomHandler {
    static private final float MAX_SCALE = 10.0f;

    private final View streamView;
    private final View parent;
    private final PreferenceConfiguration prefConfig;
    private final boolean isFillMode;
    private final boolean isTopMode;
    private final ScaleGestureDetector scaleGestureDetector;
    private final GestureDetector gestureDetector;
    private float scaleFactor = 1.0f;
    private float childX, childY = 0;
    private float parentWidth, parentHeight = 0;
    private float childWidth, childHeight = 0;

    public PanZoomHandler(Context context, View streamView, View parent, PreferenceConfiguration prefConfig) {
        this.streamView = streamView;
        this.parent = parent;
        this.prefConfig = prefConfig;
        this.isFillMode = prefConfig.videoScaleMode == PreferenceConfiguration.ScaleMode.FILL;
        this.isTopMode = prefConfig.enableDisplayTopCenter;
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());

        // Everything gets easier with 0,0 as the pivot point
        streamView.setPivotX(0);
        streamView.setPivotY(0);
        parent.setPivotX(0);
        parent.setPivotY(0);
    }

    public void handleTouchEvent(MotionEvent motionEvent) {
        scaleGestureDetector.onTouchEvent(motionEvent);
        gestureDetector.onTouchEvent(motionEvent);
    }

    private void updateDimensions() {
        childX = streamView.getX();
        childY = streamView.getY();

        childHeight = streamView.getHeight() * scaleFactor;
        childWidth = streamView.getWidth() * scaleFactor;
        parentWidth = parent.getWidth();
        parentHeight = parent.getHeight();
    }

    private void constrainToBounds() {
        updateDimensions();

        if (parentWidth >= childWidth) {
            childX = (parentWidth - childWidth) / 2;
        } else {
            float boundaryX = childWidth - parentWidth;
            childX = Math.max(-boundaryX, Math.min(childX, 0));
        }

        if (parentHeight >= childHeight) {
            if (isTopMode) {
                childY = 0;
            } else {
                childY = (parentHeight - childHeight) / 2;
            }
        } else {
            float boundaryY = childHeight - parentHeight;
            childY = Math.max(-boundaryY, Math.min(childY, 0));
        }

        streamView.setX(childX);
        streamView.setY(childY);
    }

    public void handleSurfaceChange() {
        if (childWidth == 0) {
            return;
        }

        float prevChildWidth = childWidth;
        float prevParentWidth = parentWidth;
        float prevParentHeight = parentHeight;

        float prevViewCenterX = childX - prevParentWidth / 2;
        float prevViewCenterY = childY - prevParentHeight / 2;

        updateDimensions();

        float viewScale = childWidth / prevChildWidth;

        float newViewCenterX = prevViewCenterX * viewScale;
        float newViewCenterY = prevViewCenterY * viewScale;

        childX = newViewCenterX + parentWidth / 2;
        childY = newViewCenterY + parentHeight / 2;

        streamView.setX((int)childX);
        streamView.setY((int)childY);

        constrainToBounds();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float newScaleFactor = scaleFactor * detector.getScaleFactor();
            newScaleFactor = Math.max(1, Math.min(newScaleFactor, MAX_SCALE)); // Apply minimum scale

            // Calculate pivot point
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            childX = streamView.getX();
            childY = streamView.getY();

            float prevScaleFactor = scaleFactor;

            float dPivotX = childX - focusX;
            float dPivotY = childY - focusY;

            float moveX = dPivotX * (newScaleFactor / prevScaleFactor - 1);
            float moveY = dPivotY * (newScaleFactor / prevScaleFactor - 1);

            streamView.setScaleX(newScaleFactor);
            streamView.setScaleY(newScaleFactor);

            streamView.setX(streamView.getX() + moveX);
            streamView.setY(streamView.getY() + moveY);

            scaleFactor = newScaleFactor;

            constrainToBounds(); // Use the new method name

            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            childX = streamView.getX() - distanceX;
            childY = streamView.getY() - distanceY;

            streamView.setX(childX);
            streamView.setY(childY);

            constrainToBounds();

            return true;
        }
    }
}
