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
        /** Which glyph and colour the pin gets; null for the plain story dot. */
        @Nullable public final com.comp90018.storystep.domain.model.AttractionCategory.Kind kind;
        /** Kept places wear the star when the map is showing what is kept. */
        public final boolean starred;
        /** A planned stop's position in the walk, 1-based; zero for a place that is not one. */
        public final int order;

        public Place(String id, String name, GeoPoint location) {
            this(id, name, location, null, false);
        }

        public Place(String id, String name, GeoPoint location,
                     @Nullable com.comp90018.storystep.domain.model.AttractionCategory.Kind kind,
                     boolean starred) {
            this(id, name, location, kind, starred, 0);
        }

        /** A stop in a planned walk: the pin carries its number instead of a glyph. */
        public static Place stop(String id, String name, GeoPoint location, int order) {
            return new Place(id, name, location, null, false, order);
        }

        private Place(String id, String name, GeoPoint location,
                      @Nullable com.comp90018.storystep.domain.model.AttractionCategory.Kind kind,
                      boolean starred, int order) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.kind = kind;
            this.starred = starred;
            this.order = order;
        }
    }

    /** Receives the id of a pinned place when its pin is tapped. */
    public interface OnPlaceClickListener {
        void onPlaceClick(String attractionId);
    }

    /**
     * OpenStreetMap's Humanitarian style, served by OSM France without a key: full detail at
     * every walking zoom, and far less cluttered than the standard Carto rendering, which
     * covers a campus in parking and cafe icons at z18.
     *
     * <p>Warmed and quietened toward the page's paper by {@link #paperFilter()}, so the map
     * reads as the sheet the rest of the app is printed on and the app's own pins, stops and
     * route carry the colour.
     *
     * <p>Rejected: CARTO's Positron stamps "API KEY REQUIRED" across anonymous tiles; Esri's
     * World Light Gray Canvas is beautifully quiet but has no data above zoom 16 in Melbourne,
     * which is below walking zoom; OpenStreetMap's own Mapnik tiles are forbidden to apps by
     * the OSMF tile usage policy.
     */
    private static final org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase LIGHT_CANVAS =
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
        map.setTileSource(LIGHT_CANVAS);
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
            pins.add(new PinOverlay.Pin(place.id, toOsm(place.location),
                    labelledPin(place.name, place.kind, place.starred, place.order)));
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
        int brand = ContextCompat.getColor(getContext(), R.color.story_signal);
        int casing = ContextCompat.getColor(getContext(), R.color.story_surface);
        for (List<GeoPoint> segment : segments) {
            if (segment.size() < 2) {
                continue;
            }
            List<org.osmdroid.util.GeoPoint> points = new ArrayList<>(segment.size());
            for (GeoPoint p : segment) {
                points.add(toOsm(p));
            }
            // Two lines, as a map draws a road: a white casing under a brand-coloured stroke,
            // so the route reads over parks, buildings and the map's own streets alike. Round
            // caps and joins keep the corners from spiking where a path turns sharply.
            routeLines.add(stroke(points, casing, 9f, false));
            routeLines.add(stroke(points, brand, 5.5f, true));
        }
        // Added after the loop, casing first within each pair, and all of them under the pins.
        for (int i = routeLines.size() - 1; i >= 0; i--) {
            map.getOverlays().add(0, routeLines.get(i));
        }
        map.invalidate();
    }

    /** One line of the route: the casing beneath, or the coloured stroke over it. */
    private Polyline stroke(List<org.osmdroid.util.GeoPoint> points, int colour, float widthDp,
                            boolean shadowed) {
        Polyline line = new Polyline(map);
        line.setPoints(points);
        android.graphics.Paint paint = line.getOutlinePaint();
        paint.setColor(colour);
        paint.setStrokeWidth(dp(widthDp));
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        paint.setAntiAlias(true);
        if (shadowed) {
            // A hairline shadow lifts the route off the paper without darkening the colour.
            paint.setShadowLayer(dp(2f), 0, dp(0.5f), 0x33000000);
        }
        line.setInfoWindow(null);
        return line;
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
            map.post(() -> {
                map.zoomToBoundingBox(box.increaseByScale(1.5f), false);
                // A short walk otherwise frames itself at a zoom where the tiles are all
                // building outlines and the street names have gone.
                if (map.getZoomLevelDouble() > WALK_ZOOM) {
                    map.getController().setZoom(WALK_ZOOM);
                }
            });
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


    /**
     * The disc - coloured for its kind with the kind's glyph, or white with the story dot -
     * and the name in ink with a paper halo beneath it.
     */
    private android.graphics.Bitmap labelledPin(
            String name,
            @Nullable com.comp90018.storystep.domain.model.AttractionCategory.Kind kind,
            boolean starred,
            int order) {
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
        if (order > 0) {
            // A planned stop: the brand disc with its number, so the order reads off the map.
            paint.setColor(ContextCompat.getColor(getContext(), R.color.story_signal));
            canvas.drawCircle(cx, cy, disc / 2f - 2.5f * density, paint);
            android.text.TextPaint number = new android.text.TextPaint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            number.setTextSize(13f * density);
            number.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(
                    getContext(), R.font.bricolage_bold));
            number.setColor(ContextCompat.getColor(getContext(), R.color.story_signal_ink));
            String label2 = String.valueOf(order);
            float width2 = number.measureText(label2);
            canvas.drawText(label2, cx - width2 / 2f,
                    cy - (number.getFontMetrics().ascent + number.getFontMetrics().descent) / 2f,
                    number);
            return finishPin(bitmap, canvas, text, label, cx, disc, gap, textWidth, density);
        }
        int glyph = starred ? R.drawable.ic_kind_saved
                : kind == null ? 0
                : kind == com.comp90018.storystep.domain.model.AttractionCategory.Kind.FOOD
                        ? R.drawable.ic_kind_food
                : kind == com.comp90018.storystep.domain.model.AttractionCategory.Kind.DRINK
                        ? R.drawable.ic_kind_drink
                : kind == com.comp90018.storystep.domain.model.AttractionCategory.Kind.SHOPPING
                        ? R.drawable.ic_kind_shopping
                : R.drawable.ic_kind_sights;
        if (glyph == 0) {
            paint.setColor(ContextCompat.getColor(getContext(), R.color.story_signal));
            canvas.drawCircle(cx, cy, 5.5f * density, paint);
        } else {
            // A smaller coloured disc inside the white one, the glyph in white on it.
            paint.setColor(ContextCompat.getColor(getContext(), kindColour(kind, starred)));
            float inner = disc / 2f - 3 * density;
            canvas.drawCircle(cx, cy, inner, paint);
            android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(getContext(), glyph);
            if (icon != null) {
                int half = Math.round(inner * 0.62f);
                icon.setBounds(Math.round(cx) - half, Math.round(cy) - half,
                        Math.round(cx) + half, Math.round(cy) + half);
                icon.draw(canvas);
            }
        }

        return finishPin(bitmap, canvas, text, label, cx, disc, gap, textWidth, density);
    }

    /** Draws the name under a pin's disc and returns the finished bitmap. */
    private android.graphics.Bitmap finishPin(android.graphics.Bitmap bitmap,
                                              android.graphics.Canvas canvas,
                                              android.text.TextPaint text,
                                              String label,
                                              float cx,
                                              int disc,
                                              int gap,
                                              float textWidth,
                                              float density) {
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

    /** The colour of a kind's disc; the star has its own. */
    public static int kindColour(
            @Nullable com.comp90018.storystep.domain.model.AttractionCategory.Kind kind,
            boolean starred) {
        if (starred) {
            return R.color.kind_saved;
        }
        if (kind == null) {
            return R.color.story_signal;
        }
        switch (kind) {
            case FOOD:
                return R.color.kind_food;
            case DRINK:
                return R.color.kind_drink;
            case SHOPPING:
                return R.color.kind_shopping;
            default:
                return R.color.kind_sights;
        }
    }

    /**
     * Quietens the tiles and warms them toward the page's paper: enough colour left that a
     * park is green and water is blue, red and green lifted more than blue so the map's white
     * becomes the app's cream. The earlier filter took almost all the colour out and left a
     * grey rectangle the rest of the app sat on rather than contained.
     */
    private static android.graphics.ColorMatrixColorFilter paperFilter() {
        android.graphics.ColorMatrix desaturate = new android.graphics.ColorMatrix();
        desaturate.setSaturation(0.42f);
        android.graphics.ColorMatrix lift = new android.graphics.ColorMatrix(new float[] {
                0.92f, 0, 0, 0, 20,
                0, 0.92f, 0, 0, 18,
                0, 0, 0.92f, 0, 8,
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
