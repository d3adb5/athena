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
    private float childCenterX, childCenterY;
    private float parentCenterX, parentCenterY;
    private float relativeCenterX, relativeCenterY;
    private float parentWidth, parentHeight;
    private float childWidth, childHeight;

    public PanZoomHandler(Context context, View streamView, View parent, PreferenceConfiguration prefConfig) {
        this.streamView = streamView;
        this.parent = parent;
        this.prefConfig = prefConfig;
        this.isFillMode = prefConfig.videoScaleMode == PreferenceConfiguration.ScaleMode.FILL;
        this.isTopMode = prefConfig.enableDisplayTopCenter;
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void handleTouchEvent(MotionEvent motionEvent) {
        scaleGestureDetector.onTouchEvent(motionEvent);
        gestureDetector.onTouchEvent(motionEvent);
    }

    private void calculateDimensions() {
        int[] childLocation = new int[2];
        int[] parentLocation = new int[2];

        streamView.getLocationInWindow(childLocation);
        parent.getLocationInWindow(parentLocation);

        childHeight = streamView.getHeight() * scaleFactor;
        childWidth = streamView.getWidth() * scaleFactor;
        parentWidth = parent.getWidth();
        parentHeight = parent.getHeight();

        childCenterX = childLocation[0] + childWidth / 2.0f;
        childCenterY = childLocation[1] + childHeight / 2.0f;

        parentCenterX = parentLocation[0] + parentWidth / 2.0f;
        parentCenterY = parentLocation[1] + parentHeight / 2.0f;

        relativeCenterX = childCenterX - parentCenterX;
        relativeCenterY = childCenterY - parentCenterY;
    }

    private void constrainToBounds() {
        calculateDimensions();

        float posX = streamView.getX();
        float posY = streamView.getY();

        if (parentWidth >= childWidth) {
            posX -= relativeCenterX;
        } else {
            float boundaryX = (childWidth - parentWidth) / 2;
            posX += Math.max(-boundaryX, Math.min(relativeCenterX, boundaryX)) - relativeCenterX;
        }

        if (parentHeight >= childHeight) {
            if (isTopMode) {
                float boundaryY = (childHeight - parentHeight) / 2;
                posY += boundaryY - relativeCenterY;
            } else {
                posY -= relativeCenterY;
            }
        } else {
            float boundaryY = (childHeight - parentHeight) / 2;
            posY += Math.max(-boundaryY, Math.min(relativeCenterY, boundaryY)) - relativeCenterY;
        }

        streamView.setX(posX);
        streamView.setY(posY);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float newScaleFactor = scaleFactor * detector.getScaleFactor();
            newScaleFactor = Math.min(newScaleFactor, MAX_SCALE); // Apply minimum scale

            // Ensure the streamView does not scale smaller than the parent
            float minWScale = parentWidth / streamView.getWidth();
            float minHScale = parentHeight / streamView.getHeight();
            float minPossibleScale = isFillMode ? Math.max(minWScale, minHScale) : Math.min(minWScale, minHScale);
            newScaleFactor = Math.max(newScaleFactor, minPossibleScale);

            float prevScaleFactor = scaleFactor;
            scaleFactor = newScaleFactor;
            calculateDimensions();

            // Calculate pivot point
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            float dPivotX = childCenterX - focusX;
            float dPivotY = childCenterY - focusY;

            float moveX = dPivotX * (newScaleFactor / prevScaleFactor - 1);
            float moveY = dPivotY * (newScaleFactor / prevScaleFactor - 1);

            streamView.setScaleX(scaleFactor);
            streamView.setScaleY(scaleFactor);

            streamView.setX(streamView.getX() + moveX);
            streamView.setY(streamView.getY() + moveY);

            constrainToBounds(); // Use the new method name

            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            float panX = streamView.getX() - distanceX;
            float panY = streamView.getY() - distanceY;

            streamView.setX(panX);
            streamView.setY(panY);

            constrainToBounds();

            return true;
        }
    }
}
