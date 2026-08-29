package com.comp90018.storystep.data.local;

import com.comp90018.storystep.data.AttractionCategoryCodec;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/** Converts between {@link AttractionEntity} rows and domain {@link Attraction} objects. */
public final class LocalAttractionMapper {

    private static final AttractionCategory FALLBACK_CATEGORY =
            AttractionCategory.CULTURAL_SITE;

    private LocalAttractionMapper() {
        // Utility class.
    }

    public static AttractionEntity toEntity(Attraction attraction) {
        AttractionEntity entity = new AttractionEntity();
        entity.id = attraction.getId();
        entity.name = attraction.getName();
        entity.category = AttractionCategoryCodec.toCode(attraction.getCategory());
        entity.description = attraction.getDescription();
        entity.indoor = attraction.isIndoor();
        entity.latitude = attraction.getLocation().getLatitude();
        entity.longitude = attraction.getLocation().getLongitude();
        return entity;
    }

    public static Attraction toDomain(AttractionEntity entity) {
        return new Attraction(
                entity.id,
                entity.name == null ? "" : entity.name,
                AttractionCategoryCodec.fromCode(entity.category, FALLBACK_CATEGORY),
                entity.description == null ? "" : entity.description,
                entity.indoor,
                new GeoPoint(entity.latitude, entity.longitude));
    }

    public static List<AttractionEntity> toEntities(List<Attraction> attractions) {
        List<AttractionEntity> entities = new ArrayList<>(attractions.size());
        for (Attraction attraction : attractions) {
            entities.add(toEntity(attraction));
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
