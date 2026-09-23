package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** A walking route through the given points, in order. */
public class ValhallaRouteRequest {

    @SerializedName("locations")
    public List<Location> locations = new ArrayList<>();

    @SerializedName("costing")
    public String costing = "pedestrian";

    @SerializedName("directions_options")
    public DirectionsOptions directionsOptions = new DirectionsOptions();

    public static ValhallaRouteRequest through(List<GeoPoint> points) {
        ValhallaRouteRequest request = new ValhallaRouteRequest();
        for (GeoPoint point : points) {
            request.locations.add(new Location(point));
        }
        return request;
    }

    public static class Location {
        @SerializedName("lat")
        public double lat;

        @SerializedName("lon")
        public double lon;

        /** Each stop is a break: the walker stops there rather than passing through. */
        @SerializedName("type")
        public String type = "break";

        Location(GeoPoint point) {
            this.lat = point.getLatitude();
            this.lon = point.getLongitude();
        }
    }

    public static class DirectionsOptions {
        @SerializedName("units")
        public String units = "kilometers";
    }
}
