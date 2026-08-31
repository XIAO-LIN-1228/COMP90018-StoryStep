package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** Wire model reserved for the future journey-history endpoint. */
public class JourneyDto {
    @SerializedName("id") @Nullable public String id;
    @SerializedName("title") @Nullable public String title;
    @SerializedName("started_at") @Nullable public String startedAt;
    @SerializedName("duration_minutes") @Nullable public Integer durationMinutes;
    @SerializedName("distance_km") @Nullable public Double distanceKilometers;
    @SerializedName("story_count") @Nullable public Integer storyCount;
}
