package com.comp90018.storystep.ui.widget;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.GeoPoint;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * A real map: OpenStreetMap tiles with the user, the nearby places and, during a walk, the
 * destination and the recorded route.
 *
 * <p>This replaces the drawn prototype surface. It shows only what is true: where the user is,
 * where the places are, and the path the user actually walked. It never draws a suggested route
 * between them, because turn-by-turn navigation is outside the product's scope and a line to the
 * destination would read as one.
 *
 * <p>Tiles are desaturated and lifted toward the page's paper so the map sits inside the design
 * rather than shouting over it; the pins carry the colour. The OpenStreetMap attribution overlay
 * is always on, as the tile licence requires.
 */
public class PlaceMapView extends FrameLayout {

    /** A place to pin: its position and the name shown when the pin is tapped. */
    public static final class Place {
        public final String id;
        public final String name;
        public final GeoPoint location;

        public Place(String id, String name, GeoPoint location) {
            this.id = id;
            this.name = name;
            this.location = location;
        }
    }

    /** Receives the id of a pinned place when its pin is tapped. */
    public interface OnPlaceClickListener {
        void onPlaceClick(String attractionId);
    }

    private static final double DEFAULT_ZOOM = 16.0;
    private static final double WALK_ZOOM = 16.5;

    private final MapView map;
    private final List<Marker> placeMarkers = new ArrayList<>();
    private final List<Polyline> routeLines = new ArrayList<>();
    private final Drawable placeIcon;
    private final Drawable destinationIcon;
    private final Drawable userIcon;

    @Nullable private Marker userMarker;
    @Nullable private Marker destinationMarker;
    @Nullable private OnPlaceClickListener placeClickListener;
    private boolean framed;

    public PlaceMapView(Context context) {
        this(context, null);
    }

    public PlaceMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        map = new MapView(context);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getOverlayManager().getTilesOverlay().setColorFilter(paperFilter());
        map.getOverlays().add(new CopyrightOverlay(context));
        addView(map, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        placeIcon = ContextCompat.getDrawable(context, R.drawable.ic_map_pin_place);
        destinationIcon = ContextCompat.getDrawable(context, R.drawable.ic_map_pin_destination);
        userIcon = ContextCompat.getDrawable(context, R.drawable.ic_map_user);
        setContentDescription(context.getString(R.string.home_map_description));
    }

    /**
     * Where to look before any position exists. Moves the camera only - no marker - so a
     * screen with no fix yet opens on the area it will describe rather than on the whole
     * world at zoom zero. A later {@link #setOrigin} still frames on the real position.
     */
    public void centerOn(GeoPoint point) {
        if (point == null || framed) {
            return;
        }
        map.getController().setZoom(DEFAULT_ZOOM);
        map.getController().setCenter(toOsm(point));
    }

    /** The user's position. Centres the map the first time a position arrives. */
    public void setOrigin(@Nullable GeoPoint origin) {
        if (origin == null) {
            if (userMarker != null) {
                map.getOverlays().remove(userMarker);
                userMarker = null;
            }
            map.invalidate();
            return;
        }
        org.osmdroid.util.GeoPoint point = toOsm(origin);
        if (userMarker == null) {
            userMarker = new Marker(map);
            userMarker.setIcon(userIcon);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            userMarker.setInfoWindow(null);
            map.getOverlays().add(userMarker);
        }
        userMarker.setPosition(point);
        if (!framed) {
            map.getController().setZoom(DEFAULT_ZOOM);
            map.getController().setCenter(point);
            framed = true;
        }
        map.invalidate();
    }

    /**
     * Replaces every place pin. The viewport stays where the user is: "nearby" means around
     * you, and fitting every returned place would zoom out to the whole city.
     */
    public void setPlaces(List<Place> places) {
        for (Marker marker : placeMarkers) {
            map.getOverlays().remove(marker);
        }
        placeMarkers.clear();
        for (Place place : places) {
            Marker marker = new Marker(map);
            marker.setPosition(toOsm(place.location));
            marker.setIcon(placeIcon);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setTitle(place.name);
            marker.setInfoWindow(null);
            marker.setOnMarkerClickListener((m, mapView) -> {
                if (placeClickListener != null) {
                    placeClickListener.onPlaceClick(place.id);
                }
                return true;
            });
            placeMarkers.add(marker);
            map.getOverlays().add(marker);
        }
        map.invalidate();
    }

