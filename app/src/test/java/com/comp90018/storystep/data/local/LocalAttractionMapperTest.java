package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** The Room entity and the domain model must round-trip without losing information. */
public class LocalAttractionMapperTest {

    private static final Attraction ATTRACTION = Attraction.seeded(
            "old-arts",
            "Old Arts Building",
            AttractionCategory.UNIVERSITY_LANDMARK,
            "The 1924 clocktower building.",
            IndoorClassification.OUTDOOR,
            new GeoPoint(-37.79810, 144.95910));

    private static final long FETCHED_AT = 1_700_000_000_000L;

    @Test
    public void roundTripsThroughTheEntity() {
        Attraction restored = LocalAttractionMapper.toDomain(
                LocalAttractionMapper.toEntity(ATTRACTION, FETCHED_AT));

        assertEquals(ATTRACTION, restored);
    }

    @Test
    public void storesTheCategoryAsAStableLowerCaseCode() {
        AttractionEntity entity = LocalAttractionMapper.toEntity(ATTRACTION, FETCHED_AT);

        assertEquals("university_landmark", entity.category);
    }

    @Test
    public void unknownStoredCategoryFallsBackInsteadOfFailing() {
        AttractionEntity entity = LocalAttractionMapper.toEntity(ATTRACTION, FETCHED_AT);
        entity.category = "retired_category";

        assertEquals(AttractionCategory.CULTURAL_SITE,
                LocalAttractionMapper.toDomain(entity).getCategory());
    }

    @Test
    public void carriesProvenanceAndFetchTimeOntoTheRow() {
        AttractionEntity entity = LocalAttractionMapper.toEntity(ATTRACTION, FETCHED_AT);

        assertEquals("seed", entity.provenance);
        assertEquals("outdoor", entity.indoor);
        assertEquals(FETCHED_AT, entity.fetchedAtMillis);
    }

    @Test
    public void anUnrecognisedIndoorCodeReadsBackAsUnknownRatherThanAsAClaim() {
        AttractionEntity entity = LocalAttractionMapper.toEntity(ATTRACTION, FETCHED_AT);
        entity.indoor = "retired_code";

        assertEquals(IndoorClassification.UNKNOWN,
                LocalAttractionMapper.toDomain(entity).getIndoor());
    }

    @Test
    public void mapsListsInBothDirections() {
        List<AttractionEntity> entities =
                LocalAttractionMapper.toEntities(Collections.singletonList(ATTRACTION), FETCHED_AT);
        List<Attraction> attractions = LocalAttractionMapper.toDomainList(entities);

        assertEquals(1, attractions.size());
        assertTrue(attractions.contains(ATTRACTION));
    }
}
