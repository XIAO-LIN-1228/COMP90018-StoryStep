package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import java.util.List;

/**
 * One blocking fetch of the attractions around a point.
 *
 * <p>Deliberately narrower than {@link OverpassApiService}: no Retrofit type, no {@code Call}, no
 * DTO, no Overpass QL. That is what lets the caching repository be tested against a fake that
 * simply returns a list or throws — the whole cache-first, coalescing, stale-fallback policy is
 * exercised in JVM tests with no HTTP stack involved, and {@link OverpassAttractionSource} is then
 * the only class that has to be tested against a real socket.
 *
 * <p>Implementations block the calling thread and are invoked on an I/O executor. Every failure
 * arrives as an {@link AttractionUnavailableException} carrying a reason the UI can act on;
 * nothing else escapes.
 *
 * <p>An empty list is a valid answer meaning the provider knows of no attractions here. It is not
 * a failure, and the caller is expected to cache it as the area's answer.
 */
public interface AttractionSource {

    /**
     * @param center       the grid-cell centre to search around, never the user's exact position
     * @param radiusMeters the search radius, already widened to cover the whole cell
     * @param limit        the maximum number of records to ask for
     * @return validated, deduplicated attractions; possibly empty
     * @throws AttractionUnavailableException with a typed reason when no usable answer resulted
     */
    List<Attraction> fetchNearby(GeoPoint center, double radiusMeters, int limit)
            throws AttractionUnavailableException;
}
