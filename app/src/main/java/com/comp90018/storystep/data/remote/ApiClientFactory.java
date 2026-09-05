package com.comp90018.storystep.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds the Retrofit stack for a caller-supplied base URL.
 *
 * <p>The base URL is supplied by the caller rather than fixed here, because the app talks to more
 * than one service and their roots are declared next to their contracts —
 * {@link WeatherApiService#BASE_URL} is the only one wired so far. Any service that needs a
 * credential must take it from {@code local.properties} through {@code BuildConfig}, never from a
 * literal in this file.
 */
public final class ApiClientFactory {

    private static final long TIMEOUT_SECONDS = 15L;

    private ApiClientFactory() {
        // Utility class.
    }

    /**
     * @param baseUrl the service root; must end with {@code /} as Retrofit requires
     * @param debug   when true, each call logs its method, host, path and status — never its query
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

    /** The weather client. Its root is fixed by the provider, so callers do not choose one. */
    public static WeatherApiService createWeatherService(boolean debug) {
        return createRetrofit(WeatherApiService.BASE_URL, debug).create(WeatherApiService.class);
    }

    private static OkHttpClient createHttpClient(boolean debug) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (debug) {
            builder.addInterceptor(new QuerylessLoggingInterceptor());
        }
        return builder.build();
    }

    /**
     * Logs that a call happened, without logging where it pointed.
     *
     * <p>This replaces OkHttp's logging interceptor at {@code BASIC}, which an earlier version used
     * on the reasoning that it omits headers so no API key could leak. That reasoning was wrong for
     * this app. {@code BASIC} logs the whole request line including the query string, and the
     * weather request carries the coordinates of the area the user is standing in. Redacting
     * headers protects nothing when the sensitive value is a query parameter. The dependency was
     * dropped along with it, so the level cannot be raised back by accident.
     *
     * <p>What is logged is the method, host, path and response status — enough to see that a
     * refresh fired and what it answered, with the coordinates left out. NFR-04.3 is verified by
     * issuing a request for a sentinel coordinate and confirming it appears nowhere in logcat.
     */
    private static final class QuerylessLoggingInterceptor implements Interceptor {

        private static final String TAG = "StoryStepHttp";

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request();
            long startedAt = System.nanoTime();
            Response response = chain.proceed(request);
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            HttpUrl url = request.url();
            Log.d(TAG, request.method() + " " + url.host() + url.encodedPath()
                    + " -> " + response.code() + " (" + millis + "ms)");
            return response;
        }
    }
}
