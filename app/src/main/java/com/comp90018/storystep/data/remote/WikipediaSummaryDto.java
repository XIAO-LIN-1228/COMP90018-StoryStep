package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

/** The slice of a page summary this app reads: the plain-text opening paragraph. */
public class WikipediaSummaryDto {

    @SerializedName("extract")
    public String extract;
}
