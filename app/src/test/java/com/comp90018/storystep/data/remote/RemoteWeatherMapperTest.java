package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;

import org.junit.Test;

/**
 * What the provider is allowed to send, and what gets rejected.
 *
 * <p>The mapper is the only defence between an arbitrary JSON payload and a temperature on a
 * screen, so most of these cases are about refusing input rather than accepting it. Anything that
 * gets through here is trusted by everything above.
 */
public class RemoteWeatherMapperTest {

    private static final String AREA_KEY = "-757:2899";
    private static final long FETCHED_AT = 1_700_000_000_000L;

    /** Melbourne in September: UTC+10, so the local time is ten hours ahead of the instant. */
    private static final int UTC_PLUS_TEN = 10 * 3600;

    private static WeatherDto payload(Double temperature, Integer code, String time) {
        WeatherDto dto = new WeatherDto();
        dto.latitude = -37.8;
        dto.longitude = 144.95;
        dto.utcOffsetSeconds = UTC_PLUS_TEN;
        WeatherDto.Current current = new WeatherDto.Current();
        current.temperatureCelsius = temperature;
        current.weatherCode = code;
        current.time = time;
        dto.current = current;
        return dto;
    }

    private static WeatherDto validPayload() {
        return payload(14.3, 61, "2026-09-05T14:00");
    }

    @Test
    public void aCompleteResponseBecomesASnapshot() {
        WeatherSnapshot snapshot =
                RemoteWeatherMapper.toDomain(validPayload(), AREA_KEY, FETCHED_AT);

        assertNotNull(snapshot);
        assertEquals(AREA_KEY, snapshot.getAreaKey());
        assertEquals(WeatherCondition.RAIN, snapshot.getCondition());
        assertEquals(14.3, snapshot.getTemperatureCelsius(), 0.0001);
        assertEquals(FETCHED_AT, snapshot.getFetchedAtMillis());
        assertEquals(WeatherSnapshot.Provenance.NETWORK, snapshot.getProvenance());
    }

    @Test
    public void theLocalObservationTimeIsResolvedThroughTheReportedOffset() {
        // 14:00 local at UTC+10 is 04:00 UTC. Reading the local time as if it were UTC would put
        // the observation ten hours out and make a current reading look half a day old.
        WeatherSnapshot snapshot =
                RemoteWeatherMapper.toDomain(validPayload(), AREA_KEY, FETCHED_AT);

        assertNotNull(snapshot);
        assertEquals(1788580800000L, snapshot.getObservedAtMillis());
    }

    @Test
    public void aMissingOffsetIsReadAsUtc() {
        WeatherDto dto = validPayload();
        dto.utcOffsetSeconds = null;

        WeatherSnapshot snapshot = RemoteWeatherMapper.toDomain(dto, AREA_KEY, FETCHED_AT);

        assertNotNull(snapshot);
        assertEquals(1788616800000L, snapshot.getObservedAtMillis());
    }

    @Test
    public void anAbsentPayloadIsRejected() {
        assertNull(RemoteWeatherMapper.toDomain(null, AREA_KEY, FETCHED_AT));
    }

    @Test
    public void aResponseWithoutTheCurrentBlockIsRejected() {
        WeatherDto dto = validPayload();
        dto.current = null;

        assertNull(RemoteWeatherMapper.toDomain(dto, AREA_KEY, FETCHED_AT));
    }

    @Test
    public void aMissingTemperatureIsRejected() {
        assertNull(RemoteWeatherMapper.toDomain(
                payload(null, 0, "2026-09-05T14:00"), AREA_KEY, FETCHED_AT));
    }

    @Test
    public void nonFiniteTemperaturesAreRejected() {
        // A JSON number can arrive as NaN or Infinity through a lenient parser. Either would pass
        // every later comparison and render as "NaN°C".
        assertNull(RemoteWeatherMapper.toDomain(
                payload(Double.NaN, 0, "2026-09-05T14:00"), AREA_KEY, FETCHED_AT));
        assertNull(RemoteWeatherMapper.toDomain(
                payload(Double.POSITIVE_INFINITY, 0, "2026-09-05T14:00"), AREA_KEY, FETCHED_AT));
    }

    @Test
    public void impossibleTemperaturesAreRejectedRatherThanClamped() {
        assertNull(RemoteWeatherMapper.toDomain(
                payload(-273.15, 0, "2026-09-05T14:00"), AREA_KEY, FETCHED_AT));
        assertNull(RemoteWeatherMapper.toDomain(
                payload(500.0, 0, "2026-09-05T14:00"), AREA_KEY, FETCHED_AT));
    }

