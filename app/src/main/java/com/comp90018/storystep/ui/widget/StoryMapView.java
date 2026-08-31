package com.comp90018.storystep.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.comp90018.storystep.R;

/**
 * A deterministic map-shaped prototype surface used while the real map provider is undecided.
 * It deliberately draws only a straight origin-to-destination direction indicator: this avoids
 * suggesting road routing or turn-by-turn navigation that is outside the StoryStep MVP.
 */
public class StoryMapView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint directionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean explorationMode;
    private boolean compactMode;

    public StoryMapView(Context context) {
        this(context, null);
    }

    public StoryMapView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StoryMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        roadPaint.setColor(ContextCompat.getColor(context, R.color.map_road));
        roadPaint.setStrokeWidth(dp(5));
        roadPaint.setStyle(Paint.Style.STROKE);
        directionPaint.setColor(ContextCompat.getColor(context, R.color.brand_teal));
        directionPaint.setStrokeWidth(dp(4));
        directionPaint.setStrokeCap(Paint.Cap.ROUND);
        directionPaint.setStyle(Paint.Style.STROKE);
        directionPaint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(9)}, 0));
        setContentDescription(context.getString(R.string.home_map_description));
    }

    public void setExplorationMode(boolean explorationMode) {
        this.explorationMode = explorationMode;
        invalidate();
    }

    public void setCompactMode(boolean compactMode) {
        this.compactMode = compactMode;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();

        canvas.drawColor(ContextCompat.getColor(getContext(), R.color.map_land));
        drawParks(canvas, width, height);
        drawRiver(canvas, width, height);
        drawRoads(canvas, width, height);
        drawBuildings(canvas, width, height);

        float originX = width * 0.43f;
        float originY = height * 0.68f;
        float destinationX = width * 0.74f;
        float destinationY = height * 0.28f;
        if (explorationMode) {
            canvas.drawLine(originX, originY, destinationX, destinationY, directionPaint);
            drawPin(canvas, destinationX, destinationY, "", true);
        } else if (!compactMode) {
            drawPin(canvas, width * 0.72f, height * 0.23f, "1", false);
            drawPin(canvas, width * 0.22f, height * 0.43f, "2", false);
            drawPin(canvas, width * 0.78f, height * 0.72f, "3", false);
        } else {
            drawPin(canvas, destinationX, destinationY, "", true);
        }
        drawCurrentLocation(canvas, originX, originY);
    }

    private void drawParks(Canvas canvas, float width, float height) {
        paint.setColor(ContextCompat.getColor(getContext(), R.color.map_park));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(width * 0.02f, height * 0.04f,
                width * 0.30f, height * 0.92f), dp(18), dp(18), paint);
        canvas.drawRoundRect(new RectF(width * 0.58f, height * 0.42f,
                width * 0.96f, height * 0.84f), dp(18), dp(18), paint);
    }

    private void drawRiver(Canvas canvas, float width, float height) {
        paint.setColor(ContextCompat.getColor(getContext(), R.color.map_water));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(13));
        Path river = new Path();
        river.moveTo(width * 0.09f, -dp(8));
        river.cubicTo(width * 0.02f, height * 0.24f,
                width * 0.20f, height * 0.45f,
                width * 0.10f, height * 0.67f);
        river.cubicTo(width * 0.03f, height * 0.82f,
                width * 0.16f, height * 0.93f,
                width * 0.12f, height + dp(8));
        canvas.drawPath(river, paint);
    }

    private void drawRoads(Canvas canvas, float width, float height) {
        for (int column = 1; column < 7; column++) {
            float x = width * column / 7f;
            canvas.drawLine(x, 0, x + width * 0.08f, height, roadPaint);
        }
        for (int row = 1; row < 6; row++) {
            float y = height * row / 6f;
            canvas.drawLine(0, y, width, y - height * 0.07f, roadPaint);
        }
    }

    private void drawBuildings(Canvas canvas, float width, float height) {
        paint.setColor(0x55B7B8B2);
        paint.setStyle(Paint.Style.FILL);
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 5; column++) {
                float left = width * (0.32f + column * 0.12f);
                float top = height * (0.08f + row * 0.19f);
                canvas.drawRoundRect(new RectF(left, top, left + width * 0.075f,
                        top + height * 0.10f), dp(4), dp(4), paint);
            }
        }
    }

    private void drawPin(Canvas canvas, float x, float y, String label, boolean destination) {
        paint.setColor(ContextCompat.getColor(getContext(), R.color.brand_teal));
        paint.setStyle(Paint.Style.FILL);
        float radius = dp(destination ? 18 : 17);
        canvas.drawCircle(x, y, radius, paint);
        Path point = new Path();
        point.moveTo(x - radius * 0.55f, y + radius * 0.65f);
        point.lineTo(x, y + radius * 1.55f);
        point.lineTo(x + radius * 0.55f, y + radius * 0.65f);
        point.close();
        canvas.drawPath(point, paint);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.white));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(14));
        paint.setFakeBoldText(true);
        canvas.drawText(destination ? "◆" : label, x, y + dp(5), paint);
        paint.setFakeBoldText(false);
    }

    private void drawCurrentLocation(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x332389F5);
        canvas.drawCircle(x, y, dp(30), paint);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.white));
        canvas.drawCircle(x, y, dp(11), paint);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.map_location));
        canvas.drawCircle(x, y, dp(8), paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
