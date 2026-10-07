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
    @androidx.annotation.Nullable private final CommonsApiService commons;
    @androidx.annotation.Nullable private final WikidataQueryService wikidataQuery;

    /** How far from a place's coordinate a geotagged photograph may be and still be of it. */
    private static final int NEARBY_PHOTO_RADIUS_M = 80;

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
        this(service, wikidata, wikipedia, null);
    }

    /**
     * With Commons as well, a place that neither Wikidata nor Wikipedia pictures gets the best
     * photograph geotagged within a few tens of metres of it. The last link in the chain, and
     * the one that reaches the memorials and sculptures no article covers.
     */
    public OverpassAttractionSource(OverpassApiService service,
                                    @androidx.annotation.Nullable WikidataApiService wikidata,
                                    @androidx.annotation.Nullable WikipediaApiService wikipedia,
                                    @androidx.annotation.Nullable CommonsApiService commons) {
        this(service, wikidata, wikipedia, commons, null);
    }

    /**
     * With the Wikidata Query Service as well: one request for every photographed item in the
     * area, matched to places by name and distance. Reaches the landmarks whose OpenStreetMap
     * node carries no {@code wikidata} tag, which is most of them.
     */
    public OverpassAttractionSource(OverpassApiService service,
                                    @androidx.annotation.Nullable WikidataApiService wikidata,
                                    @androidx.annotation.Nullable WikipediaApiService wikipedia,
                                    @androidx.annotation.Nullable CommonsApiService commons,
                                    @androidx.annotation.Nullable WikidataQueryService wikidataQuery) {
        this.service = Objects.requireNonNull(service, "service");
        this.wikidata = wikidata;
        this.wikipedia = wikipedia;
        this.commons = commons;
        this.wikidataQuery = wikidataQuery;
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

        return withNearbyPhotographs(
                withAreaPhotographs(withPhotographs(mapped.getAttractions()), center, radiusMeters),
                center, radiusMeters);
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
            java.util.Map<String, WikipediaSummaryDto> summaries =
                    summaries(batch, idToIndex, enriched, titles, urls);
            for (String id : batch) {
                Integer index = idToIndex.get(id);
                Integer links = popularity.get(id);
                if (index == null) {
                    continue;
                }
                Attraction place = enriched.get(index);
                WikipediaSummaryDto summary = summaries.get(id);
                // Wikidata's P18 first; the article's own lead image when there is none.
                String url = urls.get(id);
                if (url == null && summary != null && summary.originalimage != null
                        && summary.originalimage.source != null) {
                    url = summary.originalimage.source;
                }
                if (url != null || links != null) {
                    place = place.withWikidata(url, links == null ? 0 : links);
                }
                if (place.getDescription().isEmpty()) {
                    String intro = summary != null && summary.extract != null
                            && !summary.extract.trim().isEmpty()
                            ? summary.extract.trim() : descriptions.get(id);
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
     * The English article's summary - opening paragraph and lead image - for each place in the
     * batch that has an article and still lacks a description or a photograph. Fetched a few at
     * a time; a summary that fails or times out is simply absent.
     */
    private java.util.Map<String, WikipediaSummaryDto> summaries(
            List<String> batch,
            java.util.Map<String, Integer> idToIndex,
            List<Attraction> places,
            java.util.Map<String, String> titles,
            java.util.Map<String, String> wikidataImages) {
        java.util.Map<String, WikipediaSummaryDto> summaries =
                new java.util.concurrent.ConcurrentHashMap<>();
        if (wikipedia == null || titles.isEmpty()) {
            return summaries;
        }
        List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<>();
        for (String id : batch) {
            Integer index = idToIndex.get(id);
            String title = titles.get(id);
            if (index == null || title == null) {
                continue;
            }
            Attraction place = places.get(index);
            boolean wantsText = place.getDescription().isEmpty();
            boolean wantsImage = place.getImageUrl() == null && !wikidataImages.containsKey(id);
            if (!wantsText && !wantsImage) {
                continue;
            }
            tasks.add(() -> {
                try {
                    retrofit2.Response<WikipediaSummaryDto> response =
                            wikipedia.summary(title.replace(' ', '_')).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        summaries.put(id, response.body());
                    }
                } catch (java.io.IOException | RuntimeException ignored) {
                    // Absent, and the Wikidata description stands in.
                }
                return null;
            });
        }
        runAll(tasks);
        return summaries;
    }

    /**
     * Every photographed Wikidata item in the area, in one query, matched to the places still
     * without a picture by name and distance. Enrichment only: a failed query changes nothing.
     */
    private List<Attraction> withAreaPhotographs(List<Attraction> attractions,
                                                 GeoPoint center,
                                                 double radiusMeters) {
        if (wikidataQuery == null) {
            return attractions;
        }
        List<Attraction> enriched = new java.util.ArrayList<>(attractions);
        List<String> names = new java.util.ArrayList<>();
        List<GeoPoint> wanted = new java.util.ArrayList<>();
        boolean any = false;
        for (Attraction place : enriched) {
            names.add(place.getName());
            GeoPoint at = place.getImageUrl() == null ? place.getLocation() : null;
            wanted.add(at);
            any |= at != null;
        }
        if (!any) {
            return enriched;
        }
        try {
            retrofit2.Response<SparqlResultsDto> response = wikidataQuery.query(
                    WikidataQueryService.photographedItemsAround(center.getLatitude(),
                            center.getLongitude(), Math.ceil(radiusMeters / 100.0) / 10.0))
                    .execute();
            if (response.isSuccessful()) {
                for (java.util.Map.Entry<Integer, String> entry
                        : WikidataAreaPhotos.assign(response.body(), names, wanted).entrySet()) {
                    enriched.set(entry.getKey(),
                            enriched.get(entry.getKey()).withImageUrl(entry.getValue()));
                }
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // No area photographs; the Commons pass still runs.
        }
        return enriched;
    }

    /**
     * A photograph for every place that still has none, from Commons: one request for the
     * five hundred geotagged files nearest the area's centre, assigned to places within
     * {@link #NEARBY_PHOTO_RADIUS_M}. One request only - Commons throttles the geosearch hard,
     * so per-place lookups are not worth attempting. A place with nothing nearby keeps its
     * placeholder.
     */
    private List<Attraction> withNearbyPhotographs(List<Attraction> attractions,
                                                   GeoPoint center,
                                                   double radiusMeters) {
        if (commons == null) {
            return attractions;
        }
        List<Attraction> enriched = new java.util.ArrayList<>(attractions);
        List<GeoPoint> wanted = new java.util.ArrayList<>();
        List<String> names = new java.util.ArrayList<>();
        boolean any = false;
        for (Attraction place : enriched) {
            GeoPoint at = place.getImageUrl() == null ? place.getLocation() : null;
            wanted.add(at);
            names.add(place.getName());
            any |= at != null;
        }
        if (!any) {
            return enriched;
        }
        try {
            retrofit2.Response<CommonsGeosearchDto> response = commons.area(
                    CommonsPhotos.coordinate(center.getLatitude(), center.getLongitude()),
                    (int) Math.ceil(radiusMeters)).execute();
            if (response.isSuccessful()) {
                for (java.util.Map.Entry<Integer, String> entry
                        : CommonsPhotos.assign(response.body(), wanted, names, NEARBY_PHOTO_RADIUS_M)
                        .entrySet()) {
                    enriched.set(entry.getKey(),
                            enriched.get(entry.getKey()).withImageUrl(entry.getValue()));
                    wanted.set(entry.getKey(), null);
                }
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // No Commons photographs; the place keeps its category illustration.
        }
        return enriched;
    }

    /** Runs the tasks a few at a time and waits for all of them; failures are theirs to swallow. */
    private static void runAll(List<java.util.concurrent.Callable<Void>> tasks) {
        if (tasks.isEmpty()) {
            return;
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
    }
}