    @Test
    public void aMissingOrUnparseableTimestampIsRejected() {
        assertNull(RemoteWeatherMapper.toDomain(
                payload(14.0, 0, null), AREA_KEY, FETCHED_AT));
        assertNull(RemoteWeatherMapper.toDomain(
                payload(14.0, 0, "yesterday afternoon"), AREA_KEY, FETCHED_AT));
    }

    @Test
    public void aUnitTheProviderDidNotHonourIsRejected() {
        // The request asks for Celsius. If the answer says Fahrenheit, reading 57 as Celsius would
        // produce a wrong temperature that still looks like a plausible one.
        WeatherDto dto = validPayload();
        dto.currentUnits = new WeatherDto.CurrentUnits();
        dto.currentUnits.temperature = "°F";

        assertNull(RemoteWeatherMapper.toDomain(dto, AREA_KEY, FETCHED_AT));
    }

    @Test
    public void theRequestedUnitIsAccepted() {
        WeatherDto dto = validPayload();
        dto.currentUnits = new WeatherDto.CurrentUnits();
        dto.currentUnits.temperature = "°C";

        assertNotNull(RemoteWeatherMapper.toDomain(dto, AREA_KEY, FETCHED_AT));
    }

    @Test
    public void anAbsentUnitBlockIsTakenAsTheRequestedUnit() {
        WeatherDto dto = validPayload();
        dto.currentUnits = null;

        assertNotNull(RemoteWeatherMapper.toDomain(dto, AREA_KEY, FETCHED_AT));
    }

    @Test
    public void wmoCodesMapToTheCategoriesTheAppActsOn() {
        assertEquals(WeatherCondition.CLEAR, RemoteWeatherMapper.toCondition(0));
        assertEquals(WeatherCondition.CLEAR, RemoteWeatherMapper.toCondition(1));
        assertEquals(WeatherCondition.CLOUDY, RemoteWeatherMapper.toCondition(2));
        assertEquals(WeatherCondition.CLOUDY, RemoteWeatherMapper.toCondition(3));
        assertEquals(WeatherCondition.CLOUDY, RemoteWeatherMapper.toCondition(45));
        assertEquals(WeatherCondition.CLOUDY, RemoteWeatherMapper.toCondition(48));
        assertEquals(WeatherCondition.RAIN, RemoteWeatherMapper.toCondition(51));
        assertEquals(WeatherCondition.RAIN, RemoteWeatherMapper.toCondition(61));
        // Freezing rain: it falls as liquid, and the only question the app asks is whether the
        // user wants to be indoors.
        assertEquals(WeatherCondition.RAIN, RemoteWeatherMapper.toCondition(67));
        assertEquals(WeatherCondition.RAIN, RemoteWeatherMapper.toCondition(80));
        assertEquals(WeatherCondition.RAIN, RemoteWeatherMapper.toCondition(95));
        assertEquals(WeatherCondition.SNOW, RemoteWeatherMapper.toCondition(71));
        assertEquals(WeatherCondition.SNOW, RemoteWeatherMapper.toCondition(77));
        assertEquals(WeatherCondition.SNOW, RemoteWeatherMapper.toCondition(85));
    }

    @Test
    public void anUnrecognisedOrAbsentCodeIsUnknownNotClear() {
        // The distinction that matters for ranking: a code this app does not understand is not
        // evidence of good weather.
        assertEquals(WeatherCondition.UNKNOWN, RemoteWeatherMapper.toCondition(null));
        assertEquals(WeatherCondition.UNKNOWN, RemoteWeatherMapper.toCondition(4));
        assertEquals(WeatherCondition.UNKNOWN, RemoteWeatherMapper.toCondition(-1));
        assertEquals(WeatherCondition.UNKNOWN, RemoteWeatherMapper.toCondition(999));
    }

    @Test
    public void anUnknownCodeStillProducesAUsableSnapshot() {
        // The temperature is good even when the condition is not understood. Rejecting the whole
        // record would lose information the user can see out of a window anyway.
        WeatherSnapshot snapshot = RemoteWeatherMapper.toDomain(
                payload(14.3, 4, "2026-09-05T14:00"), AREA_KEY, FETCHED_AT);

        assertNotNull(snapshot);
        assertEquals(WeatherCondition.UNKNOWN, snapshot.getCondition());
        assertEquals(14.3, snapshot.getTemperatureCelsius(), 0.0001);
    }
}
