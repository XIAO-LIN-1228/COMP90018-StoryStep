package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.weather.WeatherResult;

/**
 * The weather contract. Separate from {@link AttractionRepository} because it has its own service,
 * its own cache and its own freshness rule, and because a weather failure must never be able to
 * take the attraction list down with it.
 *
 * <p>Implementations follow the {@link RepositoryCallback} threading contract: work off the main
 * thread, results back on it.
 *
 * <p>Two rules bind every implementation:
 *
 * <ul>
 *   <li>A failed refresh with usable cached content is a <b>success</b> carrying a stale result,
 *       not an error. Erasing readable content to report a network problem is the failure mode
 *       this contract exists to prevent.</li>
 *   <li>An error is always a {@link WeatherUnavailableException} with a reason the UI can act on.
 *       Callers must be able to tell a timeout from a malformed payload without parsing a
 *       message.</li>
 * </ul>
 *
 * <p>The position given to {@link #loadWeather} is quantised to a
 * {@code WeatherAreaBucket} before any request is made, so consecutive location fixes do not each
 * produce a request and the user's precise coordinates are never what is sent.
 */
public interface WeatherRepository {

    /**
     * Produces the weather for the area containing {@code position}.
     *
     * <p>Cache first: a fresh stored observation answers without touching the network. Requests
     * for the same area are coalesced, so two screens asking at once produce one request.
     *
     * @param callback receives a {@link WeatherResult}, or a {@link WeatherUnavailableException}
     *                 when there is nothing at all to show
     */
    void loadWeather(GeoPoint position, RepositoryCallback<WeatherResult> callback);

    /**
     * Removes every cached observation.
     *
     * <p>Weather is not user-owned history, so this is a cache operation rather than a privacy
     * one; it exists so a test or a data-clearing action can start from a known empty state.
     */
    void clearCache(RepositoryCallback<Integer> callback);
}
