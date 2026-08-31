package com.comp90018.storystep.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/** Retrofit boundary only; the prototype never issues these calls. */
public interface JourneyApiService {

    @GET("journeys")
    Call<List<JourneyDto>> getJourneys();

    @GET("journeys/{id}")
    Call<JourneyDto> getJourney(@Path("id") String journeyId);
}
