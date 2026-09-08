package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

/**
 * Retrofit contract for the OpenStreetMap Overpass API.
 *
 * <p>Overpass takes a query in its own language rather than a set of parameters, so there is one
 * method and {@link OverpassQueryBuilder} composes what it is asked. The base URL is fixed by the
 * provider; there is no credential, no account and nothing to keep out of Git — that is why this
 * provider was chosen. See {@code docs/decisions/D-02-attraction-provider.md}.
 *
 * <p>The call is a form-encoded POST rather than a GET, and that is a privacy decision, not a
 * stylistic one. The query embeds the coordinates of the area the user is in. As a GET those
 * coordinates would sit in the request line, which is what {@code QuerylessLoggingInterceptor}
 * exists to keep out of logcat; as a POST body they are never in a URL to begin with, so the same
 * property holds even if someone later reaches for a conventional HTTP logger. Overpass documents
 * both forms.
 */
public interface OverpassApiService {

    /** The public Overpass instance. Trailing slash is required by Retrofit. */
    String BASE_URL = "https://overpass-api.de/";

    /**
     * Runs one Overpass QL query.
     *
     * <p>Overpass answers {@code 200} with an HTML error page when it is overloaded rather than
     * using a status code, so a successful response is not on its own evidence of usable data;
     * {@link OverpassAttractionSource} treats an unparseable body as a service failure.
     *
     * @param overpassQl the complete query, including its {@code [out:json]} settings block
     */
    @FormUrlEncoded
    @POST("api/interpreter")
    Call<OverpassResponseDto> query(@Field("data") String overpassQl);
}
