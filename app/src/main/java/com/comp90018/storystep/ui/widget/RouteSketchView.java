package com.comp90018.storystep.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.geo.RouteProjection;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a recorded route as it was actually sampled.
 *
 * <p>Deliberately not {@link StoryMapView}. That view draws an invented street grid, and putting a
 * real trace on top of fake roads would suggest the app followed them — the turn-by-turn
 * impression this feature is required not to give. There is no basemap here at all: no roads, no
 * north arrow, no destination pin. Just the points the session recorded, joined in the order they
 * were walked.
 *
 * <p><b>Gaps are never bridged.</b> Each recorded span gets its own {@link Path}, and there is no
 * code path anywhere in this class that calls {@code lineTo} across a boundary between two of them.
 * A gap is time the app was not collecting — often the user walking somewhere with the app closed —
 * and a line drawn through it would be a claim about a route that was never observed.
 *
 * <p>All the maths lives in {@link RouteProjection}, so it is unit-tested rather than trapped
 * inside a View.
 */
public final class RouteSketchView extends View {

    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endpointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Path> paths = new ArrayList<>();

    /** Endpoint markers, as flat x/y pairs, so a gap reads as a gap rather than as missing data. */
    private final List<float[]> endpoints = new ArrayList<>();

    private List<RouteProjection.Projected> segments;

    public RouteSketchView(Context context) {
        this(context, null);
    }

    public RouteSketchView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RouteSketchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        routePaint.setColor(ContextCompat.getColor(context, R.color.brand_teal));
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(dp(3));
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);

        endpointPaint.setColor(ContextCompat.getColor(context, R.color.brand_teal));
        endpointPaint.setStyle(Paint.Style.STROKE);
        endpointPaint.setStrokeWidth(dp(2));
    }

    /**
     * Replaces the drawn route.
     *
     * @param segments projected spans, or null to draw nothing
     */
    public void setRoute(@Nullable List<RouteProjection.Projected> segments) {
        this.segments = segments;
        rebuild();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // The projection is normalised, so the paths depend on the view's size and have to be
        // rebuilt when it changes — but still not inside onDraw.
        rebuild();
    }

    private void rebuild() {
        paths.clear();
        endpoints.clear();
        if (segments == null || segments.isEmpty()) {
            return;
        }
        float inset = dp(6);
        float usableWidth = getWidth() - inset * 2f;
        float usableHeight = getHeight() - inset * 2f;
        if (usableWidth <= 0f || usableHeight <= 0f) {
            return;
        }
        for (RouteProjection.Projected segment : segments) {
            Path path = new Path();
            float[] xs = segment.getXs();
            float[] ys = segment.getYs();
            for (int index = 0; index < xs.length; index++) {
                float x = inset + xs[index] * usableWidth;
                float y = inset + ys[index] * usableHeight;
                if (index == 0) {
                    // A new span always starts the pen down somewhere new. This moveTo is the
                    // whole mechanism by which a gap stays a gap.
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            paths.add(path);
            endpoints.add(new float[]{
                    inset + xs[0] * usableWidth,
                    inset + ys[0] * usableHeight,
                    inset + xs[xs.length - 1] * usableWidth,
                    inset + ys[ys.length - 1] * usableHeight});
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float endpointRadius = dp(3);
        for (int index = 0; index < paths.size(); index++) {
            Path path = paths.get(index);
            // A span of one point draws no line; its endpoint markers are what makes it visible.
            canvas.drawPath(path, routePaint);
            float[] ends = endpoints.get(index);
            canvas.drawCircle(ends[0], ends[1], endpointRadius, endpointPaint);
            canvas.drawCircle(ends[2], ends[3], endpointRadius, endpointPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
