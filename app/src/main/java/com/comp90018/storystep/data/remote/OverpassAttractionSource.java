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
    @androidx.annotation.Nullable private final WikidataApiService wikidata;

    public OverpassAttractionSource(OverpassApiService service) {
        this(service, null);
    }

    /**
     * @param wikidata optional; when present, places that carry a {@code wikidata} tag are given
     *                 the photograph Wikidata records for them (property P18)
     */
    public OverpassAttractionSource(OverpassApiService service,
                                    @androidx.annotation.Nullable WikidataApiService wikidata) {
        this.service = Objects.requireNonNull(service, "service");
        this.wikidata = wikidata;
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
        return withPhotographs(mapped.getAttractions());
    }

    /**
     * Attaches Wikidata's photograph to every place that names an entity and has no picture yet.
     *
     * <p>OpenStreetMap itself almost never carries an {@code image} tag, but a place with a
     * {@code wikidata} tag usually has a picture on the other end. Batched fifty ids at a time.
     * This is enrichment, not the fetch: on any failure the places come back exactly as mapped,
     * because a missing photograph must never turn a successful fetch into a failed one.
     */
    private List<Attraction> withPhotographs(List<Attraction> attractions) {
        if (wikidata == null) {
            return attractions;
        }
        java.util.Map<String, Integer> idToIndex = new java.util.LinkedHashMap<>();
        for (int i = 0; i < attractions.size(); i++) {
            Attraction attraction = attractions.get(i);
            String id = WikidataImages.wikidataId(attraction.getSourceRef());
            if (id != null && attraction.getImageUrl() == null && !idToIndex.containsKey(id)) {
                idToIndex.put(id, i);
            }
        }
        if (idToIndex.isEmpty()) {
            return attractions;
        }
        List<Attraction> enriched = new java.util.ArrayList<>(attractions);
        List<String> wanted = new java.util.ArrayList<>(idToIndex.keySet());
        for (int start = 0; start < wanted.size(); start += WikidataApiService.MAX_IDS_PER_REQUEST) {
            List<String> batch = wanted.subList(start,
                    Math.min(wanted.size(), start + WikidataApiService.MAX_IDS_PER_REQUEST));
            java.util.Map<String, String> urls;
            try {
                // Joined by hand: this layer stays free of Android types, and the JVM tests that
                // prove the contract would see a stub instead of TextUtils.
                StringBuilder ids = new StringBuilder();
                for (String id : batch) {
                    if (ids.length() > 0) {
                        ids.append('|');
                    }
                    ids.append(id);
                }
                retrofit2.Response<WikidataEntitiesDto> response =
                        wikidata.entities(ids.toString()).execute();
                if (!response.isSuccessful()) {
                    continue;
                }
                urls = WikidataImages.imageUrls(response.body());
            } catch (java.io.IOException | RuntimeException ignored) {
                continue;
            }
            for (java.util.Map.Entry<String, String> found : urls.entrySet()) {
                Integer index = idToIndex.get(found.getKey());
                if (index != null) {
                    enriched.set(index, enriched.get(index).withImageUrl(found.getValue()));
                }
            }
        }
        return enriched;
    }
}
