package com.comp90018.storystep.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/** Reserved remote contract for saved attractions; not bound in the prototype container. */
public interface UserLibraryApiService {

    @GET("me/saved-attractions")
    Call<List<AttractionDto>> getSavedAttractions();

    @PUT("me/saved-attractions/{attractionId}")
    Call<Void> saveAttraction(@Path("attractionId") String attractionId);

    @DELETE("me/saved-attractions/{attractionId}")
    Call<Void> removeSavedAttraction(@Path("attractionId") String attractionId);
}
