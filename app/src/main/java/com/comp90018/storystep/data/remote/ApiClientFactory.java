package com.comp90018.storystep.data.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds the Retrofit stack for a caller-supplied base URL.
 *
 * <p>The base URL and any API key are deliberately not defined here: service selection is a
 * later-stage decision, and credentials belong in {@code local.properties}, never in Git. Nothing
 * calls this factory yet, so the application performs no network traffic.
 */
public final class ApiClientFactory {

    private static final long TIMEOUT_SECONDS = 15L;

    private ApiClientFactory() {
        // Utility class.
    }

    /**
     * @param baseUrl the service root; must end with {@code /} as Retrofit requires
     * @param debug   when true, request and response lines are logged
     */
    public static Retrofit createRetrofit(String baseUrl, boolean debug) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("baseUrl must be provided by the caller");
        }
        Gson gson = new GsonBuilder().create();
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(createHttpClient(debug))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    public static AttractionApiService createAttractionService(String baseUrl, boolean debug) {
        return createRetrofit(baseUrl, debug).create(AttractionApiService.class);
    }

    private static OkHttpClient createHttpClient(boolean debug) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // BASIC never records headers, so no key can leak into logcat.
        logging.setLevel(debug ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);
        return new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
    }
}
