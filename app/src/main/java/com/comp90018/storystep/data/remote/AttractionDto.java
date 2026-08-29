package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Wire model for one attraction returned by the attraction service.
 *
 * <p>Fields are nullable on purpose: a response may omit or mistype anything, and
 * {@link RemoteAttractionMapper} is responsible for rejecting incomplete records rather than
 * letting them reach the domain layer.
 */
public class AttractionDto {

    @SerializedName("id")
    @Nullable
    public String id;

    @SerializedName("name")
    @Nullable
    public String name;

    @SerializedName("category")
    @Nullable
    public String category;

    @SerializedName("description")
    @Nullable
    public String description;

    @SerializedName("indoor")
    @Nullable
    public Boolean indoor;

    @SerializedName("latitude")
    @Nullable
    public Double latitude;

    @SerializedName("longitude")
    @Nullable
    public Double longitude;
}
