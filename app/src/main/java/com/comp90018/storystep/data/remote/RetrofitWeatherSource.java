package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;

import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Response;

/**
 * The Retrofit-backed weather source.
 *
 * <p>It performs one HTTP request and converts network, HTTP and malformed-response
 * failures into typed weather failures.
 */
public final class RetrofitWeatherSource implements WeatherSource {

    private static final String TEMPERATURE_UNIT = "celsius";
    private static final String TIMEZONE = "auto";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final WeatherApiService service;

    public RetrofitWeatherSource(WeatherApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public WeatherSnapshot fetch(GeoPoint center,
                                 String areaKey,
                                 long fetchedAtMillis)
            throws WeatherUnavailableException {

        Call<WeatherDto> call = service.getCurrentWeather(
                center.getLatitude(),
                center.getLongitude(),
                WeatherApiService.CURRENT_FIELDS,
                TEMPERATURE_UNIT,
                TIMEZONE);

        Response<WeatherDto> response;

        try {
            response = call.execute();
        } catch (InterruptedIOException timeout) {
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.TIMEOUT,
                    timeout);
        } catch (EOFException | MalformedJsonException unparseable) {
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.MALFORMED,
                    unparseable);
        } catch (IOException offline) {
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.NO_NETWORK,
                    offline);
        } catch (JsonParseException malformed) {
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.MALFORMED,
                    malformed);
        }

        if (!response.isSuccessful()) {
            int code = response.code();

            throw new WeatherUnavailableException(
                    code == HTTP_TOO_MANY_REQUESTS
                            ? WeatherUnavailableException.Reason.RATE_LIMITED
                            : WeatherUnavailableException.Reason.HTTP_ERROR,
                    null,
                    code,
                    retryAfterMillis(response));
        }

        WeatherSnapshot snapshot = RemoteWeatherMapper.toDomain(
                response.body(),
                areaKey,
                fetchedAtMillis);

        if (snapshot == null) {
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.MALFORMED);
        }

        return snapshot;
    }

    private static long retryAfterMillis(Response<?> response) {
        String value = response.headers().get("Retry-After");

        if (value == null) {
            return -1L;
        }

        try {
            long seconds = Long.parseLong(value.trim());

            return seconds < 0L
                    ? -1L
                    : Math.multiplyExact(seconds, 1_000L);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return -1L;
        }
    }
}