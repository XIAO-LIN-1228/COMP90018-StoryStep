package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Converts an Open-Meteo payload into a {@link WeatherSnapshot}, or rejects it.
 *
 * <p>Rejection is the whole job. Everything on {@link WeatherDto} is nullable, the numbers can be
 * non-finite, the unit can be something other than what was asked for and the timestamp can be
 * unparseable. Anything that reaches the domain layer from here has been checked, so no screen
 * has to defend itself against "NaN°C" or an observation stamped in 1970.
 */
public final class RemoteWeatherMapper {

    /** The unit string this app requests and the only one it will accept. */
    private static final String EXPECTED_TEMPERATURE_UNIT = "°C";

    /** Physically impossible readings; the record is rejected rather than clamped. */
    private static final double MIN_PLAUSIBLE_CELSIUS = -100.0;
    private static final double MAX_PLAUSIBLE_CELSIUS = 70.0;

    private RemoteWeatherMapper() {
        // Utility class.
    }

    /**
     * @param dto             the parsed response, possibly null or incomplete
     * @param areaKey         the {@code WeatherAreaBucket} key the request was made for
     * @param fetchedAtMillis when this app received the response
     * @return the snapshot, or {@code null} when the payload cannot be trusted
     */
    @Nullable
    public static WeatherSnapshot toDomain(@Nullable WeatherDto dto,
                                           String areaKey,
                                           long fetchedAtMillis) {
        if (dto == null || dto.current == null) {
            return null;
        }
        WeatherDto.Current current = dto.current;
        if (current.temperatureCelsius == null) {
            return null;
        }
        if (!isAcceptableUnit(dto.currentUnits)) {
            return null;
        }
        double celsius = current.temperatureCelsius;
        if (Double.isNaN(celsius) || Double.isInfinite(celsius)
                || celsius < MIN_PLAUSIBLE_CELSIUS || celsius > MAX_PLAUSIBLE_CELSIUS) {
            return null;
        }
        long observedAtMillis = parseObservedAt(current.time, dto.utcOffsetSeconds);
        if (observedAtMillis <= 0L) {
            return null;
        }
        return new WeatherSnapshot(
                areaKey,
                toCondition(current.weatherCode),
                celsius,
                observedAtMillis,
                fetchedAtMillis,
                WeatherSnapshot.Provenance.NETWORK);
    }

    /**
     * Accepts the requested unit, and accepts the provider not reporting one.
     *
     * <p>A missing {@code current_units} block is treated as "the provider honoured the
     * {@code temperature_unit=celsius} we asked for". A block that names a <em>different</em> unit
     * is a rejection: that is the provider telling us it ignored the request, and reading those
     * numbers as Celsius would produce a wrong temperature that still looks reasonable.
     */
    private static boolean isAcceptableUnit(@Nullable WeatherDto.CurrentUnits units) {
        if (units == null || units.temperature == null) {
            return true;
        }
        String reported = units.temperature.trim();
        return reported.isEmpty() || EXPECTED_TEMPERATURE_UNIT.equals(reported);
    }

    /**
     * Turns the provider's local timestamp into an instant.
     *
     * <p>{@code timezone=auto} means {@code current.time} is local to the queried coordinates and
     * carries no offset of its own, so it is meaningless without {@code utc_offset_seconds}. When
     * the offset is absent the value is read as UTC, which is what Open-Meteo returns when the
     * timezone parameter is not honoured.
     *
     * @return epoch milliseconds, or 0 when the timestamp is absent or unparseable
     */
    private static long parseObservedAt(@Nullable String localTime,
                                        @Nullable Integer utcOffsetSeconds) {
        if (localTime == null || localTime.trim().isEmpty()) {
            return 0L;
        }
        try {
            LocalDateTime local = LocalDateTime.parse(localTime.trim());
            ZoneOffset offset = utcOffsetSeconds == null
                    ? ZoneOffset.UTC
                    : ZoneOffset.ofTotalSeconds(utcOffsetSeconds);
            long millis = local.toInstant(offset).toEpochMilli();
            // A non-positive instant means the provider sent something near the epoch; the
            // snapshot would reject it anyway, and returning 0 keeps that decision in one place.
            return millis > 0L ? millis : 0L;
            // DateTimeException covers both an unparseable string and an offset outside the
            // +/-18h ZoneOffset accepts.
        } catch (DateTimeException unparseable) {
            return 0L;
        }
    }

    /**
     * Maps a WMO 4677 weather interpretation code, as Open-Meteo publishes it.
     *
     * <p>Grouped by what the app does with it, not by meteorological precision:
     *
     * <ul>
     *   <li>0-1 clear or mainly clear</li>
     *   <li>2-3 partly cloudy and overcast; 45-48 fog — dry, but not sunny</li>
     *   <li>51-67 drizzle and rain, 80-82 showers, 95-99 thunderstorms — liquid</li>
     *   <li>71-77 snow and grains, 85-86 snow showers — frozen</li>
     * </ul>
     *
     * <p>Freezing rain (66-67) counts as rain: it falls as liquid, and the app's only question is
     * whether the user wants to be indoors. An unrecognised or absent code is
     * {@link WeatherCondition#UNKNOWN}, never {@link WeatherCondition#CLEAR}.
     */
    static WeatherCondition toCondition(@Nullable Integer code) {
        if (code == null) {
            return WeatherCondition.UNKNOWN;
        }
        int value = code;
        if (value == 0 || value == 1) {
            return WeatherCondition.CLEAR;
        }
        if (value == 2 || value == 3 || value == 45 || value == 48) {
            return WeatherCondition.CLOUDY;
        }
        if ((value >= 51 && value <= 67)
                || (value >= 80 && value <= 82)
                || (value >= 95 && value <= 99)) {
            return WeatherCondition.RAIN;
        }
        if ((value >= 71 && value <= 77) || value == 85 || value == 86) {
            return WeatherCondition.SNOW;
        }
        return WeatherCondition.UNKNOWN;
    }
}
