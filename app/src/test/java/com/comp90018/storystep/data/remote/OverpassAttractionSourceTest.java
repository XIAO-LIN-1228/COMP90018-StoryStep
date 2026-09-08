package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URLDecoder;
import java.util.List;

import retrofit2.Retrofit;

/**
 * The Overpass stack against a real socket.
 *
 * <p>Everything above this class is tested with a fake source, so these cases cover the one thing
 * a fake cannot: that the request shape, the Gson binding and the failure translation behave
 * against an actual HTTP server. The server is local, so the suite does not depend on the public
 * Overpass instance being reachable — which, given that instance has no SLA, matters.
 *
 * <p>This is the FX-HTTP replay AT-18B asks for: valid, empty, mixed-invalid, all-invalid,
 * malformed, and the HTTP failures.
 */
public class OverpassAttractionSourceTest {

    private static final GeoPoint CENTRE = new GeoPoint(-37.795, 144.965);
    private static final double RADIUS = 3800.0;
    private static final int LIMIT = 60;

    private static final String VALID_BODY = "{"
            + "\"version\":0.6,\"generator\":\"Overpass API 0.7.62\",\"elements\":["
            + "{\"type\":\"node\",\"id\":2412481861,\"lat\":-37.8032,\"lon\":144.9713,"
            + "\"tags\":{\"name\":\"Melbourne Museum\",\"tourism\":\"museum\","
            + "\"building\":\"yes\",\"wikidata\":\"Q1747074\"}},"
            + "{\"type\":\"way\",\"id\":26621235,\"center\":{\"lat\":-37.8046,\"lon\":144.9718},"
            + "\"tags\":{\"name\":\"Royal Exhibition Building\",\"historic\":\"building\"}}"
            + "]}";

    private MockWebServer server;
    private OverpassAttractionSource source;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = ApiClientFactory.createRetrofit(server.url("/").toString(), false);
        source = new OverpassAttractionSource(retrofit.create(OverpassApiService.class));
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    // --- success -------------------------------------------------------------------------------

    @Test
    public void aValidResponseBecomesValidatedDomainRecords() throws Exception {
        server.enqueue(new MockResponse().setBody(VALID_BODY));

        List<Attraction> attractions = source.fetchNearby(CENTRE, RADIUS, LIMIT);

        assertEquals(2, attractions.size());
        assertEquals("osm:node/2412481861", attractions.get(0).getId());
        assertEquals("Melbourne Museum", attractions.get(0).getName());
        assertEquals("wikidata:Q1747074", attractions.get(0).getSourceRef());
        assertEquals("osm:way/26621235", attractions.get(1).getId());
    }

    @Test
    public void theQueryTravelsInThePostBodyRatherThanTheUrl() throws Exception {
        // The privacy property NFR-04.3 turns on: the coordinates are never in a request line that
        // a conventional HTTP logger would print.
        server.enqueue(new MockResponse().setBody(VALID_BODY));

        source.fetchNearby(CENTRE, RADIUS, LIMIT);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertFalse("no coordinate may appear in the path or query",
                request.getPath().contains("144.9"));
        // Form-encoded, so decode before looking for the query this app meant to send.
        String body = URLDecoder.decode(request.getBody().readUtf8(), "UTF-8");
        assertTrue(body.startsWith("data=[out:json]"));
        assertTrue(body.contains("(around:3800,-37.795000,144.965000)"));
        assertTrue(body.endsWith("out center 60;"));
    }

    @Test
    public void theClientNamesItselfAsOverpassPolicyAsks() throws Exception {
        server.enqueue(new MockResponse().setBody(VALID_BODY));

        source.fetchNearby(CENTRE, RADIUS, LIMIT);

        String userAgent = server.takeRequest().getHeader("User-Agent");
        assertNotNull(userAgent);
        assertTrue("expected an application name, got " + userAgent,
                userAgent.startsWith("StoryStep/"));
    }

