package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** The slice of a Places text search this app reads: the matched name and its photographs. */
public class PlacesSearchDto {

    @SerializedName("places")
    public List<Place> places;

    public static class Place {
        /** "places/ChIJ..." */
        @SerializedName("id")
        public String id;

        @SerializedName("displayName")
        public DisplayName displayName;

        @SerializedName("photos")
        public List<Photo> photos;
    }

    public static class DisplayName {
        @SerializedName("text")
        public String text;
    }

    public static class Photo {
        /** "places/ChIJ.../photos/AeJ..." - the handle a photo URL is built from. */
        @SerializedName("name")
        public String name;

        /** Who took it. Google's terms require this to be shown wherever the photo is. */
        @SerializedName("authorAttributions")
        public List<AuthorAttribution> authorAttributions;
    }

    public static class AuthorAttribution {
        @SerializedName("displayName")
        public String displayName;
    }
}
