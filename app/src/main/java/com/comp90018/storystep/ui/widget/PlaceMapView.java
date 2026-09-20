package com.comp90018.storystep.ui.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.GeoPoint;

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

    /**
     * OpenStreetMap's Humanitarian style: lighter and far less cluttered than the default
     * Mapnik rendering, served by OSM France without a key. Lifted further toward the page's
     * paper by {@link #paperFilter()} so the map recedes and the pins carry the colour.
     * (CARTO's Positron, the obvious choice, now stamps "API KEY REQUIRED" across anonymous
     * tiles.)
     */
    private static final org.osmdroid.tileprovider.tilesource.XYTileSource HUMANITARIAN =
            new org.osmdroid.tileprovider.tilesource.XYTileSource(
                    "OsmHumanitarian", 0, 19, 256, ".png",
                    new String[] {
                            "https://a.tile.openstreetmap.fr/hot/",
                            "https://b.tile.openstreetmap.fr/hot/"},
                    "© OpenStreetMap contributors · Humanitarian style");

    private static final double DEFAULT_ZOOM = 15.6;
    private static final double WALK_ZOOM = 16.5;

    private final MapView map;
    private final PinOverlay pinOverlay = new PinOverlay();
    private final List<Polyline> routeLines = new ArrayList<>();
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
        map.setTileSource(HUMANITARIAN);
        map.getOverlayManager().getTilesOverlay().setColorFilter(paperFilter());
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getOverlays().add(new CopyrightOverlay(context));
        pinOverlay.setOnPinClickListener(id -> {
            if (placeClickListener != null) {
                placeClickListener.onPlaceClick(id);
            }
        });
        map.getOverlays().add(pinOverlay);
        addView(map, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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
        List<PinOverlay.Pin> pins = new ArrayList<>(places.size());
        for (Place place : places) {
            pins.add(new PinOverlay.Pin(place.id, toOsm(place.location), labelledPin(place.name)));
        }
        pinOverlay.setPins(pins);
        map.invalidate();
        describeContents(places.size());
    }

    /**
     * Says how many places the map is showing, and where they can be read instead.
     *
     * <p>The pins are drawn on a canvas and are not reachable by touch exploration. Rather than
     * build a virtual view tree over them, the map states what it holds and points at the list,
     * which carries the same places as ordinary focusable rows with distance and category. The
     * map is a second, visual rendering of that list — useful, but never the only way to the
     * information.
     */
    private void describeContents(int placeCount) {
        setContentDescription(placeCount == 0
                ? getContext().getString(R.string.home_map_description)
                : getResources().getQuantityString(
                        R.plurals.home_map_description_count, placeCount, placeCount));
    }

    /**
     * How much of the map's bottom is covered by something else - the places sheet at rest.
     * The map's logical centre moves up by half of it, so "centred on the user" means centred
     * in the part of the map that can be seen, and the pins around the user are not hidden.
     */
    public void setCoveredBottom(int pixels) {
        map.setMapCenterOffset(0, -pixels / 2);
        map.invalidate();
    }

    /** Animates back to the user, at the default zoom. */
    public void recenter() {
        if (userMarker == null) {
            return;
        }
        map.getController().animateTo(userMarker.getPosition(), DEFAULT_ZOOM, 350L);
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


    /** The disc with the story dot, and the name in ink with a paper halo beneath it. */
    private android.graphics.Bitmap labelledPin(String name) {
        float density = getResources().getDisplayMetrics().density;
        int disc = Math.round(30 * density);
        PinOverlay.discPx = disc;
        android.text.TextPaint text = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(11.5f * density);
        text.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(
                getContext(), R.font.bricolage_semibold));
        text.setColor(ContextCompat.getColor(getContext(), R.color.story_ink));
        String label = name.length() > 22 ? name.substring(0, 21) + "…" : name;
        float textWidth = text.measureText(label);
        int width = (int) Math.ceil(Math.max(disc, textWidth + 8 * density));
        int gap = Math.round(3 * density);
        int textHeight = Math.round(text.getFontMetrics().descent - text.getFontMetrics().ascent);
        int height = disc + gap + textHeight;

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        float cx = width / 2f;
        float cy = disc / 2f;

        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x22000000);
        canvas.drawCircle(cx, cy + density, disc / 2f, paint);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.story_surface));
        canvas.drawCircle(cx, cy, disc / 2f, paint);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.story_signal));
        canvas.drawCircle(cx, cy, 5.5f * density, paint);

        // Halo first, then the ink, so the name reads over roads and labels.
        float baseline = disc + gap - text.getFontMetrics().ascent;
        android.text.TextPaint halo = new android.text.TextPaint(text);
        halo.setStyle(android.graphics.Paint.Style.STROKE);
        halo.setStrokeWidth(3 * density);
        halo.setColor(ContextCompat.getColor(getContext(), R.color.story_surface));
        canvas.drawText(label, cx - textWidth / 2f, baseline, halo);
        canvas.drawText(label, cx - textWidth / 2f, baseline, text);
        return bitmap;
    }

    /** Most colour out, a little light in: the map becomes a light grey ground. */
    private static android.graphics.ColorMatrixColorFilter paperFilter() {
        android.graphics.ColorMatrix desaturate = new android.graphics.ColorMatrix();
        desaturate.setSaturation(0.18f);
        android.graphics.ColorMatrix lift = new android.graphics.ColorMatrix(new float[] {
                0.86f, 0, 0, 0, 34,
                0, 0.86f, 0, 0, 33,
                0, 0, 0.86f, 0, 30,
                0, 0, 0, 1, 0
        });
        desaturate.postConcat(lift);
        return new android.graphics.ColorMatrixColorFilter(desaturate);
    }

    private static org.osmdroid.util.GeoPoint toOsm(GeoPoint p) {
        return new org.osmdroid.util.GeoPoint(p.getLatitude(), p.getLongitude());
    }


    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
