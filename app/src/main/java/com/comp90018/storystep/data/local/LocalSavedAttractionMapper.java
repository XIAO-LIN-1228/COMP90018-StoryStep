package com.comp90018.storystep.data.local;

import com.comp90018.storystep.data.AttractionCategoryCodec;
import com.comp90018.storystep.data.AttractionProvenanceCodec;
import com.comp90018.storystep.data.IndoorClassificationCodec;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.SavedAttraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between {@link SavedAttractionEntity} rows and domain {@link SavedAttraction} objects.
 *
 * <p>Separate from {@link LocalAttractionMapper} even though the fields overlap: the two tables
 * are free to diverge, and sharing a mapper is how a change meant for the cache quietly reaches
 * user-owned records.
 */
public final class LocalSavedAttractionMapper {

    private static final AttractionCategory FALLBACK_CATEGORY =
            AttractionCategory.CULTURAL_SITE;

    private LocalSavedAttractionMapper() {
        // Utility class.
    }

    public static SavedAttractionEntity toEntity(Attraction attraction, long savedAtMillis) {
        SavedAttractionEntity entity = new SavedAttractionEntity();
        entity.attractionId = attraction.getId();
        entity.name = attraction.getName();
        entity.category = AttractionCategoryCodec.toCode(attraction.getCategory());
        entity.description = attraction.getDescription();
        entity.indoor = IndoorClassificationCodec.toCode(attraction.getIndoor());
        entity.latitude = attraction.getLocation().getLatitude();
        entity.longitude = attraction.getLocation().getLongitude();
        entity.savedAtMillis = savedAtMillis;
        entity.provenance = AttractionProvenanceCodec.toCode(attraction.getProvenance());
        entity.sourceRef = attraction.getSourceRef();
        entity.imageUrl = attraction.getImageUrl();
        return entity;
    }

    public static SavedAttraction toDomain(SavedAttractionEntity entity) {
        Attraction attraction = new Attraction(
                entity.attractionId,
                entity.name == null ? "" : entity.name,
                AttractionCategoryCodec.fromCode(entity.category, FALLBACK_CATEGORY),
                entity.description == null ? "" : entity.description,
                IndoorClassificationCodec.fromCode(entity.indoor),
                new GeoPoint(entity.latitude, entity.longitude),
                AttractionProvenanceCodec.fromCode(entity.provenance),
                entity.sourceRef,
                entity.imageUrl);
        return new SavedAttraction(attraction, entity.savedAtMillis);
    }

    public static List<SavedAttraction> toDomainList(List<SavedAttractionEntity> entities) {
        List<SavedAttraction> saved = new ArrayList<>(entities.size());
        for (SavedAttractionEntity entity : entities) {
            saved.add(toDomain(entity));
        }
        return saved;
    }
}
