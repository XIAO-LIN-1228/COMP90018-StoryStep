package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * The slice of a Valhalla response this app reads: each leg's shape, and the totals.
 *
 * <p>Manoeuvres are deliberately absent from this class. The service returns them; reading them
 * would be the first step towards turn-by-turn navigation, which this app does not do.
 */
public class ValhallaRouteDto {

    @SerializedName("trip")
    public Trip trip;

    public static class Trip {
        @SerializedName("legs")
        public List<Leg> legs;

        @SerializedName("summary")
        public Summary summary;

        @SerializedName("status")
        public int status;
    }

    public static class Leg {
        /** The leg's path, encoded as a polyline at six decimal places. */
        @SerializedName("shape")
        public String shape;
    }

    public static class Summary {
        /** Kilometres, because the request asks for them. */
        @SerializedName("length")
        public double length;

        /** Seconds. */
        @SerializedName("time")
        public double time;
    }
}
