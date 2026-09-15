package com.comp90018.storystep.data.local;

import com.comp90018.storystep.data.AttractionCategoryCodec;
import com.comp90018.storystep.data.AttractionProvenanceCodec;
import com.comp90018.storystep.data.IndoorClassificationCodec;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between {@link AttractionEntity} rows and domain {@link Attraction} objects.
 *
 * <p>{@link #toEntity(Attraction, long)} takes the fetch time because provenance and freshness are
 * part of the row, not decoration added afterwards. An earlier version left both for the caller to
 * stamp on, and the one caller that did so was the seeding path — so any other write would have
 * produced rows claiming to come from nowhere at the epoch.
 */
public final class LocalAttractionMapper {

    private static final AttractionCategory FALLBACK_CATEGORY =
            AttractionCategory.CULTURAL_SITE;

    private LocalAttractionMapper() {
        // Utility class.
    }

    /**
     * @param fetchedAtMillis when this row is being written, in epoch milliseconds
     */
    public static AttractionEntity toEntity(Attraction attraction, long fetchedAtMillis) {
        AttractionEntity entity = new AttractionEntity();
        entity.id = attraction.getId();
        entity.name = attraction.getName();
        entity.category = AttractionCategoryCodec.toCode(attraction.getCategory());
        entity.description = attraction.getDescription();
        entity.indoor = IndoorClassificationCodec.toCode(attraction.getIndoor());
        entity.latitude = attraction.getLocation().getLatitude();
        entity.longitude = attraction.getLocation().getLongitude();
        entity.provenance = AttractionProvenanceCodec.toCode(attraction.getProvenance());
        entity.fetchedAtMillis = fetchedAtMillis;
        entity.sourceRef = attraction.getSourceRef();
        return entity;
    }

    public static Attraction toDomain(AttractionEntity entity) {
        return new Attraction(
                entity.id,
                entity.name == null ? "" : entity.name,
                AttractionCategoryCodec.fromCode(entity.category, FALLBACK_CATEGORY),
                entity.description == null ? "" : entity.description,
                IndoorClassificationCodec.fromCode(entity.indoor),
                new GeoPoint(entity.latitude, entity.longitude),
                AttractionProvenanceCodec.fromCode(entity.provenance),
                entity.sourceRef);
    }

    public static List<AttractionEntity> toEntities(List<Attraction> attractions,
                                                    long fetchedAtMillis) {
        List<AttractionEntity> entities = new ArrayList<>(attractions.size());
        for (Attraction attraction : attractions) {
            entities.add(toEntity(attraction, fetchedAtMillis));
        }
        return entities;
    }

    public static List<Attraction> toDomainList(List<AttractionEntity> entities) {
        List<Attraction> attractions = new ArrayList<>(entities.size());
        for (AttractionEntity entity : entities) {
            attractions.add(toDomain(entity));
        }
        return attractions;
    }
}
