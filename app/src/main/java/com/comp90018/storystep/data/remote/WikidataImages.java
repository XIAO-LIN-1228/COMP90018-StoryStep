package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads photograph URLs out of a Wikidata entities response.
 *
 * <p>Pure, so the parsing can be tested without a socket. P18 is "image"; its value is a Commons
 * file name, which {@link OsmImageUrls} turns into a scaled {@code Special:FilePath} URL. An
 * entity with no P18, or a malformed one, simply contributes nothing.
 */
public final class WikidataImages {

    static final String IMAGE_PROPERTY = "P18";

    private WikidataImages() {
    }

    /** Wikidata id (e.g. {@code Q1747074}) to image URL, for every entity that has a picture. */
    public static Map<String, String> imageUrls(@Nullable WikidataEntitiesDto response) {
        Map<String, String> urls = new HashMap<>();
        if (response == null || response.entities == null) {
            return urls;
        }
        for (Map.Entry<String, WikidataEntitiesDto.Entity> entry : response.entities.entrySet()) {
            String url = firstImage(entry.getValue());
            if (url != null) {
                urls.put(entry.getKey(), url);
            }
        }
        return urls;
    }

    @Nullable
    private static String firstImage(@Nullable WikidataEntitiesDto.Entity entity) {
        if (entity == null || entity.claims == null) {
            return null;
        }
        List<WikidataEntitiesDto.Claim> claims = entity.claims.get(IMAGE_PROPERTY);
        if (claims == null) {
            return null;
        }
        for (WikidataEntitiesDto.Claim claim : claims) {
            if (claim != null && claim.mainsnak != null && claim.mainsnak.datavalue != null
                    && claim.mainsnak.datavalue.value != null
                    && claim.mainsnak.datavalue.value.isJsonPrimitive()) {
                String url = OsmImageUrls.fromCommonsTag(
                        "File:" + claim.mainsnak.datavalue.value.getAsString());
                if (url != null) {
                    return url;
                }
            }
        }
        return null;
    }

    /**
     * Wikidata id to sitelink count, for every entity in the response. The count of Wikipedia
     * editions that carry an article on a place is the closest free measure of how widely it
     * is known; an entity without sitelinks counts as zero rather than being left out.
     */
    public static Map<String, Integer> popularity(@Nullable WikidataEntitiesDto response) {
        Map<String, Integer> counts = new HashMap<>();
        if (response == null || response.entities == null) {
            return counts;
        }
        for (Map.Entry<String, WikidataEntitiesDto.Entity> entry : response.entities.entrySet()) {
            WikidataEntitiesDto.Entity entity = entry.getValue();
            counts.put(entry.getKey(),
                    entity == null || entity.sitelinks == null ? 0 : entity.sitelinks.size());
        }
        return counts;
    }

    /** The {@code Q...} id inside a {@code wikidata:Q...} source reference, or null. */
    @Nullable
    public static String wikidataId(@Nullable String sourceRef) {
        if (sourceRef == null || !sourceRef.startsWith("wikidata:")) {
            return null;
        }
        String id = sourceRef.substring("wikidata:".length()).trim();
        return id.matches("Q\\d+") ? id : null;
    }
}
