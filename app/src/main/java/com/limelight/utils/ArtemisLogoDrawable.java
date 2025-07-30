package com.limelight.utils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A custom Drawable that renders a white icon representing an external screen and a mouse cursor.
 * This is an alpha mask, drawn in white on a transparent background.
 */
public class ArtemisLogoDrawable extends Drawable {

    private final Paint paint;
    private final Path path;

    public ArtemisLogoDrawable() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        path = new Path();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(path, paint);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);

        path.reset();

        float width = bounds.width();
        float height = bounds.height();

        // --- Draw the Screen (a rounded rectangle) ---
        float screenWidth = width * 0.7f;
        float screenHeight = height * 0.5f;
        float screenLeft = width * 0.1f;
        float screenTop = height * 0.15f;
        float cornerRadius = width * 0.05f;
        path.addRoundRect(new RectF(screenLeft, screenTop, screenLeft + screenWidth, screenTop + screenHeight),
                cornerRadius, cornerRadius, Path.Direction.CW);

        // --- Draw the Mouse Cursor (an arrow) ---
        Path mousePath = new Path();
        float cursorLeft = width * 0.65f;
        float cursorTop = height * 0.55f;
        float cursorSize = width * 0.3f;

        mousePath.moveTo(cursorLeft, cursorTop); // Top point of the arrow
        mousePath.lineTo(cursorLeft, cursorTop + cursorSize); // Bottom-left point
        mousePath.lineTo(cursorLeft + (cursorSize * 0.5f), cursorTop + (cursorSize * 0.7f)); // Middle-right point
        mousePath.lineTo(cursorLeft + cursorSize, cursorTop + cursorSize); // Bottom-right point
        mousePath.close();

        // Combine the screen and mouse paths into one.
        path.op(mousePath, Path.Op.UNION);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}