    @Test
    public void anEmptyElementsArrayIsAnEmptyListRatherThanAFailure() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"version\":0.6,\"elements\":[]}"));

        assertTrue(source.fetchNearby(CENTRE, RADIUS, LIMIT).isEmpty());
    }

    @Test
    public void unusableRecordsAreDroppedWhileTheRestSurvive() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"elements\":["
                + "{\"type\":\"node\",\"id\":1,\"lat\":-37.80,\"lon\":144.97,"
                + "\"tags\":{\"name\":\"Good Place\"}},"
                + "{\"type\":\"node\",\"id\":2,\"lat\":-37.80,\"lon\":144.97,\"tags\":{}},"
                + "{\"type\":\"node\",\"id\":3,\"tags\":{\"name\":\"No Coordinate\"}}"
                + "]}"));

        List<Attraction> attractions = source.fetchNearby(CENTRE, RADIUS, LIMIT);

        assertEquals(1, attractions.size());
        assertEquals("Good Place", attractions.get(0).getName());
    }

    // --- failures ------------------------------------------------------------------------------

    @Test
    public void aPayloadOfNothingUsableIsAServiceDataProblemNotAnEmptyArea() {
        server.enqueue(new MockResponse().setBody("{\"elements\":["
                + "{\"type\":\"node\",\"id\":1,\"lat\":-37.80,\"lon\":144.97,\"tags\":{}}"
                + "]}"));

        assertFailsWith(AttractionUnavailableException.Reason.MALFORMED);
    }

    @Test
    public void anHtmlErrorPageServedWithTwoHundredIsMalformed() {
        // Overpass's characteristic overload response: the status says success, the body is a web
        // page. Reporting this as an empty area would cache "nothing here" for a day.
        server.enqueue(new MockResponse()
                .setBody("<html><body><p>Error: rate_limited</p></body></html>"));

        assertFailsWith(AttractionUnavailableException.Reason.MALFORMED);
    }

    @Test
    public void anEmptyBodyIsMalformedRatherThanOffline() {
        server.enqueue(new MockResponse().setBody(""));

        assertFailsWith(AttractionUnavailableException.Reason.MALFORMED);
    }

    @Test
    public void aBodyOfTheWrongShapeIsMalformed() {
        server.enqueue(new MockResponse().setBody("{\"elements\":\"not an array\"}"));

        assertFailsWith(AttractionUnavailableException.Reason.MALFORMED);
    }

    @Test
    public void tooManyRequestsIsRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertFailsWith(AttractionUnavailableException.Reason.RATE_LIMITED);
    }

    @Test
    public void aGatewayTimeoutIsRateLimitedBecauseThatIsWhatOverpassMeansByIt() {
        // 504 is the public instance's usual way of saying it is too busy. Telling the user
        // something is broken, rather than to wait, would be the wrong advice.
        server.enqueue(new MockResponse().setResponseCode(504));

        assertFailsWith(AttractionUnavailableException.Reason.RATE_LIMITED);
    }

    @Test
    public void aServerErrorIsAnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertFailsWith(AttractionUnavailableException.Reason.HTTP_ERROR);
    }

    @Test
    public void aBadRequestIsAnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(400));

        assertFailsWith(AttractionUnavailableException.Reason.HTTP_ERROR);
    }

    @Test
    public void aDroppedConnectionIsReportedAsNoNetwork() {
        server.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        assertFailsWith(AttractionUnavailableException.Reason.NO_NETWORK);
    }

    @Test
    public void anUnreachableHostIsReportedAsNoNetwork() throws Exception {
        server.shutdown();

        assertFailsWith(AttractionUnavailableException.Reason.NO_NETWORK);
    }

    private void assertFailsWith(AttractionUnavailableException.Reason expected) {
        try {
            source.fetchNearby(CENTRE, RADIUS, LIMIT);
            fail("expected " + expected);
        } catch (AttractionUnavailableException failure) {
            assertEquals(expected, failure.getReason());
        }
    }
}
