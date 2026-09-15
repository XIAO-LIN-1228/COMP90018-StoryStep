package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Wire model for an Overpass API response.
 *
 * <p>Fields are nullable on purpose: a response may omit or mistype anything, and
 * {@link RemoteAttractionMapper} is responsible for rejecting incomplete records rather than
 * letting them reach the domain layer.
 *
 * <p>A response with {@code elements} present but empty is a valid answer meaning "nothing matched
 * here", and is not the same as a response with no {@code elements} field at all, which is a
 * malformed payload. The mapper reports which of the two it saw.
 */
public class OverpassResponseDto {

    @SerializedName("version")
    @Nullable
    public Double version;

    @SerializedName("generator")
    @Nullable
    public String generator;

    @SerializedName("elements")
    @Nullable
    public List<OverpassElementDto> elements;
}
