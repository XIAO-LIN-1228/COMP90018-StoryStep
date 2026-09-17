package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * The English Wikipedia page-summary endpoint: one article's opening paragraph, as plain text.
 * It is what puts a sentence or two of introduction under a place that OpenStreetMap only
 * names. The title is the {@code enwiki} sitelink Wikidata returned, sent as-is because the
 * endpoint expects the article's own spelling.
 */
public interface WikipediaApiService {

    String BASE_URL = "https://en.wikipedia.org/";

    @GET("api/rest_v1/page/summary/{title}")
    Call<WikipediaSummaryDto> summary(@Path(value = "title", encoded = true) String title);
}
