package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Runs one forecast call and translates every way it can fail into a typed reason, the same way
 * {@link RetrofitWeatherSource} does for current conditions.
 */
final class ForecastCalls {

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private ForecastCalls() {
    }

    /** The successful body, which may still be null or incomplete; the mapper decides. */
    @Nullable
    static <T> T execute(Call<T> call) throws ForecastUnavailableException {
        Response<T> response;
        try {
            response = call.execute();
        } catch (InterruptedIOException timeout) {
            throw new ForecastUnavailableException(
                    ForecastUnavailableException.Reason.TIMEOUT, timeout);
        } catch (EOFException | MalformedJsonException unparseable) {
            throw new ForecastUnavailableException(
                    ForecastUnavailableException.Reason.MALFORMED, unparseable);
        } catch (IOException offline) {
            throw new ForecastUnavailableException(
                    ForecastUnavailableException.Reason.NO_NETWORK, offline);
        } catch (JsonParseException | IllegalStateException malformed) {
            // Gson reports a body of the wrong JSON type (e.g. not an object) as IllegalState.
            throw new ForecastUnavailableException(
                    ForecastUnavailableException.Reason.MALFORMED, malformed);
        }

        if (!response.isSuccessful()) {
            int code = response.code();
            throw new ForecastUnavailableException(
                    code == HTTP_TOO_MANY_REQUESTS
                            ? ForecastUnavailableException.Reason.RATE_LIMITED
                            : ForecastUnavailableException.Reason.HTTP_ERROR,
                    null,
                    code,
                    retryAfterMillis(response));
        }
        return response.body();
    }

    private static long retryAfterMillis(Response<?> response) {
        String value = response.headers().get("Retry-After");
        if (value == null) {
            return -1L;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0L ? -1L : Math.multiplyExact(seconds, 1_000L);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return -1L;
        }
    }
}
