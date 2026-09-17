package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Wikidata's entity API, used for one thing: the photograph an OpenStreetMap place points at
 * through its {@code wikidata} tag. Property P18 ("image") names a Wikimedia Commons file.
 *
 * <p>One request carries up to fifty ids, so enriching an area's worth of places costs a single
 * round trip. Only the claims are requested; labels and descriptions would be wasted bytes.
 */
public interface WikidataApiService {

    String BASE_URL = "https://www.wikidata.org/";

    int MAX_IDS_PER_REQUEST = 50;

    @GET("w/api.php?action=wbgetentities&props=claims&format=json")
    Call<WikidataEntitiesDto> entities(@Query("ids") String pipeSeparatedIds);
}
