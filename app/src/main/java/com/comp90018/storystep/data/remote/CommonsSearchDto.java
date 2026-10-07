package com.comp90018.storystep.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** The slice of a Commons text search this app reads: the matching file titles. */
public class CommonsSearchDto {

    @SerializedName("query")
    public Query query;

    public static class Query {
        @SerializedName("search")
        public List<Result> search;
    }

    public static class Result {
        /** "File:Prince Alfred Hotel, Carlton.jpg" */
        @SerializedName("title")
        public String title;
    }
}
