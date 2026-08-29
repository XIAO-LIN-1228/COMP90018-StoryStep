package com.comp90018.storystep.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit contract for the attraction service.
 *
 * <p>This declares the boundary only. No call is issued in Stage 1, and no production base URL
 * or credential is committed; see {@link ApiClientFactory}.
 */
public interface AttractionApiService {

    /**
     * @param latitude     search centre latitude
     * @param longitude    search centre longitude
     * @param radiusMeters inclusive search radius in metres
     */
    @GET("attractions")
    Call<List<AttractionDto>> getNearbyAttractions(
            @Query("lat") double latitude,
            @Query("lng") double longitude,
            @Query("radius") int radiusMeters);
}
