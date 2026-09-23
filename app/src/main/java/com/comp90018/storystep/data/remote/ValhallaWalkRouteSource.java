package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.plan.PlannedRoute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

/**
 * The walking route from Valhalla, decoded into a path the map can draw.
 *
 * <p>Every failure is the same answer - null, meaning "walk it straight" - because a plan the
 * user can still read and start is worth more than an error about a routing service they never
 * asked about. Valhalla's manoeuvres are not read; see {@link ValhallaRouteDto}.
 */
public final class ValhallaWalkRouteSource implements WalkRouteSource {

    /** Valhalla encodes shapes at six decimal places, not the usual five. */
    private static final int SHAPE_PRECISION = 6;

    /** Two points make a route; one makes nothing. */
    private static final int MIN_POINTS = 2;

    private final ValhallaApiService service;

    public ValhallaWalkRouteSource(ValhallaApiService service) {
        this.service = service;
    }

    @Override
    public PlannedRoute walkThrough(List<GeoPoint> points) {
        if (points == null || points.size() < MIN_POINTS) {
            return null;
        }
        try {
            Response<ValhallaRouteDto> response =
                    service.route(ValhallaRouteRequest.through(points)).execute();
            ValhallaRouteDto body = response.body();
            if (!response.isSuccessful() || body == null || body.trip == null
                    || body.trip.legs == null || body.trip.summary == null) {
                return null;
            }
            List<GeoPoint> path = new ArrayList<>();
            for (ValhallaRouteDto.Leg leg : body.trip.legs) {
                if (leg == null || leg.shape == null) {
                    continue;
                }
                List<GeoPoint> decoded = EncodedPolyline.decode(leg.shape, SHAPE_PRECISION);
                // Legs meet at the stops; the shared point would otherwise be drawn twice.
                if (!path.isEmpty() && !decoded.isEmpty()) {
                    decoded = decoded.subList(1, decoded.size());
                }
                path.addAll(decoded);
            }
            if (path.size() < MIN_POINTS) {
                return null;
            }
            return PlannedRoute.walked(path,
                    body.trip.summary.length * 1000.0,
                    Math.round(body.trip.summary.time * 1000.0));
        } catch (IOException | RuntimeException unreachable) {
            return null;
        }
    }
}
