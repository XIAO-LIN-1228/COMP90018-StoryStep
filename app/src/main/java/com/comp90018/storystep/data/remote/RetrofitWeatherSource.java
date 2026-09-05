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
 * The Retrofit-backed {@link WeatherSource}: one HTTP call, one validated snapshot or one typed
 * failure.
 *
 * <p>The only class in the weather path that touches the network, which is what keeps the caching
 * policy testable without a socket. Its own job is narrow: issue the request, and translate every
 * way it can go wrong into a {@link WeatherUnavailableException.Reason}. A Retrofit
 * {@code IOException} and an HTTP 429 mean very different things to the user, and an untyped
 * failure here would erase that distinction for every layer above.
 */
public final class RetrofitWeatherSource implements WeatherSource {

    private static final String TEMPERATURE_UNIT = "celsius";

    /**
     * Resolves the observation time's zone from the queried coordinates, which is what makes the
     * returned timestamp interpretable. See {@code RemoteWeatherMapper}.
     */
    private static final String TIMEZONE = "auto";

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final WeatherApiService service;

    public RetrofitWeatherSource(WeatherApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public WeatherSnapshot fetch(GeoPoint center, String areaKey, long fetchedAtMillis)
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
            // InterruptedIOException also covers SocketTimeoutException, which is what OkHttp
            // raises when the connect or read timeout elapses.
        } catch (InterruptedIOException timeout) {
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.TIMEOUT, timeout);
        } catch (EOFException | MalformedJsonException unparseable) {
            // Deliberately ahead of the IOException branch. Gson signals an empty body and a body
            // that is not JSON by throwing IOException subclasses from inside the converter, so
            // catching IOException first would report a broken payload to the user as "offline" —
            // an outage they cannot fix by finding a better signal.
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.MALFORMED, unparseable);
        } catch (IOException offline) {
            // What remains really is transport: an unreachable host, a dropped connection, a DNS
            // failure. From the user's side these are all "no usable network right now".
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.NO_NETWORK, offline);
        } catch (JsonParseException malformed) {
            // Well-formed JSON of the wrong shape, for example a number where the object goes.
            throw new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.MALFORMED, malformed);
        }

        if (!response.isSuccessful()) {
            throw new WeatherUnavailableException(response.code() == HTTP_TOO_MANY_REQUESTS
                    ? WeatherUnavailableException.Reason.RATE_LIMITED
                    : WeatherUnavailableException.Reason.HTTP_ERROR);
        }
        WeatherSnapshot snapshot =
                RemoteWeatherMapper.toDomain(response.body(), areaKey, fetchedAtMillis);
        if (snapshot == null) {
            // A 200 whose body is missing what the app needs is not a success. Reporting it as
            // one would put a fabricated temperature on screen.
            throw new WeatherUnavailableException(WeatherUnavailableException.Reason.MALFORMED);
        }
        return snapshot;
    }
}
