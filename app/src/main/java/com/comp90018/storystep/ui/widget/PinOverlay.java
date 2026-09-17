package com.comp90018.storystep.ui.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws every place pin in one pass.
 *
 * <p>One overlay for all the pins rather than one Marker each: the map pans with a single draw
 * call, and the pin's bitmap - the disc with the name beneath it - is positioned so the disc's
 * centre, not the bitmap's, sits on the coordinate. A tap inside a disc reports that pin's id.
 */
final class PinOverlay extends Overlay {

    interface OnPinClickListener {
        void onPinClick(String id);
    }

    static final class Pin {
        final String id;
        final GeoPoint position;
        final Bitmap bitmap;

        Pin(String id, GeoPoint position, Bitmap bitmap) {
            this.id = id;
            this.position = position;
            this.bitmap = bitmap;
        }
    }

    /** Height of the disc at the top of every pin bitmap, in pixels; the label hangs below it. */
    static int discPx;

    private List<Pin> pins = Collections.emptyList();
    private final Point pixel = new Point();
    @Nullable private OnPinClickListener listener;

    void setPins(List<Pin> newPins) {
        pins = new ArrayList<>(newPins);

    }

    void setOnPinClickListener(@Nullable OnPinClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void draw(Canvas canvas, Projection projection) {
        for (Pin pin : pins) {
            projection.toPixels(pin.position, pixel);
            float left = pixel.x - pin.bitmap.getWidth() / 2f;
            float top = pixel.y - discPx / 2f;
            canvas.drawBitmap(pin.bitmap, left, top, null);
        }
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event, MapView mapView) {
        if (listener == null) {
            return false;
        }
        Projection projection = mapView.getProjection();
        // Last drawn is on top, so test in reverse.
        for (int i = pins.size() - 1; i >= 0; i--) {
            Pin pin = pins.get(i);
            projection.toPixels(pin.position, pixel);
            float dx = event.getX() - pixel.x;
            float dy = event.getY() - pixel.y;
            float radius = discPx * 0.75f;
            if (dx * dx + dy * dy <= radius * radius) {
                listener.onPinClick(pin.id);
                return true;
            }
        }
        return false;
    }
}
