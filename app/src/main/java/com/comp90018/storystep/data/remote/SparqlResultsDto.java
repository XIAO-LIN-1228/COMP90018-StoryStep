package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/** SPARQL JSON results: rows of variable name to bound value. */
public class SparqlResultsDto {

    @SerializedName("results")
    public Results results;

    public static class Results {
        @SerializedName("bindings")
        public List<Map<String, Binding>> bindings;
    }

    public static class Binding {
        @SerializedName("value")
        public String value;
    }
}
