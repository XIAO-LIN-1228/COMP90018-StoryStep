package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** Wire model reserved for optional notes and server-side synchronisation. */
public class MemoryDto {
    @SerializedName("id") @Nullable public String id;
    @SerializedName("journey_id") @Nullable public String journeyId;
    @SerializedName("attraction_id") @Nullable public String attractionId;
    @SerializedName("note") @Nullable public String note;
    @SerializedName("created_at") @Nullable public String createdAt;
}
