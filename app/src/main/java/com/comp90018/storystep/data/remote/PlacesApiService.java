package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Google Places, used for one thing: a photograph of a place nobody has published a reusable
 * one of. Cafes, shops and small sculpture are almost absent from Wikimedia, and Google has
 * pictures of nearly all of them.
 *
 * <p>Needs a key, which lives in {@code local.properties} and is never committed. Without one
 * the app never calls this and the places keep their drawn artwork.
 *
 * <p>Two terms of Google's shape this class and must not be quietly dropped:
 * <ul>
 *   <li>The photograph itself is <b>not</b> stored. Only the photo's name is kept, and a URL is
 *       built from it when a screen asks; the bytes are fetched fresh each time.</li>
 *   <li>Whatever a result carries in {@code authorAttributions} is shown with the photograph.</li>
 * </ul>
 */
public interface PlacesApiService {

    String BASE_URL = "https://places.googleapis.com/";

    /**
     * Finds the one place the text names, near the point given, and returns its photographs.
     *
     * <p>A field mask is required by this API and keeps the bill down: asking for the name, the
     * display name and the photographs only.
     */
    @POST("v1/places:searchText")
    Call<PlacesSearchDto> searchText(
            @Header("X-Goog-Api-Key") String apiKey,
            @Header("X-Goog-FieldMask") String fieldMask,
            @Body PlacesSearchRequest request);
}
