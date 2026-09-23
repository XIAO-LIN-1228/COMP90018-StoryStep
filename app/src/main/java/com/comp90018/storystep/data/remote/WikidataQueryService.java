package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * The Wikidata Query Service: one SPARQL request for every item with a coordinate and a
 * photograph inside a circle. The second area-wide pass for pictures, reaching the buildings
 * and monuments whose OpenStreetMap node never got a {@code wikidata} tag.
 */
public interface WikidataQueryService {

    String BASE_URL = "https://query.wikidata.org/";

    @GET("sparql?format=json")
    Call<SparqlResultsDto> query(@Query("query") String sparql);

    /** Items with a photograph within {@code radiusKm} of the point, labelled in English. */
    static String photographedItemsAround(double latitude, double longitude, double radiusKm) {
        return String.format(java.util.Locale.ROOT,
                "SELECT ?item ?itemLabel ?image ?coord WHERE { "
                        + "SERVICE wikibase:around { ?item wdt:P625 ?coord . "
                        + "bd:serviceParam wikibase:center \"Point(%.6f %.6f)\"^^geo:wktLiteral . "
                        + "bd:serviceParam wikibase:radius \"%.2f\" . } "
                        + "?item wdt:P18 ?image . "
                        + "SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\" . } "
                        + "} LIMIT 2000",
                longitude, latitude, radiusKm);
    }
}
