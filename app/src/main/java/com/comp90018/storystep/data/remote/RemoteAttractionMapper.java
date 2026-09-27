package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts Overpass payloads into domain {@link Attraction} objects, discarding records that
 * cannot be trusted.
 *
 * <p>This is the boundary REQ-18.3 is about. Everything past it is treated as sound, so every
 * doubt has to be resolved here: an element with no usable coordinate, no name or no id is
 * dropped, and a payload of nothing but such elements is reported as a service-data problem rather
 * than as "there is nothing here". Malformed remote data must degrade to fewer results, never to a
 * crash and never to a silently emptied screen.
 *
 * <p>Mapping is by rejection rather than by repair. Where OSM says nothing the record keeps
 * {@link IndoorClassification#UNKNOWN} or an empty description; nothing is synthesised to fill a
 * gap, because invented content is worse than an honest absence.
 */
public final class RemoteAttractionMapper {

    /**
     * How close two same-named records must be to be treated as the same place.
     *
     * <p>OSM routinely holds both a node and a building way for one museum, tagged alike and metres
     * apart. Without this they arrive as two rows with different ids, and the user sees the same
     * place twice in the list.
     */
    private static final double DUPLICATE_RADIUS_METERS = 40.0;

    private RemoteAttractionMapper() {
        // Utility class.
    }

    /**
     * The outcome of mapping one response.
     *
     * <p>Three states, because two of them look identical in a list of attractions and must not be
     * confused. A valid response that matched nothing is a legitimate answer worth caching — this
     * area really has no museums — while a response whose records were all unusable is a provider
     * problem, and caching it would blank the screen for a day.
     */
    public static final class Mapped {

        private final List<Attraction> attractions;
        private final boolean malformed;

        private Mapped(List<Attraction> attractions, boolean malformed) {
            this.attractions = attractions;
            this.malformed = malformed;
        }

        /** The usable records, deduplicated. Empty when {@link #isMalformed()}. */
        public List<Attraction> getAttractions() {
            return attractions;
        }

        /** True when the payload could not be trusted; the caller must not cache this as empty. */
        public boolean isMalformed() {
            return malformed;
        }
    }

    /**
     * Maps a whole response.
     *
     * <p>A null response, or one with no {@code elements} field at all, is malformed. A response
     * whose {@code elements} array is present and empty is a valid empty result. A response whose
     * elements are all unusable is malformed too: Overpass does not return records it cannot
     * describe, so a page of them means the body was not the payload it claimed to be.
     */
    public static Mapped toDomain(@Nullable OverpassResponseDto response) {
        if (response == null || response.elements == null) {
            return new Mapped(new ArrayList<>(), true);
        }
        if (response.elements.isEmpty()) {
            return new Mapped(new ArrayList<>(), false);
        }
        List<Attraction> mapped = new ArrayList<>();
        for (OverpassElementDto element : response.elements) {
            Attraction attraction = toDomain(element);
            if (attraction != null) {
                mapped.add(attraction);
            }
        }
        if (mapped.isEmpty()) {
            return new Mapped(new ArrayList<>(), true);
        }
        return new Mapped(deduplicate(mapped), false);
    }

    /**
     * Maps one element.
     *
     * @return the mapped attraction, or {@code null} when the element has no qualified id, no
     *         name, or no coordinate this app can use
     */
    @Nullable
    public static Attraction toDomain(@Nullable OverpassElementDto element) {
        if (element == null || element.tags == null) {
            return null;
        }
        String id = qualifiedId(element);
        if (id == null) {
            return null;
        }
        String name = displayName(element.tags);
        if (name == null) {
            return null;
        }
        GeoPoint location = location(element);
        if (location == null) {
            return null;
        }
        return new Attraction(
                id,
                name,
                category(element.tags),
                description(element.tags),
                indoor(element.tags),
                location,
                AttractionProvenance.OPENSTREETMAP,
                sourceRef(element.tags),
                OsmImageUrls.fromTags(element.tags),
                0,
                facts(element.tags));
    }

    /**
     * A trustworthy label the provider actually supplied.
     *
     * <p>OSM normally uses {@code name}, but some real records carry only an English, official or
     * short name. Keeping those improves coverage without inventing a destination label. A record
     * with none of these still cannot be presented safely and is rejected.
     */
    @Nullable
    private static String displayName(Map<String, String> tags) {
        String name = trimmedTag(tags, "name");
        if (name != null) {
            return name;
        }
        String english = trimmedTag(tags, "name:en");
        if (english != null) {
            return english;
        }
        String official = trimmedTag(tags, "official_name");
        return official != null ? official : trimmedTag(tags, "short_name");
    }

    /**
     * The practical facts OpenStreetMap records when somebody bothered to: opening hours,
     * contact, access, entry fee. Absent far more often than present, and taken exactly as
     * tagged - the UI tidies them for reading rather than this mapper reinterpreting them.
     *
     * <p>{@code contact:*} is the newer convention for the same facts, so each is read from
     * the plain tag first and the contact-namespaced one after.
     */
    private static com.comp90018.storystep.domain.model.PlaceFacts facts(Map<String, String> tags) {
        return new com.comp90018.storystep.domain.model.PlaceFacts(
                trimmedTag(tags, "opening_hours"),
                either(tags, "phone", "contact:phone"),
                either(tags, "website", "contact:website"),
                trimmedTag(tags, "wheelchair"),
                trimmedTag(tags, "fee"));
    }

    private static String either(Map<String, String> tags, String first, String second) {
        String value = trimmedTag(tags, first);
        return value != null ? value : trimmedTag(tags, second);
    }

    /**
     * The stable, provider-qualified identifier: {@code osm:way/26621235}.
     *
     * <p>Qualified because an OSM id is unique only within its object type — node 26621235 and way
     * 26621235 are unrelated places — and prefixed with the provider so that an id can never
     * collide with a seed record's.
     */
    @Nullable
    private static String qualifiedId(OverpassElementDto element) {
        if (element.id == null) {
            return null;
        }
        String type = element.type == null ? "" : element.type.trim().toLowerCase(Locale.ROOT);
        if (!type.equals("node") && !type.equals("way") && !type.equals("relation")) {
            return null;
        }
        return "osm:" + type + "/" + element.id;
    }

    /** Nodes carry a coordinate; ways and relations carry a computed centre. */
    @Nullable
    private static GeoPoint location(OverpassElementDto element) {
        Double latitude = element.lat;
        Double longitude = element.lon;
        if ((latitude == null || longitude == null) && element.center != null) {
            latitude = element.center.lat;
            longitude = element.center.lon;
        }
        if (latitude == null || longitude == null) {
            return null;
        }
        try {
            // GeoPoint already rejects NaN, infinity and out-of-range values; catching its
            // complaint is cheaper than restating the rule and risking the two disagreeing.
            return new GeoPoint(latitude, longitude);
        } catch (IllegalArgumentException unusable) {
            return null;
        }
    }

    /**
     * Derives one of this app's categories from OSM tags.
     *
     * <p>Ordered most specific first, because a place is commonly tagged several ways at once: the
     * Royal Exhibition Building is both {@code historic=building} and {@code tourism=attraction},
     * and the historic reading is the more informative one.
     *
     * <p>{@code UNIVERSITY_LANDMARK} is never produced. OSM has no dependable tag for it, so it
     * stays a classification only the curated seed set uses.
     */
    private static AttractionCategory category(Map<String, String> tags) {
        String tourism = tag(tags, "tourism");
        String historic = tag(tags, "historic");
        String leisure = tag(tags, "leisure");
        String amenity = tag(tags, "amenity");
        String shop = tag(tags, "shop");

        // The everyday kinds first: a cafe inside a historic arcade is still a cafe.
        if ("restaurant".equals(amenity) || "food_court".equals(amenity)
                || "fast_food".equals(amenity)) {
            return AttractionCategory.FOOD;
        }
        if ("cafe".equals(amenity) || "bar".equals(amenity) || "pub".equals(amenity)
                || "ice_cream".equals(amenity)) {
            return AttractionCategory.DRINK;
        }
        if ("marketplace".equals(amenity) || shop != null) {
            return AttractionCategory.SHOPPING;
        }

        if ("museum".equals(tourism) || "gallery".equals(tourism)) {
            return AttractionCategory.MUSEUM;
        }
        if ("artwork".equals(tourism)) {
            return AttractionCategory.PUBLIC_ART;
        }
        if (historic != null) {
            return "memorial".equals(historic) || "monument".equals(historic)
                    ? AttractionCategory.PUBLIC_ART
                    : AttractionCategory.HISTORICAL_BUILDING;
        }
        if ("park".equals(leisure) || "garden".equals(leisure)
                || "nature_reserve".equals(leisure) || "viewpoint".equals(tourism)) {
            return AttractionCategory.PARK;
        }
        return AttractionCategory.CULTURAL_SITE;
    }

    /**
     * Derives the indoor/outdoor classification, leaving it unknown when OSM does not say.
     *
     * <p>The outdoor checks come first. A park pavilion may be tagged {@code building=yes} while
     * the place a visitor experiences is the park, and testing {@code building} first would call
     * a garden indoors.
     */
    private static IndoorClassification indoor(Map<String, String> tags) {
        String leisure = tag(tags, "leisure");
        String tourism = tag(tags, "tourism");
        String historic = tag(tags, "historic");
        String amenity = tag(tags, "amenity");
        String indoorTag = tag(tags, "indoor");
        String building = tag(tags, "building");

        if ("park".equals(leisure) || "garden".equals(leisure)
                || "nature_reserve".equals(leisure)
                || "viewpoint".equals(tourism) || "artwork".equals(tourism)
                || "zoo".equals(tourism) || "theme_park".equals(tourism)
                || "memorial".equals(historic) || "monument".equals(historic)
                || "no".equals(indoorTag)) {
            return IndoorClassification.OUTDOOR;
        }
        if ("yes".equals(indoorTag)
                || "museum".equals(tourism) || "gallery".equals(tourism)
                || "aquarium".equals(tourism)
                || "theatre".equals(amenity) || "arts_centre".equals(amenity)
                || "library".equals(amenity) || "music_venue".equals(amenity)
                || "restaurant".equals(amenity) || "cafe".equals(amenity)
                || "bar".equals(amenity) || "pub".equals(amenity)
                || "food_court".equals(amenity) || tag(tags, "shop") != null
                || (building != null && !"no".equals(building))) {
            return IndoorClassification.INDOOR;
        }
        return IndoorClassification.UNKNOWN;
    }

    /**
     * The record's own readable content, or empty.
     *
     * <p>{@code description} is rare in OSM and {@code inscription} — the text physically on a
     * monument — is the next most useful thing when it is there. When neither exists the record
     * gets an empty description and the UI says so. Nothing is composed from the tags, because a
     * sentence this app wrote is not a fact about the place.
     */
    private static String description(Map<String, String> tags) {
        String description = trimmedTag(tags, "description");
        if (description != null) {
            return description;
        }
        String inscription = trimmedTag(tags, "inscription");
        return inscription != null ? inscription : "";
    }

    /**
     * A reference into the source's wider data, kept for a later enrichment step.
     *
     * <p>This is the "preserve unknown optional fields" half of REQ-18.3. Most fetched records have
     * no description, and these two tags are how a real one would eventually be found; discarding
     * them would mean a schema migration to get them back.
     */
    @Nullable
    private static String sourceRef(Map<String, String> tags) {
        String wikidata = trimmedTag(tags, "wikidata");
        if (wikidata != null) {
            return "wikidata:" + wikidata;
        }
        String wikipedia = trimmedTag(tags, "wikipedia");
        return wikipedia != null ? "wikipedia:" + wikipedia : null;
    }

    /**
     * Collapses records that describe the same place.
     *
     * <p>Two passes, because there are two ways a duplicate arrives. The same qualified id twice is
     * a plain repeat. The harder case is one landmark mapped as both a node and a building way:
     * different ids, same name, a few metres apart. Those are matched by name and proximity, and
     * the way or relation is preferred over the node — an area is the better record, and its
     * centre is a more honest coordinate than a marker someone dropped near a door.
     *
     * <p>Insertion order is otherwise preserved; the repository sorts by distance afterwards.
     */
    private static List<Attraction> deduplicate(List<Attraction> attractions) {
        Map<String, Attraction> byId = new LinkedHashMap<>();
        for (Attraction attraction : attractions) {
            byId.put(attraction.getId(), attraction);
        }

        List<Attraction> kept = new ArrayList<>(byId.size());
        for (Attraction candidate : byId.values()) {
            int existing = indexOfSamePlace(kept, candidate);
            if (existing < 0) {
                kept.add(candidate);
            } else if (prefers(candidate, kept.get(existing))) {
                kept.set(existing, candidate);
            }
        }
        return kept;
    }

    private static int indexOfSamePlace(List<Attraction> kept, Attraction candidate) {
        for (int index = 0; index < kept.size(); index++) {
            Attraction other = kept.get(index);
            if (!other.getName().equalsIgnoreCase(candidate.getName())) {
                continue;
            }
            double metres = GeoCalculator.distanceMeters(
                    other.getLocation(), candidate.getLocation());
            if (metres <= DUPLICATE_RADIUS_METERS) {
                return index;
            }
        }
        return -1;
    }

    /** An area beats a point; between two of a kind the first one seen wins. */
    private static boolean prefers(Attraction candidate, Attraction incumbent) {
        return !isNode(candidate) && isNode(incumbent);
    }

    private static boolean isNode(Attraction attraction) {
        return attraction.getId().startsWith("osm:node/");
    }

    @Nullable
    private static String tag(Map<String, String> tags, String key) {
        String value = tags.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static String trimmedTag(Map<String, String> tags, String key) {
        String value = tags.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
