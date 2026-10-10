package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import retrofit2.Retrofit;

/**
 * Both forecast clients against a local HTTP server serving the recorded answers: the declared
 * query parameters, the bindings and the failure translation.
 */
public class RetrofitForecastSourcesTest {

    private static final GeoPoint CENTRE = new GeoPoint(-37.825, 144.975);

    private MockWebServer server;
    private RetrofitHourlyForecastSource hourly;
    private RetrofitEnsembleSource ensemble;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = ApiClientFactory.createRetrofit(server.url("/").toString(), false);
        hourly = new RetrofitHourlyForecastSource(retrofit.create(ForecastApiService.class));
        ensemble = new RetrofitEnsembleSource(retrofit.create(EnsembleApiService.class));
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private void serve(int code, String body) {
        server.enqueue(new MockResponse().setResponseCode(code).setBody(body));
    }

    @Test
    public void theHourlyRequestAsksForUtcSecondsAndTheTwoFields() throws Exception {
        serve(200, ForecastFixtures.text("hourly_melbourne.json"));

        HourlyForecast forecast = hourly.fetch(CENTRE, 42L);

        assertEquals(26, forecast.getHours().size());
        assertEquals(42L, forecast.getFetchedAtMillis());
        String path = server.takeRequest().getPath();
        assertTrue(path, path.startsWith("/v1/forecast?"));
        assertTrue(path, path.contains("latitude=-37.825"));
        assertTrue(path, path.contains("longitude=144.975"));
        assertTrue(path, path.contains("hourly=precipitation_probability%2Cweather_code")
                || path.contains("hourly=precipitation_probability,weather_code"));
        assertTrue(path, path.contains("daily=sunrise%2Csunset")
                || path.contains("daily=sunrise,sunset"));
        assertTrue(path, path.contains("forecast_days=3"));
        assertTrue(path, path.contains("forecast_hours=26"));
        assertTrue(path, path.contains("timezone=auto"));
        assertTrue(path, path.contains("timeformat=unixtime"));
    }

    @Test
    public void theEnsembleRequestAsksForTheThreeModels() throws Exception {
        serve(200, ForecastFixtures.text("ensemble_melbourne.json"));

        EnsemblePrecipitation result = ensemble.fetch(CENTRE, 42L);

        assertEquals(3, result.getModels().size());
        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertTrue(path, path.startsWith("/v1/ensemble?"));
        assertTrue(path, path.contains("forecast_hours=34"));
        assertTrue(path, path.contains("icon_seamless")
                && path.contains("ecmwf_ifs025") && path.contains("gfs025"));
        assertTrue(path, path.contains("daily=sunrise"));
        assertTrue(path, path.contains("timezone=auto"));
    }

    @Test
    public void serverErrorsAreHttpFailures() {
        serve(500, "{}");
        assertReason(ForecastUnavailableException.Reason.HTTP_ERROR, true);
        serve(503, "{}");
        assertReason(ForecastUnavailableException.Reason.HTTP_ERROR, false);
    }

    @Test
    public void aRejectedRequestKeepsItsStatus() {
        serve(400, "{\"error\":true,\"reason\":\"Invalid value\"}");
        try {
            ensemble.fetch(CENTRE, 0L);
            fail("expected a failure");
        } catch (ForecastUnavailableException failure) {
            assertEquals(ForecastUnavailableException.Reason.HTTP_ERROR, failure.getReason());
            assertEquals(400, failure.getHttpStatus());
        }
    }

    @Test
    public void throttlingIsReportedWithTheServersDelay() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "3")
                .setBody("{}"));
        try {
            ensemble.fetch(CENTRE, 0L);
            fail("expected a failure");
        } catch (ForecastUnavailableException failure) {
            assertEquals(ForecastUnavailableException.Reason.RATE_LIMITED, failure.getReason());
            assertEquals(3_000L, failure.getRetryAfterMillis());
        }
    }

    @Test
    public void unusableBodiesAreMalformed() {
        serve(200, "{\"latitude\":-37.8}");
        assertReason(ForecastUnavailableException.Reason.MALFORMED, true);
        serve(200, "{\"latitude\":-37.8}");
        assertReason(ForecastUnavailableException.Reason.MALFORMED, false);
        serve(200, "not json at all");
        assertReason(ForecastUnavailableException.Reason.MALFORMED, true);
        serve(200, "not json at all");
        assertReason(ForecastUnavailableException.Reason.MALFORMED, false);
        serve(200, "[1,2,3]");
        assertReason(ForecastUnavailableException.Reason.MALFORMED, false);
    }

    @Test
    public void aDroppedConnectionIsNoNetwork() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        assertReason(ForecastUnavailableException.Reason.NO_NETWORK, true);
    }

    @Test
    public void anUnreachableHostIsNoNetwork() throws Exception {
        server.shutdown();
        assertReason(ForecastUnavailableException.Reason.NO_NETWORK, false);
    }

    private void assertReason(ForecastUnavailableException.Reason expected, boolean useHourly) {
        try {
            if (useHourly) {
                hourly.fetch(CENTRE, 0L);
            } else {
                ensemble.fetch(CENTRE, 0L);
            }
            fail("expected a " + expected + " failure");
        } catch (ForecastUnavailableException failure) {
            assertEquals(expected, failure.getReason());
        }
    }
}
