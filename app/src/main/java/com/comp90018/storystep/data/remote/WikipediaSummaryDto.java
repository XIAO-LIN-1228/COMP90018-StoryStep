package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

/** The slice of a page summary this app reads: the plain-text opening paragraph. */
public class WikipediaSummaryDto {

    @SerializedName("extract")
    public String extract;

    /** The article's lead image at full size, when it has one. */
    @SerializedName("originalimage")
    public Image originalimage;

    public static class Image {
        @SerializedName("source")
        public String source;

        @SerializedName("width")
        public int width;

        @SerializedName("height")
        public int height;
    }
}
