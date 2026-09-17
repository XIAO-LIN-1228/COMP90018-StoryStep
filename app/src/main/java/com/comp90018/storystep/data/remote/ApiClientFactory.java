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
 * than one service and their roots are declared next to their contracts:
 * {@link WeatherApiService#BASE_URL} and {@link OverpassApiService#BASE_URL}. Neither provider
 * needs a credential, which is why they were chosen. Any service that ever does must take it from
 * {@code local.properties} through {@code BuildConfig}, never from a literal in this file.
 */
public final class ApiClientFactory {

    /**
     * Default read and connect timeout.
     *
     * <p>Fifteen seconds suits a provider that answers from a precomputed forecast grid. It does
     * not suit every service this app talks to, which is why the timeout is per-client rather than
     * a constant applied to all of them; see {@link #createOverpassService(boolean)}.
     */
    private static final long DEFAULT_TIMEOUT_SECONDS = 15L;

    /**
     * Overpass runs the query when it is asked, against a live database, and a busy instance takes
     * appreciably longer than a lookup. Its own {@code [timeout:25]} setting has to elapse first
     * so the server can report the overload itself; abandoning the call sooner would turn every
     * one of those into a client-side timeout and lose the distinction.
     */
    private static final long OVERPASS_TIMEOUT_SECONDS = 30L;

    /**
     * Identifies this client to Overpass, whose usage policy asks for a contactable application
     * name rather than an anonymous default. It is a constant: no user, device or install
     * identifier goes into it.
     */
    private static final String USER_AGENT = "StoryStep/1.0 (COMP90018 student project)";

    private ApiClientFactory() {
        // Utility class.
    }

    /**
     * @param baseUrl the service root; must end with {@code /} as Retrofit requires
     * @param debug   when true, each call logs its method, host, path and status — never its query
     */
    public static Retrofit createRetrofit(String baseUrl, boolean debug) {
        return createRetrofit(baseUrl, debug, DEFAULT_TIMEOUT_SECONDS);
    }

    public static Retrofit createRetrofit(String baseUrl, boolean debug, long timeoutSeconds) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("baseUrl must be provided by the caller");
        }
        Gson gson = new GsonBuilder().create();
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(createHttpClient(debug, timeoutSeconds))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    /** The weather client. Its root is fixed by the provider, so callers do not choose one. */
    public static WeatherApiService createWeatherService(boolean debug) {
        return createRetrofit(WeatherApiService.BASE_URL, debug).create(WeatherApiService.class);
    }

    /** The attraction client. Its root is fixed by the provider, so callers do not choose one. */
    public static OverpassApiService createOverpassService(boolean debug) {
        return createRetrofit(OverpassApiService.BASE_URL, debug, OVERPASS_TIMEOUT_SECONDS)
                .create(OverpassApiService.class);
    }

    public static WikidataApiService createWikidataService(boolean debug) {
        return createRetrofit(WikidataApiService.BASE_URL, debug).create(WikidataApiService.class);
    }

    public static WikipediaApiService createWikipediaService(boolean debug) {
        return createRetrofit(WikipediaApiService.BASE_URL, debug).create(WikipediaApiService.class);
    }

    private static OkHttpClient createHttpClient(boolean debug, long timeoutSeconds) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .addInterceptor(new UserAgentInterceptor());
        if (debug) {
            builder.addInterceptor(new QuerylessLoggingInterceptor());
        }
        return builder.build();
    }

    /**
     * Names this application on every request.
     *
     * <p>Overpass's usage policy asks clients to identify themselves so that a misbehaving one can
     * be told rather than simply blocked. The value is a compile-time constant with nothing
     * user-specific in it, so this adds an application name to the request and no more.
     */
    private static final class UserAgentInterceptor implements Interceptor {

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            return chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build());
        }
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
