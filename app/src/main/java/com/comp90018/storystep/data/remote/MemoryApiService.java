package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** Reserved remote contract; memory saving remains local-only in the first frontend stage. */
public interface MemoryApiService {

    @POST("memories")
    Call<MemoryDto> createMemory(@Body MemoryDto memory);
}
