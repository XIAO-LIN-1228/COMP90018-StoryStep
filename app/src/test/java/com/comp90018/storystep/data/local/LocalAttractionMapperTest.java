package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** The Room entity and the domain model must round-trip without losing information. */
public class LocalAttractionMapperTest {

    private static final Attraction ATTRACTION = new Attraction(
            "old-arts",
            "Old Arts Building",
            AttractionCategory.UNIVERSITY_LANDMARK,
            "The 1924 clocktower building.",
            false,
            new GeoPoint(-37.79810, 144.95910));

    @Test
    public void roundTripsThroughTheEntity() {
        Attraction restored = LocalAttractionMapper.toDomain(
                LocalAttractionMapper.toEntity(ATTRACTION));

        assertEquals(ATTRACTION, restored);
    }

    @Test
    public void storesTheCategoryAsAStableLowerCaseCode() {
        AttractionEntity entity = LocalAttractionMapper.toEntity(ATTRACTION);

        assertEquals("university_landmark", entity.category);
    }

    @Test
    public void unknownStoredCategoryFallsBackInsteadOfFailing() {
        AttractionEntity entity = LocalAttractionMapper.toEntity(ATTRACTION);
        entity.category = "retired_category";

        assertEquals(AttractionCategory.CULTURAL_SITE,
                LocalAttractionMapper.toDomain(entity).getCategory());
    }

    @Test
    public void mapsListsInBothDirections() {
        List<AttractionEntity> entities =
                LocalAttractionMapper.toEntities(Collections.singletonList(ATTRACTION));
        List<Attraction> attractions = LocalAttractionMapper.toDomainList(entities);

        assertEquals(1, attractions.size());
        assertTrue(attractions.contains(ATTRACTION));
    }
}
