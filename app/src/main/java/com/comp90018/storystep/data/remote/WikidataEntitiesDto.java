package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/** The slice of a {@code wbgetentities} response this app reads: each entity's P18 claims. */
public class WikidataEntitiesDto {

    @SerializedName("entities")
    public Map<String, Entity> entities;

    public static class Entity {
        @SerializedName("claims")
        public Map<String, List<Claim>> claims;
    }

    public static class Claim {
        @SerializedName("mainsnak")
        public Snak mainsnak;
    }

    public static class Snak {
        @SerializedName("datavalue")
        public DataValue datavalue;
    }

    public static class DataValue {
        /**
         * For P18 this is the Commons file name, without the {@code File:} prefix. Kept as a raw
         * element because other properties in the same response carry objects here (an item
         * reference, a quantity, a time), and declaring it as a string would make Gson reject the
         * whole entity on the first one it met.
         */
        @SerializedName("value")
        public com.google.gson.JsonElement value;
    }
}
