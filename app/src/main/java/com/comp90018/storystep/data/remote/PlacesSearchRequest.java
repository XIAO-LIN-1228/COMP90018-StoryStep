package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.google.gson.annotations.SerializedName;

/** One place, named, biased to where the walker is looking. */
public class PlacesSearchRequest {

    /** One result: this asks "is there a photograph of this place", not "what is nearby". */
    private static final int MAX_RESULTS = 1;

    @SerializedName("textQuery")
    public String textQuery;

    @SerializedName("maxResultCount")
    public int maxResultCount = MAX_RESULTS;

    @SerializedName("locationBias")
    public LocationBias locationBias;

    public static PlacesSearchRequest near(String name, GeoPoint at, double radiusMeters) {
        PlacesSearchRequest request = new PlacesSearchRequest();
        request.textQuery = name;
        request.locationBias = new LocationBias(at, radiusMeters);
        return request;
    }

    public static class LocationBias {
        @SerializedName("circle")
        public Circle circle;

        LocationBias(GeoPoint at, double radiusMeters) {
            this.circle = new Circle(at, radiusMeters);
        }
    }

    public static class Circle {
        @SerializedName("center")
        public Center center;

        @SerializedName("radius")
        public double radius;

        Circle(GeoPoint at, double radiusMeters) {
            this.center = new Center(at);
            this.radius = radiusMeters;
        }
    }

    public static class Center {
        @SerializedName("latitude")
        public double latitude;

        @SerializedName("longitude")
        public double longitude;

        Center(GeoPoint at) {
            this.latitude = at.getLatitude();
            this.longitude = at.getLongitude();
        }
    }
}
