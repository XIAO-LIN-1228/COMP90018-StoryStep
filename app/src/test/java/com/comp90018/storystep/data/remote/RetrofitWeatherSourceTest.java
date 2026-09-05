package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import retrofit2.Retrofit;

/**
 * The Retrofit stack against a real socket.
 *
 * <p>Everything above this class is tested with a fake source, so these cases cover the one thing
 * a fake cannot: that the declared query parameters, the Gson binding and the failure translation
 * all behave against an actual HTTP server. The server is local, so the suite does not depend on
 * Open-Meteo being reachable.
 */
public class RetrofitWeatherSourceTest {

    private static final GeoPoint CENTRE = new GeoPoint(-37.825, 144.975);
    private static final String AREA_KEY = "-757:2899";
    private static final long FETCHED_AT = 1_700_000_000_000L;

    private static final String VALID_BODY = "{"
            + "\"latitude\":-37.82,\"longitude\":144.97,\"utc_offset_seconds\":36000,"
            + "\"current_units\":{\"temperature_2m\":\"°C\"},"
            + "\"current\":{\"time\":\"2026-09-05T14:00\",\"interval\":900,"
            + "\"temperature_2m\":14.3,\"weather_code\":61}}";

    private MockWebServer server;
    private RetrofitWeatherSource source;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = ApiClientFactory.createRetrofit(
                server.url("/").toString(), false);
        source = new RetrofitWeatherSource(retrofit.create(WeatherApiService.class));
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void aValidResponseBecomesASnapshot() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(VALID_BODY));

        WeatherSnapshot snapshot = source.fetch(CENTRE, AREA_KEY, FETCHED_AT);

        assertNotNull(snapshot);
        assertEquals(WeatherCondition.RAIN, snapshot.getCondition());
        assertEquals(14.3, snapshot.getTemperatureCelsius(), 0.0001);
        assertEquals(AREA_KEY, snapshot.getAreaKey());
        assertEquals(FETCHED_AT, snapshot.getFetchedAtMillis());
    }

    @Test
    public void theRequestAsksForTheFieldsAndUnitTheMapperExpects() throws Exception {
        // The mapper rejects a payload whose unit is not Celsius, so the request has to ask for
        // Celsius. These two halves have to agree, and only a real request proves they do.
        server.enqueue(new MockResponse().setResponseCode(200).setBody(VALID_BODY));

        source.fetch(CENTRE, AREA_KEY, FETCHED_AT);

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        String path = request.getPath();
        assertTrue(path, path.startsWith("/v1/forecast?"));
        assertTrue(path, path.contains("temperature_unit=celsius"));
        assertTrue(path, path.contains("timezone=auto"));
        assertTrue(path, path.contains("temperature_2m"));
        assertTrue(path, path.contains("weather_code"));
        assertTrue(path, path.contains("latitude=-37.825"));
        assertTrue(path, path.contains("longitude=144.975"));
    }

    @Test
    public void aServerErrorIsReportedAsAnHttpFailure() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        assertReason(WeatherUnavailableException.Reason.HTTP_ERROR);
    }

    @Test
    public void aNotFoundIsReportedAsAnHttpFailure() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));

        assertReason(WeatherUnavailableException.Reason.HTTP_ERROR);
    }

    @Test
    public void throttlingIsReportedSeparatelySoItIsNotRetriedLikeAnOutage() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));

        assertReason(WeatherUnavailableException.Reason.RATE_LIMITED);
    }

    @Test
    public void aSuccessfulResponseMissingTheObservationIsMalformedNotSuccessful() {
        // A 200 whose body lacks what the app needs is not a success: reporting it as one would
        // put a fabricated temperature on screen.
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"latitude\":-37.82,\"longitude\":144.97}"));

        assertReason(WeatherUnavailableException.Reason.MALFORMED);
    }

    @Test
    public void anUnparseableBodyIsMalformed() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not json at all"));

        assertReason(WeatherUnavailableException.Reason.MALFORMED);
    }

    @Test
    public void anEmptyBodyIsMalformed() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));

        assertReason(WeatherUnavailableException.Reason.MALFORMED);
    }

    @Test
    public void aDroppedConnectionIsReportedAsNoNetwork() {
        server.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        assertReason(WeatherUnavailableException.Reason.NO_NETWORK);
    }

    @Test
    public void anUnreachableHostIsReportedAsNoNetwork() throws Exception {
        server.shutdown();

        assertReason(WeatherUnavailableException.Reason.NO_NETWORK);
    }

    private void assertReason(WeatherUnavailableException.Reason expected) {
        try {
            source.fetch(CENTRE, AREA_KEY, FETCHED_AT);
            fail("expected a " + expected + " failure");
        } catch (WeatherUnavailableException unavailable) {
            assertEquals(expected, unavailable.getReason());
        }
    }
}
