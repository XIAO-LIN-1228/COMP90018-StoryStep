package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Wikimedia Commons' geosearch: the photographs uploaded with a location within a few tens of
 * metres of a point. The last resort for a place's picture, after Wikidata and Wikipedia: most
 * named landmarks, memorials and sculptures in a city have been photographed and geotagged by
 * someone, even when no article links to the file.
 */
public interface CommonsApiService {

    String BASE_URL = "https://commons.wikimedia.org/";

    /**
     * The five hundred geotagged files nearest {@code lat|lon}, each with its coordinate, size
     * and type - one request that covers most of an area's places at once. No scaled URL here:
     * the API caps those at fifty per request, so the app builds them from the file title.
     */
    @GET("w/api.php?action=query&generator=geosearch&ggsnamespace=6&ggslimit=500"
            + "&prop=coordinates|imageinfo&colimit=max&iiprop=size|mime&format=json")
    Call<CommonsGeosearchDto> area(@Query("ggscoord") String latPipeLon,
                                   @Query("ggsradius") int radiusMetres);

    /** Files (namespace 6) near {@code lat|lon}, with size, type and a scaled URL for each. */
    @GET("w/api.php?action=query&generator=geosearch&ggsnamespace=6&ggslimit=20"
            + "&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1280&format=json")
    Call<CommonsGeosearchDto> nearby(@Query("ggscoord") String latPipeLon,
                                     @Query("ggsradius") int radiusMetres);
}
