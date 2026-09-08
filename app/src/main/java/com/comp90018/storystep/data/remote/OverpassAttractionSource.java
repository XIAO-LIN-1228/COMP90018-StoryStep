package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Response;

/**
 * The Overpass-backed {@link AttractionSource}: one HTTP call, one validated list or one typed
 * failure.
 *
 * <p>The only class in the attraction path that touches the network, which is what keeps the
 * caching policy testable without a socket. Its own job is narrow: issue the request, and
 * translate every way it can go wrong into an {@link AttractionUnavailableException.Reason}.
 *
 * <p>The failure classification mirrors {@code RetrofitWeatherSource}, including the ordering of
 * the catch blocks, which is load-bearing rather than incidental. Two cases are specific to
 * Overpass and are the reason a copy of the weather logic would not have been enough:
 *
 * <ul>
 *   <li>An overloaded Overpass instance answers <b>200 with an HTML error page</b> instead of an
 *       error status. Gson fails on the body, and that arrives here as a malformed payload — which
 *       is the honest report, since the service did not give us data.</li>
 *   <li><b>504</b> is the public instance's usual way of saying it is too busy, so it is typed as
 *       rate limiting alongside 429 rather than as a generic server error. The difference matters:
 *       one tells the user to wait, the other suggests something is broken.</li>
 * </ul>
 */
public final class OverpassAttractionSource implements AttractionSource {

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_GATEWAY_TIMEOUT = 504;

    private final OverpassApiService service;

    public OverpassAttractionSource(OverpassApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public List<Attraction> fetchNearby(GeoPoint center, double radiusMeters, int limit)
            throws AttractionUnavailableException {
        Objects.requireNonNull(center, "center");
        Call<OverpassResponseDto> call = service.query(
                OverpassQueryBuilder.nearbyAttractions(center, radiusMeters, limit));
        Response<OverpassResponseDto> response;
        try {
            response = call.execute();
            // InterruptedIOException also covers SocketTimeoutException, which is what OkHttp
            // raises when the connect or read timeout elapses.
        } catch (InterruptedIOException timeout) {
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.TIMEOUT, timeout);
        } catch (EOFException | MalformedJsonException unparseable) {
            // Deliberately ahead of the IOException branch. Gson signals an empty body and a body
            // that is not JSON — an Overpass HTML error page, for instance — by throwing
            // IOException subclasses from inside the converter, so catching IOException first
            // would report a broken payload to the user as "offline": an outage they cannot fix
            // by finding a better signal.
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.MALFORMED, unparseable);
        } catch (IOException offline) {
            // What remains really is transport: an unreachable host, a dropped connection, a DNS
            // failure. From the user's side these are all "no usable network right now".
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.NO_NETWORK, offline);
        } catch (JsonParseException malformed) {
            // Well-formed JSON of the wrong shape, for example a string where the array goes.
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.MALFORMED, malformed);
        }

        if (!response.isSuccessful()) {
            int code = response.code();
            throw new AttractionUnavailableException(
                    code == HTTP_TOO_MANY_REQUESTS || code == HTTP_GATEWAY_TIMEOUT
                            ? AttractionUnavailableException.Reason.RATE_LIMITED
                            : AttractionUnavailableException.Reason.HTTP_ERROR);
        }

        RemoteAttractionMapper.Mapped mapped = RemoteAttractionMapper.toDomain(response.body());
        if (mapped.isMalformed()) {
            // A 200 whose body holds nothing this app can use is not a success. Returning an empty
            // list would let the caller cache "there is nothing here" for a day on the strength of
            // a broken response.
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.MALFORMED);
        }
        return mapped.getAttractions();
    }
}
