package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.Locale;
import java.util.Objects;

/**
 * Composes the Overpass QL query for a radius search.
 *
 * <p>A plain class with no Retrofit or Android dependency, so the exact query text is a unit-test
 * assertion rather than something only observable by making a real request. Overpass QL is a
 * language, not a parameter list: getting a clause wrong produces a valid request that quietly
 * returns the wrong places, which is precisely the kind of error a test on the generated string
 * catches and an integration test does not.
 *
 * <p>The generated query looks like:
 *
 * <pre>
 * [out:json][timeout:25];
 * (
 *   nwr["tourism"~"^(museum|gallery|artwork|attraction|viewpoint)$"](around:3800,-37.7950,144.9650);
 *   nwr["historic"](around:3800,-37.7950,144.9650);
 *   ...
 * );
 * out center 60;
 * </pre>
 *
 * <p>{@code nwr} asks for nodes, ways and relations together, because the same landmark is tagged
 * as any of the three depending on who mapped it. {@code out center} makes ways and relations
 * report a coordinate. The trailing number on {@code out} is the result limit — Overpass's own
 * form of paging, and the reason this app never needs to follow a cursor.
 */
public final class OverpassQueryBuilder {

    /**
     * Server-side timeout in seconds, declared inside the query.
     *
     * <p>Deliberately below the HTTP read timeout, so an overloaded Overpass gives up and says so
     * while the client is still listening, instead of the client abandoning a query the server is
     * still running.
     */
    private static final int SERVER_TIMEOUT_SECONDS = 25;

    private OverpassQueryBuilder() {
        // Utility class.
    }

    /**
     * Builds the query for attractions within {@code radiusMeters} of {@code center}.
     *
     * <p>The selectors are the tags this app's six categories can honestly be derived from. There
     * is deliberately no selector for a university landmark: OSM has no dependable tag for one,
     * and a guessed clause would put an invented classification in front of the user.
     *
     * @param center       the coordinate to search around — a grid-cell centre, never a raw fix
     * @param radiusMeters search radius; must be positive
     * @param limit        maximum records to return; must be positive
     */
    public static String nearbyAttractions(GeoPoint center, double radiusMeters, int limit) {
        Objects.requireNonNull(center, "center");
        if (!(radiusMeters > 0.0) || Double.isInfinite(radiusMeters)) {
            throw new IllegalArgumentException("radiusMeters must be positive: " + radiusMeters);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }

        // Rounded to whole metres and six decimal places. Overpass accepts more precision, but
        // sending it would defeat the point of querying a grid-cell centre.
        String around = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)",
                Math.round(radiusMeters), center.getLatitude(), center.getLongitude());

        StringBuilder query = new StringBuilder(512);
        query.append("[out:json][timeout:").append(SERVER_TIMEOUT_SECONDS).append("];\n");
        query.append("(\n");
        appendClause(query, "\"tourism\"~\"^(museum|gallery|artwork|attraction|viewpoint)$\"", around);
        appendClause(query, "\"historic\"", around);
        appendClause(query, "\"leisure\"~\"^(park|garden)$\"", around);
        appendClause(query, "\"amenity\"~\"^(theatre|arts_centre)$\"", around);
        query.append(");\n");
        query.append("out center ").append(limit).append(";");
        return query.toString();
    }

    private static void appendClause(StringBuilder query, String selector, String around) {
        query.append("  nwr[").append(selector).append(']').append(around).append(";\n");
    }
}
