package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.data.AttractionCategoryCodec;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts service payloads into domain {@link Attraction} objects, discarding records that
 * cannot be trusted. Malformed remote data must degrade to "fewer results", never to a crash.
 */
public final class RemoteAttractionMapper {

    private static final AttractionCategory FALLBACK_CATEGORY =
            AttractionCategory.CULTURAL_SITE;

    private RemoteAttractionMapper() {
        // Utility class.
    }

    /**
     * @return the mapped attraction, or {@code null} when a required field is missing or the
     *         coordinate is out of range
     */
    @Nullable
    public static Attraction toDomain(@Nullable AttractionDto dto) {
        if (dto == null
                || isBlank(dto.id)
                || isBlank(dto.name)
                || dto.latitude == null
                || dto.longitude == null) {
            return null;
        }
        GeoPoint location;
        try {
            location = new GeoPoint(dto.latitude, dto.longitude);
        } catch (IllegalArgumentException outOfRange) {
            return null;
        }
        return new Attraction(
                dto.id,
                dto.name,
                AttractionCategoryCodec.fromCode(dto.category, FALLBACK_CATEGORY),
                dto.description == null ? "" : dto.description,
                dto.indoor != null && dto.indoor,
                location);
    }

    /** Maps a response list, silently skipping unusable entries. */
    public static List<Attraction> toDomainList(@Nullable List<AttractionDto> dtos) {
        List<Attraction> attractions = new ArrayList<>();
        if (dtos == null) {
            return attractions;
        }
        for (AttractionDto dto : dtos) {
            Attraction attraction = toDomain(dto);
            if (attraction != null) {
                attractions.add(attraction);
            }
        }
        return attractions;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
