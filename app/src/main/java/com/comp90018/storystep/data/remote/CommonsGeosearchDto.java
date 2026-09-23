package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/** The slice of a Commons geosearch response this app reads: each file's size, type and URL. */
public class CommonsGeosearchDto {

    @SerializedName("query")
    public Query query;

    public static class Query {
        @SerializedName("pages")
        public Map<String, Page> pages;
    }

    public static class Page {
        @SerializedName("title")
        public String title;

        @SerializedName("imageinfo")
        public List<ImageInfo> imageinfo;

        /** Where the file was taken; present on the area query only. */
        @SerializedName("coordinates")
        public List<Coordinate> coordinates;
    }

    public static class Coordinate {
        @SerializedName("lat")
        public double lat;

        @SerializedName("lon")
        public double lon;
    }

    public static class ImageInfo {
        /** The file scaled to the requested width; what the app loads. */
        @SerializedName("thumburl")
        public String thumburl;

        @SerializedName("url")
        public String url;

        @SerializedName("width")
        public int width;

        @SerializedName("height")
        public int height;

        @SerializedName("mime")
        public String mime;
    }
}
