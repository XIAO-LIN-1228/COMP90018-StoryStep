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
    @androidx.annotation.Nullable private final WikipediaApiService wikipedia;

    /** Summaries are fetched one article at a time, so a few go out together. */
    private static final int SUMMARY_PARALLELISM = 6;

    public OverpassAttractionSource(OverpassApiService service) {
        this(service, null);
    }

    /**
     * @param wikidata optional; when present, places that carry a {@code wikidata} tag are given
     *                 the photograph Wikidata records for them (property P18)
     */
    public OverpassAttractionSource(OverpassApiService service,
                                    @androidx.annotation.Nullable WikidataApiService wikidata) {
        this(service, wikidata, null);
    }

    /**
     * With Wikipedia as well, a place that OpenStreetMap only names gets the opening sentences
     * of its English article as its description. Same rule as the photographs: enrichment only,
     * never a reason for the fetch to fail.
     */
    public OverpassAttractionSource(OverpassApiService service,
                                    @androidx.annotation.Nullable WikidataApiService wikidata,
                                    @androidx.annotation.Nullable WikipediaApiService wikipedia) {
        this.service = Objects.requireNonNull(service, "service");
        this.wikidata = wikidata;
        this.wikipedia = wikipedia;
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
        } catch (InterruptedIOException timeout) {
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.TIMEOUT,
                    timeout);
        } catch (EOFException | MalformedJsonException unparseable) {
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.MALFORMED,
                    unparseable);
        } catch (IOException offline) {
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.NO_NETWORK,
                    offline);
        } catch (JsonParseException malformed) {
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.MALFORMED,
                    malformed);
        }

        if (!response.isSuccessful()) {
            int code = response.code();

            throw new AttractionUnavailableException(
                    code == HTTP_TOO_MANY_REQUESTS
                            || code == HTTP_GATEWAY_TIMEOUT
                            ? AttractionUnavailableException.Reason.RATE_LIMITED
                            : AttractionUnavailableException.Reason.HTTP_ERROR,
                    null,
                    code,
                    retryAfterMillis(response));
        }

        RemoteAttractionMapper.Mapped mapped =
                RemoteAttractionMapper.toDomain(response.body());

        if (mapped.isMalformed()) {
            throw new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.MALFORMED);
        }

        return withPhotographs(mapped.getAttractions());
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

    /**
     * Attaches what Wikidata knows - the photograph and the sitelink count - to every place
     * that names an entity.
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
            if (id != null && !idToIndex.containsKey(id)) {
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
            java.util.Map<String, Integer> popularity;
            java.util.Map<String, String> titles;
            java.util.Map<String, String> descriptions;
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
                popularity = WikidataImages.popularity(response.body());
                titles = WikidataImages.wikipediaTitles(response.body());
                descriptions = WikidataImages.descriptions(response.body());
            } catch (java.io.IOException | RuntimeException ignored) {
                continue;
            }
            java.util.Map<String, String> summaries = summaries(batch, idToIndex, enriched, titles);
            for (String id : batch) {
                Integer index = idToIndex.get(id);
                Integer links = popularity.get(id);
                if (index == null) {
                    continue;
                }
                Attraction place = enriched.get(index);
                if (urls.containsKey(id) || links != null) {
                    place = place.withWikidata(urls.get(id), links == null ? 0 : links);
                }
                if (place.getDescription().isEmpty()) {
                    String intro = summaries.containsKey(id) ? summaries.get(id) : descriptions.get(id);
                    if (intro != null) {
                        place = place.withDescription(intro);
                    }
                }
                enriched.set(index, place);
            }
        }
        return enriched;
    }

    /**
     * The opening paragraph of the English article for each place in the batch that has one and
     * still lacks a description. Fetched a few at a time; a summary that fails or times out is
     * simply absent, and the Wikidata description stands in for it.
     */
    private java.util.Map<String, String> summaries(List<String> batch,
                                                    java.util.Map<String, Integer> idToIndex,
                                                    List<Attraction> places,
                                                    java.util.Map<String, String> titles) {
        java.util.Map<String, String> summaries = new java.util.concurrent.ConcurrentHashMap<>();
        if (wikipedia == null || titles.isEmpty()) {
            return summaries;
        }
        List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<>();
        for (String id : batch) {
            Integer index = idToIndex.get(id);
            String title = titles.get(id);
            if (index == null || title == null || !places.get(index).getDescription().isEmpty()) {
                continue;
            }
            tasks.add(() -> {
                try {
                    retrofit2.Response<WikipediaSummaryDto> response =
                            wikipedia.summary(title.replace(' ', '_')).execute();
                    WikipediaSummaryDto body = response.body();
                    if (response.isSuccessful() && body != null && body.extract != null
                            && !body.extract.trim().isEmpty()) {
                        summaries.put(id, body.extract.trim());
                    }
                } catch (java.io.IOException | RuntimeException ignored) {
                    // Absent, and the Wikidata description stands in.
                }
                return null;
            });
        }
        if (tasks.isEmpty()) {
            return summaries;
        }
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(
                        Math.min(SUMMARY_PARALLELISM, tasks.size()));
        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return summaries;
    }
}