    public void setOnPlaceClickListener(@Nullable OnPlaceClickListener listener) {
        placeClickListener = listener;
    }

    /** The destination of the current walk, or {@code null} to clear it. */
    public void setDestination(@Nullable GeoPoint destination) {
        if (destination == null) {
            if (destinationMarker != null) {
                map.getOverlays().remove(destinationMarker);
                destinationMarker = null;
            }
            map.invalidate();
            return;
        }
        if (destinationMarker == null) {
            destinationMarker = new Marker(map);
            destinationMarker.setIcon(destinationIcon);
            destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            destinationMarker.setInfoWindow(null);
            map.getOverlays().add(destinationMarker);
        }
        destinationMarker.setPosition(toOsm(destination));
        map.invalidate();
    }

    /**
     * The walked route, one list of points per collection segment. Segments are drawn as separate
     * lines and never joined: the gap between them is time the user was not tracked.
     */
    public void setRoute(List<List<GeoPoint>> segments) {
        for (Polyline line : routeLines) {
            map.getOverlays().remove(line);
        }
        routeLines.clear();
        int ink = ContextCompat.getColor(getContext(), R.color.story_ink);
        for (List<GeoPoint> segment : segments) {
            if (segment.size() < 2) {
                continue;
            }
            Polyline line = new Polyline(map);
            List<org.osmdroid.util.GeoPoint> points = new ArrayList<>(segment.size());
            for (GeoPoint p : segment) {
                points.add(toOsm(p));
            }
            line.setPoints(points);
            line.getOutlinePaint().setColor(ink);
            line.getOutlinePaint().setStrokeWidth(dp(4));
            line.getOutlinePaint().setStrokeCap(android.graphics.Paint.Cap.ROUND);
            line.setInfoWindow(null);
            routeLines.add(line);
            // Routes sit under the markers so the pins stay tappable.
            map.getOverlays().add(0, line);
        }
        map.invalidate();
    }

    /** Frames a finished route so every recorded point is in view. Static: no user marker. */
    public void frameRoute(List<List<GeoPoint>> segments) {
        List<org.osmdroid.util.GeoPoint> points = new ArrayList<>();
        for (List<GeoPoint> segment : segments) {
            for (GeoPoint p : segment) {
                points.add(toOsm(p));
            }
        }
        if (points.isEmpty()) {
            return;
        }
        if (points.size() == 1) {
            map.getController().setZoom(WALK_ZOOM);
            map.getController().setCenter(points.get(0));
        } else {
            BoundingBox box = BoundingBox.fromGeoPoints(points);
            map.post(() -> map.zoomToBoundingBox(box.increaseByScale(1.5f), false));
        }
        framed = true;
    }

    /** A map that is looked at, not driven: no pan, no zoom, no pins to tap. */
    public void setStatic(boolean isStatic) {
        map.setMultiTouchControls(!isStatic);
        map.setClickable(!isStatic);
        map.setFocusable(!isStatic);
        setOnTouchListener(isStatic ? (v, e) -> true : null);
    }

    /** Frames the user and the destination together for the walking screen. */
    public void frameWalk(@Nullable GeoPoint user, @Nullable GeoPoint destination) {
        if (user != null && destination != null) {
            BoundingBox box = BoundingBox.fromGeoPoints(
                    java.util.Arrays.asList(toOsm(user), toOsm(destination)));
            map.post(() -> map.zoomToBoundingBox(box.increaseByScale(1.6f), false));
            framed = true;
        } else if (user != null) {
            map.getController().setZoom(WALK_ZOOM);
            map.getController().setCenter(toOsm(user));
            framed = true;
        }
    }

    public void onResume() {
        map.onResume();
    }

    public void onPause() {
        map.onPause();
    }


    private static org.osmdroid.util.GeoPoint toOsm(GeoPoint p) {
        return new org.osmdroid.util.GeoPoint(p.getLatitude(), p.getLongitude());
    }

    /** Desaturated and lifted so the tiles read as the page's paper, not as a poster. */
    private static ColorMatrixColorFilter paperFilter() {
        ColorMatrix desaturate = new ColorMatrix();
        desaturate.setSaturation(0.35f);
        ColorMatrix lift = new ColorMatrix(new float[] {
                0.88f, 0, 0, 0, 26,
                0, 0.88f, 0, 0, 24,
                0, 0, 0.88f, 0, 20,
                0, 0, 0, 1, 0
        });
        desaturate.postConcat(lift);
        return new ColorMatrixColorFilter(desaturate);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
