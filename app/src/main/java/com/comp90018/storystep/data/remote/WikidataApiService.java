package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Wikidata's entity API, used for one thing: the photograph an OpenStreetMap place points at
 * through its {@code wikidata} tag, and how widely known it is. Property P18 ("image") names a
 * Wikimedia Commons file; the number of sitelinks - Wikipedia editions with an article on the
 * place - is the best free proxy for how prominent it is.
 *
 * <p>One request carries up to fifty ids, so enriching an area's worth of places costs a single
 * round trip. Claims, sitelinks and the short descriptions are requested; the English description is the
 * last-resort line of introduction, and the {@code enwiki} sitelink names the article whose
 * summary is the first choice.
 */
public interface WikidataApiService {

    String BASE_URL = "https://www.wikidata.org/";

    int MAX_IDS_PER_REQUEST = 50;

    @GET("w/api.php?action=wbgetentities&props=claims|sitelinks|descriptions&format=json")
    Call<WikidataEntitiesDto> entities(@Query("ids") String pipeSeparatedIds);
}
