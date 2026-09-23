package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Valhalla's routing service, run publicly and without a key by OpenStreetMap Germany.
 *
 * <p>Asked for one thing: the walking path between the stops of a plan, so the line on the map
 * follows streets and paths rather than cutting through buildings, and the distance and time are
 * a walk's rather than a crow's.
 *
 * <p>The response also carries turn-by-turn manoeuvres. StoryStep does not read them and must
 * not start to: guidance here is a direction and a distance, and turn-by-turn navigation is
 * outside this app's scope by design.
 */
public interface ValhallaApiService {

    String BASE_URL = "https://valhalla1.openstreetmap.de/";

    @POST("route")
    Call<ValhallaRouteDto> route(@Body ValhallaRouteRequest request);
}
